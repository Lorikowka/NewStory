# Спецификация v1.0

Текст извлечён из исходного DOCX; таблицы представлены последовательно по ячейкам. При неоднозначности см. source/story_engine_technical_spec_v1.docx.

Сюжетный Minecraft-движок

Техническое задание • Архитектурная спецификация • Техническая дорожная карта

Проектный документ v1.0 · 30 октября 2026 · Статус: базовая спецификация для реализации

Стартовая платформа: Minecraft Java 1.20.1 / Minecraft Forge 47.x / Java 17. Рабочее название: Story Engine (переименуем отдельно).

1. Цель, границы и итоговый продукт

Цель: создать независимый Java-движок исполнения сюжетов с приоритетом кинематографических сцен и адаптером Minecraft Forge 1.20.1. Сценарии должны описываться вне Java-кода мода и переиспользоваться без переписывания при портировании на совместимый платформенный адаптер.

Конечные артефакты MVP: (1) устанавливаемый Forge JAR; (2) чистое Java-ядро; (3) формат и компилятор Story Pack; (4) камера, диалоги и базовый timeline; (5) сохранения; (6) CLI-валидатор; (7) тесты и CI; (8) пример истории «Заброшенный храм»; (9) руководство пользователя и разработчика.

Вне MVP: визуальный редактор, универсальная NPC-анимационная система, сложные ветвящиеся кооперативные катсцены, маркетплейс, произвольный пользовательский Java-код, мультиверсионные сборки. Это кандидаты для Beta/1.0.

2. Зафиксированный технологический стек

Область

Решение

Примечание

Язык / VM

Java 17 / 64-bit JVM

Java 17 обязателен для Forge 1.20.1

Mod loader

Minecraft Forge 47.x

Точную версию закрепить в gradle.properties после проверки MDK

Сборка

Gradle Wrapper + ForgeGradle 6

Версии плагинов фиксируются; wrapper коммитится

Маппинги

Official Mojang mappings

Сопоставление API через ForgeGradle

Сериализация сюжетов

JSON + JSON Schema + строгая валидация

Конкретную библиотеку выбрать в ADR после сравнения

Тесты

JUnit 5, Forge GameTest, smoke-test dedicated server

Headless tests без Minecraft

Логи

SLF4J facade (или совместимая абстракция)

В core нет импорта логгера Forge

Форматирование

Spotless + google-java-format (кандидат)

Закрепить версию инструмента

Статический анализ

Checkstyle/SpotBugs/ArchUnit (кандидаты)

Подключать поэтапно, без ложной строгости

Профилирование

JFR, JMH для микробенчмарков

Полные измерения только в реальном сервере

CI

GitHub Actions

Java 17; tests, architecture checks, build, smoke

Запрещены обязательные JNI, Rust/C++ библиотеки, привязка core к Minecraft и незаявленный remote-code execution.

3. Архитектура и зависимости

story-pack → compiler → immutable StoryDefinition → runtime

runtime → platform-contract ← forge-adapter

runtime → cinematic-contract ← cinematic-client/forge-adapter

forge-adapter → Minecraft / Forge API

Направление зависимостей строго однонаправленное. Core, compiler, contract и headless tests не содержат net.minecraft.* и net.minecraftforge.*. Вход в runtime происходит через событийный адаптер и команды; выход — через типизированные эффекты и интерфейсы платформы.

Модуль

Ответственность

Не должен делать

engine-api

Публичные типы, ID, результаты, контракты

Импортировать Forge или API Minecraft

engine-core

Instance/Fiber, runtime loop, routing, scheduling, state

Парсить JSON или выполнять Minecraft I/O

engine-compiler

Разбор, схемы, ссылки, типы, граф IR

Запускать игровые действия

engine-persistence

Snapshots, journal/checkpoint, migrations

Сериализовать произвольные Java-объекты

engine-cinematic

SceneDefinition, tracks, cues, gates, leases

Напрямую рендерить Minecraft

platform-contract

Игровые capabilities, platform commands

Знать детали Forge

forge-1.20.1

Event bridge, game world, network, camera

Содержать сюжетные правила

tools-validator

CLI валидация Story Pack

Требовать запущенную игру

samples

Демонстрационные истории

Быть зависимостью runtime

4. Доменные сущности и жизненный цикл

Сущность

Описание / инвариант

StoryDefinition

Неизменяемый компилированный граф; id, schemaVersion, contentHash, entryNode

