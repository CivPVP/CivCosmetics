package net.civmc.civcosmetics;

import java.util.List;
import java.util.UUID;

interface CosmeticsStorage extends AutoCloseable {

    void initialize();

    CivCosmeticsPlugin.PlayerData load(UUID uuid);

    void save(CivCosmeticsPlugin.PlayerData data);

    void saveState(CivCosmeticsPlugin.PlayerData data);

    List<UUID> managedPlayerIds();

    boolean isYamlImportComplete();

    void markYamlImportComplete();

    @Override
    void close();
}
