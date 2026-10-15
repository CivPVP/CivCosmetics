package net.civmc.civcosmetics;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** One store delivery or refund, serializable for the on-disk retry queue. */
record StoreOp(String kind, @Nullable UUID uuid, @Nullable Rarity rarity, int amount, String transactionId, String packageId) {
    static final String REFUND = "refund";

    String describe() {
        return kind + " " + transactionId + (REFUND.equals(kind) ? "" : "/" + packageId + " for " + uuid);
    }

    String serialize() {
        return String.join("|", kind, uuid == null ? "-" : uuid.toString(), rarity == null ? "-" : rarity.id,
            Integer.toString(amount), transactionId, packageId);
    }

    static @Nullable StoreOp parse(String line) {
        String[] parts = line.trim().split("\\|", -1);
        if (parts.length != 6) return null;
        UUID uuid = "-".equals(parts[1]) ? null : Store.parseUuid(parts[1]);
        Rarity rarity = "-".equals(parts[2]) ? null : Rarity.parse(parts[2]);
        int amount;
        try {
            amount = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (!Store.validTransaction(parts[4])) return null;
        return switch (parts[0]) {
            case Store.KIND_TOKEN -> uuid != null && rarity != null && amount > 0 && Store.validPackage(parts[5])
                ? new StoreOp(parts[0], uuid, rarity, amount, parts[4], parts[5]) : null;
            case Store.KIND_LORE -> uuid != null && amount > 0 && Store.validPackage(parts[5])
                ? new StoreOp(parts[0], uuid, null, amount, parts[4], parts[5]) : null;
            case Store.KIND_FOUNDER -> uuid != null && Store.validPackage(parts[5])
                ? new StoreOp(parts[0], uuid, null, 1, parts[4], parts[5]) : null;
            case REFUND -> new StoreOp(REFUND, null, null, 0, parts[4], "-");
            default -> null;
        };
    }
}
