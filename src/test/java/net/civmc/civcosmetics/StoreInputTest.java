package net.civmc.civcosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoreInputTest {

    private static final UUID PLAYER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @Test
    void acceptsDashedAndUndashedUuids() {
        assertEquals(PLAYER, Store.parseUuid("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        assertEquals(PLAYER, Store.parseUuid("069a79f444e94726a5befca90e38aaf5"));
        assertNull(Store.parseUuid("Notch"));
        assertNull(Store.parseUuid("1-2-3-4-5"));
        assertNull(Store.parseUuid(null));
    }

    @Test
    void rejectsTransactionAndPackageIdsThatCouldBreakTheQueueOrSql() {
        assertTrue(Store.validTransaction("tbx-26929122a16272-dd3c4a"));
        assertFalse(Store.validTransaction("tbx|1"));
        assertFalse(Store.validTransaction(""));
        assertFalse(Store.validTransaction("x".repeat(65)));
        assertTrue(Store.validPackage("token_epic"));
        assertFalse(Store.validPackage("Token Epic"));
    }

    @Test
    void amountsAcceptAQuantityMultiplierWithinTheCap() {
        assertEquals(3, Store.parseAmount("3"));
        assertEquals(6, Store.parseAmount("3x2"));
        assertEquals(-1, Store.parseAmount("0"));
        assertEquals(-1, Store.parseAmount("3x0"));
        assertEquals(-1, Store.parseAmount("100x3"));
        assertEquals(-1, Store.parseAmount("-1"));
        assertEquals(-1, Store.parseAmount("{purchaseQuantity}"));
    }

    @Test
    void queuedOperationsRoundTrip() {
        StoreOp token = new StoreOp(Store.KIND_TOKEN, PLAYER, Rarity.EPIC, 2, "tbx-1", "token_epic");
        StoreOp lore = new StoreOp(Store.KIND_LORE, PLAYER, null, 3, "tbx-2", "lore_3");
        StoreOp founder = new StoreOp(Store.KIND_FOUNDER, PLAYER, null, 1, "tbx-3", "founder");
        StoreOp refund = new StoreOp(StoreOp.REFUND, null, null, 0, "tbx-1", "-");
        for (StoreOp op : new StoreOp[] {token, lore, founder, refund}) {
            assertEquals(op, StoreOp.parse(op.serialize()));
        }
        assertNull(StoreOp.parse("token|" + PLAYER + "|mythic|1|tbx-1|token_x"));
        assertNull(StoreOp.parse("garbage"));
    }

    @Test
    void raritiesParseCaseInsensitively() {
        assertEquals(Rarity.LEGENDARY, Rarity.parse("Legendary"));
        assertNull(Rarity.parse("mythic"));
        assertEquals("civ.cosmetic.rarity.rare", Rarity.RARE.permission());
    }
}
