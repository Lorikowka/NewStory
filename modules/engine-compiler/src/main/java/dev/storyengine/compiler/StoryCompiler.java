package dev.storyengine.compiler;

import com.google.gson.*;
import com.google.gson.stream.*;
import dev.storyengine.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Strict bounded JSON compiler. No class loading, scripting, polymorphic deserialization or I/O effects. */
public final class StoryCompiler {
    public static final int MAX_BYTES = 1_048_576;
    public static final int MAX_NODES = 4096;
    private static CompilationException error(String path, String code, String message) {
        return new CompilationException(path, "COMPILER_" + code, message);
    }
    public StoryDefinition compile(Path file, Set<String> capabilities) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw error(file.toString(), "SIZE", "Reduce file below 1 MiB");
            try { return compile(new String(bytes, StandardCharsets.UTF_8), capabilities); }
            catch (CompilationException ex) { throw error(file + ":" + ex.path(), "INVALID", ex.getMessage()); }
        }
    }
    public StoryDefinition compile(String json, Set<String> capabilities) {
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw error("$", "SIZE", "Reduce file below 1 MiB");
        JsonObject root;
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            root = object(read(reader, 0), "$");
            if (reader.peek() != JsonToken.END_DOCUMENT) throw error("$", "JSON", "Remove trailing data");
        } catch (IOException | IllegalStateException ex) { throw error("$", "JSON", "Invalid strict JSON: " + ex.getMessage()); }
        fields(root, "$", "schemaVersion", "id", "entry", "nodes");
        if (integer(root.get("schemaVersion"), "$.schemaVersion") != 1) throw error("$.schemaVersion", "VERSION", "Use schemaVersion 1");
        StoryId id;
        try { id = new StoryId(string(root.get("id"), "$.id")); }
        catch (IllegalArgumentException ex) { throw error("$.id", "ID", "Use lowercase namespace:path"); }
        String entry = string(root.get("entry"), "$.entry");
        JsonObject rawNodes = object(root.get("nodes"), "$.nodes");
        if (rawNodes.size() < 1 || rawNodes.size() > MAX_NODES) throw error("$.nodes", "SIZE", "Use 1..4096 nodes");
        Map<String, StoryDefinition.Node> nodes = new LinkedHashMap<>();
        for (var pair : rawNodes.entrySet()) {
            String path = "$.nodes." + pair.getKey();
            if (!pair.getKey().matches("[a-zA-Z0-9_-]{1,64}")) throw error(path, "ID", "Use a short alphanumeric node ID");
            JsonObject n = object(pair.getValue(), path);
            fields(n, path, "finish", "next", "await", "delay", "action");
            int kinds = (n.has("finish") ? 1 : 0) + (n.has("await") ? 1 : 0) + (n.has("delay") ? 1 : 0) + (n.has("action") ? 1 : 0);
            if (kinds > 1) throw error(path, "NODE", "Choose exactly one operation");
            StoryDefinition.Node node;
            if (n.has("finish")) {
                if (!n.get("finish").equals(new JsonPrimitive(true)) || n.has("next")) throw error(path, "NODE", "finish must be true and have no next");
                node = new StoryDefinition.Finish();
            } else {
                String next = string(n.get("next"), path + ".next");
                if (n.has("await")) {
                    JsonObject wait = object(n.get("await"), path + ".await");
                    fields(wait, path + ".await", "type");
                    node = new StoryDefinition.Await(string(wait.get("type"), path + ".await.type"), next);
                } else if (n.has("delay")) {
                    long ticks = integer(n.get("delay"), path + ".delay");
                    if (ticks < 1 || ticks > 1_000_000) throw error(path + ".delay", "RANGE", "Use 1..1000000 ticks");
                    node = new StoryDefinition.Delay(ticks, next);
                } else if (n.has("action")) {
                    JsonObject a = object(n.get("action"), path + ".action");
                    fields(a, path + ".action", "type", "value");
                    String type = string(a.get("type"), path + ".action.type");
                    if (!Set.of("engine:message", "engine:title").contains(type)) throw error(path, "ACTION", "Use engine:message or engine:title");
                    if (!capabilities.contains(type)) throw error(path, "CAPABILITY", "Platform must provide " + type);
                    node = new StoryDefinition.Action(type, string(a.get("value"), path + ".action.value"), next);
                } else node = new StoryDefinition.Jump(next);
            }
            nodes.put(pair.getKey(), node);
        }
        if (!nodes.containsKey(entry)) throw error("$.entry", "REFERENCE", "Entry must name an existing node");
        for (var pair : nodes.entrySet()) {
            if (pair.getValue().next() != null && !nodes.containsKey(pair.getValue().next()))
                throw error("$.nodes." + pair.getKey() + ".next", "REFERENCE", "Target node does not exist");
        }
        // Iterative walk avoids stack overflow even on adversarial graphs.
        Set<String> checked = new HashSet<>();
        for (String start : nodes.keySet()) {
            Set<String> path = new HashSet<>(); String current = start;
            while (current != null && !checked.contains(current)) {
                if (!path.add(current)) throw error("$.nodes." + current, "CYCLE", "Insert await or delay into the cycle");
                var n = nodes.get(current);
                current = n instanceof StoryDefinition.Await || n instanceof StoryDefinition.Delay ? null : n.next();
            }
            checked.addAll(path);
        }
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
            return new StoryDefinition(id, hash, entry, nodes);
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > 32) throw error(reader.getPath(), "DEPTH", "Reduce nesting to 32 levels");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject result = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (result.has(key)) throw error(reader.getPath(), "DUPLICATE", "Remove duplicate key " + key);
                    result.add(key, read(reader, depth + 1));
                }
                reader.endObject(); yield result;
            }
            case BEGIN_ARRAY -> {
                JsonArray result = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) result.add(read(reader, depth + 1));
                reader.endArray(); yield result;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new java.math.BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw error(reader.getPath(), "JSON", "Expected JSON value");
        };
    }
    private static JsonObject object(JsonElement e, String path) {
        if (e == null || !e.isJsonObject()) throw error(path, "TYPE", "Expected object");
        return e.getAsJsonObject();
    }
    private static String string(JsonElement e, String path) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw error(path, "TYPE", "Expected string");
        String value = e.getAsString();
        if (value.isBlank() || value.length() > 4096) throw error(path, "SIZE", "Use 1..4096 characters");
        return value;
    }
    private static long integer(JsonElement e, String path) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw error(path, "TYPE", "Expected integer");
        try { return e.getAsBigDecimal().longValueExact(); }
        catch (ArithmeticException ex) { throw error(path, "TYPE", "Expected bounded integer"); }
    }
    private static void fields(JsonObject object, String path, String... allowed) {
        Set<String> names = Set.of(allowed);
        for (String key : object.keySet()) if (!names.contains(key)) throw error(path + "." + key, "FIELD", "Remove unsupported field");
    }
}
