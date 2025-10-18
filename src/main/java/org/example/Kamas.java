package org.example;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

/**
 * Utilitaires pour parser et formatter les montants en kamas.
 */
public final class Kamas {

    private Kamas() {
        // Utility class
    }

    /**
     * Parse un montant de kamas en acceptant les formats courants (FR, Dofus, etc.).
     *
     * @param raw           valeur saisie par l'utilisateur
     * @param defaultValue  valeur de repli si la chaîne est invalide
     * @return montant parsé, borné entre 0 et Integer.MAX_VALUE
     */
    public static int parseFlexible(String raw, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }

        String cleaned = raw.trim()
                .toLowerCase()
                .replace('\u00A0', ' ') // espace insécable
                .replace(" ", "")
                .replace("_", "")
                .replace("kk", "k")     // 50kk -> 50k
                .replace("mk", "m");    // 1mk -> 1m

        if (cleaned.isEmpty()) {
            return defaultValue;
        }

        cleaned = cleaned.replace(',', '.');

        long multiplier = 1;
        char last = cleaned.charAt(cleaned.length() - 1);
        if (last == 'k') {
            multiplier = 1_000;
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        } else if (last == 'm') {
            multiplier = 1_000_000;
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        } else if (last == 'g') {
            multiplier = 1_000_000_000;
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        double baseValue;
        try {
            baseValue = Double.parseDouble(cleaned);
        } catch (NumberFormatException ex) {
            try {
                Number parsed = NumberFormat.getNumberInstance(Locale.FRANCE).parse(cleaned);
                baseValue = parsed.doubleValue();
            } catch (ParseException ignored) {
                return defaultValue;
            }
        }

        long result = Math.round(baseValue * multiplier);
        if (result < 0) {
            result = 0;
        } else if (result > Integer.MAX_VALUE) {
            result = Integer.MAX_VALUE;
        }
        return (int) result;
    }

    /**
     * Formatte un montant de kamas en style français (séparateur espace).
     */
    public static String formatFr(int value) {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.FRANCE);
        String formatted = nf.format(value);
        return formatted.replace('\u00A0', ' ');
    }
}
