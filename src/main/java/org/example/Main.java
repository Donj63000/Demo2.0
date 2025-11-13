package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Dimension2D;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.stream.Collectors;

public class Main extends Application {

    // Résolution de référence (maquette)
    public static final double DESIGN_WIDTH  = 1920;
    public static final double DESIGN_HEIGHT = 1080;
    private static final double INITIAL_WINDOW_WIDTH  = 1280;
    private static final double INITIAL_WINDOW_HEIGHT = 720;
    private static final double MIN_WINDOW_WIDTH = 1024;
    private static final double MIN_WINDOW_HEIGHT = 640;

    // Rayon de la roue
    public static final double WHEEL_RADIUS  = 280;
    private static final double LEFT_COL_WIDTH  = 400;
    private static final double RIGHT_COL_WIDTH = 360;

    // Durée du spin
    public static final double SPIN_DURATION = 5.0; // en secondes

    private Stage primaryStage;
    private BorderPane rootPane;
    private Scene scene;
    private ScaledContentPane scaledViewport;
    private boolean windowedFullscreen;
    private double savedWindowX = Double.NaN;
    private double savedWindowY = Double.NaN;
    private double savedWindowWidth = INITIAL_WINDOW_WIDTH;
    private double savedWindowHeight = INITIAL_WINDOW_HEIGHT;
    private boolean savedResizable = true;
    private Button windowedFullscreenButton;

    private DonationsLedger donationsLedger;
    private Integer currentRoundId;
    private Users users;
    private Gains gains;
    private Historique historique;
    private Resultat resultat;
    private Roue roue;
    private final Map<Participant, ChangeListener<Boolean>> participationListeners = new IdentityHashMap<>();
    private String lastSnapshotSignature;
    private boolean wheelRefreshSuppressed;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        // Root principal
        this.rootPane = new BorderPane();
        BorderPane root = rootPane;
        rootPane.setPrefSize(DESIGN_WIDTH, DESIGN_HEIGHT);
        rootPane.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        rootPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        // === 1) Titre + Résultat (en haut) ===
        Titre bandeau = new Titre();
        resultat = new Resultat();

        // Rapprochés : spacing = 4 px
        HBox topBox = new HBox(resultat.getNode());
        topBox.setAlignment(Pos.CENTER);
        topBox.setMaxWidth(Double.MAX_VALUE);

        VBox topContainer = new VBox(1,
                bandeau.getNode(),
                topBox
        );
        topContainer.setAlignment(Pos.TOP_LEFT);
        topContainer.setPadding(new Insets(0, 0, 2, 0));
        topContainer.setFillWidth(true);
        root.setTop(topContainer);

        // === 2) Participants (gauche) ===
        users = new Users();
        donationsLedger = new DonationsLedger();
        gains = new Gains(users.getParticipants());
        historique = new Historique(gains, donationsLedger);
        gains.setCarryOver(donationsLedger.computeCarryOver());
        Button historyButton = new Button("Historique");
        historyButton.setOnAction(e -> historique.show());
        Theme.styleButton(historyButton);

        VBox leftBox = new VBox(8, historyButton, users.getRootPane());
        leftBox.setPadding(new Insets(6, 10, 6, 18));
        leftBox.setAlignment(Pos.TOP_LEFT);

        // Largeur fixe, hauteur adaptable pour libérer l'espace vertical
        leftBox.setPrefWidth(LEFT_COL_WIDTH);
        leftBox.setMinWidth(LEFT_COL_WIDTH);
        leftBox.setMaxWidth(LEFT_COL_WIDTH);
        leftBox.setPrefHeight(Region.USE_COMPUTED_SIZE);
        root.setLeft(leftBox);

        // === 3) Gains (droite) ===
        VBox rightBox = new VBox(8, gains.getRootPane());
        rightBox.setPadding(new Insets(4, 18, 8, 10));
        rightBox.setAlignment(Pos.TOP_CENTER);
        rightBox.setPrefWidth(RIGHT_COL_WIDTH);
        rightBox.setMinWidth(RIGHT_COL_WIDTH);
        rightBox.setMaxWidth(RIGHT_COL_WIDTH);
        rightBox.setPrefHeight(Region.USE_COMPUTED_SIZE);
        root.setRight(rightBox);

        // === 4) Roue au centre ===
        roue = new Roue(resultat);
        Region wheelRoot = (Region) roue.getRootPane();
        wheelRoot.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        wheelRoot.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        StackPane centerPane = new StackPane(wheelRoot);
        centerPane.setAlignment(Pos.CENTER);
        centerPane.setPadding(Insets.EMPTY);
        centerPane.setTranslateX(0);
        centerPane.setTranslateY(0);

