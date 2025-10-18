package org.example;

import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Append-only ledger used as the single source of truth for donations and payouts between launches.
 */
public final class DonationsLedger {

    private static final String HEADER = "ts;round;type;player;amount";
    private static final Path LEDGER_FILE = Path.of("loterie-dons.csv");

    private void ensureHeader() throws IOException {
        if (Files.exists(LEDGER_FILE)) {
            return;
        }
        Files.writeString(
                LEDGER_FILE,
                HEADER + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW
        );
    }

    public synchronized void appendRoundDonations(int roundId,
                                                  ObservableList<Participant> participants,
                                                  int bonus) throws IOException {
        ensureHeader();
        List<String> lines = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Participant participant : participants) {
            int amount = Math.max(0, participant.getKamas());
            if (amount <= 0) {
                continue;
            }
            DonationEntry entry = new DonationEntry(
                    now,
                    roundId,
                    DonationEntry.Type.DON,
                    participant.getName(),
                    amount
            );
            lines.add(entry.toCsv());
        }

        if (bonus > 0) {
            DonationEntry bonusEntry = new DonationEntry(
                    now,
                    roundId,
                    DonationEntry.Type.BONUS,
                    "__BONUS__",
                    bonus
            );
            lines.add(bonusEntry.toCsv());
        }

        if (!lines.isEmpty()) {
            Files.writeString(
                    LEDGER_FILE,
                    String.join(System.lineSeparator(), lines) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND
            );
        }
    }

    public synchronized void appendPayout(int roundId, String winner, int amount) throws IOException {
        ensureHeader();
        DonationEntry entry = new DonationEntry(
                LocalDateTime.now(),
                roundId,
                DonationEntry.Type.PAYOUT,
                winner,
                amount
        );
        Files.writeString(
                LEDGER_FILE,
                entry.toCsv() + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND
        );
    }

    public synchronized List<DonationEntry> loadAll() {
        if (!Files.exists(LEDGER_FILE)) {
            return List.of();
        }
        try {
            return Files.readAllLines(LEDGER_FILE, StandardCharsets.UTF_8)
                    .stream()
                    .skip(1)
                    .filter(line -> !line.isBlank())
                    .map(DonationEntry::fromCsv)
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            System.err.println("Impossible de lire le ledger : " + ex.getMessage());
            return List.of();
        }
    }

    public int computeCarryOver() {
        long incoming = 0;
        long outgoing = 0;

        for (DonationEntry entry : loadAll()) {
            switch (entry.getType()) {
                case DON, BONUS -> incoming += entry.getAmount();
                case PAYOUT -> outgoing += entry.getAmount();
            }
        }

        long carry = incoming - outgoing;
        if (carry < 0) {
            carry = 0;
        } else if (carry > Integer.MAX_VALUE) {
            carry = Integer.MAX_VALUE;
        }
        return (int) carry;
    }

    public int getNextRoundId() {
        return loadAll().stream()
                .mapToInt(DonationEntry::getRoundId)
                .max()
                .orElse(0) + 1;
    }

    public Map<String, Integer> cumulativeByPlayer() {
        Map<String, Integer> totals = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (DonationEntry entry : loadAll()) {
            if (entry.getType() == DonationEntry.Type.DON) {
                totals.merge(entry.getPlayer(), entry.getAmount(), Integer::sum);
            }
        }
        return totals;
    }

    /**
     * Clears the ledger so the carry-over resets to zero while preserving the CSV header.
     */
    public synchronized void resetCarryOver() throws IOException {
        Files.writeString(
                LEDGER_FILE,
                HEADER + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }
}
