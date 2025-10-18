# Repository Guidelines

## Project Structure & Module Organization
- Primary JavaFX sources live in `src/main/java/org/example/`, including `Launcher`, dialogs, and domain models such as `DonationsLedger` and `Roue`. Group related additions in the same package or a meaningful subpackage.
- UI resources (FXML, CSS, images) belong in `src/main/resources/` so they ship with the fat jar; load them via the classpath.
- Tests mirror the main tree under `src/test/java/org/example/`. Keep runtime data snapshots (`loterie-dons.csv`, `loterie-historique.txt`, `loterie-save.txt`) in the repository root.

## Build, Test, and Development Commands
- `mvn clean package` builds the shaded jar at `target/demoloterie.jar` using Java 21.
- `mvn javafx:run` launches `org.example.Launcher` for quick UI checks.
- `java -jar target/demoloterie.jar` executes the packaged app after a build.
- `mvn test` runs all JUnit 5 tests; focus a single suite with `mvn test -Dtest=ClassNameTest`.

## Coding Style & Naming Conventions
- Follow Java 21 standards: 4-space indentation, no tabs, lines under roughly 120 characters.
- Classes use UpperCamelCase (e.g., `Resultat`), methods and fields use lowerCamelCase, and constants stay SCREAMING_SNAKE_CASE.
- Keep code in the `org.example` namespace unless a clear module boundary demands otherwise. Add brief comments only for non-obvious logic.

## Testing Guidelines
- Use JUnit 5 under `src/test/java/org/example/`. Name suites with the `*Test` suffix (`DonationsLedgerTest`).
- Favor deterministic tests: seed RNG-dependent classes (`Roue`, `Gains`) and mock or fixture CSV inputs when verifying ledger behaviors.
- Run `mvn test` before submitting changes and document any manual UI checks.

## Commit & Pull Request Guidelines
- Write imperative commit subjects under 60 characters (e.g., `Refine ledger persistence`) and expand in the body if context helps.
- Reference issues when available (`Fix #12`) and call out UI or data changes, including screenshots or before/after notes.
- PRs should describe intent, list key changes, cite test evidence (`mvn test`, `mvn javafx:run`), and mention any CSV/TXT migrations or data format impacts.

## Data & Configuration Notes
- Preserve existing CSV/TXT layouts when updating runtime data; document irreversible transformations.
- Verify new JavaFX assets render correctly on Windows (default target platform) and flag additional platform needs early.
