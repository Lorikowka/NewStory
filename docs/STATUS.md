> Актуализация 2 ноября 2026: независимые модули, включая Kotlin host, прошли Gradle build на JDK 17. Kotlin host, локальные .se.kts, метаданные спикера и GitHub Actions уже есть в коде. Ниже сохранена хронология; ранние списки отсутствующих функций устарели. Актуальный Forge JAR и новый Kotlin-путь ещё требуют проверки. Камера, выборы и snapshots не завершены. Для Gradle используйте JAVA_HOME на JDK 17.
# Статус реализации

## Структура

Мод переведён на структуру предоставленного MDK: корневые src/main/java, src/main/resources, build.gradle и команды runClient/runServer. Wrapper взят из ZIP пользователя. Независимые подпроекты находятся в modules/. Документы и исходный ZIP — в docs/.

## Реализовано

- Immutable StoryDefinition, идентификаторы и состояния Fiber.
- Single-owner runtime, bounded ingress, адресные события, индексированные ожидания, таймеры, FIFO execution budgets, отмена и изоляция ошибки экземпляра.
- Строгий JSON-компилятор: типы, неизвестные поля, дубли, ссылки, capabilities, циклы без yield, лимиты размера/глубины.
- CLI validate/run и пример abandoned-temple: событие → задержка → финал.
- Forge пока содержит только точку регистрации мода. Корневая сборка включает код ядра и компилятора в JAR.

## Проверено

После переноса JUnit Launcher на Java 17 выполнил 9 тестов: 9 успешны. Проверены ожидание/возобновление, повтор события, адресация, отмена, таймеры, fairness, изоляция ошибки, backpressure, 1000 ожидающих экземпляров, диагностика компилятора и неизменяемость определения. CLI выполнил demo:temple до COMPLETED.

Проверки запускались напрямую через Java с локально доступными зависимостями. При компиляции с JAR-classpath Java 17 печатает AccessDeniedException при закрытии ZIP, хотя создаёт классы; это ограничивает воспроизводимость проверки в текущей среде. Попытка использовать распакованные зависимости также не дала чистой компиляции. Успех JUnit не означает успех Gradle build.

Полная Gradle/Forge-сборка остановилась на разрешении net.minecraftforge:forge:1.20.1-47.4.28:userdev в offline-режиме. Minecraft client/server не запускались; CI не запускался. Готовый игровой MVP пока не подтверждён.

## Далее по ТЗ

Проверить сборку Forge с доступными зависимостями, добавить серверные команды и event bridge. Затем реализовать cinematic/dialogue, persistence и полноценный Story Pack (manifest, schemas, resources). Нынешний формат — первый поднабор: message/title, await, delay, jump, finish; он не объявляется полной схемой v1.

## Продолжение реализации

Добавлен серверный bridge: /story list, validate, start, signal, inspect, stop; title/message через стандартные пакеты Minecraft. История включена в ресурсы мода. Выход, смерть и смена измерения отменяют экземпляр. Forge-код ещё не скомпилирован и не проверен в игре; инструкция приёмки — GAMEPLAY_TEST.md.

Добавлены headless CameraTrack (smoothstep, shortest-path yaw, ограничение длительности) и ResourceLeases (атомарное получение набора ресурсов и идемпотентное освобождение). Это основа, а не готовая игровая камера.

## Проверка 31 октября
Внешний build-local.log заканчивается на MCP listLibraries без результата; mapped Forge JAR отсутствует. Добавлен scripts/build.cmd для сборки с видимым выводом и кодом завершения. Исправлен разбор namespaced события в /story signal, добавлен регрессионный тест Brigadier. Полная игровая сборка пока не подтверждена.

## Подтверждённая сборка 31 октября, 10:20

Пользователь выполнил scripts/build.cmd вне ограниченной среды. Артефакт build/libs/storyengine-0.1.0-SNAPSHOT.jar создан; проверено наличие mods.toml, StoryEngineMod, StoryRuntime, StoryCompiler и stories/temple.json. Отчёты Gradle: 13 тестов, 0 failures, 0 errors. Предыдущие ограничения компиляции в среде агента больше не означают, что текущая сборка не проверена.

SHA-256 проверенного JAR: 17A11CBFE8863900490E948E3F1A15743395A6BE313276ACA2C061CF4F2872B7.

Следующая проверка — scripts/run-client.cmd и игровой сценарий из GAMEPLAY_TEST.md. Запуск клиента/dedicated server ещё не подтверждён. Камера в игре, dialogue UI, persistence и полный Story Pack остаются незавершёнными; полный MVP по ТЗ ещё не готов.

