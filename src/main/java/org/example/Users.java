package org.example;

import javafx.collections.*;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.converter.IntegerStringConverter;

import java.util.Set;

/**
 * Table des participants : chaque ligne reçoit une couleur unique,
 * indépendamment du pseudo (→ palettes vraiment distinctes).
 */
public class Users {

    private final ObservableList<Participant> participants = FXCollections.observableArrayList();
    private final TableView<Participant>      table        = new TableView<>(participants);
    private final VBox                        root         = new VBox(6);

    private static final double GOLDEN_ANGLE = 137.50776405003785;
    private static final int DEFAULT_INSCRIPTION_K = Participant.DEFAULT_STAKE;

    public Users(){

        /* === Colonnes ================================================= */
        TableColumn<Participant,Boolean> colReplay = new TableColumn<>("Rejoue ?");
        TableColumn<Participant,Boolean> colPaid   = new TableColumn<>("Payé ?");
        TableColumn<Participant,String>  colNom    = new TableColumn<>("Nom");
        TableColumn<Participant,Integer> colKamas  = new TableColumn<>("Kamas");
        TableColumn<Participant,String>  colDon    = new TableColumn<>("Don");

        colNom  .setCellValueFactory(p -> p.getValue().nameProperty());
        colKamas.setCellValueFactory(p -> p.getValue().kamasProperty().asObject());
        colDon  .setCellValueFactory(p -> p.getValue().donationProperty());
        colReplay.setCellValueFactory(p -> p.getValue().willReplayProperty());
        colPaid.setCellValueFactory(p -> p.getValue().paidProperty());

        /* === Cellule colorée par INDEX de ligne ======================= */
        colNom.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty){
                super.updateItem(item, empty);
                if(empty || item==null){ setText(null); setStyle(""); return; }

                setText(item); setFont(Font.font("Arial", FontWeight.BOLD, 15));

                int idx = getIndex();
                double hue = (idx * GOLDEN_ANGLE) % 360;
                javafx.scene.paint.Color c = javafx.scene.paint.Color.hsb(hue,.85,.9).brighter();
                setStyle("-fx-text-fill:" + Theme.toWebColor(c) + ";");
            }
        });
        colKamas.setCellFactory(c -> new TextFieldTableCell<>(new IntegerStringConverter() {
            @Override
            public Integer fromString(String value) {
                return Kamas.parseFlexible(value, 0);
            }

            @Override
            public String toString(Integer value) {
                return Kamas.formatFr(value == null ? 0 : Math.max(0, value));
            }
        }));
        colDon  .setCellFactory(TextFieldTableCell.forTableColumn());

        colNom.setOnEditCommit(event -> {
            Participant participant = event.getRowValue();
            if (participant != null) {
                participant.setName(event.getNewValue());
            }
        });
        colKamas.setOnEditCommit(event -> {
            Participant participant = event.getRowValue();
            if (participant != null) {
                Integer newVal = event.getNewValue();
                int value = newVal == null ? 0 : Math.max(0, newVal);
                participant.setKamas(value);
            }
        });
        colDon.setOnEditCommit(event -> {
            Participant participant = event.getRowValue();
            if (participant != null) {
                participant.setDonation(event.getNewValue());
            }
        });

        colReplay.setCellFactory(column -> new ReplayTableCell());
        colReplay.setPrefWidth(90);
        colReplay.setEditable(true);

        colPaid.setCellFactory(column -> new PaidTableCell());
        colPaid.setPrefWidth(80);
        colPaid.setEditable(true);

        table.getColumns().setAll(colReplay, colPaid, colNom, colKamas, colDon);
        table.setEditable(true);
        table.setPrefHeight(520);
        Theme.styleTableView(table);

        /* === Formulaire =============================================== */
        TextField tNom   = new TextField(); tNom.setPromptText("Pseudo");  Theme.styleTextField(tNom);
        tNom.setPrefWidth(140);
        TextField tKamas = new TextField();
        tKamas.setPrefWidth(120);
        tKamas.setPromptText(Kamas.formatFr(DEFAULT_INSCRIPTION_K) + " (par défaut)");
        Theme.styleTextField(tKamas);
        TextField tDon   = new TextField(); tDon.setPromptText("Don");    Theme.styleTextField(tDon);
        tDon.setPrefWidth(140);

        tKamas.setTextFormatter(new TextFormatter<>(change -> {
            String next = change.getControlNewText();
            return next.matches("[0-9kKmMgG., _\u00A0]*") ? change : null;
        }));

        Button add = new Button("Ajouter");
        Theme.styleButton(add);
        add.setFocusTraversable(false);
        Button del = new Button("Supprimer");
        Theme.styleButton(del);
        del.setFocusTraversable(false);

        HBox inputRow = new HBox(6, tNom, tKamas, tDon);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        HBox buttonRow = new HBox(6, add, del);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        add.setOnAction(e -> {
            String n = tNom.getText().trim();
            if(!n.isEmpty()){
                String rawKamas = tKamas.getText();
                int stake = (rawKamas == null || rawKamas.trim().isEmpty())
                        ? DEFAULT_INSCRIPTION_K
                        : Kamas.parseFlexible(rawKamas, DEFAULT_INSCRIPTION_K);
                Participant participant = new Participant(n, 0, tDon.getText().trim());
                participant.setStake(stake > 0 ? stake : DEFAULT_INSCRIPTION_K);
                participant.setPaid(false);
                participant.setWillReplay(true);
                participants.add(participant);
                tNom.clear(); tKamas.clear(); tDon.clear();
            }
        });
        del.setOnAction(e -> {
            Participant sel = table.getSelectionModel().getSelectedItem();
            if(sel!=null) participants.remove(sel);
        });

        /* === Layout ==================================================== */
        Label lbl = new Label("Participants :");
        Theme.styleCapsuleLabel(lbl, "#4facfe", "#00f2fe");

        root.getChildren().addAll(lbl, table, inputRow, buttonRow);

        /* === Sync roue ↔ table ======================================== */
        participants.addListener((ListChangeListener<Participant>) change -> {
            // Reconstruit la roue dès que la liste change via Main
        });
    }

    /* === API ========================================================== */
    public ObservableList<Participant> getParticipants(){ return participants; }
    public ObservableList<String> getParticipantNames(){
        return FXCollections.observableArrayList(
                participants.stream()
                        .filter(p -> p.isWillReplay() && p.isPaid())
                        .map(Participant::getName)
                        .toList()
        );
    }
    public Node getRootPane(){ return root; }

    public void resetKamasToZero() {
        for (Participant participant : participants) {
            participant.setKamas(0);
        }
        table.refresh();
    }

    public Participant findByNameIgnoreCase(String name) {
        for (Participant participant : participants) {
            if (participant.getName().equalsIgnoreCase(name)) {
                return participant;
            }
        }
        return null;
    }

    public void removeParticipantsNotIn(Set<String> namesToKeep) {
        if (namesToKeep == null || namesToKeep.isEmpty()) {
            participants.clear();
        } else {
            participants.removeIf(participant -> namesToKeep.stream()
                    .noneMatch(n -> n.equalsIgnoreCase(participant.getName())));
        }
    }

    public void clearAll() {
        participants.clear();
    }

    /** TableCell used for the "Rejoue ?" column. */
    private static final class ReplayTableCell extends TableCell<Participant, Boolean> {
        private final CheckBox checkBox = new CheckBox();

        private ReplayTableCell() {
            setAlignment(Pos.CENTER);
            checkBox.setOnAction(event -> {
                Participant participant = getTableRow() == null ? null : getTableRow().getItem();
                if (participant == null) {
                    return;
                }
                participant.setWillReplay(checkBox.isSelected());
                updateBadge(participant.isWillReplay());
            });
        }

        @Override
        protected void updateItem(Boolean item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setGraphic(null);
                setStyle("");
                return;
            }
            Participant participant = getTableRow() == null ? null : getTableRow().getItem();
            boolean selected = participant != null && participant.isWillReplay();
            checkBox.setSelected(selected);
            setGraphic(checkBox);
            updateBadge(selected);
        }

        private void updateBadge(boolean selected) {
            setStyle(selected
                    ? "-fx-background-color: rgba(46, 204, 113, 0.35);"
                    : "-fx-background-color: rgba(231, 76, 60, 0.35);");
        }
    }

    /** TableCell used for the "Payé ?" column with automatic stake adjustments. */
    private static final class PaidTableCell extends TableCell<Participant, Boolean> {
        private final CheckBox checkBox = new CheckBox();

        private PaidTableCell() {
            setAlignment(Pos.CENTER);
            checkBox.setOnAction(event -> {
                Participant participant = getTableRow() == null ? null : getTableRow().getItem();
                if (participant == null) {
                    return;
                }
                boolean shouldBePaid = checkBox.isSelected();
                boolean currentlyPaid = participant.isPaid();
                if (currentlyPaid == shouldBePaid) {
                    updateBadge(shouldBePaid);
                    return;
                }
                participant.setPaid(shouldBePaid);
                int stakeAmount = DEFAULT_INSCRIPTION_K;
                participant.setStake(stakeAmount);
                if (shouldBePaid) {
                    participant.setKamas(Math.max(0, participant.getKamas() + stakeAmount));
                } else {
                    participant.setKamas(Math.max(0, participant.getKamas() - stakeAmount));
                }
                updateBadge(shouldBePaid);
            });
        }

        @Override
        protected void updateItem(Boolean item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setGraphic(null);
                setStyle("");
                return;
            }
            Participant participant = getTableRow() == null ? null : getTableRow().getItem();
            boolean selected = participant != null && participant.isPaid();
            checkBox.setSelected(selected);
            setGraphic(checkBox);
            updateBadge(selected);
        }

        private void updateBadge(boolean selected) {
            setStyle(selected
                    ? "-fx-background-color: rgba(46, 204, 113, 0.45);"
                    : "-fx-background-color: rgba(231, 76, 60, 0.45);");
        }
    }
}
