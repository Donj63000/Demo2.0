# DemoLoterie

Application JavaFX simulant une loterie avec gestion d'un registre de dons et une roue de gains. Le projet cible Java 21 et fournit un jar autonome prêt à l'emploi.

## Construire l'application
- `mvn clean package` produit le jar autonome `target/demoloterie.jar`.
- `mvn javafx:run` exécute le lanceur `org.example.Launcher` pour tester rapidement l'UI.
- `mvn clean package -Pwindows-installer` (Windows + JDK 21) génère un installateur `.exe` dans `target/installer/`.

## Exécuter
- `java -jar target/demoloterie.jar` lance l'application empaquetée.
- Double-cliquez sur l'exécutable généré pour installer l'application avec raccourci menu/désinstallation standard Windows.

## Ressources utiles
- Le playbook complet se trouve dans `AGENTS.md` (structure des modules, conventions, tests).
