package net.civmc.civcosmetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.jetbrains.annotations.Nullable;

/**
 * Deny-list for player-written item names and lore. Several plugins identify items by their name or lore text
 * (FactoryMod compaction, Bastion blocks, repair kits), so letting players write that text forges those items.
 */
final class LoreGuard {

    /** Used when config.yml has no {@code lore.denied-text} list. Keep in sync with the shipped config. */
    static final List<String> DEFAULT_DENIED_TEXT = List.of(
        "Compacted",                    // FactoryMod decompact_stack.compact_lore
        "Crate",                        // FactoryMod compaction crate input
        "Factory Repair Kit",           // FactoryMod repair kit name
        "Repairs FactoryMod factories", // FactoryMod repair kit lore
        "Printing Plate",               // FactoryMod printing plate name
        "Bastion",                      // Bastion block names/lore (City/Claim Bastion, "Bastion")
        "This bastion will protect",    // Bastion block lore
        "civstats-sword"                // CivStats sword-lore marker
    );

    /**
     * Used when config.yml has no {@code moderation.denied-text} list. For player-chosen titles and kill messages,
     * which must not pass as staff or server messages.
     */
    static final List<String> DEFAULT_MODERATION_TEXT = List.of(
        "Admin", "Staff", "Moderator", "Owner", "[Mod]", "[OP]", "[Dev]", "[Helper]", "[Server]"
    );

    private static final Pattern OBFUSCATED_CODE = Pattern.compile("[&§][kK]");

    private final List<String> denied = new ArrayList<>();
    private final List<String> displays = new ArrayList<>();

    /** Removes obfuscation codes. Repeats until stable: a single pass turns "&&kk" into "&k". */
    static String stripObfuscated(String raw) {
        String previous;
        do {
            previous = raw;
            raw = OBFUSCATED_CODE.matcher(raw).replaceAll("");
        } while (!raw.equals(previous));
        return raw;
    }

    LoreGuard(List<String> deniedText) {
        for (String entry : deniedText) {
            if (entry == null) continue;
            String normalized = normalize(entry);
            if (normalized.isEmpty()) continue;
            denied.add(normalized);
            displays.add(entry.trim());
        }
    }

    /**
     * Returns the configured entry that {@code plainText} contains, or null. Matching ignores case, colour and
     * formatting codes, whitespace and invisible (format/control) characters.
     */
    @Nullable String deniedMatch(String plainText) {
        String normalized = normalize(plainText);
        if (normalized.isEmpty()) return null;
        for (int i = 0; i < denied.size(); i++) {
            if (normalized.contains(denied.get(i))) return displays.get(i);
        }
        return null;
    }

    static String normalize(String text) {
        if (text == null) return "";
        String stripped = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text));
        if (stripped == null) return "";
        StringBuilder out = new StringBuilder(stripped.length());
        stripped.codePoints().forEach(cp -> {
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp) || Character.isISOControl(cp)) return;
            int type = Character.getType(cp);
            if (type == Character.FORMAT || type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK) return;
            out.appendCodePoint(cp);
        });
        return out.toString().toLowerCase(Locale.ROOT);
    }
}
