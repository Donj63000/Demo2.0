package org.example;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TitledPane;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Dialog allowing the operator to select which players paid for the current round,
 * optionally prune previous participants, and capture new entrants at the same time.
 */
public final class NouveauxPayantsDialog extends Stage {

    public static final class Payant {
        public final String name;
        public final int kamas;

        public Payant(String name, int kamas) {
            this.name = name;
            this.kamas = kamas;
        }
    }

    public static final class Result {
        public final List<Payant> payants;
        public final boolean retirerAnciensNonPayants;

        public Result(List<Payant> payants, boolean retirerAnciensNonPayants) {
            this.payants = payants;
            this.retirerAnciensNonPayants = retirerAnciensNonPayants;
        }
    }

    private static final class Row {
        final BooleanProperty paid = new SimpleBooleanProperty(false);
        final StringProperty name = new SimpleStringProperty("");
        final IntegerProperty kamas = new SimpleIntegerProperty(0);

        Row(String name, int kamas) {
            this.name.set(name);
            this.kamas.set(kamas);
        }
    }

    public NouveauxPayantsDialog(ObservableList<Participant> participantsCourants,
                                 Consumer<Result> onValidate) {
        setTitle("Nouveaux payants");
        initModality(Modality.APPLICATION_MODAL);

        ObservableList<Row> rows = FXCollections.observableArrayList();
        for (Participant participant : participantsCourants) {
            rows.add(new Row(participant.getName(), 0));
        }

        TableView<Row> table = new TableView<>(rows);
        table.setEditable(true);
        Theme.styleTableView(table);

        TableColumn<Row, Boolean> colPaid = new TableColumn<>("Payé ?");
        colPaid.setCellValueFactory(cellData -> cellData.getValue().paid);
        colPaid.setCellFactory(tc -> {
            CheckBoxTableCell<Row, Boolean> cell = new CheckBoxTableCell<>();
            cell.setAlignment(javafx.geometry.Pos.CENTER);
            return cell;
        });
        colPaid.setPrefWidth(80);
        colPaid.setEditable(true);

        TableColumn<Row, String> colName = new TableColumn<>("Joueur");
        colName.setCellValueFactory(cellData -> cellData.getValue().name);
        colName.setCellFactory(TextFieldTableCell.forTableColumn());
        colName.setPrefWidth(220);

        TableColumn<Row, Integer> colKamas = new TableColumn<>("Mise (k)");
        colKamas.setCellValueFactory(cellData -> cellData.getValue().kamas.asObject());
        colKamas.setCellFactory(column -> {
            TextFieldTableCell<Row, Integer> cell =
                    new TextFieldTableCell<>(new IntegerStringConverter() {
                        @Override
                        public Integer fromString(String value) {
                            return Kamas.parseFlexible(value, 0);
                        }

                        @Override
                        public String toString(Integer value) {
                            return Kamas.formatFr(value == null ? 0 : Math.max(0, value));
                        }
                    });
            cell.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            return cell;
        });
        colKamas.setPrefWidth(120);

        table.getColumns().setAll(colPaid, colName, colKamas);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TextField tfName = new TextField();
        tfName.setPromptText("Nom");
        Theme.styleTextField(tfName);
        tfName.setPrefWidth(160);

        TextField tfKamas = new TextField();
        tfKamas.setPromptText("Mise");
        Theme.styleTextField(tfKamas);
        tfKamas.setPrefWidth(100);
        tfKamas.setTextFormatter(new TextFormatter<>(change -> {
            String next = change.getControlNewText();
            return next.matches("[0-9kKmMgG., _\u00A0]*") ? change : null;
        }));

        Button btnAdd = new Button("Ajouter");
        Theme.styleButton(btnAdd);
        btnAdd.setOnAction(event -> {
            String name = tfName.getText() == null ? "" : tfName.getText().trim();
            int kamas = Kamas.parseFlexible(tfKamas.getText(), 0);
            if (name.isBlank()) {
                alert("Nom manquant");
                return;
            }
            if (kamas <= 0) {
                alert("La mise doit être un entier > 0");
                return;
            }
            Row row = new Row(name, kamas);
            Row deduped = rowDedup(rows, row);
            if (!rows.contains(deduped)) {
                rows.add(deduped);
            }
            tfName.clear();
            tfKamas.clear();
        });

        Label lblAjouter = new Label("Ajouter :");
        lblAjouter.setTextFill(Theme.TEXT_DEFAULT);

        HBox addBar = new HBox(8, lblAjouter, tfName, tfKamas, btnAdd);
        addBar.setPadding(new Insets(0, 0, 6, 0));
        HBox.setHgrow(tfName, Priority.NEVER);
        HBox.setHgrow(tfKamas, Priority.NEVER);

        CheckBox cbRetirer = new CheckBox("Retirer les anciens de la liste qui n'ont pas payé ?");
        cbRetirer.setSelected(false);
        cbRetirer.setTextFill(Theme.TEXT_DEFAULT);

        Button btnValider = new Button("Valider");
        Theme.styleButton(btnValider);
        btnValider.setDefaultButton(true);
        btnValider.setOnAction(event -> {
            List<Row> valides = rows.stream()
                    .filter(row -> row.paid.get()
                            && !row.name.get().isBlank()
                            && row.kamas.get() > 0)
                    .collect(Collectors.toList());
            if (valides.isEmpty()) {
                alert("Aucun payant sélectionné avec une mise > 0");
                return;
            }

            Map<String, Integer> merged = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (Row row : valides) {
                merged.merge(row.name.get().trim(), row.kamas.get(), Integer::sum);
            }

            List<Payant> payants = merged.entrySet().stream()
                    .map(entry -> new Payant(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            onValidate.accept(new Result(payants, cbRetirer.isSelected()));
            close();
        });

        Button btnAnnuler = new Button("Annuler");
        Theme.styleButton(btnAnnuler);
        btnAnnuler.setCancelButton(true);
        btnAnnuler.setOnAction(event -> close());

        HBox actions = new HBox(10, btnValider, btnAnnuler);

        Label description = new Label("Coche les joueurs qui ont payé, renseigne leur mise pour ce tour, "
                + "et ajoute les nouveaux inscrits si nécessaire.");
        description.setWrapText(true);
        description.setTextFill(Theme.TEXT_DEFAULT);

        Label paneTitle = new Label("Sélection des payants");
        paneTitle.setTextFill(Theme.TEXT_DEFAULT);
        TitledPane pane = new TitledPane("Sélection des payants", table);
        pane.setExpanded(true);
        pane.setCollapsible(false);
        pane.setText(null);
        pane.setGraphic(paneTitle);
        pane.setStyle("-fx-background-color: rgba(5, 20, 60, 0.45);");

        VBox root = new VBox(12,
                description,
                pane,
                addBar,
                cbRetirer,
                actions);
        root.setPadding(new Insets(12));
        Theme.styleDialogRoot(root);

        setScene(new Scene(root, 720, 520));
    }

    private static void alert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private static Row rowDedup(ObservableList<Row> rows, Row candidate) {
        for (Row row : rows) {
            if (row.name.get().equalsIgnoreCase(candidate.name.get())) {
                row.kamas.set(row.kamas.get() + candidate.kamas.get());
                row.paid.set(true);
                return row;
            }
        }
        candidate.paid.set(true);
        return candidate;
    }
}
