package org.example;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.beans.value.ChangeListener;

import java.util.IdentityHashMap;
import java.util.Map;

public class Gains {

    // Données principales
    private final ObservableList<Participant> participants;
    private final ObservableList<String> objets;
    private final SimpleIntegerProperty extraKamas;
    private final SimpleIntegerProperty carryOver = new SimpleIntegerProperty(0);
    private final ReadOnlyIntegerWrapper totalKamas = new ReadOnlyIntegerWrapper(0);
    private final Map<Participant, ChangeListener<Number>> kamasListeners = new IdentityHashMap<>();
    private final Map<Participant, ChangeListener<String>> donationListeners = new IdentityHashMap<>();

    // UI
    private final TextField txtExtra;
    private final Label lblTotal;
    private final ListView<String> listView;

    private final VBox root;

    /** Constructeur */
    public Gains(ObservableList<Participant> participants) {
        this.participants = participants;
        this.objets       = FXCollections.observableArrayList();
        this.extraKamas   = new SimpleIntegerProperty(0);

        /* ========== 1) CAGNOTTE ========== */
        txtExtra = new TextField("0");
        txtExtra.setPrefWidth(60);
        Theme.styleTextField(txtExtra);
        txtExtra.setTextFormatter(new TextFormatter<>(change -> {
            String next = change.getControlNewText();
            return next.matches("[0-9kKmMgG., _\u00A0]*") ? change : null;
        }));

        lblTotal = new Label();
        Theme.styleCapsuleLabel(lblTotal, "#4facfe", "#00f2fe");

        lblTotal.textProperty().bind(
                Bindings.createStringBinding(
                        () -> "Cagnotte : " + Kamas.formatFr(totalKamas.get()) + " k",
                        totalKamas
                )
        );
        extraKamas.addListener((obs, oldVal, newVal) -> recomputeTotal());
        carryOver.addListener((obs, oldVal, newVal) -> recomputeTotal());

        // Bouton : ajoute le montant du champ à la cagnote
        Button btnAddKamas = new Button("Ajouter");
        Theme.styleButton(btnAddKamas);
        btnAddKamas.setOnAction(e -> applyBonusDelta(true));

        // Bouton : supprime le montant complémentaire
        Button btnRemoveKamas = new Button("Supprimer");
        Theme.styleButton(btnRemoveKamas);
        btnRemoveKamas.setOnAction(e -> applyBonusDelta(false));

        Button btnResetBonus = new Button("Réinitialiser cagnotte");
        Theme.styleButton(btnResetBonus);
        btnResetBonus.setTooltip(new Tooltip("Met le bonus à 0 ; les mises des joueurs restent inchangées"));
        btnResetBonus.disableProperty().bind(extraKamas.isEqualTo(0));
        btnResetBonus.setOnAction(e -> resetBonusWithConfirmation());

        HBox bonusButtons = new HBox(10, btnAddKamas, btnRemoveKamas, btnResetBonus);

        /* ========== 2) OBJETS ========== */
        listView = new ListView<>(objets);
        listView.setPrefSize(160, 300);
        Theme.styleListView(listView);

        // Gros, vert
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setStyle("-fx-font-size: 18px; -fx-text-fill: green;");
                }
            }
        });

        // Saisie / ajout / suppression d'objets
        TextField txtNew = new TextField();
        txtNew.setPromptText("Nouvel objet…");
        Theme.styleTextField(txtNew);

        Button btnAdd = new Button("Ajouter");
        Theme.styleButton(btnAdd);
        btnAdd.setOnAction(e -> {
            String v = txtNew.getText().trim();
            if (!v.isEmpty()) {
                objets.add(v);
                txtNew.clear();
            }
        });

        Button btnDel = new Button("Supprimer");
        Theme.styleButton(btnDel);
        btnDel.setOnAction(e -> {
            String sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                objets.remove(sel);
            }
        });

        Label lblObjets = new Label("Objets :");
        Theme.styleCapsuleLabel(lblObjets, "#ff9a9e", "#fad0c4");

        // Mise à jour auto depuis les participants
        participants.addListener((ListChangeListener<Participant>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    change.getAddedSubList().forEach(this::attachParticipantListeners);
                }
                if (change.wasRemoved()) {
                    change.getRemoved().forEach(this::detachParticipantListeners);
                }
            }
            refreshObjets();
            recomputeTotal();
        });
        participants.forEach(this::attachParticipantListeners);
        refreshObjets();
        recomputeTotal();

        VBox objetsBox = new VBox(6, lblObjets, listView, txtNew, btnAdd, btnDel);
        objetsBox.setPadding(new Insets(8, 0, 0, 0));

        VBox vbKamas = new VBox(6,
                txtExtra,
                bonusButtons
        );

        root = new VBox(10,
                lblTotal,
                vbKamas,
                new Separator(),
                objetsBox
        );
    }

    private void applyBonusDelta(boolean add) {
        int delta = Kamas.parseFlexible(txtExtra.getText(), 0);
        if (delta <= 0) {
            showWarn("Saisis un entier > 0");
            txtExtra.selectAll();
            return;
        }
        if (add) {
            setExtraKamas(getExtraKamas() + delta);
        } else {
            setExtraKamas(Math.max(0, getExtraKamas() - delta));
        }
        txtExtra.clear();
    }

    private void refreshObjets() {
        objets.setAll(
                participants.stream()
                        .map(Participant::getDonation)
                        .filter(s -> s != null && !s.isBlank() && !s.equals("-"))
                        .toList()
        );
    }

    public Node getRootPane() {
        return root;
    }

    public int getExtraKamas() {
        return extraKamas.get();
    }

    public void setExtraKamas(int value) {
        int sanitized = Math.max(0, value);
        if (sanitized != extraKamas.get()) {
            extraKamas.set(sanitized);
        } else {
            recomputeTotal();
        }
        txtExtra.setText(Kamas.formatFr(sanitized));
    }

    public void resetBonus() {
        if (extraKamas.get() != 0) {
            extraKamas.set(0);
        } else {
            recomputeTotal();
        }
        txtExtra.clear();
    }

    public void setCarryOver(int value) {
        carryOver.set(Math.max(0, value));
    }

    public int getCarryOver() {
        return carryOver.get();
    }

    public SimpleIntegerProperty carryOverProperty() {
        return carryOver;
    }

    public ReadOnlyIntegerProperty totalKamasProperty() {
        return totalKamas.getReadOnlyProperty();
    }

    public int getTotalKamas() {
        return totalKamas.get();
    }

    public ObservableList<String> getObjets() {
        return objets;
    }

    private void attachParticipantListeners(Participant participant) {
        if (participant == null) {
            return;
        }
        ChangeListener<Number> kamasListener = (obs, oldVal, newVal) -> recomputeTotal();
        participant.kamasProperty().addListener(kamasListener);
        kamasListeners.put(participant, kamasListener);

        ChangeListener<String> donationListener = (obs, oldVal, newVal) -> refreshObjets();
        participant.donationProperty().addListener(donationListener);
        donationListeners.put(participant, donationListener);
    }

    private void detachParticipantListeners(Participant participant) {
        if (participant == null) {
            return;
        }
        ChangeListener<Number> kamasListener = kamasListeners.remove(participant);
        if (kamasListener != null) {
            participant.kamasProperty().removeListener(kamasListener);
        }
        ChangeListener<String> donationListener = donationListeners.remove(participant);
        if (donationListener != null) {
            participant.donationProperty().removeListener(donationListener);
        }
        recomputeTotal();
    }

    private void recomputeTotal() {
        int sumPlayers = participants.stream().mapToInt(Participant::getKamas).sum();
        int bonus = Math.max(0, extraKamas.get());
        // Only show current round pot: participants + manual bonus.
        totalKamas.set(sumPlayers + bonus);
    }

    private void resetBonusWithConfirmation() {
        if (extraKamas.get() == 0) {
            txtExtra.clear();
            return;
        }
        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Remettre le bonus manuel à 0 ?\n"
                        + "Les mises des joueurs et le report (carry-over) resteront inchangés.",
                ButtonType.YES,
                ButtonType.NO
        );
        confirm.setHeaderText("Réinitialiser la cagnotte (bonus)");
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }
        resetBonus();
    }

    private static void showWarn(String msg) {
        new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK).showAndWait();
    }
}
