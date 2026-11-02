# ADR-0001: границы bootstrap

Статус: принято для начальной реализации.

Java 17, Minecraft 1.20.1, Forge 47.4.28 (recommended на официальной странице загрузок при проверке). ForgeGradle 6.0.54 и Gradle 8.8 закреплены; совместимость полной сборки ещё требует проверки. Источники: https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html и https://docs.minecraftforge.net/en/1.20.1/gettingstarted/.

На старте два модуля: engine-core (включая первые API-типы) и forge-1.20.1. Compiler, cinematic, persistence выделяются по мере реализации. Ядро не знает Minecraft, Forge, JSON и игровых часов. Forge подключается флагом -PwithForge, чтобы headless-тесты не требовали загрузки Minecraft.

Для тестов выбран JUnit 5.10.2. Wrapper 8.8 взят из соседнего TellingEngine как стандартный инструмент сборки; прикладной код не переносился. Скачать официальный MDK не удалось из-за сетевого ограничения среды.

Рабочий mod id: storyengine. Лицензия до решения владельца — All Rights Reserved, без обещания открытой лицензии. JSON-библиотека, модель команд и persistence будут определены отдельными ADR до их реализации.
