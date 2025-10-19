package org.example;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Fenêtre affichant l'historique des tirages.
 * Chaque tirage est ajouté sous forme de ligne descriptive.
 */
public class Historique extends Stage {

    private final ObservableList<HistoryEntry> lignes = FXCollections.observableArrayList();
    private final ListView<HistoryEntry> listView;
    private final Gains gains;
    private final DonationsLedger ledger;
    private Tooltip activeTooltip;

    private static final Path FILE = Path.of("loterie-historique.txt");
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Historique(Gains gains, DonationsLedger ledger) {
        this.gains = gains;
        this.ledger = ledger;
        setTitle("Historique des tirages");

        listView = new ListView<>(lignes);
        Theme.styleListView(listView);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(HistoryEntry item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.summary());
            }
        });
        listView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                HistoryEntry entry = listView.getSelectionModel().getSelectedItem();
                if (entry != null) {
                    showEntryTooltip(entry, event.getScreenX(), event.getScreenY());
                }
            }
        });
        listView.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> hideActiveTooltip());

        Button btnSuppr = new Button("Supprimer");
        Theme.styleButton(btnSuppr);
        btnSuppr.setOnAction(e -> {
            int idx = listView.getSelectionModel().getSelectedIndex();
            if (idx >= 0) {
                lignes.remove(idx);
                hideActiveTooltip();
            }
        });

        HBox actions = new HBox(10, btnSuppr);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(4, 0, 0, 0));

        VBox root = new VBox(12, listView, actions);
        root.setPadding(new Insets(14));
        Theme.styleDialogRoot(root);
        Scene scene = new Scene(root, 400, 300);
        setScene(scene);

        setOnHidden(e -> hideActiveTooltip());

        // Charge l'historique depuis le fichier s'il existe
        loadHistory();

        // Sauvegarde automatique à chaque modification
        lignes.addListener((ListChangeListener<HistoryEntry>) c -> saveHistory());
    }

    /**
     * Ajoute une ligne dans l'historique pour le tirage indiqué.
     * @param pseudo       gagnant (ou {@code null} si perdu)
     * @param potKamas     montant du pot pour ce tirage
     * @param participants participants admissibles lors du tirage
     */
    public void logResult(String pseudo, int potKamas, List<String> participants, int roundId) {
        LocalDateTime now = LocalDateTime.now();
        StringBuilder sb = new StringBuilder();
        sb.append(now.format(FORMATTER)).append(" - ");
        if (pseudo != null) {
            sb.append("Vainqueur : ").append(pseudo)
                    .append(" - Gains : ").append(potKamas).append(" k");
            if (!gains.getObjets().isEmpty()) {
                sb.append(" + ").append(String.join(", ", gains.getObjets()));
            }
        } else {
            sb.append("Perdu");
        }

        List<String> cleanedParticipants = participants == null
                ? List.of()
                : participants.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableList());

        HistoryEntry entry = new HistoryEntry(
                now,
                sb.toString(),
                pseudo,
                Math.max(0, potKamas),
                cleanedParticipants,
                roundId
        );
        lignes.add(entry);
    }

    private void loadHistory() {
        try {
            if (Files.exists(FILE)) {
                List<HistoryEntry> loaded = Files.readAllLines(FILE).stream()
                        .map(HistoryEntry::deserialize)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                lignes.setAll(loaded);
            }
        } catch (IOException ex) {
            System.err.println("Impossible de relire l'historique : " + ex.getMessage());
        }
    }

    private void saveHistory() {
        try {
            List<String> serialized = lignes.stream()
                    .map(HistoryEntry::serialize)
                    .collect(Collectors.toList());
            Files.write(FILE, serialized);
        } catch (IOException ex) {
            System.err.println("Impossible de sauvegarder l'historique : " + ex.getMessage());
        }
    }

    private void showEntryTooltip(HistoryEntry entry, double screenX, double screenY) {
        hideActiveTooltip();
        String details = buildDetails(entry);
        if (details.isBlank()) {
            return;
        }
        Tooltip tooltip = new Tooltip(details);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(460);
        tooltip.setAutoHide(true);
        tooltip.show(listView, screenX + 12, screenY + 12);
        activeTooltip = tooltip;

        PauseTransition hideDelay = new PauseTransition(Duration.seconds(6));
        hideDelay.setOnFinished(evt -> {
            if (activeTooltip == tooltip) {
                tooltip.hide();
                activeTooltip = null;
            }
        });
        hideDelay.play();
    }

    private void hideActiveTooltip() {
        if (activeTooltip != null) {
            activeTooltip.hide();
            activeTooltip = null;
        }
    }
    
    private String buildDetails(HistoryEntry entry) {
        if (entry == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (entry.summary() != null && !entry.summary().isBlank()) {
            sb.append(entry.summary());
        } else if (entry.timestamp() != null) {
            sb.append(entry.timestamp().format(FORMATTER));
        } else {
            sb.append("Tirage inconnu");
        }

        DonationsLedger.RoundRecord roundRecord = null;
        if (entry.roundId() != null) {
            roundRecord = ledger.findRoundRecord(entry.roundId()).orElse(null);
        }

        if (entry.timestamp() != null) {
            String stamp = entry.timestamp().format(FORMATTER);
            if (entry.summary() == null || !entry.summary().startsWith(stamp)) {
                sb.append("\nHorodatage : ").append(stamp);
            }
        }

        sb.append("\nTour : ");
        if (entry.roundId() != null) {
            sb.append("#").append(entry.roundId());
        } else {
            sb.append("Non référencé");
        }

        int potValue = roundRecord != null ? roundRecord.pot() : entry.potKamas();
        sb.append("\nPot total : ");
        if (potValue > 0) {
            sb.append(Kamas.formatFr(potValue)).append(" k");
        } else {
            sb.append("Non disponible");
        }

        if (roundRecord != null) {
            sb.append("\nBonus : ");
            if (roundRecord.bonus() > 0) {
                sb.append(Kamas.formatFr(roundRecord.bonus())).append(" k");
            } else {
                sb.append("—");
            }

            sb.append("\nPayout : ");
            if (roundRecord.hasWinner()) {
                sb.append(Kamas.formatFr(roundRecord.payout())).append(" k");
            } else {
                sb.append("—");
            }
        }

        String winner = roundRecord != null && roundRecord.hasWinner()
                ? roundRecord.winner()
                : entry.winner();
        sb.append("\nGagnant : ");
        if (winner != null && !winner.isBlank()) {
            sb.append(winner);
        } else {
            sb.append("Aucun (pot conservé)");
        }

        if (roundRecord != null && !roundRecord.donations().isEmpty()) {
            sb.append("\nContributions :");
            roundRecord.donations().forEach((name, amount) ->
                    sb.append("\n  - ").append(name).append(" : ").append(Kamas.formatFr(amount)).append(" k"));
        } else if (!entry.participants().isEmpty()) {
            sb.append("\nParticipants : ").append(String.join(", ", entry.participants()));
        }

        return sb.toString();
    }

    private record HistoryEntry(LocalDateTime timestamp,
                                String summary,
                                String winner,
                                int potKamas,
                                List<String> participants,
                                Integer roundId) {

        private static final String FIELD_SEPARATOR = "\t";
        private static final String LIST_SEPARATOR = ",";

        private static HistoryEntry deserialize(String rawLine) {
            if (rawLine == null) {
                return null;
            }
            String[] parts = rawLine.split(FIELD_SEPARATOR, -1);

            if (parts.length == 1) {
                String summary = parts[0];
                LocalDateTime ts = tryExtractTimestamp(summary);
                return new HistoryEntry(ts, summary, null, 0, List.of(), null);
            }

            LocalDateTime timestamp = parseTimestamp(parts[0]);
            String summary = decodeOrFallback(parts, 1, rawLine);
            String winner = decodeOrNull(parts, 2);
            int pot = parseIntSafe(parts, 3);
            List<String> participants = parseParticipants(parts);
            Integer roundId = parseIntegerOrNull(parts, 5);

            return new HistoryEntry(timestamp, summary, winner, pot, participants, roundId);
        }

        private String serialize() {
            String timestampToken = timestamp != null ? timestamp.format(FORMATTER) : "";
            String summaryToken = encode(summary);
            String winnerToken = encode(winner);
            String potToken = Integer.toString(Math.max(0, potKamas));
            String participantsToken = participants.isEmpty()
                    ? ""
                    : participants.stream()
                    .map(HistoryEntry::encode)
                    .collect(Collectors.joining(LIST_SEPARATOR));
            String roundIdToken = roundId == null ? "" : Integer.toString(Math.max(0, roundId));

            return String.join(FIELD_SEPARATOR,
                    timestampToken,
                    summaryToken,
                    winnerToken,
                    potToken,
                    participantsToken,
                    roundIdToken
            );
        }

        private static String encode(String value) {
            if (value == null || value.isEmpty()) {
                return "";
            }
            return Base64.getEncoder()
                    .encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }

        private static String decode(String value) {
            if (value == null || value.isEmpty()) {
                return "";
            }
            try {
                return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                return value;
            }
        }

        private static String decodeOrNull(String[] parts, int index) {
            if (index >= parts.length || parts[index].isEmpty()) {
                return null;
            }
            return decode(parts[index]);
        }

        private static String decodeOrFallback(String[] parts, int index, String fallback) {
            if (index >= parts.length || parts[index].isEmpty()) {
                return fallback;
            }
            return decode(parts[index]);
        }

        private static LocalDateTime parseTimestamp(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return LocalDateTime.parse(raw, FORMATTER);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }

        private static int parseIntSafe(String[] parts, int index) {
            if (index >= parts.length || parts[index].isBlank()) {
                return 0;
            }
            try {
                return Integer.parseInt(parts[index]);
            } catch (NumberFormatException ex) {
                return 0;
            }
        }

        private static List<String> parseParticipants(String[] parts) {
            if (parts.length <= 4 || parts[4].isEmpty()) {
                return List.of();
            }
            String[] tokens = parts[4].split(LIST_SEPARATOR, -1);
            List<String> participants = new ArrayList<>(tokens.length);
            for (String token : tokens) {
                if (token.isEmpty()) {
                    continue;
                }
                participants.add(decode(token));
            }
            return List.copyOf(participants);
        }

        private static Integer parseIntegerOrNull(String[] parts, int index) {
            if (index >= parts.length || parts[index].isBlank()) {
                return null;
            }
            try {
                return Integer.valueOf(parts[index]);
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        private static LocalDateTime tryExtractTimestamp(String summary) {
            if (summary == null || summary.length() < 19) {
                return null;
            }
            String prefix = summary.substring(0, 19);
            try {
                return LocalDateTime.parse(prefix, FORMATTER);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }
}
