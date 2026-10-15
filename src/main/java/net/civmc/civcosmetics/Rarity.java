package net.civmc.civcosmetics;

import java.util.Locale;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

/**
 * Cosmetic rarity. Each rarity is unlocked in full by {@code civ.cosmetic.rarity.<id>} (granted by the store ranks),
 * and one cosmetic of a rarity can be redeemed with a token of that rarity.
 */
enum Rarity {
    COMMON("common", "Common", "&f", Material.IRON_NUGGET),
    RARE("rare", "Rare", "&9", Material.LAPIS_LAZULI),
    EPIC("epic", "Epic", "&5", Material.AMETHYST_SHARD),
    LEGENDARY("legendary", "Legendary", "&6", Material.GOLD_INGOT);

    final String id;
    final String label;
    final String color;
    final Material tokenIcon;

    Rarity(String id, String label, String color, Material tokenIcon) {
        this.id = id;
        this.label = label;
        this.color = color;
        this.tokenIcon = tokenIcon;
    }

    String permission() {
        return "civ.cosmetic.rarity." + id;
    }

    /** Colored label such as "&9Rare". */
    String display() {
        return color + label;
    }

    static @Nullable Rarity parse(@Nullable String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (Rarity rarity : values()) {
            if (rarity.id.equals(normalized)) return rarity;
        }
        return null;
    }
}
