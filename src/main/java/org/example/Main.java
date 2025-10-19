package org.example;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
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

    // Dimensions de la fenêtre
    public static final double SCENE_WIDTH   = 1200;
    public static final double SCENE_HEIGHT  = 900;

    // Rayon de la roue
    public static final double WHEEL_RADIUS  = 280;

    // Durée du spin
    public static final double SPIN_DURATION = 5.0; // en secondes

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
        // Root principal
        BorderPane root = new BorderPane();

        // === 1) Titre + Résultat (en haut) ===
        Titre bandeau = new Titre();
        resultat = new Resultat();

        // Rapprochés : spacing = 4 px
        HBox topBox = new HBox(resultat.getNode());
        topBox.setAlignment(Pos.CENTER);

        VBox topContainer = new VBox(1,
                bandeau.getNode(),
                topBox
        );
        topContainer.setAlignment(Pos.TOP_LEFT);
        topContainer.setPadding(new Insets(0, 0, 2, 0));
        root.setTop(topContainer);

        // Image de fond
        root.setBackground(Theme.makeBackgroundCover("/img.png"));

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
        leftBox.setPrefWidth(400);
        leftBox.setMinWidth(360);
        leftBox.setPrefHeight(Region.USE_COMPUTED_SIZE);
        root.setLeft(leftBox);

        // === 3) Gains (droite) ===
        VBox rightBox = new VBox(8, gains.getRootPane());
        rightBox.setPadding(new Insets(4, 18, 8, 10));
        rightBox.setAlignment(Pos.TOP_CENTER);
        rightBox.setPrefWidth(360);
        rightBox.setMinWidth(320);
        rightBox.setPrefHeight(Region.USE_COMPUTED_SIZE);
        root.setRight(rightBox);

        // === 4) Roue au centre ===
        roue = new Roue(resultat);
        StackPane centerPane = new StackPane(roue.getRootPane());
        centerPane.setAlignment(Pos.TOP_CENTER);
        centerPane.setPadding(new Insets(0, 0, 0, 0));
        centerPane.setMaxSize(WHEEL_RADIUS * 2 + 40, WHEEL_RADIUS * 2 + 40);
        centerPane.setTranslateY(-6);
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
            // Bascule l'état "fullscreen" à chaque clic
            boolean current = primaryStage.isFullScreen();
            primaryStage.setFullScreen(!current);
        });
        Button[] buttonsToLock = {
                spinButton,
                optionsButton,
                resetButton,
                saveButton,
                cleanButton,
                fullScreenButton,
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
                fullScreenButton
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
        Scene scene = new Scene(root, SCENE_WIDTH, SCENE_HEIGHT);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/app.css")).toExternalForm()
        );
        primaryStage.setTitle("Loterie de la guilde Evolution [By Coca]");
        primaryStage.setScene(scene);

        // -> Optionnel : enlever l'indication pour quitter le fullscreen
        // primaryStage.setFullScreenExitHint("");

        primaryStage.show();
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
