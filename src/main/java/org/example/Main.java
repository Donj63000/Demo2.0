package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Dimension2D;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class Main extends Application {

    public static final double DESIGN_WIDTH = 1920;
    public static final double DESIGN_HEIGHT = 1080;
    private static final double INITIAL_WINDOW_WIDTH = 1280;
    private static final double INITIAL_WINDOW_HEIGHT = 720;
    private static final double MIN_WINDOW_WIDTH = 1024;
    private static final double MIN_WINDOW_HEIGHT = 640;

    public static final double WHEEL_RADIUS = 280;
    private static final double LEFT_COL_WIDTH = 400;
    private static final double RIGHT_COL_WIDTH = 360;

    public static final double SPIN_DURATION = 5.0;

    private Stage primaryStage;
    private BorderPane rootPane;
    private Scene scene;
    private ScaledContentPane scaledViewport;

    private Screen lastKnownScreen;

    private boolean windowedFullscreen;
    private double savedWindowX = Double.NaN;
    private double savedWindowY = Double.NaN;
    private double savedWindowWidth = INITIAL_WINDOW_WIDTH;
    private double savedWindowHeight = INITIAL_WINDOW_HEIGHT;
    private boolean savedResizable = true;

    private Button windowedFullscreenButton;
    private Button adaptScreenButton;

    private boolean adaptiveScalingEnabled;
    private boolean applyingStageBounds;
    private boolean suppressExitFullScreenRestore;
    private boolean suppressExitMaximizedRestore;

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
    private static final String PREF_ADAPTIVE = "ui.adaptiveScaling";

    private static boolean shouldAutoEnableAdaptive(Screen screen) {
        if (screen == null) {
            return false;
        }
        Rectangle2D b = screen.getVisualBounds();
        double s = Math.min(b.getWidth() / DESIGN_WIDTH, b.getHeight() / DESIGN_HEIGHT);
        return Double.isFinite(s) && s >= 1.15;
    }

    private boolean loadAdaptivePreference(Screen screen) {
        boolean defaultValue = shouldAutoEnableAdaptive(screen);
        try {
            Preferences p = Preferences.userNodeForPackage(Main.class);
            return p.getBoolean(PREF_ADAPTIVE, defaultValue);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private void saveAdaptivePreference() {
        try {
            Preferences p = Preferences.userNodeForPackage(Main.class);
            p.putBoolean(PREF_ADAPTIVE, adaptiveScalingEnabled);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        rootPane = new BorderPane();
        BorderPane root = rootPane;
        rootPane.setPrefSize(DESIGN_WIDTH, DESIGN_HEIGHT);
        rootPane.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        rootPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Titre bandeau = new Titre();
        resultat = new Resultat();

        HBox topBox = new HBox(resultat.getNode());
        topBox.setAlignment(Pos.CENTER);
        topBox.setMaxWidth(Double.MAX_VALUE);

        VBox topContainer = new VBox(1, bandeau.getNode(), topBox);
        topContainer.setAlignment(Pos.TOP_LEFT);
        topContainer.setPadding(new Insets(0, 0, 2, 0));
        topContainer.setFillWidth(true);
        root.setTop(topContainer);

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
        leftBox.setPrefWidth(LEFT_COL_WIDTH);
        leftBox.setMinWidth(LEFT_COL_WIDTH);
        leftBox.setMaxWidth(LEFT_COL_WIDTH);
        leftBox.setPrefHeight(Region.USE_COMPUTED_SIZE);
        root.setLeft(leftBox);

        VBox rightBox = new VBox(8, gains.getRootPane());
        rightBox.setPadding(new Insets(4, 18, 8, 10));
        rightBox.setAlignment(Pos.TOP_CENTER);
        rightBox.setPrefWidth(RIGHT_COL_WIDTH);
        rightBox.setMinWidth(RIGHT_COL_WIDTH);
        rightBox.setMaxWidth(RIGHT_COL_WIDTH);
        rightBox.setPrefHeight(Region.USE_COMPUTED_SIZE);
        root.setRight(rightBox);

        roue = new Roue(resultat);
        Region wheelRoot = (Region) roue.getRootPane();
        wheelRoot.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        wheelRoot.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        StackPane centerPane = new StackPane(wheelRoot);
        centerPane.setAlignment(Pos.CENTER);
        centerPane.setPadding(Insets.EMPTY);
        root.setCenter(centerPane);

        loadSavedState();

        users.getParticipants().forEach(this::attachParticipationListener);
        roue.updateWheelDisplay(users.getParticipantNames());

        users.getParticipants().addListener((ListChangeListener<Participant>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    change.getAddedSubList().forEach(this::attachParticipationListener);
                }
                if (change.wasRemoved()) {
                    change.getRemoved().forEach(this::detachParticipationListener);
                }
            }
            roue.updateWheelDisplay(users.getParticipantNames());
        });

        Button spinButton = new Button("Lancer la roue !");
        spinButton.setFont(Font.font("Arial", 16));

        Button optionsButton = new Button("Options...");
        optionsButton.setOnAction(e -> {
            OptionRoue optWin = new OptionRoue();
            optWin.showAndWait();
            centerOnCurrentScreen();
            roue.updateWheelDisplay(users.getParticipantNames());
        });

        Button resetButton = new Button("Reset Position");
        resetButton.setOnAction(e -> roue.resetPosition());

        Button saveButton = new Button("Sauvegarder état");
        saveButton.setOnAction(e -> {
            try {
                Save.save(users.getParticipants(), gains.getObjets(), gains.getExtraKamas());
                resultat.setMessage("État sauvegardé ✔");
            } catch (IOException ex) {
                resultat.setMessage("Erreur de sauvegarde ✖");
                ex.printStackTrace();
            }
        });

        Button cleanButton = new Button("Nettoyer");
        cleanButton.setOnAction(e -> handleCleanAll());

        Button fullScreenButton = new Button("Plein écran");
        fullScreenButton.setOnAction(e -> toggleFullScreen());

        windowedFullscreenButton = new Button("Plein écran fenêtré");
        windowedFullscreenButton.setOnAction(e -> setWindowedFullscreen(!windowedFullscreen));
        updateWindowedFullscreenButton();

        adaptScreenButton = new Button("Adapter l'écran");
        adaptScreenButton.setOnAction(e -> handleAdaptScreenToggle());
        Theme.styleButton(adaptScreenButton);
        updateAdaptScreenButton();

        Button[] buttonsToLock = {
                spinButton,
                optionsButton,
                resetButton,
                saveButton,
                cleanButton,
                fullScreenButton,
                windowedFullscreenButton,
                adaptScreenButton,
                historyButton
        };

        spinButton.setOnAction(e -> handleSpin(spinButton, buttonsToLock));

        Button[] bottomButtons = {
                spinButton,
                optionsButton,
                resetButton,
                saveButton,
                cleanButton,
                fullScreenButton,
                windowedFullscreenButton,
                adaptScreenButton
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

        scaledViewport = new ScaledContentPane(rootPane, DESIGN_WIDTH, DESIGN_HEIGHT);
        scaledViewport.setPadding(Insets.EMPTY);
        scaledViewport.setBackground(Theme.makeBackgroundCover("/img.png"));

        Screen initialScreen = Screen.getPrimary();
        lastKnownScreen = initialScreen;

        adaptiveScalingEnabled = loadAdaptivePreference(initialScreen);
        refreshViewportUpscale();

        Dimension2D initialSize = computeSceneSize(initialScreen, adaptiveScalingEnabled);
        scene = new Scene(scaledViewport, initialSize.getWidth(), initialSize.getHeight());
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/app.css")).toExternalForm());

        primaryStage.setTitle("Loterie de la guilde Evolution [By Coca]");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(MIN_WINDOW_WIDTH);
        primaryStage.setMinHeight(MIN_WINDOW_HEIGHT);

        savedWindowWidth = initialSize.getWidth();
        savedWindowHeight = initialSize.getHeight();
        Point2D initialPosition = computeCenteredWindowPosition(initialScreen.getVisualBounds(), initialSize);
        savedWindowX = initialPosition.getX();
        savedWindowY = initialPosition.getY();
        applyStageBounds(savedWindowX, savedWindowY, savedWindowWidth, savedWindowHeight, false);

        primaryStage.fullScreenProperty().addListener((obs, ov, nv) -> {
            refreshViewportUpscale();
            if (!nv) {
                if (suppressExitFullScreenRestore) {
                    suppressExitFullScreenRestore = false;
                    return;
                }
                Platform.runLater(() -> restoreWindowBoundsIfAvailable(false));
            }
        });

        primaryStage.maximizedProperty().addListener((obs, ov, nv) -> {
            refreshViewportUpscale();
            if (!nv) {
                if (suppressExitMaximizedRestore) {
                    suppressExitMaximizedRestore = false;
                    return;
                }
                Platform.runLater(() -> restoreWindowBoundsIfAvailable(false));
            }
        });

        ChangeListener<Number> stageMoveResizeListener = (obs, ov, nv) -> onStageBoundsChanged();
        primaryStage.xProperty().addListener(stageMoveResizeListener);
        primaryStage.yProperty().addListener(stageMoveResizeListener);
        primaryStage.widthProperty().addListener(stageMoveResizeListener);
        primaryStage.heightProperty().addListener(stageMoveResizeListener);

        primaryStage.show();

        Platform.runLater(() -> {
            centerOnCurrentScreen();
            rememberWindowBounds();
            refreshViewportUpscale();
        });
    }

    private void loadSavedState() {
        try {
            Path f = Path.of("loterie-save.txt");
            if (!Files.exists(f)) {
                return;
            }
            List<String> lines = Files.readAllLines(f);
            boolean objetsPart = false;
            boolean bonusPart = false;
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("#")) {
                    String low = trimmed.toLowerCase();
                    objetsPart = low.startsWith("#objets");
                    bonusPart = low.startsWith("#bonus");
                    continue;
                }
                if (bonusPart) {
                    gains.setExtraKamas(Kamas.parseFlexible(trimmed, 0));
                    continue;
                }
                if (objetsPart) {
                    gains.getObjets().add(trimmed);
                    continue;
                }
                String[] parts = trimmed.split(";", 3);
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
        } catch (Exception ex) {
            System.err.println("Impossible de relire la sauvegarde : " + ex.getMessage());
        }
    }

    private void handleSpin(Button spinButton, Button[] buttonsToLock) {
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
        final int snapshotRoundId;
        try {
            snapshotRoundId = ensureRoundSnapshot(snapshotSignature);
        } catch (IOException ex) {
            resultat.setMessage("Erreur enregistrement dons : " + ex.getMessage());
            ex.printStackTrace();
            setButtonsDisabled(false, buttonsToLock);
            return;
        }

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
                withWheelRefreshSuppressed(() -> users.getParticipants().forEach(p -> p.setPaid(false)));
                setButtonsDisabled(false, buttonsToLock);
            }
        });

        roue.updateWheelDisplay(tickets);
        roue.spinTheWheel(tickets);
    }

    private void handleCleanAll() {
        ButtonType confirmType = new ButtonType("Oui, nettoyer", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Cela supprime tous les participants, les objets et le bonus.",
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
    }

    private void toggleFullScreen() {
        if (primaryStage == null) {
            return;
        }
        if (windowedFullscreen) {
            setWindowedFullscreen(false);
        }
        if (!primaryStage.isFullScreen()) {
            rememberWindowBounds();
        }
        primaryStage.setFullScreen(!primaryStage.isFullScreen());
    }

    private static Dimension2D computeSceneSize(Screen screen, boolean adaptiveMode) {
        Screen effective = screen != null ? screen : Screen.getPrimary();
        Rectangle2D bounds = effective.getVisualBounds();
        return adaptiveMode ? computeAdaptiveSceneSize(bounds) : computeDesignSceneSize(bounds);
    }

    static Dimension2D computeDesignSceneSize(Rectangle2D bounds) {
        double aspectRatio = DESIGN_WIDTH / DESIGN_HEIGHT;
        double maxWidth = Math.min(DESIGN_WIDTH, bounds.getWidth());
        double maxHeight = Math.min(DESIGN_HEIGHT, bounds.getHeight());

        double width = maxWidth;
        double height = width / aspectRatio;
        if (height > maxHeight) {
            height = maxHeight;
            width = height * aspectRatio;
        }

        double minWidthCandidate = Math.min(MIN_WINDOW_WIDTH, bounds.getWidth());
        double minHeightCandidate = Math.min(MIN_WINDOW_HEIGHT, bounds.getHeight());

        if (width < minWidthCandidate) {
            width = minWidthCandidate;
            height = width / aspectRatio;
            if (height > maxHeight) {
                height = maxHeight;
                width = height * aspectRatio;
            }
        }
        if (height < minHeightCandidate) {
            height = minHeightCandidate;
            width = height * aspectRatio;
            if (width > maxWidth) {
                width = maxWidth;
                height = width / aspectRatio;
            }
        }

        return new Dimension2D(width, height);
    }

    static Dimension2D computeAdaptiveSceneSize(Rectangle2D bounds) {
        double aspectRatio = DESIGN_WIDTH / DESIGN_HEIGHT;
        double maxWidth = bounds.getWidth() * 0.92;
        double maxHeight = bounds.getHeight() * 0.92;

        double width = maxWidth;
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
            if (height > maxHeight) {
                height = maxHeight;
                width = height * aspectRatio;
            }
        }
        if (height < minHeightCandidate) {
            height = minHeightCandidate;
            width = height * aspectRatio;
            if (width > maxWidth) {
                width = maxWidth;
                height = width / aspectRatio;
            }
        }

        width = Math.min(width, bounds.getWidth());
        height = Math.min(height, bounds.getHeight());

        return new Dimension2D(Math.max(320, width), Math.max(240, height));
    }

    static Point2D computeCenteredWindowPosition(Rectangle2D bounds, Dimension2D windowSize) {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        if (windowSize == null) {
            throw new IllegalArgumentException("windowSize must not be null");
        }
        double x = bounds.getMinX() + (bounds.getWidth() - windowSize.getWidth()) * 0.5;
        double y = bounds.getMinY() + (bounds.getHeight() - windowSize.getHeight()) * 0.5;
        return new Point2D(Math.max(bounds.getMinX(), x), Math.max(bounds.getMinY(), y));
    }

    private void handleAdaptScreenToggle() {
        if (primaryStage == null) {
            return;
        }
        if (windowedFullscreen) {
            setWindowedFullscreen(false);
        }

        boolean wasFullScreen = primaryStage.isFullScreen();
        boolean wasMaximized = primaryStage.isMaximized();

        if (wasFullScreen) {
            suppressExitFullScreenRestore = true;
            primaryStage.setFullScreen(false);
        }
        if (wasMaximized) {
            suppressExitMaximizedRestore = true;
            primaryStage.setMaximized(false);
        }

        if (wasFullScreen || wasMaximized) {
            Platform.runLater(() -> setAdaptiveScaling(!adaptiveScalingEnabled));
        } else {
            setAdaptiveScaling(!adaptiveScalingEnabled);
        }
    }

    private void setAdaptiveScaling(boolean enabled) {
        if (adaptiveScalingEnabled == enabled) {
            return;
        }
        adaptiveScalingEnabled = enabled;
        refreshViewportUpscale();
        updateAdaptScreenButton();
        applySceneSizeForCurrentMode(getCurrentScreen());
        saveAdaptivePreference();
    }

    private void applySceneSizeForCurrentMode(Screen targetScreen) {
        if (primaryStage == null || scaledViewport == null) {
            return;
        }
        Screen screen = targetScreen != null ? targetScreen : Screen.getPrimary();
        lastKnownScreen = screen;

        if (windowedFullscreen) {
            applyWindowedFullscreenBounds(screen);
            return;
        }
        if (primaryStage.isFullScreen()) {
            return;
        }

        if (primaryStage.isMaximized()) {
            suppressExitMaximizedRestore = true;
            primaryStage.setMaximized(false);
        }

        Dimension2D size = computeSceneSize(screen, adaptiveScalingEnabled);
        Point2D position = computeCenteredWindowPosition(screen.getVisualBounds(), size);
        applyStageBounds(position.getX(), position.getY(), size.getWidth(), size.getHeight(), true);
    }

    private void updateAdaptScreenButton() {
        if (adaptScreenButton == null) {
            return;
        }
        adaptScreenButton.setText(adaptiveScalingEnabled ? "Format maquette" : "Adapter l'écran");
    }

    private void onStageBoundsChanged() {
        if (primaryStage == null || applyingStageBounds) {
            return;
        }
        Screen screen = getCurrentScreen();
        if (screen != null) {
            lastKnownScreen = screen;
        }
        if (windowedFullscreen) {
            applyWindowedFullscreenBounds(screen);
            return;
        }
        rememberWindowBounds();
    }

    private void rememberWindowBounds() {
        if (primaryStage == null
                || applyingStageBounds
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

    private void refreshViewportUpscale() {
        if (scaledViewport == null) {
            return;
        }
        boolean allowUpscale = adaptiveScalingEnabled
                || windowedFullscreen
                || (primaryStage != null && (primaryStage.isFullScreen() || primaryStage.isMaximized()));
        scaledViewport.setAllowUpscale(allowUpscale);
    }

    private Screen getCurrentScreen() {
        if (primaryStage == null) {
            return Screen.getPrimary();
        }
        double x = primaryStage.getX();
        double y = primaryStage.getY();
        double w = Math.max(1, primaryStage.getWidth());
        double h = Math.max(1, primaryStage.getHeight());

        double centerX = x + w / 2.0;
        double centerY = y + h / 2.0;

        for (Screen screen : Screen.getScreens()) {
            if (screen.getVisualBounds().contains(centerX, centerY)) {
                return screen;
            }
        }

        Rectangle2D windowBounds = new Rectangle2D(x, y, w, h);
        Screen bestMatch = null;
        double bestArea = 0;
        for (Screen screen : Screen.getScreens()) {
            double overlap = intersectionArea(windowBounds, screen.getVisualBounds());
            if (overlap > bestArea) {
                bestArea = overlap;
                bestMatch = screen;
            }
        }
        return bestMatch != null ? bestMatch : Screen.getPrimary();
    }

    private void centerOnCurrentScreen() {
        if (primaryStage == null || windowedFullscreen || primaryStage.isFullScreen() || primaryStage.isMaximized()) {
            return;
        }
        Screen screen = getCurrentScreen();
        if (screen == null) {
            return;
        }
        double width = primaryStage.getWidth();
        double height = primaryStage.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        Point2D position = computeCenteredWindowPosition(screen.getVisualBounds(), new Dimension2D(width, height));
        lastKnownScreen = screen;
        applyStageBounds(position.getX(), position.getY(), width, height, false);
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
            windowedFullscreen = true;

            boolean wasFullScreen = primaryStage.isFullScreen();
            boolean wasMaximized = primaryStage.isMaximized();

            if (wasFullScreen) {
                suppressExitFullScreenRestore = true;
                primaryStage.setFullScreen(false);
            }
            if (wasMaximized) {
                suppressExitMaximizedRestore = true;
                primaryStage.setMaximized(false);
            }

            primaryStage.setResizable(false);

            Screen screen = getCurrentScreen();
            final Screen targetScreen = (screen != null) ? screen : Screen.getPrimary();
            lastKnownScreen = targetScreen;

            Platform.runLater(() -> applyWindowedFullscreenBounds(targetScreen));
        } else {
            windowedFullscreen = false;
            primaryStage.setResizable(savedResizable);
            restoreWindowBoundsIfAvailable(false);
        }

        updateWindowedFullscreenButton();
        refreshViewportUpscale();
    }

    private void restoreWindowBoundsIfAvailable(boolean forceCenter) {
        if (primaryStage == null || windowedFullscreen) {
            return;
        }

        Screen screen = getCurrentScreen();
        if (screen == null) {
            screen = Screen.getPrimary();
        }
        Rectangle2D bounds = screen.getVisualBounds();

        double width = savedWindowWidth;
        double height = savedWindowHeight;
        if (!Double.isFinite(width) || width <= 0 || !Double.isFinite(height) || height <= 0) {
            applySceneSizeForCurrentMode(screen);
            return;
        }

        double minWidthCandidate = Math.min(MIN_WINDOW_WIDTH, bounds.getWidth());
        double minHeightCandidate = Math.min(MIN_WINDOW_HEIGHT, bounds.getHeight());

        width = clamp(width, minWidthCandidate, bounds.getWidth());
        height = clamp(height, minHeightCandidate, bounds.getHeight());

        double x = savedWindowX;
        double y = savedWindowY;

        if (forceCenter || !Double.isFinite(x) || !Double.isFinite(y)) {
            Point2D position = computeCenteredWindowPosition(bounds, new Dimension2D(width, height));
            x = position.getX();
            y = position.getY();
        } else {
            double maxX = bounds.getMaxX() - width;
            double maxY = bounds.getMaxY() - height;
            x = clamp(x, bounds.getMinX(), maxX);
            y = clamp(y, bounds.getMinY(), maxY);
        }

        applyStageBounds(x, y, width, height, false);
        lastKnownScreen = screen;
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }

    private void applyWindowedFullscreenBounds(Screen screen) {
        Screen target = screen != null ? screen : Screen.getPrimary();
        Rectangle2D bounds = target.getVisualBounds();
        applyStageBounds(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight(), false);
    }

    private void applyStageBounds(double x, double y, double width, double height, boolean updateSavedBounds) {
        if (primaryStage == null) {
            return;
        }

        applyingStageBounds = true;
        try {
            if (primaryStage.isMaximized()) {
                suppressExitMaximizedRestore = true;
                primaryStage.setMaximized(false);
            }
            if (Double.isFinite(width) && width > 0) {
                primaryStage.setWidth(width);
            }
            if (Double.isFinite(height) && height > 0) {
                primaryStage.setHeight(height);
            }
            if (Double.isFinite(x)) {
                primaryStage.setX(x);
            }
            if (Double.isFinite(y)) {
                primaryStage.setY(y);
            }
        } finally {
            applyingStageBounds = false;
        }

        if (updateSavedBounds) {
            rememberWindowBounds();
        }
    }

    private void updateWindowedFullscreenButton() {
        if (windowedFullscreenButton != null) {
            windowedFullscreenButton.setText(windowedFullscreen ? "Quitter plein écran fenêtré" : "Plein écran fenêtré");
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
        if (lastSnapshotSignature == null || !lastSnapshotSignature.equals(snapshotSignature) || currentRoundId == null) {
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
            withWheelRefreshSuppressed(() -> users.getParticipants().forEach(p -> p.setPaid(false)));
            currentRoundId = null;
            lastSnapshotSignature = null;
            return null;
        }
        String snapshotSignature = buildSnapshotSignature();
        try {
            int roundId = ensureRoundSnapshot(snapshotSignature);
            users.resetKamasToZero();
            gains.resetBonus();
            withWheelRefreshSuppressed(() -> users.getParticipants().forEach(p -> p.setPaid(false)));
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
