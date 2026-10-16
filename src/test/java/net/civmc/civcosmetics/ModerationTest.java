package net.civmc.civcosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModerationTest {

    private final LoreGuard guard = new LoreGuard(LoreGuard.DEFAULT_MODERATION_TEXT);

    @Test
    void staffImpersonationIsRefusedThroughColourAndSpacing() {
        assertNotNull(guard.deniedMatch("&c[Mod]"));
        assertNotNull(guard.deniedMatch("&4A d m i n"));
        assertNotNull(guard.deniedMatch("&cSTAFF"));
        assertNotNull(guard.deniedMatch("[ op ]"));
        assertNotNull(guard.deniedMatch("Ad⁣min"));
    }

    @Test
    void ordinaryTextPasses() {
        assertNull(guard.deniedMatch("&bKing of PvP"));
        assertNull(guard.deniedMatch("{killer} destroyed {victim}"));
        assertNull(guard.deniedMatch(""));
    }

    @Test
    void obfuscationCodesCannotBeRebuiltFromNestedInput() {
        assertEquals("abc", LoreGuard.stripObfuscated("a&&kkb&K&&KKc"));
        assertEquals("x", LoreGuard.stripObfuscated("x§k"));
        assertEquals("&cRed", LoreGuard.stripObfuscated("&cRed"));
    }

    @Test
    void emptyListDeniesNothing() {
        assertNull(new LoreGuard(List.of()).deniedMatch("Admin"));
    }

    @Test
    void cooldownBlocksUntilItExpires() {
        Cooldowns cooldowns = new Cooldowns();
        UUID id = UUID.randomUUID();
        assertEquals(0L, cooldowns.remainingMillis(id, 1_000L, 10_000L));
        cooldowns.mark(id, 1_000L);
        assertEquals(10_000L, cooldowns.remainingMillis(id, 1_000L, 10_000L));
        assertEquals(4_000L, cooldowns.remainingMillis(id, 7_000L, 10_000L));
        assertEquals(0L, cooldowns.remainingMillis(id, 11_000L, 10_000L));
    }

    @Test
    void cooldownIsPerPlayerAndCanBeDisabledOrCleared() {
        Cooldowns cooldowns = new Cooldowns();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        cooldowns.mark(a, 0L);
        assertEquals(0L, cooldowns.remainingMillis(b, 1L, 10_000L));
        assertEquals(0L, cooldowns.remainingMillis(a, 1L, 0L));
        cooldowns.clear(a);
        assertEquals(0L, cooldowns.remainingMillis(a, 1L, 10_000L));
    }

    @Test
    void cooldownIsStrictIfTheClockGoesBackwards() {
        Cooldowns cooldowns = new Cooldowns();
        UUID id = UUID.randomUUID();
        cooldowns.mark(id, 50_000L);
        assertEquals(10_000L, cooldowns.remainingMillis(id, 40_000L, 10_000L));
    }

    @Test
    void legacyJavaSerializationIsRecognised() {
        assertTrue(LoreHistoryFormat.isLegacyJavaSerialization(new byte[] {(byte) 0xAC, (byte) 0xED, 0, 5}));
        assertFalse(LoreHistoryFormat.isLegacyJavaSerialization(new byte[] {0x1F, (byte) 0x8B, 8}));
        assertFalse(LoreHistoryFormat.isLegacyJavaSerialization(new byte[] {(byte) 0xAC}));
        assertFalse(LoreHistoryFormat.isLegacyJavaSerialization(null));
    }

    @Test
    void fingerprintDetectsAnyChangeToTheHistory() {
        List<byte[]> base = List.of(new byte[] {1, 2}, new byte[] {3});
        String fingerprint = LoreHistoryFormat.fingerprint(base);
        assertEquals(fingerprint, LoreHistoryFormat.fingerprint(List.of(new byte[] {1, 2}, new byte[] {3})));
        assertNotEquals(fingerprint, LoreHistoryFormat.fingerprint(List.of(new byte[] {3}, new byte[] {1, 2})));
        assertNotEquals(fingerprint, LoreHistoryFormat.fingerprint(List.of(new byte[] {1, 2})));
        assertNotEquals(fingerprint, LoreHistoryFormat.fingerprint(List.of(new byte[] {1}, new byte[] {2, 3})));
        assertNotEquals(fingerprint, LoreHistoryFormat.fingerprint(List.of()));
    }
}
