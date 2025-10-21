# Agent Playbook

Guidance for repository maintainers and automation agents contributing to DemoLoterie. Follow this checklist-oriented playbook to stay consistent with the rest of the project.

## Mission Brief
- Build and extend the JavaFX lottery simulator packaged as `org.example.Launcher`.
- Keep new code within the `org.example` namespace unless a clear boundary demands otherwise.
- Ship portable builds via the shaded jar produced by Maven.

## Quick Start Checklist
- [ ] Review open issues and existing TODOs before starting a task.
- [ ] Sync dependencies and build once with `mvn clean package`.
- [ ] Run or inspect the UI using `mvn javafx:run` when altering scenes or flows.
- [ ] Execute `mvn test` before submitting work; add focused suites with `-Dtest=ClassNameTest`.
- [ ] Capture manual UI or data checks in your notes or PR description.

## Project Layout Essentials
- Application sources live in `src/main/java/org/example/`, including `Launcher`, dialogs, `DonationsLedger`, `Roue`, and other domain models. Group related additions in the same package or a concise subpackage.
- UI assets (FXML, CSS, images) belong in `src/main/resources/` so they ship with the fat jar. Load them via the classpath to avoid absolute paths.
- Tests mirror the main tree under `src/test/java/org/example/`. Keep runtime data snapshots—`loterie-dons.csv`, `loterie-historique.txt`, `loterie-save.txt`—at the repository root.

## Everyday Commands
- Build the shaded jar: `mvn clean package` (outputs `target/demoloterie.jar`).
- Quick UI loop: `mvn javafx:run` (launches `org.example.Launcher`).
- Run packaged app: `java -jar target/demoloterie.jar` after a build.
- Unit tests: `mvn test` or target a suite with `mvn test -Dtest=ClassNameTest`.
- Windows installer: `mvn clean package -Pwindows-installer` (requires JDK 21+ on Windows, outputs `.exe` in `target/installer/`).

## Coding Standards
- Java 21 style, 4-space indentation, no tabs, and lines under roughly 120 characters.
- Classes use UpperCamelCase, methods and fields use lowerCamelCase, constants stay SCREAMING_SNAKE_CASE.
- Add brief comments only when logic is non-obvious; prefer self-descriptive names.
- When introducing new packages, stay under `org.example` unless there is a justified module boundary.

## Testing & QA Expectations
- JUnit 5 lives under `src/test/java/org/example/`; name suites with the `*Test` suffix (e.g., `DonationsLedgerTest`).
- Keep tests deterministic: seed RNG-dependent classes such as `Roue` and `Gains`, and mock or fixture CSV inputs when verifying ledger behavior.
- Always run `mvn test` before shipping changes. Mention additional manual or UI checks in your PR notes.
- If bugs relate to runtime files, create or update fixtures rather than manipulating user data.

## Data & Configuration Discipline
- Preserve existing CSV/TXT layouts when updating runtime data. Document irreversible transformations in PRs.
- Verify new JavaFX assets render correctly on Windows (target platform) and flag extra platform needs early.
- Store ancillary configuration in `src/main/resources/` so it travels with the shaded jar.

## Commit & PR Workflow
- Use imperative commit subjects under 60 characters (e.g., `Refine ledger persistence`). Expand in the body when context helps.
- Reference issues when available (`Fix #12`) and call out UI or data changes, including screenshots or before/after notes.
- PRs should highlight intent, list key changes, cite test evidence (`mvn test`, `mvn javafx:run`), and mention any data format impacts or migrations.

## When in Doubt
- Prefer conservative, incremental changes; leave TODO markers with context if a larger refactor is required.
- Ask for clarification before moving files out of the documented structure.
- Keep local experiments out of commits—clean before submitting.
