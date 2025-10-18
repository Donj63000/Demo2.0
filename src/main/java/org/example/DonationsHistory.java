package org.example;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Window that aggregates ledger entries by round so operators can sanity check pots, winners,
 * and per-player contributions without digging through the raw CSV.
 */
public final class DonationsHistory extends Stage {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public DonationsHistory(DonationsLedger ledger, ReadOnlyIntegerProperty currentPot) {
        setTitle("Historique des dons");
        Objects.requireNonNull(currentPot, "currentPot");

        ObservableList<DonationsLedger.RoundRecord> rounds =
                FXCollections.observableArrayList(ledger.getRoundRecords());
        TableView<DonationsLedger.RoundRecord> roundsTable = buildRoundTable(rounds);
        TableView<Map.Entry<String, Integer>> participantsTable = buildParticipantsTable();
        TableView<DonationEntry> ledgerTable = buildLedgerTable(ledger);

        roundsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, selected) -> refreshParticipants(participantsTable, selected)
        );
        if (!rounds.isEmpty()) {
            roundsTable.getSelectionModel().selectLast();
        } else {
            refreshParticipants(participantsTable, null);
        }

        Label potLabel = new Label();
        potLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "Cagnotte : " + formatAmount(currentPot.get()) + " k",
                currentPot
        ));
        Theme.styleCapsuleLabel(potLabel, "#4776e6", "#8e54e9");

        VBox root = new VBox(12,
                potLabel,
                new TitledPane("Tours enregistrés", roundsTable),
                new TitledPane("Détails du tour sélectionné", participantsTable),
                new TitledPane("Journal brut (debug)", ledgerTable)
        );
        root.setPadding(new Insets(12));

        Scene scene = new Scene(root, 820, 680);
        setScene(scene);
    }

    private static TableView<DonationsLedger.RoundRecord> buildRoundTable(
            ObservableList<DonationsLedger.RoundRecord> rounds) {

        TableView<DonationsLedger.RoundRecord> table = new TableView<>(rounds);
        Theme.styleTableView(table);

        TableColumn<DonationsLedger.RoundRecord, Number> colRound = new TableColumn<>("Tour");
        colRound.setPrefWidth(70);
        colRound.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().roundId()));

        TableColumn<DonationsLedger.RoundRecord, String> colDate = new TableColumn<>("Horodatage");
        colDate.setPrefWidth(180);
        colDate.setCellValueFactory(cell -> new SimpleStringProperty(
                FORMAT.format(cell.getValue().timestamp())));

        TableColumn<DonationsLedger.RoundRecord, String> colPot = new TableColumn<>("Pot (k)");
        colPot.setPrefWidth(120);
        colPot.setCellValueFactory(cell -> new SimpleStringProperty(
                formatAmount(cell.getValue().pot())));

        TableColumn<DonationsLedger.RoundRecord, String> colBonus = new TableColumn<>("Bonus (k)");
        colBonus.setPrefWidth(120);
        colBonus.setCellValueFactory(cell -> new SimpleStringProperty(
                formatAmount(cell.getValue().bonus())));

        TableColumn<DonationsLedger.RoundRecord, String> colWinner = new TableColumn<>("Gagnant");
        colWinner.setPrefWidth(180);
        colWinner.setCellValueFactory(cell -> {
            String winner = cell.getValue().winner();
            return new SimpleStringProperty(winner == null ? "—" : winner);
        });

        TableColumn<DonationsLedger.RoundRecord, String> colPayout = new TableColumn<>("Payout (k)");
        colPayout.setPrefWidth(120);
        colPayout.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().hasWinner() ? formatAmount(cell.getValue().payout()) : "—"));

        table.getColumns().setAll(colRound, colDate, colPot, colBonus, colWinner, colPayout);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        return table;
    }

    private static TableView<Map.Entry<String, Integer>> buildParticipantsTable() {
        TableView<Map.Entry<String, Integer>> table = new TableView<>();
        Theme.styleTableView(table);

        TableColumn<Map.Entry<String, Integer>, String> colPlayer = new TableColumn<>("Participant");
        colPlayer.setPrefWidth(220);
        colPlayer.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getKey()));

        TableColumn<Map.Entry<String, Integer>, String> colAmount = new TableColumn<>("Mise (k)");
        colAmount.setPrefWidth(140);
        colAmount.setCellValueFactory(cell -> new SimpleStringProperty(
                formatAmount(cell.getValue().getValue())));

        table.getColumns().setAll(colPlayer, colAmount);
        table.setPlaceholder(new Label("Sélectionne un tour pour voir le détail des mises."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        return table;
    }

    private static TableView<DonationEntry> buildLedgerTable(DonationsLedger ledger) {
        ObservableList<DonationEntry> data = FXCollections.observableArrayList(ledger.loadAll());
        TableView<DonationEntry> table = new TableView<>(data);
        Theme.styleTableView(table);

        TableColumn<DonationEntry, String> colDate = new TableColumn<>("Horodatage");
        colDate.setPrefWidth(180);
        colDate.setCellValueFactory(cell -> new SimpleStringProperty(
                FORMAT.format(cell.getValue().getTimestamp())));

        TableColumn<DonationEntry, Number> colRound = new TableColumn<>("Tour");
        colRound.setPrefWidth(70);
        colRound.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getRoundId()));

        TableColumn<DonationEntry, String> colType = new TableColumn<>("Type");
        colType.setPrefWidth(90);
        colType.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getType().name()));

        TableColumn<DonationEntry, String> colPlayer = new TableColumn<>("Joueur");
        colPlayer.setPrefWidth(180);
        colPlayer.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPlayer()));

        TableColumn<DonationEntry, Number> colAmount = new TableColumn<>("Montant (k)");
        colAmount.setPrefWidth(120);
        colAmount.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getAmount()));

        table.getColumns().setAll(colDate, colRound, colType, colPlayer, colAmount);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPrefHeight(200);
        return table;
    }

    private static void refreshParticipants(TableView<Map.Entry<String, Integer>> table,
                                            DonationsLedger.RoundRecord record) {
        ObservableList<Map.Entry<String, Integer>> data;
        if (record == null) {
            data = FXCollections.observableArrayList();
        } else {
            List<Map.Entry<String, Integer>> ordered = record.donations().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
            data = FXCollections.observableArrayList(ordered);
        }
        table.setItems(data);
    }

    private static String formatAmount(int amount) {
        return String.format("%,d", amount).replace(',', ' ');
    }
}
