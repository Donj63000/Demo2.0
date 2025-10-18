package org.example;

import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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

    public synchronized void upsertRoundSnapshot(int roundId,
                                                 ObservableList<Participant> participants,
                                                 int bonus) throws IOException {
        ensureHeader();
        List<DonationEntry> allEntries = new ArrayList<>(loadAll());
        allEntries.removeIf(entry ->
                entry.getRoundId() == roundId
                        && (entry.getType() == DonationEntry.Type.DON
                        || entry.getType() == DonationEntry.Type.BONUS));

        LocalDateTime now = LocalDateTime.now();

        for (Participant participant : participants) {
            int amount = Math.max(0, participant.getKamas());
            if (amount <= 0) {
                continue;
            }
            allEntries.add(new DonationEntry(
                    now,
                    roundId,
                    DonationEntry.Type.DON,
                    participant.getName(),
                    amount
            ));
        }

        if (bonus > 0) {
            allEntries.add(new DonationEntry(
                    now,
                    roundId,
                    DonationEntry.Type.BONUS,
                    "__BONUS__",
                    bonus
            ));
        }

        writeAll(allEntries);
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

    public synchronized List<RoundRecord> getRoundRecords() {
        Map<Integer, RoundAccumulator> perRound = new TreeMap<>();
        for (DonationEntry entry : loadAll()) {
            RoundAccumulator accumulator = perRound.computeIfAbsent(
                    entry.getRoundId(),
                    RoundAccumulator::new
            );
            accumulator.touch(entry);
        }
        return perRound.values().stream()
                .map(RoundAccumulator::toRecord)
                .collect(Collectors.toList());
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

    private void writeAll(List<DonationEntry> entries) throws IOException {
        ensureHeader();
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        entries.stream()
                .filter(entry -> entry != null)
                .sorted(Comparator.comparing(DonationEntry::getTimestamp))
                .map(DonationEntry::toCsv)
                .forEach(lines::add);
        Files.write(
                LEDGER_FILE,
                (String.join(System.lineSeparator(), lines) + System.lineSeparator())
                        .getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE
        );
    }

    public static final class RoundRecord {
        private final int roundId;
        private final LocalDateTime timestamp;
        private final Map<String, Integer> donations;
        private final int bonus;
        private final String winner;
        private final int payout;

        private RoundRecord(int roundId,
                            LocalDateTime timestamp,
                            Map<String, Integer> donations,
                            int bonus,
                            String winner,
                            int payout) {
            this.roundId = roundId;
            this.timestamp = timestamp;
            this.donations = Map.copyOf(donations);
            this.bonus = Math.max(0, bonus);
            this.winner = winner;
            this.payout = Math.max(0, payout);
        }

        public int roundId() {
            return roundId;
        }

        public LocalDateTime timestamp() {
            return timestamp;
        }

        public Map<String, Integer> donations() {
            return donations;
        }

        public int bonus() {
            return bonus;
        }

        public String winner() {
            return winner;
        }

        public int payout() {
            return payout;
        }

        public int pot() {
            int totalDonations = donations.values().stream().mapToInt(Integer::intValue).sum();
            return totalDonations + bonus;
        }

        public boolean hasWinner() {
            return winner != null && !winner.isBlank();
        }
    }

    private static final class RoundAccumulator {
        private final int roundId;
        private final Map<String, Integer> donations = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        private int bonus;
        private String winner;
        private int payout;
        private LocalDateTime timestamp;

        private RoundAccumulator(int roundId) {
            this.roundId = roundId;
        }

        private void touch(DonationEntry entry) {
            if (entry.getTimestamp() != null) {
                if (timestamp == null || entry.getTimestamp().isAfter(timestamp)) {
                    timestamp = entry.getTimestamp();
                }
            }
            switch (entry.getType()) {
                case DON -> donations.put(entry.getPlayer(), entry.getAmount());
                case BONUS -> bonus = entry.getAmount();
                case PAYOUT -> {
                    winner = entry.getPlayer();
                    payout = entry.getAmount();
                }
            }
        }

        private RoundRecord toRecord() {
            LocalDateTime ts = timestamp == null ? LocalDateTime.now() : timestamp;
            return new RoundRecord(roundId, ts, donations, bonus, winner, payout);
        }
    }
}
