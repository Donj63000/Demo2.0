package org.example;

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
import java.util.Map;

/**
 * Window presenting every ledger line plus cumulative donations per player.
 */
public final class DonationsHistory extends Stage {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public DonationsHistory(DonationsLedger ledger) {
        setTitle("Historique des dons");

        TableView<DonationEntry> ledgerTable = buildLedgerTable(ledger);
        TableView<Map.Entry<String, Integer>> totalsTable = buildTotalsTable(ledger);

        Label carryLabel = new Label("Cagnotte cumulée actuelle : "
                + formatAmount(ledger.computeCarryOver()) + " k");
        Theme.styleCapsuleLabel(carryLabel, "#4776e6", "#8e54e9");

        VBox root = new VBox(12,
                carryLabel,
                new TitledPane("Journal des dons", ledgerTable),
                new TitledPane("Cumuls par joueur", totalsTable)
        );
        root.setPadding(new Insets(12));

        Scene scene = new Scene(root, 760, 620);
        setScene(scene);
    }

    private TableView<DonationEntry> buildLedgerTable(DonationsLedger ledger) {
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
        colAmount.setPrefWidth(140);
        colAmount.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getAmount()));

        table.getColumns().setAll(colDate, colRound, colType, colPlayer, colAmount);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        return table;
    }

    private TableView<Map.Entry<String, Integer>> buildTotalsTable(DonationsLedger ledger) {
        ObservableList<Map.Entry<String, Integer>> data =
                FXCollections.observableArrayList(ledger.cumulativeByPlayer().entrySet());
        TableView<Map.Entry<String, Integer>> table = new TableView<>(data);
        Theme.styleTableView(table);

        TableColumn<Map.Entry<String, Integer>, String> colPlayer = new TableColumn<>("Joueur");
        colPlayer.setPrefWidth(200);
        colPlayer.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getKey()));

        TableColumn<Map.Entry<String, Integer>, Number> colTotal = new TableColumn<>("Cumul (k)");
        colTotal.setPrefWidth(140);
        colTotal.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getValue()));

        table.getColumns().setAll(colPlayer, colTotal);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        return table;
    }

    private static String formatAmount(int amount) {
        return String.format("%,d", amount).replace(',', ' ');
    }
}
