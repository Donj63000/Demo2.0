package org.example;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
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
        txtExtra.setPrefWidth(90);
        Theme.styleTextField(txtExtra);
        txtExtra.getStyleClass().add("bonus-field");
        txtExtra.setTextFormatter(new TextFormatter<>(change -> {
            String next = change.getControlNewText();
            return next.matches("[0-9kKmMgG., _\u00A0]*") ? change : null;
        }));

        lblTotal = new Label();
        lblTotal.getStyleClass().add("pot-card-value");

        Label lblPotTitle = new Label("Cagnotte");
        lblPotTitle.getStyleClass().add("pot-card-title");

        Label lblCarry = new Label();
        lblCarry.getStyleClass().add("pot-card-subtitle");

        lblTotal.textProperty().bind(
                Bindings.createStringBinding(
                        () -> Kamas.formatFr(totalKamas.get()) + " k",
                        totalKamas
                )
        );
        lblCarry.textProperty().bind(
                Bindings.createStringBinding(
                        () -> "Bonus manuel : " + Kamas.formatFr(extraKamas.get()) + " k",
                        extraKamas
                )
        );
        extraKamas.addListener((obs, oldVal, newVal) -> recomputeTotal());
        carryOver.addListener((obs, oldVal, newVal) -> recomputeTotal());

        VBox potContent = new VBox(2, lblPotTitle, lblTotal, lblCarry);
        potContent.setAlignment(Pos.CENTER_LEFT);
        StackPane potCard = new StackPane(potContent);
        StackPane.setAlignment(potContent, Pos.CENTER_LEFT);
        potCard.getStyleClass().add("pot-card");
        potCard.setMaxWidth(Double.MAX_VALUE);

        Button btnAddKamas = new Button("Ajouter");
        Theme.styleButton(btnAddKamas);
        btnAddKamas.setOnAction(e -> applyBonusDelta(true));

        Button btnRemoveKamas = new Button("Supprimer");
        Theme.styleButton(btnRemoveKamas);
        btnRemoveKamas.setOnAction(e -> applyBonusDelta(false));

        Button btnResetBonus = new Button("Réinitialiser cagnotte");
        Theme.styleButton(btnResetBonus);
        btnResetBonus.setTooltip(new Tooltip("Met le bonus à 0 ; les mises des joueurs restent inchangées"));
        btnResetBonus.disableProperty().bind(extraKamas.isEqualTo(0));
        btnResetBonus.setOnAction(e -> resetBonusWithConfirmation());

        Label bonusTitle = new Label("Bonus manuel");
        bonusTitle.getStyleClass().add("side-card-title");

        HBox bonusButtons = new HBox(8, txtExtra, btnAddKamas, btnRemoveKamas);
        bonusButtons.getStyleClass().add("bonus-row");
        bonusButtons.setAlignment(Pos.CENTER_LEFT);

        VBox bonusCard = new VBox(10, bonusTitle, bonusButtons, btnResetBonus);
        bonusCard.getStyleClass().add("side-card");
        bonusCard.setAlignment(Pos.CENTER_LEFT);
        btnResetBonus.setMaxWidth(Double.MAX_VALUE);
        bonusCard.setMaxWidth(Double.MAX_VALUE);

        /* ========== 2) OBJETS ========== */
        listView = new ListView<>(objets);
        listView.setPrefWidth(220);
        listView.setPrefHeight(190);
        listView.setMaxHeight(220);
        listView.setFixedCellSize(36);
        Theme.styleListView(listView);
        listView.setFocusTraversable(false);

        listView.setCellFactory(lv -> new ListCell<>() {
            {
                getStyleClass().add("app-list-cell");
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                }
            }
        });

        Label placeholder = new Label("Aucun lot pour l'instant");
        placeholder.getStyleClass().add("list-placeholder");
        listView.setPlaceholder(placeholder);

        TextField txtNew = new TextField();
        txtNew.setPromptText("Nouvel objet…");
        Theme.styleTextField(txtNew);
        txtNew.getStyleClass().add("object-field");
        txtNew.setMaxWidth(Double.MAX_VALUE);

        Button btnAdd = new Button("Ajouter");
        Theme.styleButton(btnAdd);
        btnAdd.setFocusTraversable(false);
        btnAdd.setOnAction(e -> {
            String v = txtNew.getText().trim();
            if (!v.isEmpty()) {
                objets.add(v);
                txtNew.clear();
            }
        });

        Button btnDel = new Button("Supprimer");
        Theme.styleButton(btnDel);
        btnDel.setFocusTraversable(false);
        btnDel.setOnAction(e -> {
            String sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                objets.remove(sel);
            }
        });

        Label lblObjets = new Label("Objets & lots");
        lblObjets.getStyleClass().add("side-card-title");

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

        HBox objetsActions = new HBox(8, btnAdd, btnDel);
        objetsActions.getStyleClass().add("bonus-row");
        objetsActions.setAlignment(Pos.CENTER_LEFT);

        VBox objetsCard = new VBox(10, lblObjets, listView, txtNew, objetsActions);
        objetsCard.getStyleClass().add("side-card");
        objetsCard.setAlignment(Pos.CENTER_LEFT);
        objetsCard.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(listView, Priority.ALWAYS);

        root = new VBox(18,
                potCard,
                bonusCard,
                objetsCard
        );
        root.getStyleClass().add("side-panel");
        root.setFillWidth(true);
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
