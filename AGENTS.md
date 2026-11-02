# Repository Guidelines

## Project Structure & Module Organization

Forge integration lives in src/main/java and src/main/resources. Independent modules live in modules/: engine-core, engine-compiler, engine-script-api, engine-script-compiler, and tools-validator. Tests live in each project under src/test. Documentation is in docs/ and examples in samples/. Preserve user content in run/storyengine and Minecraft worlds in run/saves.

## Build, Test, and Development Commands

Use JDK 17 and the Gradle 8.8 wrapper. Set JAVA_HOME before invoking Gradle. Run .\gradlew.bat -p modules build for independent modules, .\gradlew.bat build for Forge and packaging checks, and .\gradlew.bat runClient for manual gameplay verification. First builds need network access. See README.md.

## Coding Style & Naming Conventions

No language, formatter, or linter has been selected. Follow the conventions of the chosen language and configure formatting before substantial implementation begins. Use consistent indentation within each file, descriptive identifiers, and filenames that reflect module responsibilities. Keep changes focused; avoid mixing feature work with unrelated formatting changes.

## Testing Guidelines

JUnit 5 is configured; no coverage threshold is established. Add automated tests alongside new behavior and document how to run them. Use descriptive test names that identify the behavior and expected outcome. Cover normal operation, boundary conditions, and relevant failure cases. Record any manual verification steps when automated checks are unavailable.

## Commit & Pull Request Guidelines

No Git metadata or commit history is available, so existing message conventions cannot be inferred. Use concise, imperative commit subjects, such as `Add initial project configuration`. Keep each commit focused on a coherent change.

Pull requests should explain the purpose, summarize behavior changes, and report validation performed. Link related issues when available and include screenshots for visible interface changes. Identify configuration changes or new dependencies explicitly.

## Security & Configuration

Keep credentials and private configuration out of version control. When configuration becomes necessary, provide sanitized examples and document required environment variables.
