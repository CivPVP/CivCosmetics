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
import java.util.Collection;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.sql.Timestamp;
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
                CREATE TABLE IF NOT EXISTS civcosmetics_purchases (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    transaction_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                    package_id VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                    uuid CHAR(36) NOT NULL,
                    kind VARCHAR(16) NOT NULL,
                    rarity VARCHAR(16) NULL,
                    amount INT NOT NULL,
                    spent INT NOT NULL DEFAULT 0,
                    status VARCHAR(16) NOT NULL,
                    note VARCHAR(255) NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (transaction_id, package_id),
                    UNIQUE KEY uq_civcosmetics_purchases_id (id),
                    INDEX idx_civcosmetics_purchases_uuid (uuid, kind, status)
                ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS civcosmetics_token_redemptions (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    uuid CHAR(36) NOT NULL,
                    transaction_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                    package_id VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                    rarity VARCHAR(16) NOT NULL,
                    category VARCHAR(32) NOT NULL,
                    grant_id VARCHAR(128) NOT NULL,
                    status VARCHAR(16) NOT NULL DEFAULT 'active',
                    redeemed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    INDEX idx_civcosmetics_redemptions_tx (transaction_id, package_id),
                    INDEX idx_civcosmetics_redemptions_uuid (uuid)
                ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS civcosmetics_refunded_transactions (
                    transaction_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                    refunded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (transaction_id)
                ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                """);
        }
        // Schema 2: other servers bump this when they change a player's grants, credits or tokens.
        if (!columnExists(connection, "civcosmetics_player_state", "entitlements_version")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE civcosmetics_player_state ADD COLUMN entitlements_version BIGINT NOT NULL DEFAULT 0");
            } catch (SQLException e) {
                // Another server starting at the same moment added it first (ER_DUP_FIELDNAME).
                if (e.getErrorCode() != 1060) throw e;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO civcosmetics_meta (meta_key, meta_value)
                VALUES ('schema-version', '2')
                ON DUPLICATE KEY UPDATE meta_value = '2'
                """);
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
            """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    @Override
    public CivCosmeticsPlugin.PlayerData load(UUID uuid) {
        CivCosmeticsPlugin.PlayerData data = new CivCosmeticsPlugin.PlayerData(uuid);
        try (Connection connection = getConnection()) {
            loadState(connection, data);
            loadGrants(connection, data);
            data.tokens.putAll(loadTokens(connection, uuid));
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
                   kill_message, pending_kill_message, lore_credits, last_trim, entitlements_version
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
                data.entitlementsVersion = result.getLong("entitlements_version");
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
                    saveGrantChanges(connection, data.uuid, grantSnapshots.get(data.uuid), currentGrants);
                    bumpVersion(connection, data.uuid);
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

    /** Lore credits are deliberately absent: they only change through atomic deltas, so a stale copy can't overwrite them. */
    private void saveState(Connection connection, CivCosmeticsPlugin.PlayerData data) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO civcosmetics_player_state
                (uuid, selected_kill_effect, selected_trail, selected_tag, custom_prefix, saved_custom_prefix,
                 kill_message, pending_kill_message, last_trim)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                selected_kill_effect = VALUES(selected_kill_effect),
                selected_trail = VALUES(selected_trail),
                selected_tag = VALUES(selected_tag),
                custom_prefix = VALUES(custom_prefix),
                saved_custom_prefix = VALUES(saved_custom_prefix),
                kill_message = VALUES(kill_message),
                pending_kill_message = VALUES(pending_kill_message),
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
            statement.setLong(9, data.lastTrim);
            statement.executeUpdate();
        }
    }

    /**
     * Writes only the grants this server added or removed since it last read them. Grants another server added in the
     * meantime are left alone. With no previous read (legacy import) it only adds.
     */
    private void saveGrantChanges(Connection connection, UUID uuid,
                                  Map<CivCosmeticsPlugin.Category, Set<String>> previous,
                                  Map<CivCosmeticsPlugin.Category, Set<String>> current) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                 "INSERT IGNORE INTO civcosmetics_grants (uuid, category, grant_id) VALUES (?, ?, ?)");
             PreparedStatement delete = connection.prepareStatement(
                 "DELETE FROM civcosmetics_grants WHERE uuid = ? AND category = ? AND grant_id = ?")) {
            for (CivCosmeticsPlugin.Category category : CivCosmeticsPlugin.Category.values()) {
                Set<String> now = current.get(category);
                Set<String> before = previous == null ? Set.of() : previous.get(category);
                for (String grant : now) {
                    if (before.contains(grant)) continue;
                    insert.setString(1, uuid.toString());
                    insert.setString(2, category.id);
                    insert.setString(3, grant);
                    insert.addBatch();
                }
                if (previous == null) continue;
                for (String grant : before) {
                    if (now.contains(grant)) continue;
                    delete.setString(1, uuid.toString());
                    delete.setString(2, category.id);
                    delete.setString(3, grant);
                    delete.addBatch();
                }
            }
            insert.executeBatch();
            delete.executeBatch();
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
    public void importLegacy(CivCosmeticsPlugin.PlayerData data) {
        try (Connection connection = getConnection()) {
            inTransaction(connection, () -> {
                saveState(connection, data);
                if (data.loreCredits > 0) {
                    // Set-to-at-least, so re-running a partly finished import never adds credits twice.
                    try (PreparedStatement credits = connection.prepareStatement(
                        "UPDATE civcosmetics_player_state SET lore_credits = GREATEST(lore_credits, ?) WHERE uuid = ?")) {
                        credits.setInt(1, data.loreCredits);
                        credits.setString(2, data.uuid.toString());
                        credits.executeUpdate();
                    }
                }
                saveGrantChanges(connection, data.uuid, null, normalizedGrantMap(data));
                saveLoreHistory(connection, data);
                return null;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to import cosmetics for " + data.uuid, e);
        }
    }

    @Override
    public Store.Entitlements loadEntitlements(UUID uuid) {
        CivCosmeticsPlugin.PlayerData data = new CivCosmeticsPlugin.PlayerData(uuid);
        try (Connection connection = getConnection()) {
            int credits = 0;
            long version = 0;
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT lore_credits, entitlements_version FROM civcosmetics_player_state WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        credits = result.getInt("lore_credits");
                        version = result.getLong("entitlements_version");
                    }
                }
            }
            loadGrants(connection, data);
            Map<CivCosmeticsPlugin.Category, Set<String>> grants = normalizedGrantMap(data);
            return new Store.Entitlements(grants, credits, loadTokens(connection, uuid), version);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load entitlements for " + uuid, e);
        }
    }

    private Map<Rarity, Integer> loadTokens(Connection connection, UUID uuid) throws SQLException {
        Map<Rarity, Integer> tokens = Store.emptyTokens();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT rarity, SUM(amount - spent) AS remaining
            FROM civcosmetics_purchases
            WHERE uuid = ? AND kind = 'token' AND status = 'granted'
            GROUP BY rarity
            """)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Rarity rarity = Rarity.parse(result.getString("rarity"));
                    if (rarity != null) tokens.put(rarity, Math.max(0, result.getInt("remaining")));
                }
            }
        }
        return tokens;
    }

    @Override
    public Map<UUID, Long> entitlementVersions(Collection<UUID> uuids) {
        Map<UUID, Long> versions = new HashMap<>();
        if (uuids.isEmpty()) return versions;
        List<UUID> all = new ArrayList<>(uuids);
        try (Connection connection = getConnection()) {
            for (int start = 0; start < all.size(); start += 200) {
                List<UUID> chunk = all.subList(start, Math.min(all.size(), start + 200));
                String placeholders = String.join(",", java.util.Collections.nCopies(chunk.size(), "?"));
                try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT uuid, entitlements_version FROM civcosmetics_player_state WHERE uuid IN (" + placeholders + ")")) {
                    for (int i = 0; i < chunk.size(); i++) statement.setString(i + 1, chunk.get(i).toString());
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            versions.put(UUID.fromString(result.getString("uuid")), result.getLong("entitlements_version"));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read entitlement versions", e);
        }
        return versions;
    }

    @Override
    public int addLoreCredits(UUID uuid, int delta) {
        try (Connection connection = getConnection()) {
            return inTransaction(connection, () -> addLoreCredits(connection, uuid, delta));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to change lore credits for " + uuid, e);
        }
    }

    private int addLoreCredits(Connection connection, UUID uuid, int delta) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO civcosmetics_player_state (uuid, lore_credits, entitlements_version)
            VALUES (?, GREATEST(0, ?), 1)
            ON DUPLICATE KEY UPDATE
                lore_credits = GREATEST(0, lore_credits + ?),
                entitlements_version = entitlements_version + 1
            """)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, delta);
            statement.setInt(3, delta);
            statement.executeUpdate();
        }
        return loreCredits(connection, uuid, false);
    }

    private int loreCredits(Connection connection, UUID uuid, boolean lock) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT lore_credits FROM civcosmetics_player_state WHERE uuid = ?" + (lock ? " FOR UPDATE" : ""))) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    @Override
    public boolean spendLoreCredit(UUID uuid) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 UPDATE civcosmetics_player_state
                 SET lore_credits = lore_credits - 1, entitlements_version = entitlements_version + 1
                 WHERE uuid = ? AND lore_credits > 0
                 """)) {
            statement.setString(1, uuid.toString());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to spend a lore credit for " + uuid, e);
        }
    }

    @Override
    public Store.Outcome grantTokens(UUID uuid, Rarity rarity, int amount, String transactionId, String packageId) {
        return recordPurchase(uuid, Store.KIND_TOKEN, rarity, amount, transactionId, packageId, null);
    }

    @Override
    public Store.Outcome grantLoreCredits(UUID uuid, int amount, String transactionId, String packageId) {
        return recordPurchase(uuid, Store.KIND_LORE, null, amount, transactionId, packageId,
            connection -> addLoreCredits(connection, uuid, amount));
    }

    @Override
    public Store.Outcome recordFounder(UUID uuid, String transactionId, String packageId) {
        return recordPurchase(uuid, Store.KIND_FOUNDER, null, 1, transactionId, packageId, null);
    }

    /**
     * Inserts the ledger row and applies its effect in one database transaction. The (transaction, package) primary
     * key makes a repeated delivery a no-op even when two deliveries race.
     */
    private Store.Outcome recordPurchase(UUID uuid, String kind, Rarity rarity, int amount, String transactionId,
                                         String packageId, SqlAction effect) {
        try (Connection connection = getConnection()) {
            return inTransaction(connection, () -> {
                boolean refunded;
                try (PreparedStatement tombstone = connection.prepareStatement(
                    "SELECT 1 FROM civcosmetics_refunded_transactions WHERE transaction_id = ? LOCK IN SHARE MODE")) {
                    tombstone.setString(1, transactionId);
                    try (ResultSet result = tombstone.executeQuery()) {
                        refunded = result.next();
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT IGNORE INTO civcosmetics_purchases
                        (transaction_id, package_id, uuid, kind, rarity, amount, spent, status, note)
                    VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
                    """)) {
                    insert.setString(1, transactionId);
                    insert.setString(2, packageId);
                    insert.setString(3, uuid.toString());
                    insert.setString(4, kind);
                    insert.setString(5, rarity == null ? null : rarity.id);
                    insert.setInt(6, amount);
                    insert.setString(7, refunded ? Store.STATUS_REFUNDED : Store.STATUS_GRANTED);
                    insert.setString(8, refunded ? "arrived after its refund; nothing granted" : null);
                    if (insert.executeUpdate() == 0) return Store.Outcome.DUPLICATE;
                }
                if (refunded) return Store.Outcome.ALREADY_REFUNDED;
                if (effect != null) effect.run(connection);
                else bumpVersion(connection, uuid);
                return Store.Outcome.APPLIED;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to record purchase " + transactionId + "/" + packageId, e);
        }
    }

    @Override
    public Store.RefundResult refund(String transactionId) {
        try (Connection connection = getConnection()) {
            return inTransaction(connection, () -> {
                // Remember the refund so a delivery for this transaction that arrives later grants nothing.
                try (PreparedStatement tombstone = connection.prepareStatement(
                    "INSERT IGNORE INTO civcosmetics_refunded_transactions (transaction_id) VALUES (?)")) {
                    tombstone.setString(1, transactionId);
                    tombstone.executeUpdate();
                }
                List<Store.LedgerEntry> rows = new ArrayList<>();
                try (PreparedStatement select = connection.prepareStatement("""
                    SELECT transaction_id, package_id, uuid, kind, rarity, amount, spent, status, created_at, note
                    FROM civcosmetics_purchases
                    WHERE transaction_id = ? AND status = 'granted'
                    FOR UPDATE
                    """)) {
                    select.setString(1, transactionId);
                    try (ResultSet result = select.executeQuery()) {
                        while (result.next()) rows.add(ledgerEntry(result));
                    }
                }
                int tokensRemoved = 0;
                int cosmeticsRevoked = 0;
                int creditsRemoved = 0;
                int shortfall = 0;
                for (Store.LedgerEntry row : rows) {
                    UUID uuid = UUID.fromString(row.uuid());
                    String note = null;
                    switch (row.kind()) {
                        case Store.KIND_TOKEN -> {
                            tokensRemoved += Math.max(0, row.amount() - row.spent());
                            int revoked = revokeRedemptions(connection, row.transactionId(), row.packageId());
                            cosmeticsRevoked += revoked;
                            if (revoked > 0) note = "revoked " + revoked + " redeemed cosmetic(s)";
                        }
                        case Store.KIND_LORE -> {
                            int balance = loreCredits(connection, uuid, true);
                            int removed = Math.min(balance, row.amount());
                            if (removed > 0) addLoreCredits(connection, uuid, -removed);
                            creditsRemoved += removed;
                            if (removed < row.amount()) {
                                shortfall += row.amount() - removed;
                                note = (row.amount() - removed) + " credit(s) were already spent";
                            }
                        }
                        default -> {
                            // Founder and other permission-based grants are revoked by the LuckPerms refund command.
                        }
                    }
                    try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE civcosmetics_purchases SET status = 'refunded', note = ?
                        WHERE transaction_id = ? AND package_id = ?
                        """)) {
                        update.setString(1, note);
                        update.setString(2, row.transactionId());
                        update.setString(3, row.packageId());
                        update.executeUpdate();
                    }
                    bumpVersion(connection, uuid);
                }
                return new Store.RefundResult(rows.size(), tokensRemoved, cosmeticsRevoked, creditsRemoved, shortfall);
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to refund " + transactionId, e);
        }
    }

    private int revokeRedemptions(Connection connection, String transactionId, String packageId) throws SQLException {
        List<String[]> redeemed = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("""
            SELECT id, uuid, category, grant_id FROM civcosmetics_token_redemptions
            WHERE transaction_id = ? AND package_id = ? AND status = 'active'
            FOR UPDATE
            """)) {
            select.setString(1, transactionId);
            select.setString(2, packageId);
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    redeemed.add(new String[] {result.getString("id"), result.getString("uuid"),
                        result.getString("category"), result.getString("grant_id")});
                }
            }
        }
        try (PreparedStatement delete = connection.prepareStatement(
                 "DELETE FROM civcosmetics_grants WHERE uuid = ? AND category = ? AND grant_id = ?");
             PreparedStatement mark = connection.prepareStatement(
                 "UPDATE civcosmetics_token_redemptions SET status = 'revoked' WHERE id = ?")) {
            for (String[] row : redeemed) {
                delete.setString(1, row[1]);
                delete.setString(2, row[2]);
                delete.setString(3, row[3]);
                delete.executeUpdate();
                mark.setLong(1, Long.parseLong(row[0]));
                mark.executeUpdate();
            }
        }
        return redeemed.size();
    }

    @Override
    public Store.RedeemOutcome redeemToken(UUID uuid, Rarity rarity, CivCosmeticsPlugin.Category category, String cosmeticId) {
        String grantId = cosmeticId.toLowerCase(Locale.ROOT);
        try (Connection connection = getConnection()) {
            return inTransaction(connection, () -> {
                try (PreparedStatement owned = connection.prepareStatement("""
                    SELECT 1 FROM civcosmetics_grants
                    WHERE uuid = ? AND category = ? AND (grant_id = ? OR grant_id = 'all')
                    """)) {
                    owned.setString(1, uuid.toString());
                    owned.setString(2, category.id);
                    owned.setString(3, grantId);
                    try (ResultSet result = owned.executeQuery()) {
                        if (result.next()) return Store.RedeemOutcome.ALREADY_OWNED;
                    }
                }
                String transactionId;
                String packageId;
                // Oldest unspent token first, so a refund of a newer purchase revokes as little as possible.
                try (PreparedStatement select = connection.prepareStatement("""
                    SELECT transaction_id, package_id FROM civcosmetics_purchases
                    WHERE uuid = ? AND kind = 'token' AND rarity = ? AND status = 'granted' AND spent < amount
                    ORDER BY id
                    LIMIT 1
                    FOR UPDATE
                    """)) {
                    select.setString(1, uuid.toString());
                    select.setString(2, rarity.id);
                    try (ResultSet result = select.executeQuery()) {
                        if (!result.next()) return Store.RedeemOutcome.NO_TOKEN;
                        transactionId = result.getString("transaction_id");
                        packageId = result.getString("package_id");
                    }
                }
                try (PreparedStatement spend = connection.prepareStatement("""
                    UPDATE civcosmetics_purchases SET spent = spent + 1
                    WHERE transaction_id = ? AND package_id = ? AND status = 'granted' AND spent < amount
                    """)) {
                    spend.setString(1, transactionId);
                    spend.setString(2, packageId);
                    if (spend.executeUpdate() != 1) {
                        connection.rollback();
                        return Store.RedeemOutcome.NO_TOKEN;
                    }
                }
                try (PreparedStatement grant = connection.prepareStatement(
                    "INSERT IGNORE INTO civcosmetics_grants (uuid, category, grant_id) VALUES (?, ?, ?)")) {
                    grant.setString(1, uuid.toString());
                    grant.setString(2, category.id);
                    grant.setString(3, grantId);
                    grant.executeUpdate();
                }
                try (PreparedStatement record = connection.prepareStatement("""
                    INSERT INTO civcosmetics_token_redemptions
                        (uuid, transaction_id, package_id, rarity, category, grant_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                    record.setString(1, uuid.toString());
                    record.setString(2, transactionId);
                    record.setString(3, packageId);
                    record.setString(4, rarity.id);
                    record.setString(5, category.id);
                    record.setString(6, grantId);
                    record.executeUpdate();
                }
                bumpVersion(connection, uuid);
                return Store.RedeemOutcome.REDEEMED;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to redeem a " + rarity.id + " token for " + uuid, e);
        }
    }

    @Override
    public List<Store.LedgerEntry> ledger(UUID uuid, int limit) {
        List<Store.LedgerEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT transaction_id, package_id, uuid, kind, rarity, amount, spent, status, created_at, note
                 FROM civcosmetics_purchases
                 WHERE uuid = ?
                 ORDER BY id DESC
                 LIMIT ?
                 """)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, Math.max(1, limit));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) entries.add(ledgerEntry(result));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read the purchase ledger for " + uuid, e);
        }
        return entries;
    }

    private static Store.LedgerEntry ledgerEntry(ResultSet result) throws SQLException {
        Timestamp created = result.getTimestamp("created_at");
        return new Store.LedgerEntry(
            result.getString("transaction_id"),
            result.getString("package_id"),
            result.getString("uuid"),
            result.getString("kind"),
            result.getString("rarity"),
            result.getInt("amount"),
            result.getInt("spent"),
            result.getString("status"),
            created == null ? 0L : created.getTime(),
            result.getString("note"));
    }

    @Override
    public void setGrantBaseline(UUID uuid, Map<CivCosmeticsPlugin.Category, Set<String>> grants) {
        Map<CivCosmeticsPlugin.Category, Set<String>> copy = emptyGrantMap();
        for (var entry : grants.entrySet()) {
            for (String grant : entry.getValue()) copy.get(entry.getKey()).add(grant.toLowerCase(Locale.ROOT));
        }
        grantSnapshots.put(uuid, copy);
    }

    @Override
    public void forget(UUID uuid) {
        grantSnapshots.remove(uuid);
        loreHistorySnapshots.remove(uuid);
        unreadableLoreHistory.remove(uuid);
    }

    /** Tells other servers this player's grants, credits or tokens changed, so they reload them. */
    private void bumpVersion(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO civcosmetics_player_state (uuid, entitlements_version) VALUES (?, 1)
            ON DUPLICATE KEY UPDATE entitlements_version = entitlements_version + 1
            """)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        }
    }

    private interface SqlAction {
        void run(Connection connection) throws SQLException;
    }

    private interface SqlWork<T> {
        T run() throws SQLException;
    }

    private static <T> T inTransaction(Connection connection, SqlWork<T> work) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T result = work.run();
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
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
