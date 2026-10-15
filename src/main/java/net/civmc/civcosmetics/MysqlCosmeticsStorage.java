package net.civmc.civcosmetics;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

final class MysqlCosmeticsStorage implements CosmeticsStorage {

    private static final String YAML_IMPORT_KEY = "yaml-import-complete";

    private final MysqlConfig config;
    private final Logger logger;
    private final Map<UUID, Map<CivCosmeticsPlugin.Category, Set<String>>> grantSnapshots = new ConcurrentHashMap<>();
    /** Fingerprint of the lore-history rows last loaded or written per player, so unchanged history is not rewritten. */
    private final Map<UUID, String> loreHistorySnapshots = new ConcurrentHashMap<>();
    /** Lore-history blobs that failed to deserialize on load, written back unchanged so a save never deletes them. */
    private final Map<UUID, List<byte[]>> unreadableLoreHistory = new ConcurrentHashMap<>();
    private HikariDataSource dataSource;

    MysqlCosmeticsStorage(MysqlConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    @Override
    public void initialize() {
        HikariDataSource newDataSource = null;
        try {
            newDataSource = new HikariDataSource(createPoolConfig());
            try (Connection connection = newDataSource.getConnection()) {
                migrate(connection);
            }
            dataSource = newDataSource;
        } catch (RuntimeException | SQLException e) {
            if (newDataSource != null) {
                newDataSource.close();
            }
            throw new IllegalStateException("Failed to initialize CivCosmetics MySQL storage", e);
        }
    }

    private HikariConfig createPoolConfig() {
        HikariConfig poolConfig = new HikariConfig();
        poolConfig.setPoolName("CivCosmetics");
        poolConfig.setDriverClassName("org.mariadb.jdbc.Driver");
        poolConfig.setJdbcUrl(config.jdbcUrl());
        poolConfig.setUsername(config.username());
        poolConfig.setPassword(config.password());
        poolConfig.setMaximumPoolSize(4);
        poolConfig.setMinimumIdle(0);
        poolConfig.setConnectionTimeout(Math.max(250, config.connectionTimeoutMs()));
        poolConfig.setValidationTimeout(Math.max(250, Math.min(config.connectionTimeoutMs(), 3_000)));
        poolConfig.setIdleTimeout(60_000);
        poolConfig.setInitializationFailTimeout(Math.max(250, config.connectionTimeoutMs()));
        poolConfig.addDataSourceProperty("connectTimeout", Integer.toString(config.connectionTimeoutMs()));
        poolConfig.addDataSourceProperty("socketTimeout", Integer.toString(Math.max(config.connectionTimeoutMs(), 5_000)));
        return poolConfig;
    }

    private Connection getConnection() throws SQLException {
        HikariDataSource currentDataSource = dataSource;
        if (currentDataSource == null || currentDataSource.isClosed()) {
            throw new IllegalStateException("CivCosmetics MySQL storage is not initialized");
        }
        return currentDataSource.getConnection();
    }

    private void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS civcosmetics_meta (
                    meta_key VARCHAR(64) NOT NULL,
                    meta_value TEXT NOT NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (meta_key)
                ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS civcosmetics_player_state (
                    uuid CHAR(36) NOT NULL,
                    selected_kill_effect VARCHAR(64),
                    selected_trail VARCHAR(64),
                    selected_tag VARCHAR(64),
                    custom_prefix TEXT,
                    saved_custom_prefix TEXT,
                    kill_message TEXT,
                    pending_kill_message TEXT,
                    lore_credits INT NOT NULL DEFAULT 0,
                    last_trim BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (uuid)
                ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS civcosmetics_grants (
                    uuid CHAR(36) NOT NULL,
                    category VARCHAR(32) NOT NULL,
                    grant_id VARCHAR(128) NOT NULL,
                    PRIMARY KEY (uuid, category, grant_id),
                    INDEX idx_civcosmetics_grants_uuid (uuid)
                ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS civcosmetics_lore_history (
                    uuid CHAR(36) NOT NULL,
                    slot_index INT NOT NULL,
                    item_blob LONGBLOB NOT NULL,
                    PRIMARY KEY (uuid, slot_index),
                    INDEX idx_civcosmetics_lore_uuid (uuid)
                ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                """);
            statement.executeUpdate("""
                INSERT INTO civcosmetics_meta (meta_key, meta_value)
                VALUES ('schema-version', '1')
                ON DUPLICATE KEY UPDATE meta_value = meta_value
                """);
        }
    }

    @Override
    public CivCosmeticsPlugin.PlayerData load(UUID uuid) {
        CivCosmeticsPlugin.PlayerData data = new CivCosmeticsPlugin.PlayerData(uuid);
        try (Connection connection = getConnection()) {
            loadState(connection, data);
            loadGrants(connection, data);
            loadLoreHistory(connection, data);
            grantSnapshots.put(uuid, normalizedGrantMap(data));
            return data;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load cosmetics for " + uuid, e);
        }
    }

    private void loadState(Connection connection, CivCosmeticsPlugin.PlayerData data) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT selected_kill_effect, selected_trail, selected_tag, custom_prefix, saved_custom_prefix,
                   kill_message, pending_kill_message, lore_credits, last_trim
            FROM civcosmetics_player_state
            WHERE uuid = ?
            """)) {
            statement.setString(1, data.uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return;
                }
                data.selectedKillEffect = result.getString("selected_kill_effect");
                data.selectedTrail = result.getString("selected_trail");
                data.selectedTag = result.getString("selected_tag");
                data.customPrefix = result.getString("custom_prefix");
                data.savedCustomPrefix = result.getString("saved_custom_prefix");
                data.killMessage = result.getString("kill_message");
                data.pendingKillMessage = result.getString("pending_kill_message");
                data.loreCredits = result.getInt("lore_credits");
                data.lastTrim = result.getLong("last_trim");
            }
        }
    }

    private void loadGrants(Connection connection, CivCosmeticsPlugin.PlayerData data) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT category, grant_id
            FROM civcosmetics_grants
            WHERE uuid = ?
            """)) {
            statement.setString(1, data.uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    CivCosmeticsPlugin.Category category = CivCosmeticsPlugin.Category.normal(result.getString("category"));
                    if (category != null) {
                        data.grant(category, result.getString("grant_id"));
                    }
                }
            }
        }
    }

    private void loadLoreHistory(Connection connection, CivCosmeticsPlugin.PlayerData data) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT item_blob
            FROM civcosmetics_lore_history
            WHERE uuid = ?
            ORDER BY slot_index ASC
            """)) {
            statement.setString(1, data.uuid.toString());
            List<byte[]> stored = new ArrayList<>();
            List<byte[]> unreadable = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    byte[] blob = result.getBytes("item_blob");
                    stored.add(blob);
                    ItemStack item = deserializeItem(blob);
                    if (item != null) {
                        data.loreHistory.add(item);
                    } else {
                        unreadable.add(blob);
                    }
                }
            }
            if (unreadable.isEmpty()) {
                unreadableLoreHistory.remove(data.uuid);
            } else {
                logger.warning("Keeping " + unreadable.size() + " unreadable lore history item(s) for " + data.uuid);
                unreadableLoreHistory.put(data.uuid, List.copyOf(unreadable));
            }
            loreHistorySnapshots.put(data.uuid, LoreHistoryFormat.fingerprint(stored));
        }
    }

    @Override
    public void save(CivCosmeticsPlugin.PlayerData data) {
        save(data, true);
    }

    @Override
    public void saveState(CivCosmeticsPlugin.PlayerData data) {
        save(data, false);
    }

    private void save(CivCosmeticsPlugin.PlayerData data, boolean syncGrants) {
        Map<CivCosmeticsPlugin.Category, Set<String>> currentGrants = syncGrants ? normalizedGrantMap(data) : null;
        Map<CivCosmeticsPlugin.Category, Set<String>> savedGrants = null;
        String savedLoreHistory = null;
        try (Connection connection = getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                saveState(connection, data);
                if (syncGrants && grantsChanged(data.uuid, currentGrants)) {
                    saveGrants(connection, data);
                    savedGrants = currentGrants;
                }
                savedLoreHistory = saveLoreHistory(connection, data);
                connection.commit();
                if (savedGrants != null) {
                    grantSnapshots.put(data.uuid, savedGrants);
                }
                if (savedLoreHistory != null) {
                    loreHistorySnapshots.put(data.uuid, savedLoreHistory);
                }
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save cosmetics for " + data.uuid, e);
        }
    }

    private boolean grantsChanged(UUID uuid, Map<CivCosmeticsPlugin.Category, Set<String>> currentGrants) {
        Map<CivCosmeticsPlugin.Category, Set<String>> savedGrants = grantSnapshots.get(uuid);
        return savedGrants == null || !savedGrants.equals(currentGrants);
    }

    private Map<CivCosmeticsPlugin.Category, Set<String>> normalizedGrantMap(CivCosmeticsPlugin.PlayerData data) {
        Map<CivCosmeticsPlugin.Category, Set<String>> grants = emptyGrantMap();
        for (var entry : data.grants.entrySet()) {
            Set<String> categoryGrants = grants.get(entry.getKey());
            for (String grant : entry.getValue()) {
                categoryGrants.add(grant.toLowerCase(Locale.ROOT));
            }
        }
        return grants;
    }

    private Map<CivCosmeticsPlugin.Category, Set<String>> emptyGrantMap() {
        Map<CivCosmeticsPlugin.Category, Set<String>> grants = new EnumMap<>(CivCosmeticsPlugin.Category.class);
        for (CivCosmeticsPlugin.Category category : CivCosmeticsPlugin.Category.values()) {
            grants.put(category, new HashSet<>());
        }
        return grants;
    }

    private void saveState(Connection connection, CivCosmeticsPlugin.PlayerData data) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO civcosmetics_player_state
                (uuid, selected_kill_effect, selected_trail, selected_tag, custom_prefix, saved_custom_prefix,
                 kill_message, pending_kill_message, lore_credits, last_trim)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                selected_kill_effect = VALUES(selected_kill_effect),
                selected_trail = VALUES(selected_trail),
                selected_tag = VALUES(selected_tag),
                custom_prefix = VALUES(custom_prefix),
                saved_custom_prefix = VALUES(saved_custom_prefix),
                kill_message = VALUES(kill_message),
                pending_kill_message = VALUES(pending_kill_message),
                lore_credits = VALUES(lore_credits),
                last_trim = VALUES(last_trim)
            """)) {
            statement.setString(1, data.uuid.toString());
            statement.setString(2, data.selectedKillEffect);
            statement.setString(3, data.selectedTrail);
            statement.setString(4, data.selectedTag);
            statement.setString(5, data.customPrefix);
            statement.setString(6, data.savedCustomPrefix);
            statement.setString(7, data.killMessage);
            statement.setString(8, data.pendingKillMessage);
            statement.setInt(9, data.loreCredits);
            statement.setLong(10, data.lastTrim);
            statement.executeUpdate();
        }
    }

    private void saveGrants(Connection connection, CivCosmeticsPlugin.PlayerData data) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM civcosmetics_grants WHERE uuid = ?")) {
            delete.setString(1, data.uuid.toString());
            delete.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement("""
            INSERT INTO civcosmetics_grants (uuid, category, grant_id)
            VALUES (?, ?, ?)
            """)) {
            for (var entry : data.grants.entrySet()) {
                for (String grant : entry.getValue()) {
                    insert.setString(1, data.uuid.toString());
                    insert.setString(2, entry.getKey().id);
                    insert.setString(3, grant.toLowerCase(Locale.ROOT));
                    insert.addBatch();
                }
            }
            insert.executeBatch();
        }
    }

    /** Rewrites the history rows only when they differ from what is stored. Returns the new fingerprint, or null if nothing was written. */
    private String saveLoreHistory(Connection connection, CivCosmeticsPlugin.PlayerData data) throws SQLException {
        List<byte[]> blobs = LoreHistoryFormat.rowsToStore(
            unreadableLoreHistory.getOrDefault(data.uuid, List.of()), data.loreHistory, this::serializeItem);
        if (blobs == null) {
            logger.warning("Not saving lore history for " + data.uuid + ": an item failed to serialize");
            return null;
        }
        String fingerprint = LoreHistoryFormat.fingerprint(blobs);
        if (fingerprint.equals(loreHistorySnapshots.get(data.uuid))) {
            return null;
        }
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM civcosmetics_lore_history WHERE uuid = ?")) {
            delete.setString(1, data.uuid.toString());
            delete.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement("""
            INSERT INTO civcosmetics_lore_history (uuid, slot_index, item_blob)
            VALUES (?, ?, ?)
            """)) {
            for (int index = 0; index < blobs.size(); index++) {
                insert.setString(1, data.uuid.toString());
                insert.setInt(2, index);
                insert.setBytes(3, blobs.get(index));
                insert.addBatch();
            }
            insert.executeBatch();
        }
        return fingerprint;
    }

    @Override
    public List<UUID> managedPlayerIds() {
        List<UUID> ids = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT uuid FROM civcosmetics_player_state
                 UNION
                 SELECT uuid FROM civcosmetics_grants
                 """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                ids.add(UUID.fromString(result.getString("uuid")));
            }
        } catch (SQLException | IllegalArgumentException e) {
            logger.warning("Failed to list managed CivCosmetics players: " + e.getMessage());
        }
        return ids;
    }

    @Override
    public boolean isYamlImportComplete() {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT meta_value FROM civcosmetics_meta WHERE meta_key = ?")) {
            statement.setString(1, YAML_IMPORT_KEY);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && Boolean.parseBoolean(result.getString("meta_value"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to check YAML import state", e);
        }
    }

    @Override
    public void markYamlImportComplete() {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 INSERT INTO civcosmetics_meta (meta_key, meta_value)
                 VALUES (?, 'true')
                 ON DUPLICATE KEY UPDATE meta_value = 'true'
                 """)) {
            statement.setString(1, YAML_IMPORT_KEY);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to mark YAML import complete", e);
        }
    }

    @Override
    public void close() {
        HikariDataSource currentDataSource = dataSource;
        dataSource = null;
        grantSnapshots.clear();
        loreHistorySnapshots.clear();
        unreadableLoreHistory.clear();
        if (currentDataSource != null) {
            currentDataSource.close();
        }
    }

    private byte[] serializeItem(ItemStack item) {
        try {
            return item.serializeAsBytes();
        } catch (RuntimeException e) {
            logger.warning("Failed to serialize lore history item: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private ItemStack deserializeItem(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            if (LoreHistoryFormat.isLegacyJavaSerialization(bytes)) {
                // Rows written before the serializeAsBytes format. They are rewritten in the new format on the next save.
                try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                    Object object = input.readObject();
                    return object instanceof ItemStack item ? item : null;
                }
            }
            return ItemStack.deserializeBytes(bytes);
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            logger.warning("Failed to deserialize lore history item: " + e.getMessage());
            return null;
        }
    }
}
