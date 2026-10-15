package net.civmc.civcosmetics;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/** Value types and input rules for store fulfilment (Tebex) and the purchase ledger. */
final class Store {

    /** Tebex transaction ids look like {@code tbx-26929122a16272-dd3c4a}; staff may use their own ids. */
    private static final Pattern TRANSACTION = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Pattern PACKAGE = Pattern.compile("[a-z0-9_-]{1,48}");

    static final String KIND_TOKEN = "token";
    static final String KIND_LORE = "lore";
    static final String KIND_FOUNDER = "founder";

    static final String STATUS_GRANTED = "granted";
    static final String STATUS_REFUNDED = "refunded";

    private Store() {
    }

    /** Accepts dashed or undashed UUIDs. */
    static @Nullable UUID parseUuid(@Nullable String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.length() == 32 && value.matches("[0-9a-fA-F]{32}")) {
            value = value.substring(0, 8) + "-" + value.substring(8, 12) + "-" + value.substring(12, 16) + "-"
                + value.substring(16, 20) + "-" + value.substring(20);
        }
        if (value.length() != 36) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static boolean validTransaction(@Nullable String raw) {
        return raw != null && TRANSACTION.matcher(raw).matches();
    }

    static boolean validPackage(@Nullable String raw) {
        return raw != null && PACKAGE.matcher(raw).matches();
    }

    enum Outcome {
        /** The grant was recorded and applied. */
        APPLIED,
        /** This transaction and package were already recorded; nothing changed. */
        DUPLICATE
    }

    enum RedeemOutcome { REDEEMED, NO_TOKEN, ALREADY_OWNED }

    record RefundResult(int rows, int tokensRemoved, int cosmeticsRevoked, int loreCreditsRemoved, int loreCreditShortfall) {
        boolean nothingToRefund() {
            return rows == 0;
        }
    }

    record LedgerEntry(String transactionId, String packageId, String uuid, String kind, @Nullable String rarity,
                       int amount, int spent, String status, long createdAtMillis, @Nullable String note) {
    }

    /** The parts of a player's data that other servers can change: grants, lore credits and tokens. */
    record Entitlements(Map<CivCosmeticsPlugin.Category, Set<String>> grants, int loreCredits,
                        Map<Rarity, Integer> tokens, long version) {
    }

    static Map<Rarity, Integer> emptyTokens() {
        Map<Rarity, Integer> tokens = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) tokens.put(rarity, 0);
        return tokens;
    }
}