StoryInstance

Отдельное прохождение: instanceId, scope, participants, variables, fibers

ExecutionFiber

Логический поток (не Thread): program counter, status, wait descriptor

StoryEvent

Типизированное событие: type, source, timestamp/sequence, payload

Action

Чистая декларация + обработчик; типы и capability requirements

WaitDescriptor

Сериализуемое ожидание события, тика, сигнала или join

SceneInstance

Воспроизведение сцены, scene clock, track states, ownership

ResourceLease

Временное право владения камерой, вводом, NPC или UI

StorySnapshot

Версионированное сохранение состояния исполнения и ссылок

Состояния Fiber: READY, RUNNING, WAITING, COMPLETED, FAILED, CANCELLED. Допустимые переходы определяются таблицей состояний и тестами; переход из terminal-state обратно в RUNNING запрещён. Незавершённые действия с внешними эффектами имеют idempotency key либо явную политику повторения.

5. Runtime: строгая семантика

Серверный tick принимает bounded batch входных событий и доставляет их через EventRouter; внешние потоки не мутируют состояние напрямую.

Готовые Fiber попадают в FIFO/priority ReadyQueue; fairness обеспечивается лимитом шагов на экземпляр за тик.

Шаг выполняет ровно одну ограниченную логическую операцию и возвращает Continue / Suspend / Finish / Fail.

Ожидание регистрируется до передачи управления; снятие подписки, отмена и завершение должны быть идемпотентны.

Периодические события обрабатываются Scheduler с приоритетной очередью, а не сканированием всех сценариев.

Бесконечные переходы без yield пресекаются execution budget; ошибочная история изолируется от других.

Порядок внутри одного тика фиксируется: ingest → timers → route → execute → commit state/effects → persist dirty state.

Важное ограничение: external side effects не являются атомарной транзакцией с сохранением. Для операций вроде выдачи предмета задаётся idempotency/confirmation policy. Нельзя обещать exactly-once без соответствующей модели платформенных эффектов.

6. Публичные интерфейсы — проект контракта

public interface StoryRuntime {

    StartResult start(StoryId id, StartOptions options);

    void publish(StoryEvent event);

    TickReport tick(long tickNumber, ExecutionBudget budget);

    CancelResult cancel(StoryInstanceId instanceId, CancellationReason reason);

    Optional<StorySnapshot> snapshot(StoryInstanceId instanceId);

}



public interface StoryPlatform {

    CapabilitySet capabilities();

    EffectResult apply(PlatformEffect effect);

    PlatformClock clock();

}



public interface ActionHandler<T extends ActionArguments> {

    ActionType<T> type();

    StepResult execute(ActionContext ctx, T args);

}



public interface StoryCompiler {

    CompilationResult compile(StoryPackSource source, CapabilitySet capabilities);

}

Это концептуальные сигнатуры; ADR должен решить, какие операции принимаются синхронно, а какие через очередь команд. Публичные методы документируются с инвариантами, семантикой ошибок, владением памятью и гарантиями потоков.

7. Сценарии: формат, безопасность, совместимость

Story Pack — каталог или архив с manifest.json, scenes/, stories/, dialogs/ и assets/. Manifest: packId, author, version, schemaVersion, minEngineVersion, requiredCapabilities, optionalDependencies, resourceReferences. Все ссылки имеют namespace и разрешаются на этапе компиляции.

{

  "schemaVersion": 1,

  "id": "demo:temple",

  "entry": "intro",

  "nodes": {

    "intro": {"action": {"type": "engine:dialogue", "dialog": "demo:intro"}, "next": "wait"},

    "wait": {"await": {"type": "engine:player_enter_area", "area": "demo:temple"}, "next": "end"},

    "end": {"finish": true}

  }

}

Компилятор проверяет схему и типы параметров, отсутствие циклов без yield, существование target nodes, дубликаты идентификаторов, права доступа к действиям, наличие capabilities, допустимые ресурсы, лимиты размеров. Ошибка указывает файл, JSON-path, код диагностики и предлагаемый способ исправления. Никаких eval, скриптов JavaScript или произвольной загрузки классов из Story Pack.

Схема v1 фиксируется после MVP. Новые поля по возможности обратно совместимы; breaking changes требуют нового schemaVersion и мигратора. Поведение при неподдерживаемом пакете: понятная ошибка, без частичной активации.

8. Кинематографическая подсистема

