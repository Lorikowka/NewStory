# Стиль кода

Java 17, UTF-8, 4 пробела; packages lowercase, типы PascalCase, методы camelCase, константы UPPER_SNAKE_CASE. Использовать immutable records на границах; проверять null и диапазоны в конструкторах. EditorConfig задан; автоматический форматтер пока не подключён.

Ядро не импортирует net.minecraft и net.minecraftforge: Gradle checkArchitecture проверяет исходники. Тесты JUnit 5 расположены в src/test/java; имена заканчиваются Test. Исправления ошибок сопровождаются регрессионными тестами. Публичные контракты документируются JavaDoc с ограничениями потоков. В тестах не использовать реальные часы и Thread.sleep.
