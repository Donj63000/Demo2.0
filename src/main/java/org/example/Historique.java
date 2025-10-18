package org.example;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fenêtre affichant l'historique des tirages.
 * Chaque tirage est ajouté sous forme de ligne descriptive.
 */
public class Historique extends Stage {

    private final ObservableList<HistoryEntry> entries = FXCollections.observableArrayList();
    private final ListView<HistoryEntry> listView;
    private final Gains gains;
    private final DonationsLedger ledger;
    private Tooltip activeTooltip;
    private PauseTransition tooltipHideTimer;
    private static final Path FILE = Path.of("loterie-historique.txt");
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Historique(Gains gains, DonationsLedger ledger) {
        this.gains = gains;
        this.ledger = ledger;
        setTitle("Historique des tirages");

        listView = new ListView<>(entries);
        Theme.styleListView(listView);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(HistoryEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.label());
                    setTextFill(Theme.TEXT_DEFAULT);
                }
            }
        });
        listView.setOnMouseClicked(this::handleDoubleClick);

        Button btnSuppr = new Button("Supprimer");
        Theme.styleButton(btnSuppr);
        btnSuppr.setOnAction(e -> {
            int idx = listView.getSelectionModel().getSelectedIndex();
            if (idx >= 0) {
                entries.remove(idx);
            }
        });

        Button btnReset = new Button("Reset historique");
        Theme.styleButton(btnReset);
        btnReset.setOnAction(e -> {
            if (entries.isEmpty()) {
                return;
            }
            Alert confirm = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Remettre l'historique des tirages à zéro ?",
                    ButtonType.YES,
                    ButtonType.NO
            );
            confirm.setHeaderText("Confirmer la remise à zéro");
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                return;
            }
            hideActiveTooltip();
            entries.clear();
            try {
                Files.deleteIfExists(FILE);
            } catch (IOException ex) {
                System.err.println("Impossible de supprimer le fichier d'historique : " + ex.getMessage());
            }
        });

        Button btnDonations = new Button("Historique des dons");
        Theme.styleButton(btnDonations);
        btnDonations.setOnAction(e ->
                new DonationsHistory(ledger, gains.totalKamasProperty()).show());

        HBox actions = new HBox(10, btnSuppr, btnReset, btnDonations);

        VBox root = new VBox(10, listView, actions);
        root.setPadding(new Insets(10));
        Theme.styleDialogRoot(root);
        Scene scene = new Scene(root, 420, 320);
        setScene(scene);

        loadHistory();

        entries.addListener((ListChangeListener<HistoryEntry>) c -> saveHistory());
    }

    /** Ajoute une ligne dans l'historique pour le tirage indiqué. */
    public void logResult(int roundId,
                          String pseudo,
                          int payoutKamas,
                          int roundPot,
                          Map<String, Integer> donationsSnapshot,
                          int bonusSnapshot) {
        HistoryEntry entry = buildEntry(roundId, pseudo, payoutKamas, roundPot, donationsSnapshot, bonusSnapshot);
        entries.add(entry);
        listView.scrollTo(entry);
    }

    private void loadHistory() {
        if (!Files.exists(FILE)) {
            return;
        }
        try {
            List<HistoryEntry> loaded = Files.readAllLines(FILE, StandardCharsets.UTF_8)
                    .stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .map(this::deserializeEntry)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            entries.setAll(loaded);
        } catch (IOException ex) {
            System.err.println("Impossible de relire l'historique : " + ex.getMessage());
        }
    }

    private void saveHistory() {
        try {
            List<String> serialized = entries.stream()
                    .map(HistoryEntry::serialize)
                    .collect(Collectors.toList());
            Files.write(FILE, serialized, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.err.println("Impossible de sauvegarder l'historique : " + ex.getMessage());
        }
    }

    private void handleDoubleClick(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 2) {
            return;
        }
        HistoryEntry entry = listView.getSelectionModel().getSelectedItem();
        if (entry == null) {
            return;
        }
        showDetailsTooltip(entry, event);
    }

    private void showDetailsTooltip(HistoryEntry entry, MouseEvent event) {
        hideActiveTooltip();
        HistoryEntry enriched = enrichEntryFromLedger(entry);
        String message = enriched != null && enriched.hasDetails()
                ? enriched.detailsText()
                : "Aucun détail disponible pour ce tirage.";
        activeTooltip = new Tooltip(message);
        activeTooltip.setAutoHide(true);
        activeTooltip.setWrapText(true);
        activeTooltip.setPrefWidth(320);
        activeTooltip.show(
                listView.getScene().getWindow(),
                event.getScreenX() + 12,
                event.getScreenY() + 12
        );
        if (tooltipHideTimer != null) {
            tooltipHideTimer.stop();
        }
        tooltipHideTimer = new PauseTransition(Duration.seconds(5));
        tooltipHideTimer.setOnFinished(e -> {
            hideActiveTooltip();
        });
        tooltipHideTimer.playFromStart();
    }

    private void hideActiveTooltip() {
        if (tooltipHideTimer != null) {
            tooltipHideTimer.stop();
            tooltipHideTimer = null;
        }
        if (activeTooltip != null) {
            activeTooltip.hide();
            activeTooltip = null;
        }
    }

    private HistoryEntry enrichEntryFromLedger(HistoryEntry entry) {
        if (entry == null || entry.hasDetails()) {
            return entry;
        }
        Optional<DonationsLedger.RoundRecord> record = (entry.roundId() != null)
                ? ledger.findRoundRecord(entry.roundId())
                : findLegacyRecordForLabel(entry.label());
        if (record.isEmpty()) {
            return entry;
        }
        HistoryEntry enriched = fromRecord(entry.label(), record.get());
        int index = entries.indexOf(entry);
        if (index >= 0) {
            entries.set(index, enriched);
            listView.getSelectionModel().select(index);
        }
        return enriched;
    }

    private HistoryEntry buildEntry(int roundId,
                                    String pseudo,
                                    int payoutKamas,
                                    int roundPot,
                                    Map<String, Integer> donationsSnapshot,
                                    int bonusSnapshot) {
        Optional<DonationsLedger.RoundRecord> record = ledger.findRoundRecord(roundId);
        if (record.isPresent()) {
            return fromRecord(null, record.get());
        }
        Map<String, Integer> donations = orderDonations(donationsSnapshot);
        int bonus = Math.max(0, bonusSnapshot);
        int donationsTotal = donations.values().stream().mapToInt(Integer::intValue).sum();
        int payout = Math.max(0, payoutKamas);
        int inferredPot = Math.max(0, roundPot);
        if (inferredPot == 0) {
            inferredPot = Math.max(payout, donationsTotal + bonus);
        }
        LocalDateTime timestamp = LocalDateTime.now();
        String winner = pseudo;
        String label = buildLabel(timestamp, winner, payout);
        return new HistoryEntry(label, roundId, donations, bonus, winner, payout, inferredPot, timestamp);
    }

    private HistoryEntry fromRecord(String labelOverride, DonationsLedger.RoundRecord record) {
        String label = (labelOverride != null && !labelOverride.isBlank())
                ? labelOverride
                : buildLabel(record.timestamp(), record.winner(), record.payout());
        return new HistoryEntry(
                label,
                record.roundId(),
                orderDonations(record.donations()),
                record.bonus(),
                record.winner(),
                record.payout(),
                record.pot(),
                record.timestamp()
        );
    }

    private HistoryEntry deserializeEntry(String line) {
        Integer roundId = null;
        String label = line;
        int sep = line.indexOf('|');
        if (sep > 0) {
            String prefix = line.substring(0, sep);
            try {
                roundId = Integer.parseInt(prefix);
                label = line.substring(sep + 1);
            } catch (NumberFormatException ignored) {
                roundId = null;
                label = line;
            }
        }
        return createEntry(roundId, label);
    }

    private HistoryEntry createEntry(Integer roundId, String label) {
        if (roundId == null) {
            return findLegacyRecordForLabel(label)
                    .map(record -> fromRecord(label, record))
                    .orElseGet(() -> HistoryEntry.withoutMetadata(label));
        }
        Optional<DonationsLedger.RoundRecord> record = ledger.findRoundRecord(roundId);
        if (record.isEmpty()) {
            return new HistoryEntry(label, roundId, Map.of(), 0, null, 0, 0, null);
        }
        return fromRecord(label, record.get());
    }

    private Optional<DonationsLedger.RoundRecord> findLegacyRecordForLabel(String label) {
        if (label == null || label.isBlank()) {
            return Optional.empty();
        }
        List<DonationsLedger.RoundRecord> candidates = new ArrayList<>(ledger.getRoundRecords());
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> expectedWinner = extractWinnerFromLabel(label);
        if (expectedWinner.isPresent()) {
            String winner = expectedWinner.get();
            candidates = candidates.stream()
                    .filter(record -> record.hasWinner() && winner.equalsIgnoreCase(record.winner()))
                    .collect(Collectors.toCollection(ArrayList::new));
        } else if (labelIndicatesLoss(label)) {
            candidates = candidates.stream()
                    .filter(record -> !record.hasWinner())
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        Optional<Integer> expectedAmount = extractAmountFromLabel(label);
        if (expectedAmount.isPresent() && !candidates.isEmpty()) {
            int amount = expectedAmount.get();
            List<DonationsLedger.RoundRecord> amountMatches = candidates.stream()
                    .filter(record -> record.payout() == amount || record.pot() == amount)
                    .collect(Collectors.toCollection(ArrayList::new));
            if (!amountMatches.isEmpty()) {
                candidates = amountMatches;
            }
        }

        Optional<LocalDateTime> timestamp = parseTimestampFromLabel(label);
        if (timestamp.isPresent() && !candidates.isEmpty()) {
            LocalDateTime labelTs = timestamp.get();
            DonationsLedger.RoundRecord closest = null;
            long bestDistanceSeconds = Long.MAX_VALUE;
            final long maxDriftSeconds = 15 * 60;
            for (DonationsLedger.RoundRecord candidate : candidates) {
                LocalDateTime recordTs = candidate.timestamp();
                long drift = Math.abs(java.time.Duration.between(labelTs, recordTs).toSeconds());
                if (drift < bestDistanceSeconds) {
                    bestDistanceSeconds = drift;
                    closest = candidate;
                }
            }
            if (closest != null && bestDistanceSeconds <= maxDriftSeconds) {
                return Optional.of(closest);
            }
        }

        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }

        return Optional.empty();
    }

    private Optional<LocalDateTime> parseTimestampFromLabel(String label) {
        if (label == null || label.length() < 19) {
            return Optional.empty();
        }
        String prefix = label.substring(0, Math.min(label.length(), 19)).trim();
        try {
            return Optional.of(LocalDateTime.parse(prefix, FORMATTER));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<String> extractWinnerFromLabel(String label) {
        if (label == null) {
            return Optional.empty();
        }
        Matcher matcher = WINNER_PATTERN.matcher(label);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String winner = matcher.group(1);
        if (winner == null) {
            return Optional.empty();
        }
        return Optional.of(winner.trim());
    }

    private boolean labelIndicatesLoss(String label) {
        return label != null && label.toLowerCase().contains("perdu");
    }

    private Optional<Integer> extractAmountFromLabel(String label) {
        if (label == null) {
            return Optional.empty();
        }
        Matcher matcher = AMOUNT_PATTERN.matcher(label);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String rawAmount = matcher.group(1);
        if (rawAmount == null || rawAmount.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawAmount.replace(" ", "").replace(",", "");
        try {
            return Optional.of(Integer.parseInt(normalized));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private Map<String, Integer> orderDonations(Map<String, Integer> donations) {
        if (donations == null || donations.isEmpty()) {
            return Map.of();
        }
        return donations.entrySet().stream()
                .sorted(
                        Map.Entry.<String, Integer>comparingByValue().reversed()
                                .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER)
                )
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> b,
                        LinkedHashMap::new
                ));
    }

    private String buildLabel(LocalDateTime timestamp, String winner, int payout) {
        LocalDateTime ts = timestamp != null ? timestamp : LocalDateTime.now();
        String prefix = ts.format(FORMATTER);
        if (winner != null && !winner.isBlank()) {
            return prefix + " - Vainqueur : " + winner + " - Gains : "
                    + formatAmount(Math.max(0, payout)) + " k";
        }
        return prefix + " - Perdu";
    }

    private static String formatAmount(int amount) {
        return String.format("%,d", Math.max(0, amount)).replace(',', ' ');
    }

    private static final Pattern WINNER_PATTERN =
            Pattern.compile("Vainqueur\\s*:\\s*([^\\-+]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("Gains\\s*:\\s*([0-9\\s,]+)\\s*k", Pattern.CASE_INSENSITIVE);

    private static final class HistoryEntry {
        private final String label;
        private final Integer roundId;
        private final Map<String, Integer> donations;
        private final int bonus;
        private final String winner;
        private final int payout;
        private final int pot;
        private final LocalDateTime timestamp;

        private HistoryEntry(String label,
                             Integer roundId,
                             Map<String, Integer> donations,
                             int bonus,
                             String winner,
                             int payout,
                             int pot,
                             LocalDateTime timestamp) {
            this.label = label;
            this.roundId = roundId;
            this.donations = Collections.unmodifiableMap(new LinkedHashMap<>(donations));
            this.bonus = Math.max(0, bonus);
            this.winner = winner;
            this.payout = Math.max(0, payout);
            this.pot = Math.max(0, pot);
            this.timestamp = timestamp;
        }

        static HistoryEntry withoutMetadata(String label) {
            return new HistoryEntry(label, null, Map.of(), 0, null, 0, 0, null);
        }

        String label() {
            return label;
        }

        Integer roundId() {
            return roundId;
        }

        boolean hasDetails() {
            return roundId != null
                    && (!donations.isEmpty() || bonus > 0 || (winner != null && !winner.isBlank()) || pot > 0);
        }

        String detailsText() {
            List<String> lines = new ArrayList<>();
            if (timestamp != null) {
                lines.add("Tirage du " + timestamp.format(FORMATTER));
            }
            if (roundId != null) {
                lines.add("Tour #" + roundId);
            }
            if (!donations.isEmpty()) {
                lines.add("Participants :");
                donations.forEach((name, amount) ->
                        lines.add(" - " + name + " : " + formatAmount(amount) + " k"));
            } else {
                lines.add("Participants : aucun");
            }
            if (bonus > 0) {
                lines.add("Bonus : " + formatAmount(bonus) + " k");
            }
            if (winner != null && !winner.isBlank()) {
                int displayedPayout = payout > 0 ? payout : pot;
                lines.add("Gagnant : " + winner + " (" + formatAmount(displayedPayout) + " k)");
            } else {
                lines.add("Gagnant : aucun");
            }
            if (pot > 0) {
                lines.add("Pot total : " + formatAmount(pot) + " k");
            }
            return String.join("\n", lines);
        }

        String serialize() {
            if (roundId == null) {
                return label;
            }
            return roundId + "|" + label;
        }
    }
}