Кадровые параметры (camera pose, FOV, easing) сэмплируются клиентом на RenderClock; сервер авторитетен по фазам/переходам. SceneClock поддерживает pause/resume и детерминированную выборку по локальному времени. Три типа дорожек: continuous tracks (позиция/поворот/FOV), cue tracks (звук, сигнал), gate tracks (диалог, ожидание, подтверждение).

Cue нельзя безусловно запускать повторно при seek/reconnect. Каждая cue имеет идентификатор и политику replay. Многопользовательская сцена начинается через prepare → ready → start с таймаутом; при пропуске клиента применяется отказ/деградация по политике сценария.

ResourceManager централизованно выдаёт lease на камеру, пользовательский ввод, actor control. При cancel, timeout, death, dimension change, logout или exception releaseAll должен выполняться гарантированно; клиент также имеет fail-safe восстановления стандартной камеры.

9. Forge-адаптер и networking

Forge bus listeners преобразуют события в доменные StoryEvent. Встроенный командный интерфейс администратора: /story list, /story validate, /story start, /story stop, /story inspect, /story reload (позднее). Серверные изменения мира происходят на game thread; сетевые handlers переключаются на правильный поток через enqueueWork, а не мутируют игровые сущности в Netty thread.

SimpleChannel: протокол с явной версией. Пакеты MVP: ScenePrepare, SceneReady, SceneStart, SceneControl, SceneStop, DialogueOpen, DialogueChoice, StorySync. Каждое сообщение несёт sceneInstanceId/sequence, имеет ограничение размера, проверку направления и авторизации. Клиентский выбор не принимается без серверной проверки состояния и принадлежности участника.

На dedicated server не должен загружаться клиентский renderer-код. Стороны разделены по Dist и classloading boundaries. См. документацию Forge по SimpleImpl.

10. Состояние, сохранение и восстановление

Версионированные snapshots содержат definition hash, instanceId, fiber states, wait descriptors, variables, participants и checkpoint. Persistent State хранится сервером; клиентские визуальные состояния воспроизводятся из синхронизированной декларации сцены. Атомарная запись snapshot (temp + replace), контроль целостности и резерв предыдущего состояния. Правила для прерванных сцен: отменить и восстановить управление либо восстановить с безопасной контрольной точки (по настройке).

Hot reload запрещает бесконтрольную подмену определения под запущенным StoryInstance. Разрешены три стратегии: применять только к новым инстансам, explicit migration, controlled restart. MVP использует первую.

11. Производительность и метрики

Критерий

Целевое требование / проверка

Ожидающие Fiber

Без per-fiber polling; только таймеры и адресованные события

Latency основного runtime

Целевой p99 < 1 мс на тик в согласованном эталонном профиле (не обещание без бенчмарка)

Нагрузка MVP

Тесты с 100 / 1000 ожидающих инстансов, 100 событиями/тик; профилирование

Аллокации

Профилировать и устранять необоснованные allocations; не оптимизировать вслепую

Безопасность сервера

Execution budget и bounded queues; нет бесконечных циклов без yield

Инициализация

Никакого тяжелого IO на игровом тике; компиляция на background worker со safe publish

Камера

Плавное движение client-side; визуальная оценка 60 FPS при нормальной среде

Сетевые пакеты

Не стримить transform каждый тик при наличии локального timeline

Стенд, CPU, JVM, число игроков, TPS, прогрев, длительность, количество сценариев и процентиль фиксируются в отдельной спецификации BENCHMARKS.md. Микробенчмарки не подменяют испытаний сервера.

12. Безопасность, ошибки, observability

Любые внешние Story Packs считаются недоверенными; ограничение размера, глубины, числа событий, сложности условий и памяти.

Ошибки нормализуются кодами: COMPILER_*, RUNTIME_*, PLATFORM_*, NETWORK_*, PERSISTENCE_*. Логи указывают packId/storyId/instanceId/nodeId.

Ошибка одного инстанса не валит сервер и не оставляет камеру/ввод заблокированными. Ошибки JVM уровня OutOfMemoryError не объявляются полностью обрабатываемыми.

Никаких паролей, токенов и персональных данных в логах. Админские команды имеют permission checks.

События сети защищены от replay/duplicate, имеют ограниченный payload и серверную проверку входных данных.

13. Правила написания кода

