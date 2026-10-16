package net.civmc.civcosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs against a disposable MariaDB/MySQL database only when CIVCOSMETICS_TEST_DB_HOST is set (host:port). Two storage
 * instances stand in for two servers sharing the database. Optional: CIVCOSMETICS_TEST_DB_NAME/USER/PASSWORD.
 */
class MysqlStorageIntegrationTest {

    private static MysqlCosmeticsStorage citadel;
    private static MysqlCosmeticsStorage lobby;

    @BeforeAll
    static void connect() {
        String hostPort = System.getenv("CIVCOSMETICS_TEST_DB_HOST");
        Assumptions.assumeTrue(hostPort != null && !hostPort.isBlank(), "CIVCOSMETICS_TEST_DB_HOST not set; skipping storage tests");
        String[] parts = hostPort.split(":");
        MysqlConfig config = new MysqlConfig(true, parts[0], parts.length > 1 ? Integer.parseInt(parts[1]) : 3306,
            env("CIVCOSMETICS_TEST_DB_NAME", "civcosmetics_test"), env("CIVCOSMETICS_TEST_DB_USER", "root"),
            env("CIVCOSMETICS_TEST_DB_PASSWORD", "test"), false, 5000);
        citadel = new MysqlCosmeticsStorage(config, Logger.getLogger("citadel"));
        lobby = new MysqlCosmeticsStorage(config, Logger.getLogger("lobby"));
        citadel.initialize();
        lobby.initialize();
    }

