# Story Engine

Сюжетный движок для Minecraft Java 1.20.1 / Forge 47.4.28. Структура проекта основана на предоставленном официальном MDK.

## Где что находится

```text
src/main/java/dev/storyengine/forge/  код Minecraft-мода
src/main/resources/                  mods.toml и ресурсы мода
modules/engine-core/                 независимый runtime, события и таймеры
modules/engine-compiler/             JSON → проверенный граф истории
modules/tools-validator/            CLI: validate и run
samples/abandoned-temple/           пример истории
scripts/                            вспомогательные инструменты
build.gradle                        сборка мода
settings.gradle                     подключение внутренних модулей
gradle.properties                   версии Minecraft, Forge и mod id
docs/                               ТЗ, архитектура, статус и исходный MDK
```

Для изменений мода работайте в `src/`, как в обычном MDK. `modules/` сохраняет ядро независимым от Minecraft; мод получает ядро и компилятор в своём JAR.

## Сборка и запуск

Нужен JDK 17. Если по умолчанию выбрана другая Java, в PowerShell задайте `$env:JAVA_HOME='C:\Program Files\Java\jdk-17'`.

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat -p modules build
.\gradlew.bat -p modules :tools-validator:run --args="validate ../../samples/abandoned-temple/stories/temple.json"
.\gradlew.bat -p modules :tools-validator:run --args="run ../../samples/abandoned-temple/stories/temple.json demo:enter_temple"
```

CLI запускается из `modules/tools-validator`, поэтому путь к samples начинается с `../../`. В Linux используйте `bash gradlew`. Первая сборка требует доступа к репозиториям зависимостей. `-p modules` проверяет ядро и CLI без настройки Forge. Готовый мод собирается в `build/libs/`. Перед запуском сервера пользователь самостоятельно принимает Minecraft EULA.

Первый headless-сценарий уже выполняется до `COMPLETED`: заголовок → сообщение → событие входа → задержка → финальное сообщение. Серверные команды и прежний JSON-сценарий проверены в одиночной игре. Kotlin host и локальные .se.kts добавлены, но их работа в новом выпускном JAR ещё требует игровой проверки. Сценарий проверки — docs/GAMEPLAY_TEST.md. Камера и сохранения ещё не подключены. Текущая проверка и ограничения — в [docs/STATUS.md](docs/STATUS.md).

Для сборки из CMD используйте scripts\build.cmd, для запуска клиента — scripts\run-client.cmd.

Описание сюжетной плашки: [docs/NARRATIVE_CHAT.md](docs/NARRATIVE_CHAT.md).



Проверка Kotlin из выпускного JAR (PowerShell, JDK 17): powershell -ExecutionPolicy Bypass -File scripts/test-packaged-kotlin.ps1. Сначала выполните полную сборку. Проверка запускает изолированный host из выпускного JAR без внешнего Kotlin classpath. Библиотеки извлекаются во временный каталог процесса.