Стиль: Java 17, 4 пробела, UTF-8, единые правила форматтера, package names в lowercase, типы PascalCase, методы camelCase, константы UPPER_SNAKE_CASE.

SOLID и простые зависимости: композиция вместо многоэтажных иерархий; immutable DTO/records для данных и явные интерфейсы на границах.

Запрещены глобальные mutable singleton в core, скрытая зависимость от игрового тика, reflective hacks в runtime, Thread.sleep в сценарном коде.

Методы делают одну вещь; публичные API снабжаются JavaDoc, гарантиями потокобезопасности и типизированными ошибками.

Не добавлять модуль без конкретной границы ответственности и тестов; каждая зависимость обосновывается ADR.

Исключения не проглатываются. Для ожидаемых ошибок — Result/typed exceptions; для инвариантов — fail-fast с контекстом.

Проверять nullability, ограничения чисел, offsets и timestamps; избегать магических констант.

Время, случайность и I/O абстрагируются для тестируемости; никакой системный clock внутри логики сценария.

Каждый bugfix сопровождается регрессионным тестом; perf-изменение — замером до и после.

PR: небольшие осмысленные коммиты, описание мотивации, влияния на API и способа проверки; breaking changes требуют ADR и миграционных заметок.

14. Автоматические проверки и release gates

Проверка

Условие прохождения

Build

./gradlew clean build проходит на Java 17

Unit tests

Тесты runtime, event routing, scheduler, parser, resource leases зелёные

Architecture

Запрет импортов net.minecraft/net.minecraftforge в core/compiler; отсутствие циклов модулей

Integration

Forge dev client + dedicated server запускаются; команды и пакеты работают

Regression

Сценарии на ошибки переходов, double event, cancel, reconnect, timeout

Compatibility

Story Pack из фиксированного набора samples проходит validate без изменений

Performance

Результаты benchmark сохранены; отсутствие регрессии сверх согласованного порога

Release

JAR, changelog, версия протокола, лицензия, docs, checksums, известные ограничения

15. Техническая дорожная карта и критерии приёмки

00 — Спецификация и ADR

Работы: Утвердить требования, доменные сущности, инварианты, Scope MVP, contracts, threat model.

Артефакт: SPEC.md, ARCHITECTURE.md, ADR-0001..; тестовые сценарии описаны.

Критерий приёмки: Ни один критичный механизм не имеет двух противоречивых источников истины; приоритеты P0/P1/P2 согласованы.

01 — Bootstrap

Работы: Forge MDK 1.20.1, Gradle multi-project, Java toolchain 17, test runner, CI, formatting.

Артефакт: Скелет репозитория и пустой Forge мод.

Критерий приёмки: ./gradlew build и тест dedicated server smoke проходят с нуля на чистой машине/CI.

02 — Headless Runtime

Работы: Сущности, scheduler, event router, fiber lifecycle, budgets, fake platform.

Артефакт: Детерминированный core без Minecraft.

Критерий приёмки: Unit тест: start→wait→resume→finish; concurrent fibers, timeout, cancellation, budget exhaustion, ordering.

03 — Compiler & Story Pack

Работы: Manifest, JSON schema, validator, cross-ref resolution, IR, errors.

Артефакт: Сценарий считывается из JSON, валидируется и запускается.

Критерий приёмки: Невалидные ссылки и типы выдаются с file/path; циклы без yield отклоняются; samples проходят.

04 — Forge Bridge

Работы: Серверный адаптер, игровой event bridge, команды, basic networking, integration tests.

Артефакт: История запускается по команде и событию в Minecraft.

Критерий приёмки: Dedicated server + client; player_enter_area; нет client-only classes on server.

05 — Cinematic MVP

Работы: Scene clock, camera track interpolation, cues, gates, leases, basic dialogue UI.

Артефакт: Катсцена «Заброшенный храм».

Критерий приёмки: Камера плавна; dialogue gate; skip/cancel; disconnect возвращает контроль; нет повторной выдачи cue.

06 — Persistence

Работы: Snapshots, persistence versioning, safe restore, disconnect/reconnect.

Артефакт: Прогресс переживает перезапуск.

Критерий приёмки: Тесты atomic snapshot, migration rejection, corrupted save fallback, no duplicate external side effects.

07 — Developer tools

Работы: /story inspect, validate, trace, diagnostics, CLI validator.

Артефакт: Стандартная диагностика историй.

Критерий приёмки: Ошибки содержат pack/story/node; команда inspect не блокирует сервер; разрешения работают.