    @AfterAll
    static void close() {
        if (citadel != null) citadel.close();
        if (lobby != null) lobby.close();
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String tx() {
        return "tbx-test-" + UUID.randomUUID().toString().substring(0, 13);
    }

    @Test
    void repeatedDeliveryIsIgnored() {
        UUID player = UUID.randomUUID();
        String tx = tx();
        assertEquals(Store.Outcome.APPLIED, citadel.grantTokens(player, Rarity.EPIC, 2, tx, "token_epic"));
        assertEquals(Store.Outcome.DUPLICATE, citadel.grantTokens(player, Rarity.EPIC, 2, tx, "token_epic"));
        assertEquals(2, citadel.loadEntitlements(player).tokens().get(Rarity.EPIC));

        String loreTx = tx();
        assertEquals(Store.Outcome.APPLIED, citadel.grantLoreCredits(player, 3, loreTx, "lore_3"));
        assertEquals(Store.Outcome.DUPLICATE, citadel.grantLoreCredits(player, 3, loreTx, "lore_3"));
        assertEquals(3, citadel.loadEntitlements(player).loreCredits());
    }

    @Test
    void racingDeliveriesOfOneTransactionApplyOnce() throws Exception {
        UUID player = UUID.randomUUID();
        String tx = tx();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Store.Outcome>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                MysqlCosmeticsStorage server = i % 2 == 0 ? citadel : lobby;
                calls.add(() -> server.grantLoreCredits(player, 1, tx, "netherite"));
            }
            int applied = 0;
            for (Future<Store.Outcome> result : pool.invokeAll(calls)) {
                if (result.get() == Store.Outcome.APPLIED) applied++;
            }
            assertEquals(1, applied);
            assertEquals(1, citadel.loadEntitlements(player).loreCredits());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void redeemingSpendsOneTokenAndUnlocksTheCosmetic() {
        UUID player = UUID.randomUUID();
        citadel.grantTokens(player, Rarity.RARE, 2, tx(), "token_rare");
        assertEquals(Store.RedeemOutcome.REDEEMED, lobby.redeemToken(player, Rarity.RARE, CivCosmeticsPlugin.Category.KILL_EFFECTS, "flame"));
        Store.Entitlements after = citadel.loadEntitlements(player);
        assertEquals(1, after.tokens().get(Rarity.RARE));
        assertTrue(after.grants().get(CivCosmeticsPlugin.Category.KILL_EFFECTS).contains("flame"));

        assertEquals(Store.RedeemOutcome.ALREADY_OWNED, citadel.redeemToken(player, Rarity.RARE, CivCosmeticsPlugin.Category.KILL_EFFECTS, "flame"));
        assertEquals(1, citadel.loadEntitlements(player).tokens().get(Rarity.RARE), "an owned cosmetic must not cost a token");
        assertEquals(Store.RedeemOutcome.NO_TOKEN, citadel.redeemToken(player, Rarity.LEGENDARY, CivCosmeticsPlugin.Category.TRAILS, "totem"));
    }

    @Test
    void refundRemovesUnspentTokensAndCosmeticsRedeemedWithThem() {
        UUID player = UUID.randomUUID();
        String kept = tx();
        String refunded = tx();
        citadel.grantTokens(player, Rarity.EPIC, 1, kept, "token_epic");
        citadel.grantTokens(player, Rarity.EPIC, 2, refunded, "token_epic");
        // The oldest token is spent first, so this redemption uses the purchase that is kept.
        assertEquals(Store.RedeemOutcome.REDEEMED, citadel.redeemToken(player, Rarity.EPIC, CivCosmeticsPlugin.Category.TAGS, "royal"));
        assertEquals(Store.RedeemOutcome.REDEEMED, citadel.redeemToken(player, Rarity.EPIC, CivCosmeticsPlugin.Category.TAGS, "titan"));

        Store.RefundResult result = lobby.refund(refunded);
        assertEquals(1, result.rows());
        assertEquals(1, result.tokensRemoved());
        assertEquals(1, result.cosmeticsRevoked());

        Store.Entitlements after = citadel.loadEntitlements(player);
        assertEquals(0, after.tokens().get(Rarity.EPIC));
        assertTrue(after.grants().get(CivCosmeticsPlugin.Category.TAGS).contains("royal"), "cosmetic from the kept purchase stays");
        assertFalse(after.grants().get(CivCosmeticsPlugin.Category.TAGS).contains("titan"), "cosmetic from the refunded purchase goes");

        assertTrue(citadel.refund(refunded).nothingToRefund(), "a repeated refund is a no-op");
        assertEquals("refunded", citadel.ledger(player, 10).stream()
            .filter(entry -> entry.transactionId().equals(refunded)).findFirst().orElseThrow().status());
    }

    @Test
    void loreRefundRemovesWhatIsLeftAndRecordsTheShortfall() {
        UUID player = UUID.randomUUID();
        String tx = tx();
        citadel.grantLoreCredits(player, 3, tx, "lore_3");
        assertTrue(citadel.spendLoreCredit(player));
        assertTrue(lobby.spendLoreCredit(player));
        Store.RefundResult result = citadel.refund(tx);
        assertEquals(1, result.loreCreditsRemoved());
        assertEquals(2, result.loreCreditShortfall());
        assertEquals(0, citadel.loadEntitlements(player).loreCredits());
        assertFalse(citadel.spendLoreCredit(player));
    }

    @Test
    void aStaleCopyOnAnotherServerCannotEraseAPurchase() {
        UUID player = UUID.randomUUID();
        CivCosmeticsPlugin.PlayerData lobbyCopy = lobby.load(player);
        lobbyCopy.selectedTrail = "hearts";

        // Delivered on Citadel while the player is on the lobby.
        citadel.grantLoreCredits(player, 1, tx(), "netherite");
        CivCosmeticsPlugin.PlayerData citadelCopy = citadel.load(player);
        citadelCopy.grant(CivCosmeticsPlugin.Category.TRAILS, "gold");
        citadel.save(citadelCopy);

        // The lobby saves its old copy, including a grant change of its own.
        lobbyCopy.grant(CivCosmeticsPlugin.Category.KILL_EFFECTS, "pig");
        lobby.save(lobbyCopy);

        Store.Entitlements after = citadel.loadEntitlements(player);
        assertEquals(1, after.loreCredits(), "credits survive a stale save");
        assertTrue(after.grants().get(CivCosmeticsPlugin.Category.TRAILS).contains("gold"), "the other server's grant survives");
        assertTrue(after.grants().get(CivCosmeticsPlugin.Category.KILL_EFFECTS).contains("pig"));
        assertEquals("hearts", citadel.load(player).selectedTrail);
    }

    @Test
    void aDeliveryThatArrivesAfterItsRefundGrantsNothing() {
        UUID player = UUID.randomUUID();
        String tx = tx();
        assertTrue(citadel.refund(tx).nothingToRefund());
        assertEquals(Store.Outcome.ALREADY_REFUNDED, citadel.grantTokens(player, Rarity.LEGENDARY, 1, tx, "token_legendary"));
        assertEquals(Store.Outcome.ALREADY_REFUNDED, citadel.grantLoreCredits(player, 3, tx, "lore_3"));
        Store.Entitlements after = citadel.loadEntitlements(player);
        assertEquals(0, after.tokens().get(Rarity.LEGENDARY));
        assertEquals(0, after.loreCredits());
        assertEquals("refunded", citadel.ledger(player, 5).get(0).status());
    }

    @Test
    void transactionIdsAreCaseSensitive() {
        UUID player = UUID.randomUUID();
        String tx = tx();
        assertEquals(Store.Outcome.APPLIED, citadel.grantLoreCredits(player, 1, tx, "lore_1"));
        assertEquals(Store.Outcome.APPLIED, citadel.grantLoreCredits(player, 1, tx.toUpperCase(), "lore_1"));
        assertEquals(2, citadel.loadEntitlements(player).loreCredits());
    }

    @Test
    void initializingAgainOnAnUpgradedDatabaseIsHarmless() {
        citadel.initialize();
    }

    @Test
    void changesBumpTheVersionOtherServersPoll() {
        UUID player = UUID.randomUUID();
        citadel.grantTokens(player, Rarity.COMMON, 1, tx(), "token_common");
        long before = lobby.entitlementVersions(List.of(player)).get(player);
        citadel.redeemToken(player, Rarity.COMMON, CivCosmeticsPlugin.Category.TRAILS, "hearts");
        assertTrue(lobby.entitlementVersions(List.of(player)).get(player) > before);
    }
}