        root.setCenter(centerPane);

        // Recharge la sauvegarde, s’il y en a une
        try {
            Path f = Path.of("loterie-save.txt");
            if (Files.exists(f)) {
                var lines = Files.readAllLines(f);
                boolean objetsPart = false;
                boolean bonusPart  = false;
                for (String line : lines) {
                    if (line.startsWith("#")) {
                        objetsPart = line.startsWith("#Objets");
                        bonusPart  = line.startsWith("#Bonus");
                        continue;
                    }
                    if (bonusPart) {
                        gains.setExtraKamas(Kamas.parseFlexible(line, 0));
                    } else if (objetsPart) {
                        gains.getObjets().add(line);
                    } else {
                        String[] parts = line.split(";", 3);
                        if (parts.length == 3) {
                            users.getParticipants().add(
                                    new Participant(
                                            parts[0],
                                            Kamas.parseFlexible(parts[1], 0),
                                            parts[2]
                                    )
                            );
                        }
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("Impossible de relire la sauvegarde : " + ex.getMessage());
        }

        // Mise à jour initiale de la roue
        users.getParticipants().forEach(this::attachParticipationListener);
        roue.updateWheelDisplay(users.getParticipantNames());

        // Surveille les changements sur la liste de participants
        users.getParticipants().addListener(
                (ListChangeListener<Participant>) change -> {
                    while (change.next()) {
                        if (change.wasAdded()) {
                            change.getAddedSubList().forEach(this::attachParticipationListener);
                        }
                        if (change.wasRemoved()) {
                            change.getRemoved().forEach(this::detachParticipationListener);
                        }
                    }
                    roue.updateWheelDisplay(users.getParticipantNames());
                }
        );

        // === 5) Boutons en bas ===
        Button spinButton = new Button("Lancer la roue !");
        spinButton.setFont(Font.font("Arial", 16));

        Button optionsButton = new Button("Options...");
        optionsButton.setOnAction(e -> {
            OptionRoue optWin = new OptionRoue();
            optWin.showAndWait();
            primaryStage.centerOnScreen();
            roue.updateWheelDisplay(users.getParticipantNames());
        });

        Button resetButton = new Button("Reset Position");
        resetButton.setOnAction(e -> roue.resetPosition());

        Button saveButton = new Button("Sauvegarder état");
        saveButton.setOnAction(e -> {
            try {
                Save.save(users.getParticipants(),
                        gains.getObjets(),
                        gains.getExtraKamas());
                resultat.setMessage("État sauvegardé ✔");
            } catch (IOException ex) {
                resultat.setMessage("Erreur de sauvegarde ✖");
                ex.printStackTrace();
            }
        });

        Button cleanButton = new Button("Nettoyer");
        cleanButton.setOnAction(e -> {
            ButtonType confirmType = new ButtonType("Oui, nettoyer", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancelType = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert confirm = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "T'es sûr de vouloir tout clean ? La roulette repartira de zéro et toutes les données seront effacées.",
                    confirmType,
                    cancelType
            );
            confirm.setTitle("Confirmer le nettoyage complet");
            confirm.setHeaderText("Nettoyer la loterie ?");
            confirm.initOwner(primaryStage);
            if (confirm.showAndWait().orElse(cancelType) != confirmType) {
                return;
            }
            Save.reset(users.getParticipants(), gains.getObjets());
            gains.resetBonus();
            boolean resetOk = true;
            try {
                donationsLedger.resetCarryOver();
                gains.setCarryOver(0);
            } catch (IOException ex) {
                resultat.setMessage("Erreur RAZ cagnotte cumulée : " + ex.getMessage());
                ex.printStackTrace();
                resetOk = false;
            }
            currentRoundId = null;
            lastSnapshotSignature = null;
            roue.updateWheelDisplay(users.getParticipantNames());
            if (resetOk) {
                resultat.setMessage("Nouvelle loterie prête");
            }
        });

        // === Nouveau bouton "Plein écran" ===
        Button fullScreenButton = new Button("Plein écran");
        fullScreenButton.setOnAction(e -> {
            if (windowedFullscreen) {
                setWindowedFullscreen(false);
            }
            // Bascule l'état "fullscreen" à chaque clic
            boolean current = primaryStage.isFullScreen();
            primaryStage.setFullScreen(!current);
        });
        windowedFullscreenButton = new Button("Plein écran fenêtré");
        windowedFullscreenButton.setOnAction(e -> setWindowedFullscreen(!windowedFullscreen));
        updateWindowedFullscreenButton();

        Button[] buttonsToLock = {
                spinButton,
                optionsButton,
                resetButton,
                saveButton,
                cleanButton,
                fullScreenButton,
                windowedFullscreenButton,
                historyButton
        };

        spinButton.setOnAction(e -> {
            if (spinButton.isDisable()) {
                return;
            }
            setButtonsDisabled(true, buttonsToLock);

            var tickets = users.getParticipantNames();
            List<String> participantSnapshot = new ArrayList<>(tickets);

            if (tickets.isEmpty()) {
                resultat.setMessage("Aucun participant validé (Rejoue ? + Payé ?).");
                setButtonsDisabled(false, buttonsToLock);
                return;
            }

            int potSnapshot = gains.getTotalKamas();
            if (potSnapshot <= 0) {
                resultat.setMessage("Aucune mise enregistrée pour ce tour.");
                setButtonsDisabled(false, buttonsToLock);
                return;
            }

            final int roundPot = potSnapshot;
            final String snapshotSignature = buildSnapshotSignature();
            final int[] roundIdRef = new int[1];
            try {
                roundIdRef[0] = ensureRoundSnapshot(snapshotSignature);
            } catch (IOException ex) {
                resultat.setMessage("Erreur enregistrement dons : " + ex.getMessage());
                ex.printStackTrace();
                setButtonsDisabled(false, buttonsToLock);
                return;
            }
            final int snapshotRoundId = roundIdRef[0];

            roue.setOnSpinFinished(winnerName -> {
                try {
                    if (winnerName != null) {
                        donationsLedger.appendPayout(snapshotRoundId, winnerName, roundPot);
                        finalizeRoundAndReset();
                        resultat.setMessage(winnerName + " remporte " + formatKamas(roundPot) + " k !");
                        historique.logResult(winnerName, roundPot, participantSnapshot, snapshotRoundId);
                    } else {
                        resultat.setMessage("Perdu ! Pot conservé : " + formatKamas(roundPot) + " k");
                        historique.logResult(null, roundPot, participantSnapshot, snapshotRoundId);
                    }
                } catch (IOException ex) {
                    resultat.setMessage("Erreur payout : " + ex.getMessage());
                    ex.printStackTrace();
                } finally {
                    withWheelRefreshSuppressed(() ->
                            users.getParticipants().forEach(p -> p.setPaid(false))
                    );
                    setButtonsDisabled(false, buttonsToLock);
                }
            });

            roue.updateWheelDisplay(tickets);
            roue.spinTheWheel(tickets);
        });

        Button[] bottomButtons = {
                spinButton,
                optionsButton,
                resetButton,
                saveButton,
                cleanButton,
                fullScreenButton,
                windowedFullscreenButton
        };
        for (Button button : bottomButtons) {
            Theme.styleButton(button);
            button.setMinWidth(Region.USE_PREF_SIZE);
        }
        spinButton.getStyleClass().add("primary");

        FlowPane bottomBox = new FlowPane(10, 8);
        bottomBox.setAlignment(Pos.CENTER);
        bottomBox.setPadding(new Insets(2, 12, 8, 12));
        bottomBox.prefWrapLengthProperty().bind(root.widthProperty().subtract(28));
        bottomBox.getChildren().addAll(bottomButtons);
        root.setBottom(bottomBox);

        // === 6) Scène + Stage ===
        scaledViewport = new ScaledContentPane(rootPane, DESIGN_WIDTH, DESIGN_HEIGHT);
        scaledViewport.setPadding(Insets.EMPTY);
        scaledViewport.setBackground(Theme.makeBackgroundCover("/img.png"));
        // Autorise l'UI à s'adapter aux écrans plus petits et plus grands.
        scaledViewport.setAllowUpscale(true);

        Dimension2D initialSize = computeInitialSceneSize(getCurrentScreen());
        scene = new Scene(scaledViewport, initialSize.getWidth(), initialSize.getHeight());
        savedWindowWidth = initialSize.getWidth();
        savedWindowHeight = initialSize.getHeight();
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/app.css")).toExternalForm()
        );
        primaryStage.setTitle("Loterie de la guilde Evolution [By Coca]");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(MIN_WINDOW_WIDTH);
        primaryStage.setMinHeight(MIN_WINDOW_HEIGHT);

        // -> Optionnel : enlever l'indication pour quitter le fullscreen
        // primaryStage.setFullScreenExitHint("");

        primaryStage.fullScreenProperty().addListener((obs, ov, nv) -> {
            if (!nv) {
                rememberWindowBounds();
            }
        });
        primaryStage.maximizedProperty().addListener((obs, ov, nv) -> {
            if (!nv) {
                rememberWindowBounds();
            }
        });
        ChangeListener<Number> stageMoveResizeListener = (obs, ov, nv) -> rememberWindowBounds();
        primaryStage.xProperty().addListener(stageMoveResizeListener);
        primaryStage.yProperty().addListener(stageMoveResizeListener);
        primaryStage.widthProperty().addListener(stageMoveResizeListener);
        primaryStage.heightProperty().addListener(stageMoveResizeListener);

        primaryStage.show();
        primaryStage.centerOnScreen();
        Platform.runLater(this::rememberWindowBounds);
    }

    private static Dimension2D computeInitialSceneSize(Screen screen) {
        if (screen == null) {
            screen = Screen.getPrimary();
        }
        Rectangle2D bounds = screen.getVisualBounds();
        double aspectRatio = DESIGN_WIDTH / DESIGN_HEIGHT;

        double maxWidth = bounds.getWidth() * 0.92;
        double maxHeight = bounds.getHeight() * 0.92;

        double width = Math.min(DESIGN_WIDTH, maxWidth);
        double height = width / aspectRatio;
        if (height > maxHeight) {
            height = maxHeight;
            width = height * aspectRatio;
        }

        double minWidthCandidate = Math.min(bounds.getWidth(), MIN_WINDOW_WIDTH);
        double minHeightCandidate = Math.min(bounds.getHeight(), MIN_WINDOW_HEIGHT);

        if (width < minWidthCandidate) {
            width = minWidthCandidate;
            height = width / aspectRatio;
        }
        if (height < minHeightCandidate) {
            height = minHeightCandidate;
            width = height * aspectRatio;
        }

        if (width > bounds.getWidth()) {
            width = bounds.getWidth();
            height = width / aspectRatio;
        }
        if (height > bounds.getHeight()) {
            height = bounds.getHeight();
            width = height * aspectRatio;
        }

        return new Dimension2D(Math.max(320, width), Math.max(240, height));
    }

    private void rememberWindowBounds() {
        if (primaryStage == null
                || windowedFullscreen
                || primaryStage.isFullScreen()
                || primaryStage.isMaximized()) {
            return;
        }
        savedWindowX = primaryStage.getX();
        savedWindowY = primaryStage.getY();
        savedWindowWidth = primaryStage.getWidth();
        savedWindowHeight = primaryStage.getHeight();
        savedResizable = primaryStage.isResizable();
    }

    private Screen getCurrentScreen() {
        if (primaryStage == null) {
            return Screen.getPrimary();
        }
        Rectangle2D windowBounds = new Rectangle2D(
                primaryStage.getX(),
                primaryStage.getY(),
                Math.max(1, primaryStage.getWidth()),
                Math.max(1, primaryStage.getHeight())
        );
        Screen bestMatch = null;
        double bestArea = -1;
        for (Screen screen : Screen.getScreens()) {
            double overlap = intersectionArea(windowBounds, screen.getVisualBounds());
            if (overlap > bestArea) {
                bestArea = overlap;
                bestMatch = screen;
            }
        }
        if (bestMatch != null && bestArea > 0) {
            return bestMatch;
        }
        double centerX = windowBounds.getMinX() + windowBounds.getWidth() / 2.0;
        double centerY = windowBounds.getMinY() + windowBounds.getHeight() / 2.0;
        for (Screen screen : Screen.getScreens()) {
            if (screen.getVisualBounds().contains(centerX, centerY)) {
                return screen;
            }
        }
        return Screen.getPrimary();
    }

    private static double intersectionArea(Rectangle2D a, Rectangle2D b) {
        double minX = Math.max(a.getMinX(), b.getMinX());
        double minY = Math.max(a.getMinY(), b.getMinY());
        double maxX = Math.min(a.getMaxX(), b.getMaxX());
        double maxY = Math.min(a.getMaxY(), b.getMaxY());
        double width = maxX - minX;
        double height = maxY - minY;
        if (width <= 0 || height <= 0) {
            return 0;
        }
        return width * height;
    }

    private void setWindowedFullscreen(boolean enable) {
        if (primaryStage == null || windowedFullscreen == enable) {
            return;
        }
        if (enable) {
            rememberWindowBounds();
            Rectangle2D bounds = getCurrentScreen().getVisualBounds();
            windowedFullscreen = true;
            primaryStage.setFullScreen(false);
            primaryStage.setMaximized(false);
            primaryStage.setResizable(false);
            primaryStage.setX(bounds.getMinX());
            primaryStage.setY(bounds.getMinY());
            primaryStage.setWidth(bounds.getWidth());
            primaryStage.setHeight(bounds.getHeight());
        } else {
            windowedFullscreen = false;
            primaryStage.setResizable(savedResizable);
            if (!Double.isNaN(savedWindowX) && !Double.isNaN(savedWindowY)) {
                primaryStage.setX(savedWindowX);
                primaryStage.setY(savedWindowY);
            }
            if (!Double.isNaN(savedWindowWidth) && !Double.isNaN(savedWindowHeight)) {
                primaryStage.setWidth(savedWindowWidth);
                primaryStage.setHeight(savedWindowHeight);
            }
        }
        updateWindowedFullscreenButton();
    }

    private void updateWindowedFullscreenButton() {
        if (windowedFullscreenButton != null) {
            windowedFullscreenButton.setText(
                    windowedFullscreen ? "Quitter plein écran fenêtré" : "Plein écran fenêtré"
            );
        }
    }

    private void attachParticipationListener(Participant participant) {
        if (participant == null || participationListeners.containsKey(participant)) {
            return;
        }
        ChangeListener<Boolean> listener = (obs, oldVal, newVal) -> {
            if (!wheelRefreshSuppressed) {
                roue.updateWheelDisplay(users.getParticipantNames());
            }
        };
        participant.willReplayProperty().addListener(listener);
        participant.paidProperty().addListener(listener);
        participationListeners.put(participant, listener);
    }

    private void detachParticipationListener(Participant participant) {
        ChangeListener<Boolean> listener = participationListeners.remove(participant);
        if (listener != null) {
            participant.willReplayProperty().removeListener(listener);
            participant.paidProperty().removeListener(listener);
        }
    }

    private static void setButtonsDisabled(boolean disabled, Button... buttons) {
        for (Button button : buttons) {
            if (button != null) {
                button.setDisable(disabled);
            }
        }
    }

    private void withWheelRefreshSuppressed(Runnable action) {
        boolean previous = wheelRefreshSuppressed;
        wheelRefreshSuppressed = true;
        try {
            action.run();
        } finally {
            wheelRefreshSuppressed = previous;
        }
    }

    private int ensureRoundSnapshot(String snapshotSignature) throws IOException {
        int roundId = (currentRoundId != null) ? currentRoundId : donationsLedger.getNextRoundId();
        if (lastSnapshotSignature == null
                || !lastSnapshotSignature.equals(snapshotSignature)
                || currentRoundId == null) {
            donationsLedger.upsertRoundSnapshot(roundId, users.getParticipants(), gains.getExtraKamas());
            gains.setCarryOver(donationsLedger.computeCarryOver());
            lastSnapshotSignature = snapshotSignature;
        }
        currentRoundId = roundId;
        return roundId;
    }

    private Integer finalizeRoundAndReset() {
        int total = gains.getTotalKamas();
        if (total <= 0) {
            withWheelRefreshSuppressed(() ->
                    users.getParticipants().forEach(p -> p.setPaid(false))
            );
            currentRoundId = null;
            lastSnapshotSignature = null;
            return null;
        }
        String snapshotSignature = buildSnapshotSignature();
        try {
            int roundId = ensureRoundSnapshot(snapshotSignature);
            users.resetKamasToZero();
            gains.resetBonus();
            withWheelRefreshSuppressed(() ->
                    users.getParticipants().forEach(p -> p.setPaid(false))
            );
            currentRoundId = null;
            lastSnapshotSignature = null;
            gains.setCarryOver(donationsLedger.computeCarryOver());
            return roundId;
        } catch (IOException ex) {
            resultat.setMessage("Erreur lors de l'enregistrement des dons : " + ex.getMessage());
            ex.printStackTrace();
            return null;
        }
    }

    private String buildSnapshotSignature() {
        String participantsSignature = users.getParticipants().stream()
                .filter(p -> p.getKamas() > 0)
                .sorted(Comparator.comparing(p -> {
                    String name = p.getName();
                    return name == null ? "" : name.toLowerCase();
                }))
                .map(p -> {
                    String name = p.getName();
                    String safeName = name == null ? "" : name.toLowerCase();
                    return safeName + ":" + p.getKamas();
                })
                .collect(Collectors.joining("|"));
        return participantsSignature + ";bonus=" + gains.getExtraKamas();
    }

    private static String formatKamas(int value) {
        return String.format("%,d", value).replace(',', ' ');
    }

    public static void main(String[] args) {
        launch(args);
    }
}