08 — Reliability & performance

Работы: JFR/JMH, нагрузочные испытания, fuzz parsers, GameTests, CI gates.

Артефакт: Измеренные метрики и устранение major defects.

Критерий приёмки: Согласованные бюджеты соблюдены; воспроизводимый отчёт benchmarks; нет блокирующих P0/P1.

09 — Release MVP

Работы: Документация и samples, changelog, release build, compatibility matrix.

Артефакт: MVP JAR и комплект разработчика.

Критерий приёмки: Чистая установка Forge 1.20.1, dedicated/server + client; полный демо-сценарий от триггера до финала.

10 — Beta

Работы: Advanced timeline, parallel actor tracks, editor prototype, plugin API, migrations.

Артефакт: Расширенное authoring и катсцены.

Критерий приёмки: API tests, backward compatible story format, editor↔IR roundtrip.

11 — Version port

Работы: Второй целевой адаптер, capability matrix, dual-version CI.

Артефакт: Поддержка ещё одной версии Minecraft.

Критерий приёмки: Одинаковый Story Pack проходит тестовый набор без изменений; различия задокументированы.

16. Приоритеты backlog MVP

Приоритет

Обязательно

Примечание

P0

Независимость ядра; execution state; bounded tick; компилятор и базовые события; Forge bridge; базовая камера/диалог; серверная безопасность

Без этого MVP не существует

P1

Persistence, logging/diagnostics, cancel/recovery, Story Pack validation, CLI

Обязательно до публичного MVP

P2

Визуальный редактор, сложные параллельные timelines, расширенные NPC animation, cross-loader

После MVP

17. Риски и способы снижения

Риск

Митигирование

Взаимная блокировка действий/камер

Lease-based ownership, timeout, finally-release, tests

Просадки TPS

Budget, bounded queues, event indexing, async compile, профилировка

Несовместимость Forge версий

Platform contract + capability tests + отдельный адаптер

Сложность сохранений с side effects

Idempotency keys, checkpointing, explicit recovery semantics

Слишком ранняя разработка редактора

IR-first, CLI-first, editor после стабильной схемы

Слишком много Gradle модулей

Начать с 4–6, разделять только при появлении реальной причины

Нестабильный plugin API

Internal API до Beta, semantic versioning с 1.0

18. Стартовая структура репозитория

story-engine/

  settings.gradle

  gradle.properties

  build.gradle

  gradle/wrapper/

  engine-api/

  engine-core/

  engine-compiler/

  engine-cinematic/

  forge-1.20.1/

  tools-validator/

  samples/abandoned-temple/

  docs/{SPEC,ARCHITECTURE,STORY_FORMAT,BENCHMARKS}.md

  docs/adr/

  .github/workflows/ci.yml

На bootstrap можно объединить engine-api и engine-core, а compiler и validator держать в общих тестовых исходниках; структура выше — целевая без излишнего дробления.

19. Definition of Done и первые задачи

Функция Done, только если: API задокументирован; тесты воспроизводимы; нет неверных зависимостей; учтена отмена/сбой; добавлены диагностика и changelog для пользовательских изменений; CI зелёный. Release Done: демонстрация проходит на чистом dedicated server, опубликованы JAR и контрольные суммы.

Создать ADR-0001: Forge 1.20.1, Java 17, headless core; закрепить модульные границы.

Получить официальный Forge 1.20.1 MDK и зафиксировать конкретную версию 47.x, wrapper, toolchain.

Создать Gradle skeleton; запустить runClient и runServer; настроить GitHub Actions.

Создать первые типы StoryId, StoryDefinition, StoryInstanceId, FiberState, StepResult.

Написать FakePlatform и тест «start→event wait→resume→complete».

Добавить ArchUnit/проверку запрещённых импортов и CODE_STYLE.md.

20. Источники и решения для уточнения

Официальная документация Forge 1.20.1: https://docs.minecraftforge.net/en/1.20.1/gettingstarted/

Документация Forge SimpleImpl: https://docs.minecraftforge.net/en/1.20.x/networking/simpleimpl/

Загрузки Forge 1.20.1: https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html

Неутверждённые детали: конкретный Forge 47.x patch, библиотека JSON, мод id и окончательное название, лицензия, контракт multiplayer party scope, формат сериализации snapshot, численные пороги нагрузочного стенда. Решения принимаются отдельными ADR перед соответствующим этапом.