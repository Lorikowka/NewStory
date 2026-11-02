package dev.storyengine.core;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

/** Single-owner runtime; only publish is thread-safe. Tick numbers must strictly increase.
 * Events are addressed to instances. A tick routes events before executing ready fibers;
 * events published before an await is registered are not retained for future waits.
 */
public final class StoryRuntime {
    public record Event(StoryInstanceId instance, String type) {
        public Event { Objects.requireNonNull(instance); Objects.requireNonNull(type); }
    }
    public record View(StoryInstanceId id, StoryId story, String hash, String node,
                       FiberState state, String waitingEvent, long wakeTick, long effects, String error) {}
    public record TickReport(int events, int steps, int ready) {}
    private record Timer(long tick, long order, StoryInstanceId id) {}
    private static final class Instance {
        final StoryInstanceId id;
        final StoryDefinition definition;
        String node;
        FiberState state = FiberState.READY;
        String waitingEvent;
        long wakeTick = -1;
        long effects;
        String error;
        Instance(StoryInstanceId id, StoryDefinition definition) {
            this.id = id; this.definition = definition; this.node = definition.entry();
        }
    }
    private final Thread owner = Thread.currentThread();
    private final StoryPlatform platform;
    private final int maxInstances;
    private final Map<StoryInstanceId, Instance> instances = new LinkedHashMap<>();
    private final ArrayDeque<StoryInstanceId> ready = new ArrayDeque<>();
    private final Map<Event, Instance> waits = new HashMap<>();
    private final PriorityQueue<Timer> timers = new PriorityQueue<>(
            Comparator.comparingLong(Timer::tick).thenComparingLong(Timer::order));
    private final ArrayBlockingQueue<Event> incoming;
    private long tick = -1;
    private long timerSequence;

    public StoryRuntime(StoryPlatform platform, int queueCapacity, int maxInstances) {
        this.platform = Objects.requireNonNull(platform);
        if (maxInstances < 1) throw new IllegalArgumentException("maxInstances");
        this.maxInstances = maxInstances;
        incoming = new ArrayBlockingQueue<>(queueCapacity);
    }
    private void owner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Runtime owner thread required");
    }
    public void start(StoryInstanceId id, StoryDefinition definition) {
        owner(); Objects.requireNonNull(id); Objects.requireNonNull(definition);
        if (instances.size() >= maxInstances) throw new IllegalStateException("RUNTIME_INSTANCE_LIMIT");
        if (instances.containsKey(id)) throw new IllegalArgumentException("Duplicate instance ID");
        instances.put(id, new Instance(id, definition)); ready.add(id);
    }
    /** Returns false when bounded ingress is full; callers must handle backpressure. */
    public boolean publish(Event event) { return incoming.offer(Objects.requireNonNull(event)); }
    public Optional<View> inspect(StoryInstanceId id) {
        owner(); Instance i = instances.get(id);
        return i == null ? Optional.empty() : Optional.of(new View(i.id, i.definition.id(),
                i.definition.contentHash(), i.node, i.state, i.waitingEvent, i.wakeTick, i.effects, i.error));
    }
    public List<View> instances() { owner(); return instances.keySet().stream().map(id -> inspect(id).orElseThrow()).toList(); }
    public boolean cancel(StoryInstanceId id) {
        owner(); Instance i = instances.get(id);
        if (i == null || i.state.isTerminal()) return false;
        clearWait(i); ready.remove(id); i.state = FiberState.CANCELLED; return true;
    }
    /** Explicitly releases retained terminal history and capacity. */
    public boolean forget(StoryInstanceId id) {
        owner(); Instance i = instances.get(id);
        if (i == null || !i.state.isTerminal()) return false;
        instances.remove(id); return true;
    }
    private void clearWait(Instance i) {
        if (i.waitingEvent != null) waits.remove(new Event(i.id, i.waitingEvent));
        if (i.wakeTick >= 0) timers.removeIf(t -> t.id().equals(i.id));
        i.waitingEvent = null; i.wakeTick = -1;
    }
    private void resume(Instance i) { clearWait(i); i.state = FiberState.READY; ready.add(i.id); }
    /** Limits routing, timer delivery and execution separately; FIFO round-robin grants at most
     * perInstanceBudget operations to each instance per tick. No sleeping-fiber polling. */
    public TickReport tick(long number, int eventBudget, int stepBudget, int perInstanceBudget) {
        owner();
        if (number <= tick || eventBudget < 1 || stepBudget < 1 || perInstanceBudget < 1)
            throw new IllegalArgumentException("Monotonic ticks and positive budgets required");
        tick = number;
        List<Event> batch = new ArrayList<>(); incoming.drainTo(batch, eventBudget);
        int deliveredTimers = 0;
        while (!timers.isEmpty() && timers.peek().tick() <= tick && deliveredTimers++ < eventBudget) {
            Timer t = timers.remove(); Instance i = instances.get(t.id());
            if (i != null && i.state == FiberState.WAITING && i.wakeTick == t.tick()) resume(i);
        }
        for (Event event : batch) { Instance i = waits.get(event); if (i != null) resume(i); }
        Map<StoryInstanceId, Integer> counts = new HashMap<>();
        int steps = 0;
        int remaining = ready.size();
        while (steps < stepBudget && !ready.isEmpty() && remaining > 0) {
            Instance i = instances.get(ready.remove());
            int count = counts.getOrDefault(i.id, 0);
            if (count >= perInstanceBudget) { ready.add(i.id); remaining--; continue; }
            counts.put(i.id, count + 1); steps++;
            i.state = FiberState.RUNNING;
            try { step(i); }
            catch (RuntimeException ex) {
                clearWait(i); i.state = FiberState.FAILED;
                i.error = "RUNTIME_ACTION_FAILED: " + ex.getClass().getSimpleName();
            }
            if (i.state == FiberState.RUNNING) { i.state = FiberState.READY; ready.add(i.id); }
            remaining = ready.size();
        }
        return new TickReport(batch.size(), steps, ready.size());
    }
    private void step(Instance i) {
        StoryDefinition.Node node = i.definition.nodes().get(i.node);
        if (node instanceof StoryDefinition.Finish) { i.state = FiberState.COMPLETED; return; }
        if (node instanceof StoryDefinition.Await wait) {
            i.waitingEvent = wait.event(); i.state = FiberState.WAITING;
            waits.put(new Event(i.id, wait.event()), i);
        } else if (node instanceof StoryDefinition.Delay delay) {
            i.wakeTick = Math.addExact(tick, delay.ticks()); i.state = FiberState.WAITING;
            timers.add(new Timer(i.wakeTick, timerSequence++, i.id));
        } else if (node instanceof StoryDefinition.Action action) {
            String key = i.id.value() + ":" + i.effects;
            if (!platform.apply(i.id, key, action.type(), action.value()))
                throw new IllegalStateException("Platform rejected effect");
            i.effects++;
        } else if (node instanceof StoryDefinition.Dialogue dialogue) {
            String key = i.id.value() + ":" + i.effects;
            if (!platform.applyDialogue(i.id, key, dialogue))
                throw new IllegalStateException("Platform rejected dialogue");
            i.effects++;
        }
        i.node = node.next();
    }
}
