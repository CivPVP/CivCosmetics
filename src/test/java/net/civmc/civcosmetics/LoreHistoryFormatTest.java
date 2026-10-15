package net.civmc.civcosmetics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoreHistoryFormatTest {

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] serialize(String item) {
        return item.startsWith("broken") ? null : bytes(item);
    }

    @Test
    void unreadableRowsAreWrittenBackUnchanged() {
        byte[] unreadable = bytes("unreadable");

        List<byte[]> rows = LoreHistoryFormat.rowsToStore(List.of(unreadable), List.of("sword", "bow"),
            LoreHistoryFormatTest::serialize);

        assertNotNull(rows);
        assertEquals(3, rows.size());
        assertArrayEquals(unreadable, rows.get(0));
        assertArrayEquals(bytes("sword"), rows.get(1));
        assertArrayEquals(bytes("bow"), rows.get(2));
    }

    @Test
    void anItemThatFailsToSerializeAbortsTheRewrite() {
        assertNull(LoreHistoryFormat.rowsToStore(List.of(), List.of("sword", "broken", "bow"),
            LoreHistoryFormatTest::serialize));
    }
}
