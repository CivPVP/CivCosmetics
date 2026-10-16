package net.civmc.civcosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MenuLayoutTest {

    private static final Set<Integer> CONTENT = Set.of(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43);

    @Test
    void everyLayoutStaysInsideTheBorderAndIsMirrorSymmetric() {
        for (int perRow = 1; perRow <= 7; perRow++) {
            for (int count = 0; count <= MenuLayout.CONTENT_CAPACITY; count++) {
                List<Integer> slots = MenuLayout.symmetricSlots(count, perRow);
                assertEquals(Math.min(count, perRow * MenuLayout.CONTENT_ROWS), slots.size(), "count " + count + " perRow " + perRow);
                assertEquals(slots.size(), new HashSet<>(slots).size(), "no slot is used twice");
                for (int slot : slots) {
                    assertTrue(CONTENT.contains(slot), "slot " + slot + " is outside the content area");
                    int mirrored = (slot / 9) * 9 + (8 - slot % 9);
                    assertTrue(slots.contains(mirrored), "slot " + slot + " has no mirror for count " + count + " perRow " + perRow);
                }
            }
        }
    }

    @Test
    void fullPagesFillTheContentArea() {
        assertEquals(CONTENT, new HashSet<>(MenuLayout.symmetricSlots(28, 7)));
    }

    @Test
    void evenRowsSkipTheMiddleColumn() {
        assertEquals(List.of(0, 1, 2, 4, 5, 6), MenuLayout.rowColumns(6));
        assertEquals(List.of(2, 4), MenuLayout.rowColumns(2));
        assertEquals(List.of(1, 2, 3, 4, 5), MenuLayout.rowColumns(5));
    }

    @Test
    void spacedLayoutMatchesTheHub() {
        assertEquals(List.of(20, 22, 24, 29, 31, 33), MenuLayout.spacedSlots(6));
        for (int count = 1; count <= 9; count++) {
            List<Integer> slots = MenuLayout.spacedSlots(count);
            for (int slot : slots) {
                int mirrored = (slot / 9) * 9 + (8 - slot % 9);
                assertTrue(slots.contains(mirrored), "spaced slot " + slot + " has no mirror for count " + count);
            }
        }
    }
}
