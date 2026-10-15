package net.civmc.civcosmetics;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Network-shared storage. Several servers read and write the same rows, so anything another server may change
 * (grants, lore credits, tokens) is written as a small delta, never as a whole-row snapshot.
 */
interface CosmeticsStorage extends AutoCloseable {

    void initialize();

    CivCosmeticsPlugin.PlayerData load(UUID uuid);

    /** Saves selections and lore history, plus grants added or removed on this server since the last load. */
    void save(CivCosmeticsPlugin.PlayerData data);

    /** Saves selections and lore history only. Never writes grants or lore credits. */
    void saveState(CivCosmeticsPlugin.PlayerData data);

    /** Writes a legacy YAML player in full, including lore credits. Only for the one-time YAML import. */
    void importLegacy(CivCosmeticsPlugin.PlayerData data);

    List<UUID> managedPlayerIds();

    boolean isYamlImportComplete();

    void markYamlImportComplete();

    Store.Entitlements loadEntitlements(UUID uuid);

    /** Current entitlement versions for the given players; players without a row are omitted. */
    Map<UUID, Long> entitlementVersions(Collection<UUID> uuids);

    /** Adds {@code delta} lore credits (may be negative; the balance never drops below zero). Returns the new balance. */
    int addLoreCredits(UUID uuid, int delta);

    /** Atomically spends one lore credit. Returns false when the balance is zero. */
    boolean spendLoreCredit(UUID uuid);

    Store.Outcome grantTokens(UUID uuid, Rarity rarity, int amount, String transactionId, String packageId);

    Store.Outcome grantLoreCredits(UUID uuid, int amount, String transactionId, String packageId);

    Store.Outcome recordFounder(UUID uuid, String transactionId, String packageId);

    /** Revokes everything granted under a transaction that has not been refunded yet. Repeating it is a no-op. */
    Store.RefundResult refund(String transactionId);

    Store.RedeemOutcome redeemToken(UUID uuid, Rarity rarity, CivCosmeticsPlugin.Category category, String cosmeticId);

    List<Store.LedgerEntry> ledger(UUID uuid, int limit);

    @Override
    void close();
}
