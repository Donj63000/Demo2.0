package org.example;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Immutable ledger entry representing either a donation, a bonus or a payout.
 * Persisted inside loterie-dons.csv using a semi-colon separated format.
 */
public final class DonationEntry {

    public enum Type { DON, BONUS, PAYOUT }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final LocalDateTime timestamp;
    private final int roundId;
    private final Type type;
    private final String player;
    private final int amount;

    public DonationEntry(LocalDateTime timestamp, int roundId, Type type, String player, int amount) {
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.roundId = roundId;
        this.type = Objects.requireNonNull(type, "type");
        this.player = player == null ? "" : player;
        this.amount = amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getRoundId() {
        return roundId;
    }

    public Type getType() {
        return type;
    }

    public String getPlayer() {
        return player;
    }

    public int getAmount() {
        return amount;
    }

    public String toCsv() {
        return FORMATTER.format(timestamp)
                + ';' + roundId
                + ';' + type.name()
                + ';' + sanitize(player)
                + ';' + amount;
    }

    public static DonationEntry fromCsv(String line) {
        String[] parts = line.split(";", -1);
        if (parts.length < 5) {
            throw new IllegalArgumentException("Invalid ledger line: " + line);
        }
        return new DonationEntry(
                LocalDateTime.parse(parts[0], FORMATTER),
                Integer.parseInt(parts[1]),
                Type.valueOf(parts[2]),
                parts[3],
                Integer.parseInt(parts[4])
        );
    }

    private static String sanitize(String raw) {
        return raw == null ? "" : raw.replace(";", " ");
    }
}
