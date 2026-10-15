package net.civmc.civcosmetics;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

final class YamlCosmeticsImporter {

    private YamlCosmeticsImporter() {
    }

    static void importOnce(CosmeticsStorage storage, File playerFolder, Logger logger) {
        if (playerFolder == null || !playerFolder.isDirectory()) {
            storage.markYamlImportComplete();
            return;
        }
        if (storage.isYamlImportComplete()) {
            return;
        }
        File[] files = playerFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            storage.markYamlImportComplete();
            return;
        }
        int imported = 0;
        for (File file : files) {
            String rawUuid = file.getName().substring(0, file.getName().length() - 4);
            try {
                CivCosmeticsPlugin.PlayerData data = loadYaml(UUID.fromString(rawUuid), file);
                storage.importLegacy(data);
                imported++;
            } catch (IllegalArgumentException e) {
                logger.warning("Skipping invalid CivCosmetics player file " + file.getName());
            } catch (RuntimeException e) {
                throw new IllegalStateException("Failed importing CivCosmetics YAML file " + file.getName(), e);
            }
        }
        storage.markYamlImportComplete();
        logger.info("Imported " + imported + " CivCosmetics YAML player files into MySQL.");
    }

    private static CivCosmeticsPlugin.PlayerData loadYaml(UUID uuid, File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        CivCosmeticsPlugin.PlayerData data = new CivCosmeticsPlugin.PlayerData(uuid);
        data.selectedKillEffect = yaml.getString("selected.kill-effect");
        data.selectedTrail = yaml.getString("selected.trail");
        data.selectedTag = yaml.getString("selected.tag");
        data.customPrefix = yaml.getString("prefix.custom");
        data.savedCustomPrefix = yaml.getString("prefix.saved-custom");
        data.killMessage = yaml.getString("kill-message");
        data.pendingKillMessage = yaml.getString("pending-kill-message");
        data.loreCredits = yaml.getInt("lore.credits", 0);
        data.lastTrim = yaml.getLong("trim.last-use", 0L);

        ConfigurationSection grants = yaml.getConfigurationSection("grants");
        if (grants != null) {
            for (String key : grants.getKeys(false)) {
                CivCosmeticsPlugin.Category category = CivCosmeticsPlugin.Category.normal(key);
                if (category != null) {
                    data.grants.get(category).addAll(grants.getStringList(key));
                }
            }
        }

        List<?> history = yaml.getList("lore.history", List.of());
        for (Object entry : history) {
            if (entry instanceof ItemStack stack) {
                data.loreHistory.add(stack);
            }
        }
        return data;
    }
}
