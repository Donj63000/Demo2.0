package org.example;

import javafx.collections.*;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
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
    private final VBox                        root         = new VBox(10);

    private static final double GOLDEN_ANGLE = 137.50776405003785;

    public Users(){

        /* === Colonnes ================================================= */
        TableColumn<Participant,String>  colNom   = new TableColumn<>("Nom");
        TableColumn<Participant,Integer> colKamas = new TableColumn<>("Kamas");
        TableColumn<Participant,String>  colDon   = new TableColumn<>("Don");

        colNom  .setCellValueFactory(p -> p.getValue().nameProperty());
        colKamas.setCellValueFactory(p -> p.getValue().kamasProperty().asObject());
        colDon  .setCellValueFactory(p -> p.getValue().donationProperty());

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
        colKamas.setCellFactory(c -> new TextFieldTableCell<>(new IntegerStringConverter()));
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
                participant.setKamas(event.getNewValue() == null ? 0 : event.getNewValue());
            }
        });
        colDon.setOnEditCommit(event -> {
            Participant participant = event.getRowValue();
            if (participant != null) {
                participant.setDonation(event.getNewValue());
            }
        });

        table.getColumns().addAll(colNom, colKamas, colDon);
        table.setEditable(true);
        table.setPrefHeight(600);
        Theme.styleTableView(table);

        /* === Formulaire =============================================== */
        TextField tNom   = new TextField(); tNom.setPromptText("Pseudo");  Theme.styleTextField(tNom);
        TextField tKamas = new TextField(); tKamas.setPromptText("Kamas"); Theme.styleTextField(tKamas);
        TextField tDon   = new TextField(); tDon.setPromptText("Don");    Theme.styleTextField(tDon);

        Button add = new Button("Ajouter");   Theme.styleButton(add);
        Button del = new Button("Supprimer"); Theme.styleButton(del);

        add.setOnAction(e -> {
            String n = tNom.getText().trim();
            if(!n.isEmpty()){
                int k = parseSafeInt(tKamas.getText(), 0);
                participants.add(new Participant(n,k,tDon.getText().trim()));
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

        root.getChildren().addAll(lbl, table, tNom, tKamas, tDon, add, del);

        /* === Sync roue ↔ table ======================================== */
        participants.addListener((ListChangeListener<Participant>) change -> {
            // Reconstruit la roue dès que la liste change via Main
        });
    }

    /* === API ========================================================== */
    public ObservableList<Participant> getParticipants(){ return participants; }
    public ObservableList<String> getParticipantNames(){
        return FXCollections.observableArrayList(participants.stream().map(Participant::getName).toList());
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

    private static int parseSafeInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
