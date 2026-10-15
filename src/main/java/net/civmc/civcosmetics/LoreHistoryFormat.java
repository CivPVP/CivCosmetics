package net.civmc.civcosmetics;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;

/** Helpers for the lore-history blobs stored in MariaDB. */
final class LoreHistoryFormat {

    private LoreHistoryFormat() {
    }

    /**
     * True for blobs written by the old {@code BukkitObjectOutputStream} (a Java serialization stream, magic
     * {@code 0xACED}). New rows hold {@code ItemStack#serializeAsBytes} output, which is gzip ({@code 0x1F8B}).
     */
    static boolean isLegacyJavaSerialization(byte[] blob) {
        return blob != null && blob.length >= 2 && (blob[0] & 0xFF) == 0xAC && (blob[1] & 0xFF) == 0xED;
    }

    /**
     * The rows to store for a player's lore history: the blobs that could not be read on load, unchanged, then the
     * serialized items. Unreadable rows are never dropped, so a save can not delete history it failed to load.
     *
     * @return The rows, or null if any item fails to serialize. The caller must then leave the stored rows alone.
     */
    static <T> List<byte[]> rowsToStore(List<byte[]> unreadable, List<T> items, Function<T, byte[]> serializer) {
        List<byte[]> rows = new ArrayList<>(unreadable);
        for (T item : items) {
            byte[] blob = serializer.apply(item);
            if (blob == null) {
                return null;
            }
            rows.add(blob);
        }
        return rows;
    }

    /**
     * A compact fingerprint of an ordered list of blobs (SHA-256 over length-prefixed contents). Two lists have
     * the same fingerprint only if they hold the same blobs in the same order, so it can stand in for the
     * stored rows when deciding whether a save has to rewrite them.
     */
    static String fingerprint(List<byte[]> blobs) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(4).putInt(blobs.size()).array());
            for (byte[] blob : blobs) {
                digest.update(ByteBuffer.allocate(4).putInt(blob.length).array());
                digest.update(blob);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
