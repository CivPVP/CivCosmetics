package net.civmc.civcosmetics;

import java.util.ArrayList;
import java.util.List;

/**
 * Slot maths for the six-row cosmetics menus. The content area is the 7x4 block inside the border (slots 10-16,
 * 19-25, 28-34, 37-43); every layout here is mirror-symmetric around the centre column.
 */
final class MenuLayout {

    private MenuLayout() {
    }

    static final int CONTENT_COLUMNS = 7;
    static final int CONTENT_ROWS = 4;
    static final int CONTENT_CAPACITY = CONTENT_COLUMNS * CONTENT_ROWS;

    static int contentSlot(int row, int column) {
        return (row + 1) * 9 + column + 1;
    }

    /**
     * Symmetric placement in the content area: rows are centred vertically, an odd remainder is centred, and an even
     * remainder leaves the middle column empty so both halves mirror each other.
     */
    static List<Integer> symmetricSlots(int count, int perRow) {
        List<Integer> slots = new ArrayList<>();
        if (count <= 0) return slots;
        int width = Math.max(1, Math.min(CONTENT_COLUMNS, perRow));
        int rows = Math.min(CONTENT_ROWS, (count + width - 1) / width);
        int firstRow = (CONTENT_ROWS - rows) / 2;
        int remaining = Math.min(count, rows * width);
        for (int row = 0; row < rows; row++) {
            int inRow = Math.min(width, remaining);
            remaining -= inRow;
            for (int column : rowColumns(inRow)) slots.add(contentSlot(firstRow + row, column));
        }
        return slots;
    }

    /** Content columns (0-6) for {@code count} items centred in one row. Even counts skip the middle column. */
    static List<Integer> rowColumns(int count) {
        List<Integer> columns = new ArrayList<>();
        if (count % 2 == 1) {
            int start = (CONTENT_COLUMNS - count) / 2;
            for (int i = 0; i < count; i++) columns.add(start + i);
        } else {
            int half = count / 2;
            for (int i = half; i >= 1; i--) columns.add(3 - i);
            for (int i = 1; i <= half; i++) columns.add(3 + i);
        }
        return columns;
    }

    /** Up to 3 items per row on every other column (like the hub), for menus with a few large choices. */
    static List<Integer> spacedSlots(int count) {
        List<Integer> slots = new ArrayList<>();
        int rows = Math.min(CONTENT_ROWS, (count + 2) / 3);
        int firstRow = (CONTENT_ROWS - rows) / 2;
        int remaining = Math.min(count, rows * 3);
        for (int row = 0; row < rows; row++) {
            int inRow = Math.min(3, remaining);
            remaining -= inRow;
            List<Integer> columns = switch (inRow) {
                case 1 -> List.of(3);
                case 2 -> List.of(2, 4);
                default -> List.of(1, 3, 5);
            };
            for (int column : columns) slots.add(contentSlot(firstRow + row, column));
        }
        return slots;
    }
}