## Игровая проверка 31 октября, 10:45–10:51

Проверены run/logs/latest.log и debug.log после запуска пользователем. Minecraft 1.20.1 / Forge 47.4.28 загрузились, integrated server запущен. В 10:50:33 история стартовала и вывела первую реплику; в 10:51:01 появилась финальная реплика. В 10:51:28 inspect подтвердил node=end, state=COMPLETED, effects=3, error=null. Мир сохранён, сервер и клиент завершились штатно.

Первый игровой срез (start → event wait → resume → delay → complete) подтверждён в одиночной игре. Это не проверка dedicated server, отмены во время сцены, игрового renderer камеры или persistence. Полный MVP по исходному ТЗ ещё не завершён.

## Сюжетная плашка и каталог контента
Добавлены клиентский overlay, ограниченная очередь, S2C-протокол и локальные PNG-портреты. Каталог gameDir/storyengine содержит assets/heads и scripts. Пример temple.json копируется только при отсутствии и читается при старте мира. NPC ещё не подключены. Прямая проверка javac создала классы без ошибок исходников, но при закрытии JAR остаётся AccessDeniedException среды. Gradle и визуальная проверка новой версии пока не выполнены. См. NARRATIVE_CHAT.md.

Проверка новых тестов напрямую через JUnit: StoryFilesTest (2) и SignalCommandTest (1), все 3 прошли. Проверено создание каталогов и сохранение пользовательского файла. Это не повторный полный Gradle build и не визуальный тест overlay.

## План Kotlin DSL и первый контракт

Новая спецификация сценариев v0.2 и старые документы сведены в `IMPLEMENTATION_PLAN.md`. Владелец подтвердил Kotlin как язык пользовательских `.se.kts`; Java остаётся языком ядра. Добавлен независимый модуль `engine-script-api` с `StoryNPC` и последовательным builder (`say`, `waitTicks`), который создаёт immutable `StoryDefinition`. Встроенная проверка `/story chatdemo` теперь строится через этот API. `javac` успешно скомпилировал API вместе с ядром; отдельный smoke выполнил `say → wait → say` до COMPLETED. Два новых JUnit-теста прошли: исполнение графа и отклонение неверных аргументов.

## Kotlin host и данные спикера

Добавлен отдельный `engine-script-compiler` на Kotlin 1.9.24: `.se.kts` без обёртки, `npc<Oren>()`, `say`, `wait(2.seconds)`, CLI и пример. Прямой Kotlin-компилятор собрал host; пример выдал VALID и прошёл headless runtime до COMPLETED. Два JUnit-теста host прошли. Gradle в ограниченной среде не стартовал из-за `native-platform.dll`; полный build остаётся на проверку вне среды.

Graph получил типизированный Dialogue с метаданными StoryNPC. Forge-пакет передаёт имя, RGB-цвет и id PNG-головы; старые `engine:message` используют Narrator. После этого изменения прошли 3 теста script API и 2 теста Kotlin host. Прямой `javac` с Forge classpath создал классы без ошибок исходников, но при закрытии внешнего JAR вновь выдал AccessDeniedException. Полный Gradle build и визуальная проверка нового пути в игре ещё не выполнены. Kotlin host пока не упакован в мод и не запускает файлы в мире; см. `KOTLIN_SCRIPTS.md`.

## Подтверждённая сборка Kotlin-прототипа

Пользователь выполнил сборку вне ограниченной среды. Проверены артефакт `build/libs/storyengine-0.1.0-SNAPSHOT.jar` от 31.10.2026 12:06 и XML-отчёты: 21 тест, 0 failures/errors. Этот JAR содержит `engine-script-api`, но ещё не содержит Kotlin host; это успешная сборка предыдущего среза.

## Подключение локальных Kotlin-сценариев — ожидает новую сборку

В коде добавлены загрузка до 32 файлов `gameDir/storyengine/scripts/*.se.kts` при старте мира, id вида `local:chapter_01`, команды list/validate/start, сохранение пользовательского файла и пример `chapter_01.se.kts`. Ошибки отдельных файлов логируются без отмены остальных. Forge Jar-in-Jar должен вложить Kotlin host и его зависимости; Gradle-задача `verifyBundledKotlin` проверяет metadata и классы в выпускном JAR. Прямой `javac` проверил Java-исходники без ошибок, а отдельный smoke загрузил локальный Kotlin-файл и отверг неверное имя. Новая полная сборка и проверка в игре ещё не выполнены.


