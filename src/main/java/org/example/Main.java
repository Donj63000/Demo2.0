package org.example;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class Main extends Application {

    // Dimensions de la fenêtre
    public static final double SCENE_WIDTH   = 1200;
    public static final double SCENE_HEIGHT  = 900;

    // Rayon de la roue
    public static final double WHEEL_RADIUS  = 320;

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

        VBox topContainer = new VBox(4,
                bandeau.getNode(),
                topBox
        );
        topContainer.setAlignment(Pos.TOP_LEFT);
        root.setTop(topContainer);

        // Image de fond
        root.setBackground(Theme.makeBackgroundCover("/img.png"));

        // === 2) Participants (gauche) ===
        users = new Users();
        VBox leftBox = new VBox(10, users.getRootPane());
        // On supprime le padding-top
        leftBox.setPadding(new Insets(0, 10, 10, 20));
        leftBox.setAlignment(Pos.TOP_CENTER);

        // Agrandit la zone : 420 px large × 820 px haut
        leftBox.setPrefSize(420, 820);
        root.setLeft(leftBox);

        // === 3) Gains (droite) ===
        donationsLedger = new DonationsLedger();
        gains = new Gains(users.getParticipants());
        historique = new Historique(gains, donationsLedger);
        gains.setCarryOver(donationsLedger.computeCarryOver());
        VBox rightBox = new VBox(10, gains.getRootPane());
        // Padding-top = 0 => ils sont “collés” sous le titre
        rightBox.setPadding(new Insets(0, 20, 10, 10));
        rightBox.setAlignment(Pos.TOP_CENTER);

        // Agrandit la zone : 400 px large × 820 px haut
        rightBox.setPrefSize(400, 820);
        root.setRight(rightBox);

        // === 4) Roue au centre ===
        roue = new Roue(resultat);
        StackPane centerPane = new StackPane(roue.getRootPane());
        centerPane.setAlignment(Pos.CENTER);
        centerPane.setMaxSize(WHEEL_RADIUS * 2 + 50, WHEEL_RADIUS * 2 + 50);
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
        Button btnNouveauxPayants = new Button("Nouveaux payants");
        btnNouveauxPayants.setOnAction(e -> {
            NouveauxPayantsDialog dialog = new NouveauxPayantsDialog(users.getParticipants(), result -> {
                Set<String> namesPaid = new HashSet<>();
                for (NouveauxPayantsDialog.Payant payant : result.payants) {
                    namesPaid.add(payant.name);
                    Participant existing = users.findByNameIgnoreCase(payant.name);
                    if (existing == null) {
                        Participant participant = new Participant(payant.name, payant.kamas, "-");
                        participant.setStake(Math.max(0, payant.kamas));
                        participant.setWillReplay(true);
                        participant.setPaid(true);
                        users.getParticipants().add(participant);
                    } else {
                        existing.setKamas(Math.max(0, existing.getKamas() + payant.kamas));
                        existing.setStake(Math.max(0, payant.kamas));
                        existing.setWillReplay(true);
                        existing.setPaid(true);
                    }
                }

                if (result.retirerAnciensNonPayants) {
                    users.removeParticipantsNotIn(namesPaid);
                }

                if (!result.payants.isEmpty()) {
                    currentRoundId = (currentRoundId == null)
                            ? donationsLedger.getNextRoundId()
                            : currentRoundId;
                    resultat.setMessage("Payants mis à jour pour le tour #" + currentRoundId);
                } else {
                    resultat.setMessage("Aucun payant sélectionné.");
                }

                roue.updateWheelDisplay(users.getParticipantNames());
            });
            dialog.initOwner(primaryStage);
            dialog.showAndWait();
        });

        Button spinButton = new Button("Lancer la roue !");
        spinButton.setFont(Font.font("Arial", 16));
        spinButton.setOnAction(e -> {
            if (spinButton.isDisable()) {
                return;
            }
            spinButton.setDisable(true);

            var tickets = users.getParticipantNames();

            if (tickets.isEmpty()) {
                resultat.setMessage("Aucun participant validé (Rejoue ? + Payé ?).");
                spinButton.setDisable(false);
                return;
            }

            int potSnapshot = gains.getTotalKamas();
            if (potSnapshot <= 0) {
                resultat.setMessage("Aucune mise enregistrée pour ce tour.");
                spinButton.setDisable(false);
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
                spinButton.setDisable(false);
                return;
            }
            final int snapshotRoundId = roundIdRef[0];

            roue.setOnSpinFinished(winnerName -> {
                try {
                    if (winnerName != null) {
                        donationsLedger.appendPayout(snapshotRoundId, winnerName, roundPot);
                        finalizeRoundAndReset();
                        resultat.setMessage(winnerName + " remporte " + formatKamas(roundPot) + " k !");
                        historique.logResult(winnerName, roundPot);
                    } else {
                        resultat.setMessage("Perdu ! Pot conservé : " + formatKamas(roundPot) + " k");
                        historique.logResult(null, 0);
                    }
                } catch (IOException ex) {
                    resultat.setMessage("Erreur payout : " + ex.getMessage());
                    ex.printStackTrace();
                } finally {
                    withWheelRefreshSuppressed(() ->
                            users.getParticipants().forEach(p -> p.setPaid(false))
                    );
                    spinButton.setDisable(false);
                }
            });

            roue.updateWheelDisplay(tickets);
            roue.spinTheWheel(tickets);
        });

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
            Save.reset(users.getParticipants(), gains.getObjets());
            gains.resetBonus();
            currentRoundId = null;
            lastSnapshotSignature = null;
            roue.updateWheelDisplay(users.getParticipantNames());
            resultat.setMessage("Nouvelle loterie prête");
        });

        Button resetCarryButton = new Button("RAZ cagnotte cumulée");
        resetCarryButton.setOnAction(e -> {
            Alert confirm = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Remettre la cagnotte cumulée (report) à 0 ?\n"
                            + "Cette action réinitialise le fichier loterie-dons.csv.",
                    ButtonType.YES,
                    ButtonType.NO
            );
            confirm.setHeaderText("Réinitialiser la cagnotte cumulée");
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                return;
            }
            try {
                donationsLedger.resetCarryOver();
                gains.setCarryOver(0);
                currentRoundId = null;
                lastSnapshotSignature = null;
                resultat.setMessage("Cagnotte cumulée remise à 0.");
            } catch (IOException ex) {
                resultat.setMessage("Erreur RAZ cagnotte cumulée : " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        Button btnFin = new Button("Fin de la loterie");
        btnFin.setOnAction(e -> {
            Integer rid = finalizeRoundAndReset();
            try {
                users.clearAll();
                gains.getObjets().clear();
                gains.resetBonus();
                Save.save(users.getParticipants(), gains.getObjets(), gains.getExtraKamas());
                gains.setCarryOver(donationsLedger.computeCarryOver());
                roue.updateWheelDisplay(users.getParticipantNames());
                currentRoundId = null;
                lastSnapshotSignature = null;
                String msg = "Loterie clôturée. "
                        + (rid != null ? "Tour #" + rid + " enregistré. " : "")
                        + "Cagnotte cumulée : " + formatKamas(gains.getCarryOver()) + " k.";
                resultat.setMessage(msg);
            } catch (IOException ex) {
                resultat.setMessage("Erreur lors de la fermeture : " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        // Bouton Historique
        Button historyButton = new Button("Historique");
        historyButton.setOnAction(e -> historique.show());

        // === Nouveau bouton "Plein écran" ===
        Button fullScreenButton = new Button("Plein écran");
        fullScreenButton.setOnAction(e -> {
            // Bascule l'état "fullscreen" à chaque clic
            boolean current = primaryStage.isFullScreen();
            primaryStage.setFullScreen(!current);
        });

        Button[] bottomButtons = {
                btnNouveauxPayants,
                spinButton,
                optionsButton,
                resetButton,
                saveButton,
                cleanButton,
                resetCarryButton,
                btnFin,
                fullScreenButton,
                historyButton
        };
        for (Button button : bottomButtons) {
            Theme.styleButton(button);
            button.setMinWidth(Region.USE_PREF_SIZE);
        }
        spinButton.getStyleClass().add("primary");
        btnFin.getStyleClass().add("danger");

        FlowPane bottomBox = new FlowPane(12, 12);
        bottomBox.setAlignment(Pos.CENTER);
        bottomBox.setPadding(new Insets(10, 20, 50, 20));
        bottomBox.prefWrapLengthProperty().bind(root.widthProperty().subtract(40));
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