## Проверка упаковки 2 ноября 2026
Исправлена ошибка: jarJar не включал классы внутренних модулей, которые добавлялись только в slim JAR. Проверка verifyBundledKotlin теперь проходит. scripts/test-packaged-kotlin.ps1 запустил KotlinStoryCompiler из выпускного JAR с вложенными зависимостями: VALID smoke:chapter_01, nodes=4. Это проверка упаковки, не замена игровому запуску Forge. Добавлены тесты отсутствующего каталога и лимита 32 файлов. Контрольная сумма выпускного JAR обновлена.


## Проверка 2 ноября: reload и события

Добавлены /story reload с фоновой компиляцией доверенных Kotlin-файлов, атомарной заменой каталога и сохранением прежнего каталога при ошибке. Активные экземпляры сохраняют свои immutable графы. Фоновый поток использует classloader мода, результат применяется серверным потоком. Добавлены awaitEvent и Forge bridge для engine:block_interact (правый клик основной рукой, только история этого игрока).

runServer прошёл bootstrap Forge и остановился на eula=false; полноценный dedicated-server smoke не выполнен. runClient загрузил OpenGL, Forge и текстуры без зафиксированной ошибки загрузки мода, но захват окна дважды завершился window capture timed out. Мир и сюжетный overlay не проверены. Тестовый клиент после этого закрыт агентом; ненулевой exit code runClient вызван этим завершением, а не зарегистрированным падением мода.

## Итог автоматических проверок 2 ноября 2026

Полная Gradle/Forge build на JDK 17 завершилась успешно: 32 теста, 0 failures/errors. Проверены фоновая reload с прежним графом активного экземпляра, отказ каталога при синтаксической ошибке, удалённые файлы, awaitEvent с адресацией и все три режима реплик. Проверка verifyBundledKotlin прошла. Скрипт test-packaged-kotlin.ps1 с samples/kotlin/dialogue_modes.se.kts запустил компиляцию из выпускного JAR и его вложенных зависимостей: VALID smoke:dialogue_modes, nodes=6. Ручные игровые проверки новых функций остаются в GAMEPLAY_TEST.md.

Проверка текущего Kotlin-файла через /story validate local:id переведена на фоновую компиляцию без активации. Полная локальная сборка прошла: 34 теста, 0 failures/errors. Первый push d9e06d0 прошёл обе GitHub Actions jobs: headless и forge. Для новых изменений удалённый CI запускается отдельно.

## Режимы портретов — проверка 2 ноября
Добавлены CUSTOM_TEXTURE/NONE/FALLBACK с сохранением четырёхаргументного конструктора StoryNPC. NONE убирает иконку и отступ, FALLBACK не читает PNG. Narrative-протокол — v4. Полная локальная build успешна: 35 тестов, 0 failures/errors. Пример portrait_modes.se.kts скомпилирован из выпускного JAR: VALID smoke:portrait_modes, nodes=6. Визуальная проверка и AUTO_RENDER остаются открытыми. Второй коммит 781aae3 прошёл headless и forge на GitHub Actions.

## Игровая проверка: найденные ошибки

При открытии мира все четыре локальных сценария не загрузились с ошибкой kotlin/jvm/internal/Intrinsics. Обычного implementation classpath оказалось недостаточно для Forge development runtime. Добавлен kotlin-scripting-jvm-host в minecraftLibrary с проверкой необходимых транзитивных библиотек.

При вводе /story start local:portrait_modes клиент сообщил Expected whitespace to end one argument на двоеточии. Для start/validate StringArgumentType.word заменён на ResourceLocationArgument; добавлен регрессионный тест namespaced ID и лишних аргументов.

Добавлен ограниченный LRU-кэш PNG-портретов по SHA-256 (32 текстуры). Повторные реплики используют текстуру из кэша; изменение PNG создаёт другой ключ. Clear/stop/logout и вытеснение освобождают ресурсы. Тесты проверяют LRU, замену, идемпотентную очистку и попытку освобождения всех ресурсов при ошибке одного.

Повторный захват окна Minecraft завершился FrameArrived/window capture timed out. Пользователь проверяет игру вручную; исправления требуют нового запуска клиента. Эти изменения ещё не означают успешную визуальную приёмку или реализацию AUTO_RENDER.

Исправления команд, minecraftLibrary и кэша прошли полную локальную Gradle build: 39 тестов, 0 failures/errors. Проверка наличия Kotlin stdlib, scripting host и compiler в minecraftLibrary теперь входит в check. Повторный игровой запуск начат; успешная загрузка Kotlin в мире ещё не подтверждена.
