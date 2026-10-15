package net.civmc.civcosmetics;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.milkbowl.vault.chat.Chat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Squid;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.loot.Lootable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CivCosmeticsPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final long[] STORAGE_RETRY_DELAYS_MS = {250L, 1_000L, 3_000L};
    private static final long FAILED_SAVE_RETRY_TICKS = 20L * 30L;
    private static final long LOAD_RETRY_TICKS = 20L * 10L;
    private static final int EFFECT_ENTITY_LIFETIME_TICKS = 25;
    private static final LegacyComponentSerializer LORE_KEY_SERIALIZER = LegacyComponentSerializer.builder()
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build();

    private static final List<Integer> TAG_SLOTS = List.of(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34);
    private static final List<Integer> ADMIN_PLAYER_SLOTS = List.of(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43);
    private static final List<Integer> ADMIN_OPTION_SLOTS = TAG_SLOTS;
    private static final List<Integer> ADMIN_CATEGORY_SLOTS = List.of(11, 13, 15, 20, 22, 24);

    // Read by PlaceholderAPI callbacks, which TAB may run off the main thread.
    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private final List<NamedCosmetic> tags = new ArrayList<>();
    private final List<NamedCosmetic> killEffects = new ArrayList<>();
    private final List<NamedCosmetic> trails = new ArrayList<>();
    private final List<KillMessagePreset> killMessagePresets = new ArrayList<>();
    private final List<String> trimPatterns = new ArrayList<>();
    private final List<String> trimMaterials = new ArrayList<>();
    private final List<GrantPreset> grantPresets = new ArrayList<>();
    private final Map<UUID, String> pendingPrefix = new HashMap<>();
    private final Map<UUID, String> pendingLore = new HashMap<>();
    private final Map<UUID, LoreDraft> loreDrafts = new HashMap<>();
    private final Map<UUID, Integer> activeTrailsByPlayer = new HashMap<>();
    private final Set<UUID> loadingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<Consumer<PlayerData>>> loadCallbacks = new ConcurrentHashMap<>();
    private final Map<UUID, PendingSave> failedSaves = new ConcurrentHashMap<>();
    private final AtomicBoolean failedSaveRetryInFlight = new AtomicBoolean();
    private final AtomicBoolean entitlementPollInFlight = new AtomicBoolean();
    private final AtomicBoolean storeRetryInFlight = new AtomicBoolean();
    /** Store deliveries that could not reach the database. Kept on disk and retried; the ledger makes retries safe. */
    private final List<StoreOp> pendingStoreOps = new ArrayList<>();
    private File pendingStoreOpsFile;
    private Map<Rarity, String> rarityRanks = new EnumMap<>(Rarity.class);
    private final Map<UUID, Long> lastEffectPreview = new HashMap<>();
    private final Set<Entity> effectEntities = new HashSet<>();
    private LoreGuard loreGuard;
    /** Deny-list for player-written titles and kill messages (staff impersonation); separate from the lore list. */
    private LoreGuard moderationGuard;
    private int killMessageMaxLength;
    private long linkCooldownMillis;
    private final Cooldowns linkCooldowns = new Cooldowns();
    /** Players with an enforceWornTrims task already queued, so a burst of clicks schedules only one. */
    private final Set<UUID> pendingTrimChecks = ConcurrentHashMap.newKeySet();
    private boolean loreAllowStacks;
    private long effectPreviewCooldownMillis;
    private final Map<UUID, List<UUID>> lastManagedPlayerIds = new HashMap<>();
    private Object killMessageProviderProxy;
    private Class<?> killMessageProviderClass;
    private boolean civChatKillProviderRegistered;
    private PlaceholderExpansion expansion;
    private Chat vaultChat;
    private File playerFolder;
    private CosmeticsStorage storage;
    private ExecutorService storageExecutor;
    private volatile boolean storageHealthy;
    private volatile boolean shuttingDown;
    private String prefix;
    private String storeUrl;
    private String loreEditorUrl;
    private int prefixMaxLength;
    private int loreMaxLines;
    private int loreMaxTotalCharacters;
    private int trimCooldownHours;
    private boolean trimEnforcement;
    private boolean weaponLinkEnabled;
    private boolean weaponLinkCraftedByEnabled;
    private boolean weaponLinkCraftedByShowInLore;
    private boolean weaponLinkCraftedByShowInHover;
    private String weaponLinkCraftedByLineFormat;
    private boolean weaponLinkSlayerEnabled;
    private boolean weaponLinkSlayerShowInLore;
    private boolean weaponLinkSlayerShowInHover;
    private int weaponLinkSlayerVisibleLines;
    private int weaponLinkSlayerHoverLimit;
    private String weaponLinkSlayerLineFormat;
    private String weaponLinkSlayerMoreFormat;
    private double weaponLinkFallbackRange;
    private Set<Material> weaponLinkMaterials = new HashSet<>();
    private NamespacedKey customLoreNameKey;
    private NamespacedKey customLoreLinesKey;
    private NamespacedKey ownedLoreKey;
    private NamespacedKey originalNameKey;
    private NamespacedKey effectEntityKey;
    private NamespacedKey weaponKillsKey;
    private NamespacedKey weaponCrafterUuidKey;
    private NamespacedKey weaponCrafterNameKey;
    private boolean killEffectSoundsEnabled;
    private float killEffectSoundVolume;
    private float killEffectPreviewSoundVolume;
    private int maxTrailTicks;
    private int maxActiveTrailsPerPlayer;
    private int maxActiveTrailsGlobal;
    private double stuckVelocityThreshold;
    private int stuckTicksBeforeStop;
    private int activeTrailTasks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        storageExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "CivCosmetics Storage");
            thread.setDaemon(true);
            return thread;
        });
        storageHealthy = true;
        loadSettings();
        playerFolder = new File(getDataFolder(), "players");
        customLoreNameKey = new NamespacedKey(this, "custom_lore_name");
        customLoreLinesKey = new NamespacedKey(this, "custom_lore_lines");
        ownedLoreKey = new NamespacedKey(this, "owned_lore");
        originalNameKey = new NamespacedKey(this, "original_name");
        effectEntityKey = new NamespacedKey(this, "effect_entity");
        weaponKillsKey = new NamespacedKey(this, "weapon_kills");
        weaponCrafterUuidKey = new NamespacedKey(this, "weapon_crafter_uuid");
        weaponCrafterNameKey = new NamespacedKey(this, "weapon_crafter_name");
        if (!initializeStorage()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        registerDefaults();
        getServer().getPluginManager().registerEvents(this, this);
        registerCommands();
        setupVault();
        registerKillMessageProvider();
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            expansion = new CivCosmeticsPlaceholders(this);
            expansion.register();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::retryFailedSaves, FAILED_SAVE_RETRY_TICKS, FAILED_SAVE_RETRY_TICKS);
        long refreshTicks = Math.max(5L, getConfig().getLong("entitlement-refresh-seconds", 15L)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, this::pollEntitlements, refreshTicks, refreshTicks);
        pendingStoreOpsFile = new File(getDataFolder(), "pending-store-ops.txt");
        loadPendingStoreOps();
        Bukkit.getScheduler().runTaskTimer(this, this::retryPendingStoreOps, 20L * 30L, 20L * 60L);
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        if (expansion != null) {
            expansion.unregister();
        }
        Bukkit.getServicesManager().unregisterAll(this);
        removeEffectEntities();
        if (storage != null && storageExecutor != null && storageHealthy) {
            // Flush every loaded player plus any save that failed earlier and is still waiting for a retry.
            Set<UUID> flushed = new HashSet<>();
            for (PlayerData data : new ArrayList<>(players.values())) {
                PlayerData snapshot = data.copy();
                flushed.add(snapshot.uuid);
                storageExecutor.execute(() -> writeSnapshot(snapshot, false, false));
            }
            for (PendingSave pending : new ArrayList<>(failedSaves.values())) {
                if (flushed.contains(pending.snapshot().uuid)) continue;
                storageExecutor.execute(() -> writeSnapshot(pending.snapshot(), pending.includeGrants(), false));
            }
        }
        if (storageExecutor != null) {
            storageExecutor.shutdown();
            try {
                if (!storageExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    getLogger().warning("Timed out waiting for CivCosmetics storage saves during shutdown.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (storage != null) {
            storage.close();
        }
    }

    private boolean initializeStorage() {
        MysqlConfig mysql = MysqlConfig.from(getConfig().getConfigurationSection("mysql"));
        if (!mysql.enabled()) {
            getLogger().severe("CivCosmetics requires MySQL for network-safe storage. Enable mysql.enabled in config.yml.");
            return false;
        }
        try {
            storage = new MysqlCosmeticsStorage(mysql, getLogger());
            // A database that is briefly unavailable at boot should not leave the plugin disabled.
            withRetry(() -> {
                storage.initialize();
                return null;
            });
            if (getConfig().getBoolean("mysql.import-yaml-on-enable", true)) {
                storageExecutor.execute(() -> {
                    try {
                        YamlCosmeticsImporter.importOnce(storage, playerFolder, getLogger());
                    } catch (RuntimeException e) {
                        logStorageFailure("import old YAML cosmetics", null, e);
                    }
                });
            }
            return true;
        } catch (RuntimeException e) {
            getLogger().severe("Could not initialize CivCosmetics MySQL storage: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void loadSettings() {
        prefix = color(getConfig().getString("prefix", "&b&lCIV&f&lPVP"));
        storeUrl = getConfig().getString("store-url", "https://civpvp.net/store");
        rarityRanks = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) {
            String fallback = switch (rarity) {
                case COMMON -> "&7Iron";
                case RARE -> "&bDiamond";
                case EPIC, LEGENDARY -> "&dNetherite";
            };
            rarityRanks.put(rarity, getConfig().getString("store.rarity-ranks." + rarity.id, fallback));
        }
        loreEditorUrl = getConfig().getString("lore-editor-url", "https://civpvp.net/lore-editor");
        prefixMaxLength = getConfig().getInt("prefix-max-length", 16);
        loreMaxLines = Math.max(1, getConfig().getInt("lore.max-lines", 30));
        loreMaxTotalCharacters = Math.max(1, getConfig().getInt("lore.max-total-characters", 4096));
        loreAllowStacks = getConfig().getBoolean("lore.allow-stacks", false);
        loreGuard = new LoreGuard(getConfig().contains("lore.denied-text")
            ? getConfig().getStringList("lore.denied-text")
            : LoreGuard.DEFAULT_DENIED_TEXT);
        moderationGuard = new LoreGuard(getConfig().contains("moderation.denied-text")
            ? getConfig().getStringList("moderation.denied-text")
            : LoreGuard.DEFAULT_MODERATION_TEXT);
        killMessageMaxLength = Math.max(1, getConfig().getInt("moderation.kill-message-max-length", 100));
        linkCooldownMillis = Math.max(0L, getConfig().getLong("weapon-link.cooldown-seconds", 10L)) * 1000L;
        effectPreviewCooldownMillis = Math.max(0L, getConfig().getLong("preview-cooldown-ms", 3000L));
        trimCooldownHours = getConfig().getInt("trim-cooldown-hours", 12);
        trimEnforcement = getConfig().getBoolean("trim-enforcement", true);
        weaponLinkEnabled = getConfig().getBoolean("weapon-link.enabled", true);
        int legacyVisibleKillLines = Math.max(0, getConfig().getInt("weapon-link.visible-kill-lines", 5));
        int legacyHoverKillLimit = Math.max(1, getConfig().getInt("weapon-link.hover-kill-limit", 50));
        weaponLinkCraftedByEnabled = getConfig().getBoolean("weapon-link.crafted-by.enabled", true);
        weaponLinkCraftedByShowInLore = getConfig().getBoolean("weapon-link.crafted-by.show-in-lore", true);
        weaponLinkCraftedByShowInHover = getConfig().getBoolean("weapon-link.crafted-by.show-in-hover", true);
        weaponLinkCraftedByLineFormat = getConfig().getString("weapon-link.crafted-by.line-format", "&7Crafted by: &b{name}");
        weaponLinkSlayerEnabled = getConfig().getBoolean("weapon-link.slayer-of.enabled", true);
        weaponLinkSlayerShowInLore = getConfig().getBoolean("weapon-link.slayer-of.show-in-lore", true);
        weaponLinkSlayerShowInHover = getConfig().getBoolean("weapon-link.slayer-of.show-in-hover", true);
        weaponLinkSlayerVisibleLines = Math.max(0, getConfig().contains("weapon-link.slayer-of.visible-lines")
            ? getConfig().getInt("weapon-link.slayer-of.visible-lines")
            : legacyVisibleKillLines);
        weaponLinkSlayerHoverLimit = Math.max(1, getConfig().contains("weapon-link.slayer-of.hover-limit")
            ? getConfig().getInt("weapon-link.slayer-of.hover-limit")
            : legacyHoverKillLimit);
        weaponLinkSlayerLineFormat = getConfig().getString("weapon-link.slayer-of.line-format", "&7Slayer of &c{name}");
        weaponLinkSlayerMoreFormat = getConfig().getString("weapon-link.slayer-of.more-format", "&8and {count} more");
        weaponLinkFallbackRange = Math.max(0, getConfig().getDouble("weapon-link.fallback-local-range", 1000));
        weaponLinkMaterials = parseMaterials(getConfig().getStringList("weapon-link.allowed-materials"), Set.of(
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
            Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD));
        killEffectSoundsEnabled = getConfig().getBoolean("kill-effect-sounds.enabled", true);
        killEffectSoundVolume = (float) getConfig().getDouble("kill-effect-sounds.volume", 0.55);
        killEffectPreviewSoundVolume = (float) getConfig().getDouble("kill-effect-sounds.preview-volume", 0.35);
        maxTrailTicks = Math.max(20, getConfig().getInt("trails.max-ticks", 200));
        maxActiveTrailsPerPlayer = Math.max(1, getConfig().getInt("trails.max-active-per-player", 3));
        maxActiveTrailsGlobal = Math.max(1, getConfig().getInt("trails.max-active-global", 120));
        stuckVelocityThreshold = Math.max(0.0, getConfig().getDouble("trails.stuck-velocity-threshold", 0.03));
        stuckTicksBeforeStop = Math.max(1, getConfig().getInt("trails.stuck-ticks-before-stop", 5));
        loadGrantPresets();
    }

    private void loadGrantPresets() {
        grantPresets.clear();
        ConfigurationSection section = getConfig().getConfigurationSection("grant-presets");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection presetSection = section.getConfigurationSection(key);
            if (presetSection == null) continue;
            GrantPreset preset = new GrantPreset(
                key.toLowerCase(Locale.ROOT),
                presetSection.getString("display", "&e" + key),
                material(presetSection.getString("icon", "GOLD_INGOT")),
                Math.max(0, presetSection.getInt("lore-credits", 0))
            );
            ConfigurationSection grants = presetSection.getConfigurationSection("grants");
            if (grants != null) {
                for (String categoryKey : grants.getKeys(false)) {
                    Category category = Category.normal(categoryKey);
                    if (category == null) continue;
                    for (String id : grants.getStringList(categoryKey)) {
                        if (id != null && !id.isBlank()) preset.grant(category, id);
                    }
                }
            }
            grantPresets.add(preset);
        }
    }

    private void setupVault() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return;
        RegisteredServiceProvider<Chat> registration = getServer().getServicesManager().getRegistration(Chat.class);
        if (registration != null) {
            vaultChat = registration.getProvider();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerKillMessageProvider() {
        if (!Bukkit.getPluginManager().isPluginEnabled("CivChat2")) return;
        try {
            killMessageProviderClass = Class.forName("vg.civcraft.mc.civchat2.kill.KillMessageProvider");
            killMessageProviderProxy = Proxy.newProxyInstance(
                killMessageProviderClass.getClassLoader(),
                new Class<?>[] { killMessageProviderClass },
                (proxy, method, args) -> {
                    if (!method.getName().equals("getKillMessage") || args == null || args.length < 4) return null;
                    if (!(args[0] instanceof Player killer) || !(args[1] instanceof Player victim)) return null;
                    ItemStack weapon = args[2] instanceof ItemStack item ? item : null;
                    Component defaultWeaponDescription = args[3] instanceof Component component
                        ? component
                        : component(args[3] == null ? "their weapon" : args[3].toString());
                    Component rendered = getKillMessage(killer, victim, weapon, defaultWeaponDescription);
                    if (rendered == null) return null;
                    if (method.getReturnType().equals(String.class)) {
                        return LegacyComponentSerializer.legacySection().serialize(rendered);
                    }
                    return rendered;
                });
            Bukkit.getServicesManager().register((Class) killMessageProviderClass, killMessageProviderProxy, this, ServicePriority.Normal);
            civChatKillProviderRegistered = true;
        } catch (ClassNotFoundException e) {
            getLogger().warning("CivChat2 is installed, but its custom kill message provider API was not found. Falling back to Paper death messages.");
        } catch (Throwable t) {
            getLogger().warning("Failed to hook CivChat2 custom kill messages; falling back to Paper death messages: " + t.getMessage());
        }
    }

    private void registerCommands() {
        for (String command : List.of(
            "cosmetics", "killeffects", "trails", "tags", "settitle", "cleartitle",
            "setkillmsg", "clearkillmsg", "setlore", "clearlore", "settrim", "link",
            "cosadmin", "cosadmingui", "titleadmin", "addlorecredit")) {
            Objects.requireNonNull(getCommand(command), command).setExecutor(this);
            Objects.requireNonNull(getCommand(command), command).setTabCompleter(this);
        }
    }

    private void registerDefaults() {
        tags.add(new NamedCosmetic("champion", "&#FFB000&l✦ ᴄʜᴀᴍᴘɪᴏɴ &f", Material.NAME_TAG, Rarity.LEGENDARY));
        tags.add(new NamedCosmetic("legend", "&#FFD166&l★ ʟᴇɢᴇɴᴅ &f", Material.NAME_TAG, Rarity.LEGENDARY));
        tags.add(new NamedCosmetic("knight", "&#9AD0FF&l⚔ ᴋɴɪɢʜᴛ &f", Material.NAME_TAG, Rarity.RARE));
        tags.add(new NamedCosmetic("royal", "&#B388FF&l♛ ʀᴏʏᴀʟ &f", Material.NAME_TAG, Rarity.EPIC));
        tags.add(new NamedCosmetic("veteran", "&#A8A8A8&l✚ ᴠᴇᴛᴇʀᴀɴ &f", Material.NAME_TAG, Rarity.COMMON));
        tags.add(new NamedCosmetic("builder", "&#F4A261&l▣ ʙᴜɪʟᴅᴇʀ &f", Material.NAME_TAG, Rarity.COMMON));
        tags.add(new NamedCosmetic("hunter", "&#7ED957&l⌖ ʜᴜɴᴛᴇʀ &f", Material.NAME_TAG, Rarity.COMMON));
        tags.add(new NamedCosmetic("titan", "&#56CCF2&l◆ ᴛɪᴛᴀɴ &f", Material.NAME_TAG, Rarity.EPIC));
        tags.add(new NamedCosmetic("ember", "&#FF6B35&l🔥 ᴇᴍʙᴇʀ &f", Material.NAME_TAG, Rarity.RARE));
        tags.add(new NamedCosmetic("frost", "&#7FDBFF&l❄ ꜰʀᴏꜱᴛ &f", Material.NAME_TAG, Rarity.RARE));
        tags.add(new NamedCosmetic("void", "&#8E44AD&l✧ ᴠᴏɪᴅ &f", Material.NAME_TAG, Rarity.EPIC));
        tags.add(new NamedCosmetic("crimson", "&#D7263D&l✦ ᴄʀɪᴍꜱᴏɴ &f", Material.NAME_TAG, Rarity.RARE));
        tags.add(new NamedCosmetic("lucky", "&#2ECC71&l☘ ʟᴜᴄᴋʏ &f", Material.NAME_TAG, Rarity.COMMON));
        tags.add(new NamedCosmetic("celestial", "&#F8F7FF&l☾ ᴄᴇʟᴇꜱᴛɪᴀʟ &f", Material.NAME_TAG, Rarity.LEGENDARY));
        tags.add(new NamedCosmetic("rogue", "&#95A5A6&l⌁ ʀᴏɢᴜᴇ &f", Material.NAME_TAG, Rarity.COMMON));
        tags.add(new NamedCosmetic("guardian", "&#00B894&l⬟ ɢᴜᴀʀᴅɪᴀɴ &f", Material.NAME_TAG, Rarity.RARE));
        tags.add(new NamedCosmetic("alchemist", "&#C3FF00&l⚗ ᴀʟᴄʜᴇᴍɪꜱᴛ &f", Material.NAME_TAG, Rarity.RARE));
        tags.add(new NamedCosmetic("voyager", "&#4D96FF&l✈ ᴠᴏʏᴀɢᴇʀ &f", Material.NAME_TAG, Rarity.COMMON));
        tags.add(new NamedCosmetic("phantom", "&#BFC0FF&l☁ ᴘʜᴀɴᴛᴏᴍ &f", Material.NAME_TAG, Rarity.EPIC));
        tags.add(new NamedCosmetic("reaper", "&#2D3436&l☠ ʀᴇᴀᴘᴇʀ &f", Material.NAME_TAG, Rarity.EPIC));

        killEffects.add(new NamedCosmetic("pig", "&d&lPIG ASCENSION", Material.PORKCHOP, Rarity.COMMON));
        killEffects.add(new NamedCosmetic("dragon", "&5&lDRAGON", Material.DRAGON_EGG, Rarity.EPIC));
        killEffects.add(new NamedCosmetic("thunder", "&b&lLIGHTNING", Material.LIGHTNING_ROD, Rarity.RARE));
        killEffects.add(new NamedCosmetic("flame", "&6&lFLAME", Material.CAMPFIRE, Rarity.RARE));
        killEffects.add(new NamedCosmetic("heartbeat", "&c&lHEARTBEAT", Material.RED_DYE, Rarity.COMMON));
        killEffects.add(new NamedCosmetic("warden", "&3&lWARDEN", Material.WARDEN_SPAWN_EGG, Rarity.EPIC));
        killEffects.add(new NamedCosmetic("firework", "&f&lFIREWORK", Material.FIREWORK_ROCKET, Rarity.COMMON));
        killEffects.add(new NamedCosmetic("snow", "&b&lFROSTBITE", Material.POWDER_SNOW_BUCKET, Rarity.COMMON));
        killEffects.add(new NamedCosmetic("trident", "&9&lTRIDENT", Material.TRIDENT, Rarity.RARE));
        killEffects.add(new NamedCosmetic("crystal", "&d&lCRYSTAL", Material.AMETHYST_CLUSTER, Rarity.RARE));
        killEffects.add(new NamedCosmetic("blood", "&4&lBLOOD EXPLOSION", Material.REDSTONE_BLOCK, Rarity.EPIC));
        killEffects.add(new NamedCosmetic("explosion", "&4&lEXPLOSION", Material.TNT, Rarity.RARE));
        killEffects.add(new NamedCosmetic("void", "&d&lVOID", Material.ENDER_EYE, Rarity.EPIC));
        killEffects.add(new NamedCosmetic("wealth", "&6&lWEALTH", Material.GOLD_INGOT, Rarity.RARE));
        killEffects.add(new NamedCosmetic("squid", "&0&lSQUID MISSILE", Material.INK_SAC, Rarity.RARE));
        killEffects.add(new NamedCosmetic("tornado", "&3&lVORTEX", Material.SCULK, Rarity.EPIC));
        killEffects.add(new NamedCosmetic("lava", "&c&lLAVA", Material.MAGMA_CREAM, Rarity.RARE));
        killEffects.add(new NamedCosmetic("music", "&a&lMUSIC", Material.NOTE_BLOCK, Rarity.COMMON));
        killEffects.add(new NamedCosmetic("cloud", "&f&lJUDGEMENT", Material.END_ROD, Rarity.LEGENDARY));
        killEffects.add(new NamedCosmetic("slime", "&2&lSLIME", Material.SLIME_BALL, Rarity.COMMON));
        killEffects.add(new NamedCosmetic("cookies", "&6&lCOOKIE FOUNTAIN", Material.COOKIE, Rarity.COMMON));
        killEffects.add(new NamedCosmetic("soul_rift", "&3&lSOUL RIFT", Material.SOUL_LANTERN, Rarity.LEGENDARY));
        killEffects.add(new NamedCosmetic("meteor", "&6&lMETEOR", Material.FIRE_CHARGE, Rarity.LEGENDARY));
        killEffects.add(new NamedCosmetic("shatter", "&b&lSHATTER", Material.BLUE_STAINED_GLASS, Rarity.EPIC));
        killEffects.add(new NamedCosmetic("crown", "&e&lCROWN", Material.GOLDEN_HELMET, Rarity.LEGENDARY));
        killEffects.add(new NamedCosmetic("glitch", "&d&lGLITCH", Material.ENDER_PEARL, Rarity.EPIC));
        killEffects.add(new NamedCosmetic("execution", "&4&lEXECUTION", Material.IRON_AXE, Rarity.LEGENDARY));

        trails.add(new NamedCosmetic("hearts", "&c&lHEART", Material.RED_DYE, Rarity.COMMON));
        trails.add(new NamedCosmetic("flame", "&6&lFLAME", Material.BLAZE_POWDER, Rarity.RARE));
        trails.add(new NamedCosmetic("magic", "&d&lMAGIC", Material.GHAST_TEAR, Rarity.EPIC));
        trails.add(new NamedCosmetic("music", "&a&lMUSIC", Material.NOTE_BLOCK, Rarity.COMMON));
        trails.add(new NamedCosmetic("smoke", "&8&lSMOKE", Material.GUNPOWDER, Rarity.COMMON));
        trails.add(new NamedCosmetic("crit", "&b&lCRIT", Material.DIAMOND_SWORD, Rarity.RARE));
        trails.add(new NamedCosmetic("slime", "&2&lSLIME", Material.SLIME_BALL, Rarity.COMMON));
        trails.add(new NamedCosmetic("soul", "&3&lSOUL", Material.SOUL_TORCH, Rarity.EPIC));
        trails.add(new NamedCosmetic("ender", "&5&lENDER", Material.ENDER_PEARL, Rarity.LEGENDARY));
        trails.add(new NamedCosmetic("gold", "&6&lGOLD", Material.GOLD_NUGGET, Rarity.RARE));
        trails.add(new NamedCosmetic("frost", "&b&lFROST", Material.SNOWBALL, Rarity.RARE));
        trails.add(new NamedCosmetic("totem", "&a&lTOTEM", Material.TOTEM_OF_UNDYING, Rarity.LEGENDARY));

        // Founder-exclusive set (civ.cosmetic.founder), sold before launch only.
        tags.add(new NamedCosmetic("founder", "&#FFB000&l✪ ꜰᴏᴜɴᴅᴇʀ &f", Material.NETHER_STAR, Rarity.LEGENDARY, true));
        killEffects.add(new NamedCosmetic("founder", "&6&lFOUNDER'S CROWN", Material.NETHER_STAR, Rarity.LEGENDARY, true));
        trails.add(new NamedCosmetic("founder", "&6&lFOUNDER", Material.NETHER_STAR, Rarity.LEGENDARY, true));
        applyRarityOverrides();

        killMessagePresets.add(new KillMessagePreset("classic", "&e&lClassic", Material.PAPER, "&8{victim} &7was killed by &8{killer} &7{item}"));
        killMessagePresets.add(new KillMessagePreset("deleted", "&c&lDeleted", Material.TNT, "&c{victim} &7was deleted by &6{killer} &7{item}"));
        killMessagePresets.add(new KillMessagePreset("sent_spawn", "&a&lSent To Spawn", Material.COMPASS, "&6{killer} &7sent &c{victim} &7back to spawn &8{item}"));
        killMessagePresets.add(new KillMessagePreset("outplayed", "&b&lOutplayed", Material.DIAMOND_SWORD, "&b{victim} &7got outplayed by &f{killer} &8{item}"));
        killMessagePresets.add(new KillMessagePreset("vanished", "&5&lVanished", Material.ENDER_PEARL, "&5{victim} &7vanished after meeting &d{killer} &8{item}"));
        killMessagePresets.add(new KillMessagePreset("royal", "&6&lRoyal", Material.GOLDEN_HELMET, "&e{killer} &6claimed victory over &e{victim} &8{item}"));

        trimPatterns.addAll(List.of("coast", "dune", "eye", "rib", "sentry", "snout", "spire", "tide", "vex", "ward", "wild", "wayfinder", "raiser", "shaper", "host", "silence", "bolt", "flow"));
        trimMaterials.addAll(List.of("amethyst", "copper", "diamond", "emerald", "gold", "iron", "lapis", "netherite", "quartz", "redstone"));
    }

    /** Optional config overrides: {@code cosmetic-rarities.<category>.<id>: common|rare|epic|legendary}. */
    private void applyRarityOverrides() {
        ConfigurationSection section = getConfig().getConfigurationSection("cosmetic-rarities");
        if (section == null) return;
        for (Category category : List.of(Category.KILL_EFFECTS, Category.TRAILS, Category.TAGS)) {
            ConfigurationSection overrides = section.getConfigurationSection(category.id);
            if (overrides == null) continue;
            List<NamedCosmetic> list = categoryCosmetics(category);
            for (String id : overrides.getKeys(false)) {
                Rarity rarity = Rarity.parse(overrides.getString(id));
                NamedCosmetic cosmetic = find(list, id.toLowerCase(Locale.ROOT));
                if (rarity == null || cosmetic == null) {
                    getLogger().warning("Ignoring cosmetic-rarities." + category.id + "." + id + ": unknown cosmetic or rarity.");
                    continue;
                }
                list.set(list.indexOf(cosmetic), new NamedCosmetic(cosmetic.id(), cosmetic.display(), cosmetic.icon(), rarity, cosmetic.founder()));
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("cosadmin")) {
            return cosAdmin(sender, args);
        }
        if (name.equals("titleadmin")) {
            return titleAdmin(sender, args);
        }
        if (name.equals("addlorecredit")) {
            return addLoreCredit(sender, args);
        }
        if (name.equals("cosadmingui")) {
            if (!(sender instanceof Player player)) return true;
            openAdminPlayers(player);
            return true;
        }
        if (name.equals("cosmetics") && args.length > 0 && args[0].equalsIgnoreCase("help")) {
            sendCosmeticsHelp(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only");
            return true;
        }
        if (!requireLoaded(player)) {
            return true;
        }
        return switch (name) {
            case "cosmetics" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
                    sendCosmeticsHelp(player);
                } else {
                    openHub(player);
                }
                yield true;
            }
            case "killeffects" -> {
                openKillEffects(player);
                yield true;
            }
            case "trails" -> {
                openTrails(player);
                yield true;
            }
            case "tags" -> {
                openTags(player, args.length > 0 ? parseInt(args[0], 1) : 1);
                yield true;
            }
            case "settitle" -> {
                setTitle(player, String.join(" ", args));
                yield true;
            }
            case "cleartitle" -> {
                clearPrefix(player);
                msg(player, "&eTitle cleared. Your rank prefix will show again.");
                yield true;
            }
            case "setkillmsg" -> {
                setKillMessage(player, String.join(" ", args));
                yield true;
            }
            case "clearkillmsg" -> {
                clearKillMessage(player);
                yield true;
            }
            case "setlore" -> {
                handleSetLore(player, args);
                yield true;
            }
            case "clearlore" -> {
                openLoreClearConfirm(player);
                yield true;
            }
            case "link" -> {
                linkHeldItem(player);
                yield true;
            }
            case "settrim" -> {
                openTrimPatterns(player);
                yield true;
            }
            default -> false;
        };
    }

    private boolean cosAdmin(CommandSender sender, String[] args) {
        if (args.length > 0 && STORE_SUBCOMMANDS.contains(args[0].toLowerCase(Locale.ROOT))) {
            return storeCommand(sender, args);
        }
        if (!sender.hasPermission("civ.cosmetic.admin")) {
            msg(sender, "&cNo permission!");
            return true;
        }
        if (args.length < 1 || args[0].equalsIgnoreCase("help")) {
            msg(sender, "&e/cosadmin grant <player> <category> [id|all]");
            msg(sender, "&e/cosadmin revoke <player> <category> [id|all]");
            msg(sender, "&e/cosadmin clear <player> <category>");
            msg(sender, "&e/cosadmin preset <player> <preset> [replace]");
            msg(sender, "&e/cosadmin grantall <player> [loreCredits]");
            msg(sender, "&e/cosadmin patreon <player>");
            msg(sender, "&e/cosadmin ledger <player|uuid> &7- recent store purchases");
            msg(sender, "&e/cosadmingui");
            msg(sender, "&7Console only: &e/cosadmin token <uuid> <rarity> <amount> <transaction> [package]");
            msg(sender, "&7Console only: &e/cosadmin founder <uuid> <transaction> [package]");
            msg(sender, "&7Console only: &e/cosadmin refund <transaction>");
            msg(sender, "&7Console only: &e/addlorecredit <uuid> <amount> <transaction> [package]");
            return true;
        }
        if (args[0].equalsIgnoreCase("ledger")) {
            if (args.length < 2) {
                msg(sender, "&cUsage: /cosadmin ledger <player|uuid>");
                return true;
            }
            resolveTarget(sender, args[1], target -> showLedger(sender, target));
            return true;
        }
        if (args.length < 2) {
            msg(sender, "&cUsage: /cosadmin <grant|revoke|clear|preset> <player> <category|preset> [id|replace]");
            return true;
        }
        resolveTarget(sender, args[1], target -> cosAdmin(sender, args, target));
        return true;
    }

    private boolean cosAdmin(CommandSender sender, String[] args, OfflinePlayer target) {
        if (!requireLoaded(sender, target.getUniqueId(), () -> cosAdmin(sender, args, target))) return true;
        PlayerData data = load(target.getUniqueId());
        if (args[0].equalsIgnoreCase("grantall") || args[0].equalsIgnoreCase("patreon")) {
            int loreCredits = args[0].equalsIgnoreCase("patreon") ? 1 : (args.length > 2 ? parseInt(args[2], 0) : 0);
            grantAll(data);
            adjustLoreCredits(data, Math.max(0, loreCredits));
            saveWithGrants(data);
            Player online = target.getPlayer();
            if (online != null) validateSelections(online);
            msg(sender, "&aGranted all cosmetics to &f" + playerName(target.getUniqueId()) + " &7(+" + Math.max(0, loreCredits) + " lore credits)");
            return true;
        }
        if (args[0].equalsIgnoreCase("preset")) {
            if (args.length < 3) {
                msg(sender, "&cUsage: /cosadmin preset <player> <preset> [replace]");
                return true;
            }
            GrantPreset preset = findPreset(args[2]);
            if (preset == null) {
                msg(sender, "&cUnknown preset. Available: " + presetIds());
                return true;
            }
            boolean replace = args.length > 3 && args[3].equalsIgnoreCase("replace");
            if (replace) clearAllGrants(data);
            applyPreset(data, preset);
            saveWithGrants(data);
            Player online = target.getPlayer();
            if (online != null) validateSelections(online);
            msg(sender, "&aApplied &e" + preset.id + " &ato &f" + playerName(target.getUniqueId()) + (replace ? " &7(replaced old grants)" : ""));
            return true;
        }
        if (args.length < 3) {
            msg(sender, "&cUsage: /cosadmin <grant|revoke|clear> <player> <category> [id|all]");
            return true;
        }
        Category category = Category.normal(args[2]);
        if (category == null) {
            msg(sender, "&cUnknown category.");
            return true;
        }
        String id = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "all";
        if (args[0].equalsIgnoreCase("grant")) {
            data.grant(category, id);
            if (category == Category.TAGS && id.equals("all")) data.grant(Category.TITLE, "all");
            saveWithGrants(data);
            msg(sender, "&aGranted &e" + category.id + ":" + id + " &ato &f" + playerName(target.getUniqueId()));
        } else if (args[0].equalsIgnoreCase("revoke")) {
            data.revoke(category, id);
            if (category == Category.TAGS && id.equals("all")) data.revoke(Category.TITLE, "all");
            saveWithGrants(data);
            Player online = target.getPlayer();
            if (online != null) validateSelections(online);
            msg(sender, "&cRevoked &e" + category.id + ":" + id + " &cfrom &f" + playerName(target.getUniqueId()));
        } else if (args[0].equalsIgnoreCase("clear")) {
            clearCategoryGrants(data, category);
            saveWithGrants(data);
            Player online = target.getPlayer();
            if (online != null) validateSelections(online);
            msg(sender, "&cCleared " + category.id + " grants from " + playerName(target.getUniqueId()));
        }
        return true;
    }

    private boolean titleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("civ.cosmetic.admin")) {
            msg(sender, "&cNo permission!");
            return true;
        }
        if (args.length < 2) {
            msg(sender, "&cUsage: /titleadmin <set|clear> <player> [prefix]");
            return true;
        }
        resolveTarget(sender, args[1], target -> titleAdmin(sender, args, target));
        return true;
    }

    private boolean titleAdmin(CommandSender sender, String[] args, OfflinePlayer target) {
        if (!requireLoaded(sender, target.getUniqueId(), () -> titleAdmin(sender, args, target))) return true;
        PlayerData data = load(target.getUniqueId());
        if (args[0].equalsIgnoreCase("clear")) {
            data.customPrefix = null;
            data.selectedTag = null;
            save(data);
            Player online = target.getPlayer();
            if (online != null) applyPrefix(online);
            msg(sender, "&eCleared title for " + playerName(target.getUniqueId()));
        } else if (args[0].equalsIgnoreCase("set")) {
            if (args.length < 3) {
                msg(sender, "&cUsage: /titleadmin set <player> <prefix>");
                return true;
            }
            String raw = cleanPrefix(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
            if (visibleLength(raw) > prefixMaxLength) {
                msg(sender, "&cThat title is too long. Max " + prefixMaxLength + " visible characters.");
                return true;
            }
            data.customPrefix = raw;
            data.savedCustomPrefix = raw;
            data.selectedTag = null;
            data.grant(Category.TITLE, "all");
            saveWithGrants(data);
            Player online = target.getPlayer();
            if (online != null) applyPrefix(online);
            msg(sender, "&aSet title for " + playerName(target.getUniqueId()) + ": &r" + raw);
        }
        return true;
    }

    private boolean addLoreCredit(CommandSender sender, String[] args) {
        if (args.length >= 3) {
            // With a transaction id this is a store delivery: console only, UUID only, recorded in the ledger.
            if (!isConsole(sender)) {
                msg(sender, "&cStore deliveries can only be run from the server console.");
                return true;
            }
            UUID uuid = Store.parseUuid(args[0]);
            int amount = Store.parseAmount(args[1]);
            String transaction = args[2];
            String packageId = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "lore";
            if (uuid == null || amount <= 0 || !Store.validTransaction(transaction) || !Store.validPackage(packageId)) {
                msg(sender, "&cUsage: /addlorecredit <uuid> <amount|NxQuantity> <transaction> [package]");
                return true;
            }
            submitStoreOp(sender, new StoreOp(Store.KIND_LORE, uuid, null, amount, transaction, packageId));
            return true;
        }
        if (!sender.hasPermission("civ.cosmetic.admin")) {
            msg(sender, "&cNo permission!");
            return true;
        }
        if (args.length < 1) {
            msg(sender, "&cUsage: /addlorecredit <player> [amount]");
            return true;
        }
        int amount = args.length > 1 ? parseInt(args[1], 0) : 1;
        if (amount <= 0) {
            msg(sender, "&cAmount must be positive.");
            return true;
        }
        resolveTarget(sender, args[0], target -> addLoreCredit(sender, target, amount));
        return true;
    }

    private boolean addLoreCredit(CommandSender sender, OfflinePlayer target, int amount) {
        if (!requireLoaded(sender, target.getUniqueId(), () -> addLoreCredit(sender, target, amount))) return true;
        PlayerData data = load(target.getUniqueId());
        adjustLoreCredits(data, amount);
        msg(sender, "&aAdded &e" + amount + " &alore credits to &f" + playerName(target.getUniqueId()));
        return true;
    }

    private static final Set<String> STORE_SUBCOMMANDS = Set.of("token", "founder", "refund");

    private static boolean isConsole(CommandSender sender) {
        return sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender;
    }

    /**
     * Store fulfilment (Tebex). Console only, so no player permission can mint tokens or credits. Every delivery is
     * keyed by (transaction, package) in the purchase ledger, so a repeated command changes nothing.
     */
    private boolean storeCommand(CommandSender sender, String[] args) {
        if (!isConsole(sender)) {
            msg(sender, "&cStore commands can only be run from the server console.");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "token" -> {
                UUID uuid = args.length > 1 ? Store.parseUuid(args[1]) : null;
                Rarity rarity = args.length > 2 ? Rarity.parse(args[2]) : null;
                int amount = args.length > 3 ? Store.parseAmount(args[3]) : -1;
                String transaction = args.length > 4 ? args[4] : null;
                String packageId = args.length > 5 ? args[5].toLowerCase(Locale.ROOT) : rarity == null ? null : "token_" + rarity.id;
                if (uuid == null || rarity == null || amount <= 0
                    || !Store.validTransaction(transaction) || !Store.validPackage(packageId)) {
                    msg(sender, "&cUsage: /cosadmin token <uuid> <common|rare|epic|legendary> <amount|NxQuantity> <transaction> [package]");
                    return true;
                }
                submitStoreOp(sender, new StoreOp(Store.KIND_TOKEN, uuid, rarity, amount, transaction, packageId));
            }
            case "founder" -> {
                UUID uuid = args.length > 1 ? Store.parseUuid(args[1]) : null;
                String transaction = args.length > 2 ? args[2] : null;
                String packageId = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "founder";
                if (uuid == null || !Store.validTransaction(transaction) || !Store.validPackage(packageId)) {
                    msg(sender, "&cUsage: /cosadmin founder <uuid> <transaction> [package]");
                    return true;
                }
                submitStoreOp(sender, new StoreOp(Store.KIND_FOUNDER, uuid, null, 1, transaction, packageId));
            }
            case "refund" -> {
                String transaction = args.length > 1 ? args[1] : null;
                if (!Store.validTransaction(transaction)) {
                    msg(sender, "&cUsage: /cosadmin refund <transaction>");
                    return true;
                }
                submitStoreOp(sender, new StoreOp(StoreOp.REFUND, null, null, 0, transaction, "-"));
            }
            default -> msg(sender, "&cUnknown store command.");
        }
        return true;
    }

    private void submitStoreOp(CommandSender sender, StoreOp op) {
        boolean backlog;
        synchronized (pendingStoreOps) {
            backlog = !pendingStoreOps.isEmpty();
        }
        if (backlog) {
            // Keep Tebex's order: a refund must never run ahead of the delivery it refunds.
            queueStoreOp(op, "earlier store operations are still queued");
            msg(sender, "&e" + op.describe() + " was queued behind earlier store operations.");
            retryPendingStoreOps();
            return;
        }
        if (!storageHealthy || storage == null || storageExecutor == null || storageExecutor.isShutdown()) {
            queueStoreOp(op, "storage unavailable");
            msg(sender, "&eStorage unavailable; " + op.describe() + " was queued and will be retried.");
            return;
        }
        storageExecutor.execute(() -> {
            try {
                String result = withRetry(() -> applyStoreOp(op));
                getLogger().info("[Store] " + op.describe() + ": " + result);
                runOnMain(() -> {
                    msg(sender, "&a" + op.describe() + ": &f" + result);
                    afterStoreOp(op);
                });
            } catch (RuntimeException e) {
                queueStoreOp(op, e.getMessage());
                runOnMain(() -> msg(sender, "&e" + op.describe() + " could not be written yet; it was queued and will be retried."));
            }
        });
    }

    /** Storage thread. Returns a one-line result for the console and log. */
    private String applyStoreOp(StoreOp op) {
        return switch (op.kind()) {
            case Store.KIND_TOKEN -> outcome(storage.grantTokens(op.uuid(), op.rarity(), op.amount(), op.transactionId(), op.packageId()),
                op.amount() + " " + op.rarity().label + " token(s)");
            case Store.KIND_LORE -> outcome(storage.grantLoreCredits(op.uuid(), op.amount(), op.transactionId(), op.packageId()),
                op.amount() + " lore credit(s)");
            case Store.KIND_FOUNDER -> outcome(storage.recordFounder(op.uuid(), op.transactionId(), op.packageId()),
                "Founder purchase recorded");
            case StoreOp.REFUND -> {
                Store.RefundResult result = storage.refund(op.transactionId());
                if (result.nothingToRefund()) yield "nothing to refund (unknown or already refunded)";
                yield "refunded " + result.rows() + " ledger row(s); removed " + result.tokensRemoved() + " unspent token(s), "
                    + result.cosmeticsRevoked() + " redeemed cosmetic(s), " + result.loreCreditsRemoved() + " lore credit(s)"
                    + (result.loreCreditShortfall() > 0 ? " (" + result.loreCreditShortfall() + " already spent)" : "");
            }
            default -> throw new IllegalArgumentException("Unknown store operation " + op.kind());
        };
    }

    private static String outcome(Store.Outcome outcome, String what) {
        return switch (outcome) {
            case APPLIED -> "granted " + what;
            case DUPLICATE -> "already delivered; ignored";
            case ALREADY_REFUNDED -> "this transaction was already refunded; recorded, nothing granted";
        };
    }

    /** Main thread: show the change to an online player now instead of waiting for the next poll. */
    private void afterStoreOp(StoreOp op) {
        if (op.uuid() == null) {
            pollEntitlements();
            return;
        }
        refreshEntitlements(op.uuid(), null);
    }

    /** Schedules work on the main thread unless the plugin is disabling; never throws. */
    private void runOnMain(Runnable task) {
        if (!isEnabled()) return;
        try {
            Bukkit.getScheduler().runTask(this, task);
        } catch (IllegalStateException ignored) {
            // Disabled between the check and the call.
        }
    }

    private void queueStoreOp(StoreOp op, @Nullable String reason) {
        getLogger().severe("[Store] Could not apply " + op.describe() + " (" + reason + "); queued in "
            + pendingStoreOpsFile.getName() + " for retry.");
        synchronized (pendingStoreOps) {
            pendingStoreOps.add(op);
            writePendingStoreOps();
        }
    }

    private void loadPendingStoreOps() {
        if (!pendingStoreOpsFile.isFile()) return;
        try {
            synchronized (pendingStoreOps) {
                for (String line : Files.readAllLines(pendingStoreOpsFile.toPath(), StandardCharsets.UTF_8)) {
                    StoreOp op = StoreOp.parse(line);
                    if (op != null) pendingStoreOps.add(op);
                    else if (!line.isBlank()) getLogger().severe("[Store] Unreadable queued store operation: " + line);
                }
            }
            if (!pendingStoreOps.isEmpty()) {
                getLogger().warning("[Store] " + pendingStoreOps.size() + " queued store operation(s) will be retried.");
            }
        } catch (IOException e) {
            getLogger().severe("[Store] Could not read " + pendingStoreOpsFile + ": " + e.getMessage());
        }
    }

    /** Caller holds the pendingStoreOps lock. Writes atomically so a crash can't leave a half-written queue. */
    private void writePendingStoreOps() {
        try {
            getDataFolder().mkdirs();
            File temp = new File(getDataFolder(), pendingStoreOpsFile.getName() + ".tmp");
            List<String> lines = new ArrayList<>();
            for (StoreOp op : pendingStoreOps) lines.add(op.serialize());
            Files.write(temp.toPath(), lines, StandardCharsets.UTF_8);
            Files.move(temp.toPath(), pendingStoreOpsFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            getLogger().severe("[Store] Could not write " + pendingStoreOpsFile + ": " + e.getMessage());
        }
    }

    /** Main thread, periodic. Applies queued operations in order and stops at the first failure to keep that order. */
    private void retryPendingStoreOps() {
        List<StoreOp> queued;
        synchronized (pendingStoreOps) {
            if (pendingStoreOps.isEmpty()) return;
            queued = new ArrayList<>(pendingStoreOps);
        }
        if (!storageHealthy || storage == null || storageExecutor == null || storageExecutor.isShutdown()) return;
        if (!storeRetryInFlight.compareAndSet(false, true)) return;
        storageExecutor.execute(() -> {
            try {
                for (StoreOp op : queued) {
                    String result;
                    try {
                        result = applyStoreOp(op);
                    } catch (RuntimeException e) {
                        logStorageFailure("retry " + op.describe(), op.uuid(), e);
                        return;
                    }
                    getLogger().info("[Store] Retried " + op.describe() + ": " + result);
                    synchronized (pendingStoreOps) {
                        pendingStoreOps.remove(op);
                        writePendingStoreOps();
                    }
                    runOnMain(() -> afterStoreOp(op));
                }
            } finally {
                storeRetryInFlight.set(false);
            }
        });
    }

    private void showLedger(CommandSender sender, OfflinePlayer target) {
        UUID uuid = target.getUniqueId();
        runStorage("read the purchase ledger", uuid, () -> storage.ledger(uuid, 15), entries -> {
            msg(sender, "&6Store purchases for &f" + playerName(uuid) + " &7(" + uuid + ")");
            if (entries.isEmpty()) {
                msg(sender, "&7No store purchases recorded.");
                return;
            }
            for (Store.LedgerEntry entry : entries) {
                String what = switch (entry.kind()) {
                    case Store.KIND_TOKEN -> entry.amount() + "x " + entry.rarity() + " token (" + entry.spent() + " spent)";
                    case Store.KIND_LORE -> entry.amount() + " lore credit(s)";
                    default -> entry.kind();
                };
                msg(sender, (Store.STATUS_REFUNDED.equals(entry.status()) ? "&c" : "&a") + entry.status()
                    + " &7" + entry.transactionId() + " &8/ &7" + entry.packageId() + " &f" + what
                    + (entry.note() == null ? "" : " &8(" + entry.note() + ")"));
            }
        }, () -> msg(sender, "&cCould not read the purchase ledger right now."));
    }

    /**
     * Resolves a player name without blocking the main thread. Online and cached players resolve immediately;
     * anything else is looked up asynchronously (which may hit Mojang) and the action runs back on the main thread.
     */
    private void resolveTarget(CommandSender sender, String name, Consumer<OfflinePlayer> action) {
        UUID uuid = Store.parseUuid(name);
        if (uuid != null) {
            // A UUID needs no lookup and works for players who are offline or have never joined this server.
            action.accept(Bukkit.getOfflinePlayer(uuid));
            return;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            action.accept(online);
            return;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            action.accept(cached);
            return;
        }
        msg(sender, "&7Looking up &f" + name + "&7...");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            OfflinePlayer looked = Bukkit.getOfflinePlayer(name);
            Bukkit.getScheduler().runTask(this, () -> {
                if (!isEnabled()) return;
                // Unknown names fall back to a name-based (version 3) offline UUID; never bind grants to that.
                if (looked.getUniqueId().version() == 3 && !looked.hasPlayedBefore()) {
                    msg(sender, "&cCould not resolve player &f" + name + "&c.");
                    return;
                }
                if (!looked.hasPlayedBefore() && !looked.isOnline()) {
                    msg(sender, "&e" + name + " has never joined; using UUID &f" + looked.getUniqueId() + "&e.");
                }
                action.accept(looked);
            });
        });
    }

    private void sendCosmeticsHelp(CommandSender sender) {
        msg(sender, "&6&lCivCosmetics Commands");
        msg(sender, "&e/cosmetics &7- open the main cosmetics menu.");
        msg(sender, "&e/killeffects &7- choose kill effects.");
        msg(sender, "&e/trails &7- choose projectile trails.");
        msg(sender, "&e/tags &7- choose premade tags/prefixes.");
        msg(sender, "&e/settitle <text> &7- preview and save a custom prefix.");
        msg(sender, "&e/cleartitle &7- clear your custom prefix/tag.");
        msg(sender, "&e/setkillmsg <message> &7- custom death message. Tokens: &f{killer} {victim} {item}&7.");
        msg(sender, "&e/clearkillmsg &7- clear your custom kill message.");
        msg(sender, "&e/settrim &7- apply unlocked armor trims to held armor.");
        msg(sender, "&e/link &7- show your held sword in chat with crafted/slayer info.");
        msg(sender, "&e/clearlore &7- clear custom name/lore from held item.");
        msg(sender, "&e/setlore <Name|Lore line 1|Lore line 2> &7- short item lore.");
        msg(sender, "&e/setlore book &7- read long item lore from an offhand book.");
        msg(sender, "&e/setlore draft start <name> &7- start long lore draft.");
        msg(sender, "&e/setlore draft add <line> &7- add a draft line.");
        msg(sender, "&e/setlore draft setname <name> &7- change draft item name.");
        msg(sender, "&e/setlore draft remove <lineNumber> &7- remove a draft line.");
        msg(sender, "&e/setlore draft preview|apply|clear &7- finish or clear draft.");
        msg(sender, "&e/setlore editor &7- lore editor: &b" + loreEditorUrl);
        msg(sender, "&eStore: &b" + storeUrl + " &7(ranks, tokens, lore credits)");
        if (sender.hasPermission("civ.cosmetic.admin")) {
            msg(sender, "&6&lAdmin");
            msg(sender, "&e/cosadmin help &7- grant/revoke/preset commands.");
            msg(sender, "&e/cosadmingui &7- admin GUI.");
            msg(sender, "&e/cosadmin grantall <player> [loreCredits] &7- grant all cosmetics.");
            msg(sender, "&e/cosadmin patreon <player> &7- grant all cosmetics + one lore credit.");
            msg(sender, "&e/titleadmin set|clear <player> [prefix] &7- manage player title.");
            msg(sender, "&e/addlorecredit <player|uuid> [amount] &7- add lore credits.");
            msg(sender, "&e/cosadmin ledger <player|uuid> &7- recent store purchases.");
            msg(sender, "&7Store deliveries and refunds are console-only; see /cosadmin help.");
        }
    }

    private void setTitle(Player player, String raw) {
        if (raw.isBlank() || List.of("reset", "clear", "none").contains(raw.toLowerCase(Locale.ROOT))) {
            clearPrefix(player);
            msg(player, "&eTitle cleared. Your rank prefix will show again.");
            return;
        }
        if (raw.equalsIgnoreCase("menu") || raw.equalsIgnoreCase("tags")) {
            openTags(player, 1);
            return;
        }
        if (!has(player, Category.TITLE, "all")) {
            msg(player, "&cYou do not have access to custom titles.");
            return;
        }
        String clean = cleanPrefix(raw);
        if (visibleLength(clean) > prefixMaxLength) {
            msg(player, "&cTitle too long! Max " + prefixMaxLength + " visible characters.");
            return;
        }
        if (moderationGuard.deniedMatch(clean) != null) {
            msg(player, "&cThat title is not allowed.");
            return;
        }
        pendingPrefix.put(player.getUniqueId(), clean);
        openPrefixPreview(player, clean);
    }

    private void setKillMessage(Player player, String raw) {
        if (raw.isBlank()) {
            msg(player, "&cUsage: /setkillmsg <message>");
            return;
        }
        if (!has(player, Category.KILLMSG, "all")) {
            msg(player, "&cYou do not have access to custom kill messages.");
            return;
        }
        raw = LoreGuard.stripObfuscated(raw);
        if (visibleLength(raw) > killMessageMaxLength) {
            msg(player, "&cKill message too long! Max " + killMessageMaxLength + " visible characters.");
            return;
        }
        if (moderationGuard.deniedMatch(raw) != null) {
            msg(player, "&cThat kill message is not allowed.");
            return;
        }
        raw = normalizeKillMessage(raw);
        data(player).pendingKillMessage = raw;
        save(data(player));
        openKillMessagePreview(player, raw);
    }

    private void clearKillMessage(Player player) {
        if (!has(player, Category.KILLMSG, "all")) {
            msg(player, "&cYou do not have access to custom kill messages.");
            return;
        }
        data(player).killMessage = null;
        data(player).pendingKillMessage = null;
        save(data(player));
        msg(player, "&eKill message reset to default.");
    }

    private void clearLoreHistory(Player player) {
        data(player).loreHistory.clear();
        save(data(player));
        openLoreHistory(player);
        msg(player, "&cYour lore preview history has been cleared.");
    }

    private void handleSetLore(Player player, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("book")) {
            setLoreFromBook(player);
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("draft")) {
            handleLoreDraft(player, Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("editor")) {
            sendLoreEditor(player);
            return;
        }
        setLore(player, String.join(" ", args));
    }

    private void setLore(Player player, String raw) {
        if (raw.isBlank()) {
            msg(player, "&cUsage: /setlore <Name|Lore line 1|Lore line 2>");
            msg(player, "&eLong lore: &f/setlore book &7or &f/setlore draft start <name>");
            msg(player, "&eEditor: &b" + loreEditorUrl);
            return;
        }
        previewLore(player, raw);
    }

    private void previewLore(Player player, String raw) {
        if (!canUseLore(player)) {
            msg(player, "&cYou need a Lore Credit to customize items. Get one at &e" + storeUrl + "&c.");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            msg(player, "&cHold an item first.");
            return;
        }
        PlayerData data = data(player);
        if (!player.isOp() && data.loreCredits <= 0) {
            msg(player, "&cYou do not have any Lore Credits. Get one at &e" + storeUrl + "&c.");
            return;
        }
        String validation = validateLore(raw, item);
        if (validation != null) {
            msg(player, validation);
            return;
        }
        pendingLore.put(player.getUniqueId(), raw);
        openLorePreview(player, raw);
    }

    private void setLoreFromBook(Player player) {
        ItemStack book = player.getInventory().getItemInOffHand();
        if (book.getType() != Material.WRITABLE_BOOK && book.getType() != Material.WRITTEN_BOOK) {
            msg(player, "&cPut a writable or written book in your offhand first.");
            return;
        }
        if (!(book.getItemMeta() instanceof BookMeta meta)) {
            msg(player, "&cThat book has no readable pages.");
            return;
        }
        List<String> lines = new ArrayList<>();
        for (String page : meta.getPages()) {
            lines.addAll(Arrays.asList(page.split("\\R", -1)));
        }
        int nameIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                nameIndex = i;
                break;
            }
        }
        if (nameIndex < 0) {
            msg(player, "&cThe book needs at least one non-empty line for the item name.");
            return;
        }
        List<String> parts = new ArrayList<>();
        parts.add(lines.get(nameIndex));
        parts.addAll(lines.subList(nameIndex + 1, lines.size()));
        previewLore(player, String.join("|", parts));
    }

    private void handleLoreDraft(Player player, String[] args) {
        if (args.length == 0) {
            msg(player, "&e/setlore draft start <name>");
            msg(player, "&e/setlore draft add <line>");
            msg(player, "&e/setlore draft preview|apply|clear");
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        UUID uuid = player.getUniqueId();
        switch (action) {
            case "start" -> {
                if (args.length < 2) {
                    msg(player, "&cUsage: /setlore draft start <name>");
                    return;
                }
                loreDrafts.put(uuid, new LoreDraft(String.join(" ", Arrays.copyOfRange(args, 1, args.length))));
                msg(player, "&aLore draft started. Add lines with &f/setlore draft add <line>&a.");
            }
            case "setname" -> {
                LoreDraft draft = loreDrafts.get(uuid);
                if (draft == null) {
                    msg(player, "&cStart a draft first with /setlore draft start <name>.");
                    return;
                }
                if (args.length < 2) {
                    msg(player, "&cUsage: /setlore draft setname <name>");
                    return;
                }
                draft.name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                msg(player, "&aDraft name updated.");
            }
            case "add" -> {
                LoreDraft draft = loreDrafts.get(uuid);
                if (draft == null) {
                    msg(player, "&cStart a draft first with /setlore draft start <name>.");
                    return;
                }
                if (args.length < 2) {
                    msg(player, "&cUsage: /setlore draft add <line>");
                    return;
                }
                draft.lines.add(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                msg(player, "&aAdded lore line &e" + draft.lines.size() + "&a.");
            }
            case "remove" -> {
                LoreDraft draft = loreDrafts.get(uuid);
                if (draft == null) {
                    msg(player, "&cStart a draft first with /setlore draft start <name>.");
                    return;
                }
                if (args.length < 2) {
                    msg(player, "&cUsage: /setlore draft remove <lineNumber>");
                    return;
                }
                int index = parseInt(args[1], -1) - 1;
                if (index < 0 || index >= draft.lines.size()) {
                    msg(player, "&cNo draft line exists with that number.");
                    return;
                }
                draft.lines.remove(index);
                msg(player, "&eRemoved draft line.");
            }
            case "clear" -> {
                loreDrafts.remove(uuid);
                msg(player, "&eLore draft cleared.");
            }
            case "preview" -> {
                LoreDraft draft = loreDrafts.get(uuid);
                if (draft == null) {
                    msg(player, "&cStart a draft first with /setlore draft start <name>.");
                    return;
                }
                previewLore(player, draft.raw());
            }
            case "apply" -> {
                LoreDraft draft = loreDrafts.get(uuid);
                if (draft == null) {
                    msg(player, "&cStart a draft first with /setlore draft start <name>.");
                    return;
                }
                if (applyLore(player, draft.raw())) {
                    loreDrafts.remove(uuid);
                }
            }
            default -> msg(player, "&cUnknown draft action. Use /setlore draft for help.");
        }
    }

    private String validateLore(String raw, ItemStack item) {
        if (!loreAllowStacks && item.getAmount() > 1) {
            return "&cCustom lore can only be applied to a single item, not a stack.";
        }
        String[] parts = raw.split("\\|", -1);
        int loreLines = Math.max(0, parts.length - 1);
        if (loreLines > loreMaxLines) {
            return "&cThat lore has too many lines. Max " + loreMaxLines + ".";
        }
        if (raw.length() > loreMaxTotalCharacters) {
            return "&cThat lore is too long. Max " + loreMaxTotalCharacters + " stored characters.";
        }
        // Other plugins recognise items by name/lore text (e.g. FactoryMod "Compacted"); never let players forge it.
        for (String part : parts) {
            String denied = loreGuard.deniedMatch(PlainTextComponentSerializer.plainText().serialize(component(part)));
            if (denied != null) {
                return "&cThat name or lore contains reserved text (&f" + denied + "&c) used by server items.";
            }
        }
        return null;
    }

    private void sendLoreEditor(Player player) {
        player.sendMessage(component("&bLore editor: &f" + loreEditorUrl)
            .clickEvent(ClickEvent.openUrl(loreEditorUrl))
            .hoverEvent(HoverEvent.showText(component("&eClick to open the lore editor"))));
    }

    private boolean applyLore(Player player, String raw) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            msg(player, "&cHold an item first.");
            return false;
        }
        PlayerData data = data(player);
        if (!player.isOp() && data.loreCredits <= 0) {
            msg(player, "&cYou do not have any Lore Credits. Get one at &e" + storeUrl + "&c.");
            return false;
        }
        String validation = validateLore(raw, item);
        if (validation != null) {
            msg(player, validation);
            return false;
        }
        if (player.isOp()) {
            finishLore(player, item, raw);
            return true;
        }
        // Spend the credit in the database first; the item only changes once that succeeded.
        UUID uuid = player.getUniqueId();
        ItemStack expected = item.clone();
        runStorage("spend a lore credit", uuid, () -> storage.spendLoreCredit(uuid), spent -> {
            Player online = Bukkit.getPlayer(uuid);
            PlayerData live = players.get(uuid);
            if (!spent) {
                if (live != null) live.loreCredits = 0;
                if (online != null) msg(online, "&cYou do not have any Lore Credits. Get one at &e" + storeUrl + "&c.");
                return;
            }
            if (live != null) live.loreCredits = Math.max(0, live.loreCredits - 1);
            ItemStack held = online == null ? null : online.getInventory().getItemInMainHand();
            if (online == null || held == null || !held.isSimilar(expected) || held.getAmount() != expected.getAmount()) {
                // The item changed while the credit was being spent: give the credit back rather than lose it.
                submitStoreOp(Bukkit.getConsoleSender(), new StoreOp(Store.KIND_LORE, uuid, null, 1,
                    "refund-" + UUID.randomUUID().toString().replace("-", "").substring(0, 19), "lore_return"));
                if (online != null) msg(online, "&eYour held item changed, so nothing was applied and the credit was returned.");
                return;
            }
            finishLore(online, held, raw);
        }, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) msg(online, "&cCouldn't use a Lore Credit right now; nothing was changed. Try again in a moment.");
        });
        return true;
    }

    private void finishLore(Player player, ItemStack item, String raw) {
        PlayerData data = data(player);
        applyLoreToItem(item, raw);
        data.loreHistory.add(item.clone());
        while (data.loreHistory.size() > 27) {
            data.loreHistory.remove(0);
        }
        save(data);
        msg(player, player.isOp() ? "&aItem updated." : "&aItem updated. &eLore Credit used.");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
    }

    private void applyLoreToItem(ItemStack item, String raw) {
        String[] parts = raw.split("\\|", -1);
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(customLoreNameKey, PersistentDataType.STRING)) {
            // Remember a name set by vanilla or another plugin so /clearlore can restore it.
            if (meta.hasDisplayName()) {
                meta.getPersistentDataContainer().set(originalNameKey, PersistentDataType.STRING,
                    GsonComponentSerializer.gson().serialize(meta.displayName()));
            } else {
                meta.getPersistentDataContainer().remove(originalNameKey);
            }
        }
        meta.displayName(component(parts[0]));
        meta.getPersistentDataContainer().set(customLoreNameKey, PersistentDataType.STRING, parts[0]);
        if (parts.length > 1) {
            List<String> rawLore = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                rawLore.add(parts[i]);
            }
            meta.getPersistentDataContainer().set(customLoreLinesKey, PersistentDataType.STRING, String.join("\n", rawLore));
        } else {
            meta.getPersistentDataContainer().remove(customLoreLinesKey);
        }
        rebuildWeaponLore(meta);
        item.setItemMeta(meta);
    }

    /** Removes only what CivCosmetics added; names and lore from vanilla or other plugins are left intact. */
    private void clearLoreFromItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        var pdc = meta.getPersistentDataContainer();
        if (!pdc.has(customLoreNameKey, PersistentDataType.STRING) && !hasCosmeticLoreData(meta)
            && !pdc.has(ownedLoreKey, PersistentDataType.LIST.strings())) {
            return; // Nothing of ours on this item: leave it byte-for-byte untouched.
        }
        if (!pdc.has(ownedLoreKey, PersistentDataType.LIST.strings()) && hasCosmeticLoreData(meta)) {
            // Pre-tracking item: its whole lore was rendered by this plugin, so record it as ours before clearing.
            List<String> legacy = new ArrayList<>();
            if (meta.lore() != null) {
                for (Component line : meta.lore()) legacy.add(loreKey(line));
            }
            pdc.set(ownedLoreKey, PersistentDataType.LIST.strings(), legacy);
        }
        if (pdc.has(customLoreNameKey, PersistentDataType.STRING)) {
            String original = pdc.get(originalNameKey, PersistentDataType.STRING);
            Component restored = null;
            if (original != null) {
                try {
                    restored = GsonComponentSerializer.gson().deserialize(original);
                } catch (RuntimeException ignored) {
                }
            }
            meta.displayName(restored);
        }
        pdc.remove(customLoreNameKey);
        pdc.remove(originalNameKey);
        pdc.remove(customLoreLinesKey);
        rebuildWeaponLore(meta);
        item.setItemMeta(meta);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Player menus. Every browsing menu shares one frame: six rows, a border, the player's profile in the top centre,
    // a 7x4 content area laid out symmetrically, and navigation in fixed bottom-row slots (Back 45, Previous 48,
    // Close 49, Next 50, Store 53, with paired actions in 47 and 51). Confirmation dialogs share one three-row
    // layout: confirm on the left, the subject in the centre, cancel on the right.
    // ---------------------------------------------------------------------------------------------------------------

    private static final int SLOT_PROFILE = 4;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_LEFT_ACTION = 47;
    private static final int SLOT_PREVIOUS = 48;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_RIGHT_ACTION = 51;
    private static final int SLOT_STORE = 53;
    private Inventory frame(Player player, Menu menu, String title, @Nullable Object data) {
        Inventory inv = gui(menu, 54, "&8" + title, data);
        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < 54; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == 5 || column == 0 || column == 8) inv.setItem(slot, border);
        }
        inv.setItem(SLOT_PROFILE, profileItem(player));
        button(inv, SLOT_CLOSE, item(Material.BARRIER, "&c&lClose"), click -> player.closeInventory());
        return inv;
    }

    private Inventory dialog(Menu menu, String title) {
        Inventory inv = gui(menu, 27, "&8" + title, null);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        return inv;
    }

    private void button(Inventory inv, int slot, ItemStack item, Consumer<ClickType> action) {
        inv.setItem(slot, item);
        ((GuiHolder) inv.getHolder()).actions.put(slot, action);
    }

    private void backButton(Inventory inv, String target, Runnable action) {
        button(inv, SLOT_BACK, item(Material.ARROW, "&7« Back", "&8To " + target), click -> action.run());
    }

    private void storeButton(Inventory inv, Player player) {
        button(inv, SLOT_STORE, item(Material.EMERALD, "&a&lStore",
            "&7Ranks, cosmetic tokens", "&7and lore credits.", "", "&b" + storeHost(), "", "&eClick for the link"), click -> {
            player.closeInventory();
            player.sendMessage(component(prefix + " &r&7Visit the store: &b&n" + storeUrl)
                .clickEvent(ClickEvent.openUrl(storeUrl))
                .hoverEvent(HoverEvent.showText(component("&eClick to open the store"))));
        });
    }

    private void pageButtons(Inventory inv, int page, int pages, Consumer<Integer> open) {
        if (page > 1) button(inv, SLOT_PREVIOUS, item(Material.ARROW, "&e« Previous Page", "&7Page " + (page - 1) + " of " + pages), click -> open.accept(page - 1));
        if (page < pages) button(inv, SLOT_NEXT, item(Material.ARROW, "&eNext Page »", "&7Page " + (page + 1) + " of " + pages), click -> open.accept(page + 1));
    }

    private String storeHost() {
        return storeUrl.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
    }

    private ItemStack profileItem(Player player) {
        PlayerData data = data(player);
        String rank = vaultPrefix(player).strip();
        List<String> lore = new ArrayList<>();
        lore.add("&7Rank: " + (rank.isEmpty() ? "&fMember" : rank));
        lore.add("&7Title: &f" + currentPrefixText(data));
        lore.add("");
        lore.add("&7Lore Credits: &e" + data.loreCredits);
        lore.add("&7Tokens:");
        for (Rarity rarity : Rarity.values()) {
            lore.add("&8 ▪ " + rarity.display() + " &8× &f" + data.tokens(rarity));
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(component("&e&l" + player.getName()));
        meta.lore(lore.stream().map(this::component).toList());
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack glow(ItemStack stack, boolean glow) {
        if (!glow) return stack;
        ItemMeta meta = stack.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        stack.setItemMeta(meta);
        return stack;
    }

    private String perkRank(String perk, String fallback) {
        return getConfig().getString("store.perk-ranks." + perk, fallback);
    }

    private void deny(Player player, String message) {
        msg(player, message);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, .8f, 1f);
    }

    private static String titleCase(String raw) {
        StringBuilder out = new StringBuilder();
        for (String word : raw.split("[_ ]")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    // --- Hub ---

    private void openHub(Player player) {
        PlayerData data = data(player);
        Inventory inv = frame(player, Menu.HUB, "CivPvP ▸ Cosmetics", null);
        button(inv, 20, categoryTile(player, Category.TAGS, Material.NAME_TAG, "&d&lTitles",
            "Show a title in front of your name.", currentPrefixText(data)), click -> openTags(player, 1));
        button(inv, 22, categoryTile(player, Category.KILL_EFFECTS, Material.TNT, "&c&lKill Effects",
            "Play an effect when you get a kill.", selectedName(killEffects, data.selectedKillEffect)), click -> openKillEffects(player, 1));
        button(inv, 24, categoryTile(player, Category.TRAILS, Material.ARROW, "&e&lProjectile Trails",
            "Leave a trail behind arrows and pearls.", selectedName(trails, data.selectedTrail)), click -> openTrails(player, 1));

        boolean trims = has(player, Category.TRIM, "all");
        button(inv, 29, item(Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, "&b&lArmor Trims",
            "&7Apply any trim pattern and material", "&7to the armor in your hand.", "",
            trims ? "&a✔ Unlocked" : "&c✖ Locked",
            trims ? "&eClick while holding armor" : "&7Included with the " + perkRank("trim", "&bDiamond") + " &7rank."),
            click -> {
                if (!has(player, Category.TRIM, "all")) deny(player, "&cArmor trims are included with the " + perkRank("trim", "&bDiamond") + " &crank.");
                else openTrimPatterns(player);
            });
        boolean killMessages = has(player, Category.KILLMSG, "all");
        button(inv, 31, item(Material.PAPER, "&6&lKill Messages",
            "&7Choose what chat says when", "&7you take someone down.", "",
            "&7Equipped: &f" + currentKillMessageText(data), "",
            killMessages ? "&eClick to browse" : "&7Included with the " + perkRank("killmsg", "&dNetherite") + " &7rank."),
            click -> openKillMessages(player));
        button(inv, 33, item(Material.WRITABLE_BOOK, "&f&lItem Lore",
            "&7Rename an item and give it", "&7your own lore.", "",
            "&7Lore Credits: &e" + data.loreCredits, "", "&eClick to open"), click -> openLoreMenu(player));

        int totalTokens = 0;
        for (Rarity rarity : Rarity.values()) totalTokens += data.tokens(rarity);
        List<String> tokenLore = new ArrayList<>(List.of("&7Each token unlocks one cosmetic", "&7of its rarity, forever.", ""));
        for (Rarity rarity : Rarity.values()) tokenLore.add("&8 ▪ " + rarity.display() + " &8× &f" + data.tokens(rarity));
        tokenLore.add("");
        tokenLore.add("&eClick for details");
        button(inv, 40, glow(item(Material.SUNFLOWER, "&6&lTokens &7(" + totalTokens + ")", tokenLore.toArray(String[]::new)), totalTokens > 0),
            click -> openTokens(player));

        button(inv, SLOT_BACK, item(Material.BOOK, "&e&lHelp", "&7Commands and tips.", "", "&eClick to show in chat"), click -> {
            player.closeInventory();
            sendCosmeticsHelp(player);
        });
        storeButton(inv, player);
        player.openInventory(inv);
    }

    private ItemStack categoryTile(Player player, Category category, Material icon, String name, String description, String equipped) {
        List<NamedCosmetic> list = categoryCosmetics(category);
        int unlocked = 0;
        for (NamedCosmetic cosmetic : list) if (has(player, category, cosmetic.id())) unlocked++;
        return item(icon, name,
            "&7" + description, "",
            "&7Unlocked: &a" + unlocked + "&7/&f" + list.size(),
            "&7Equipped: &f" + equipped, "",
            "&eClick to browse");
    }

    private String selectedName(List<NamedCosmetic> list, @Nullable String id) {
        NamedCosmetic cosmetic = id == null ? null : find(list, id);
        return cosmetic == null ? "&7None" : cosmetic.display();
    }

    // --- Cosmetic lists (titles, kill effects, trails) ---

    private void openKillEffects(Player player) {
        openKillEffects(player, 1);
    }

    private void openKillEffects(Player player, int page) {
        openCosmeticList(player, Category.KILL_EFFECTS, page);
    }

    private void openTrails(Player player) {
        openTrails(player, 1);
    }

    private void openTrails(Player player, int page) {
        openCosmeticList(player, Category.TRAILS, page);
    }

    private void openTags(Player player, int page) {
        openCosmeticList(player, Category.TAGS, page);
    }

    /** Store order: Common to Legendary, then Founder exclusives; ties keep their configured order. */
    private List<NamedCosmetic> sortedCosmetics(Category category) {
        List<NamedCosmetic> sorted = new ArrayList<>(categoryCosmetics(category));
        sorted.sort((a, b) -> {
            if (a.founder() != b.founder()) return a.founder() ? 1 : -1;
            return Integer.compare(a.rarity().ordinal(), b.rarity().ordinal());
        });
        return sorted;
    }

    private void openCosmeticList(Player player, Category category, int requestedPage) {
        List<NamedCosmetic> list = sortedCosmetics(category);
        int pages = Math.max(1, (list.size() + MenuLayout.CONTENT_CAPACITY - 1) / MenuLayout.CONTENT_CAPACITY);
        int page = Math.max(1, Math.min(pages, requestedPage));
        String title = switch (category) {
            case KILL_EFFECTS -> "Kill Effects";
            case TRAILS -> "Projectile Trails";
            default -> "Titles";
        };
        Inventory inv = frame(player, Menu.COSMETIC_LIST, "Cosmetics ▸ " + title + (pages > 1 ? " (" + page + "/" + pages + ")" : ""), null);
        PlayerData data = data(player);
        int offset = (page - 1) * MenuLayout.CONTENT_CAPACITY;
        List<NamedCosmetic> shown = list.subList(offset, Math.min(list.size(), offset + MenuLayout.CONTENT_CAPACITY));
        List<Integer> slots = MenuLayout.symmetricSlots(shown.size(), MenuLayout.CONTENT_COLUMNS);
        for (int i = 0; i < shown.size(); i++) {
            NamedCosmetic cosmetic = shown.get(i);
            button(inv, slots.get(i), cosmeticItem(player, data, category, cosmetic),
                click -> clickCosmetic(player, category, cosmetic, click, page));
        }
        if (category == Category.TAGS) {
            button(inv, SLOT_LEFT_ACTION, customTitleItem(player, data), click -> clickCustomTitle(player));
            button(inv, SLOT_RIGHT_ACTION, item(Material.GRAY_DYE, "&c&lReset Title", "&7Show only your rank tag.", "", "&eClick to reset"), click -> {
                clearPrefix(player);
                msg(player, "&eTitle reset. Your rank tag shows on its own.");
                openTags(player, page);
            });
        } else {
            button(inv, SLOT_LEFT_ACTION, item(Material.OAK_SIGN, "&e&lHow It Works",
                "&eLeft-click &7an unlocked " + (category == Category.KILL_EFFECTS ? "effect" : "trail") + " to equip it.",
                "&eRight-click &7to preview it.",
                "&7Click your equipped one to take it off.", "",
                "&7Locked ones show how to unlock them."), click -> { });
            String selected = category == Category.KILL_EFFECTS ? data.selectedKillEffect : data.selectedTrail;
            if (selected != null) {
                button(inv, SLOT_RIGHT_ACTION, item(Material.GRAY_DYE, "&c&lUnequip", "&7Equipped: &f" + selectedName(categoryCosmetics(category), selected), "", "&eClick to unequip"),
                    click -> {
                        unequip(player, category);
                        openCosmeticList(player, category, page);
                    });
            }
        }
        pageButtons(inv, page, pages, next -> openCosmeticList(player, category, next));
        backButton(inv, "Cosmetics", () -> openHub(player));
        storeButton(inv, player);
        player.openInventory(inv);
    }

    private boolean isEquipped(PlayerData data, Category category, String id) {
        return switch (category) {
            case KILL_EFFECTS -> id.equals(data.selectedKillEffect);
            case TRAILS -> id.equals(data.selectedTrail);
            case TAGS -> id.equals(data.selectedTag);
            default -> false;
        };
    }

    private ItemStack cosmeticItem(Player player, PlayerData data, Category category, NamedCosmetic cosmetic) {
        boolean owned = has(player, category, cosmetic.id());
        boolean equipped = owned && isEquipped(data, category, cosmetic.id());
        List<String> lore = new ArrayList<>();
        lore.add(cosmetic.founder() ? "&6✪ Founder Exclusive" : cosmetic.rarity().color + "● " + cosmetic.rarity().label);
        if (category == Category.TAGS) lore.add("&7Preview: &r" + cosmetic.display() + "&f" + player.getName());
        lore.add("");
        if (equipped) {
            lore.add("&a✔ Equipped");
            lore.add("");
            lore.add("&eClick &7to unequip");
            if (category != Category.TAGS) lore.add("&eRight-click &7to preview");
        } else if (owned) {
            lore.add("&a✔ Unlocked");
            lore.add("");
            lore.add("&eClick &7to equip");
            if (category != Category.TAGS) lore.add("&eRight-click &7to preview");
        } else {
            lore.add("&c✖ Locked");
            if (cosmetic.founder()) {
                lore.add("&7Exclusive to &6Founders&7.");
            } else {
                Rarity rarity = cosmetic.rarity();
                lore.add("&7Included with the " + rarityRanks.get(rarity) + " &7rank,");
                lore.add("&7or one " + rarity.display() + " Token&7.");
                lore.add("");
                int tokens = data.tokens(rarity);
                if (tokens > 0) {
                    lore.add("&eClick &7to redeem a token &8(&f" + tokens + " &8left)");
                } else {
                    lore.add("&b" + storeHost());
                }
            }
        }
        return glow(item(cosmetic.icon(), cosmetic.display(), lore.toArray(String[]::new)), equipped);
    }

    private void clickCosmetic(Player player, Category category, NamedCosmetic cosmetic, ClickType click, int page) {
        if (!has(player, category, cosmetic.id())) {
            if (!cosmetic.founder() && data(player).tokens(cosmetic.rarity()) > 0) {
                openRedeemConfirm(player, category, cosmetic, page);
            } else if (cosmetic.founder()) {
                deny(player, "&cThat cosmetic is exclusive to Founders.");
            } else {
                deny(player, "&cLocked. Get the " + rarityRanks.get(cosmetic.rarity()) + " &crank or a "
                    + cosmetic.rarity().display() + " Token &cat &b" + storeHost() + "&c.");
            }
            return;
        }
        if (click.isRightClick() && category != Category.TAGS) {
            if (tryPreview(player)) {
                player.closeInventory();
                if (category == Category.KILL_EFFECTS) playKillEffect(cosmetic.id(), player.getLocation().add(0, 1, 0), player, true);
                else previewTrail(player, cosmetic.id());
            }
            return;
        }
        PlayerData data = data(player);
        if (isEquipped(data, category, cosmetic.id())) {
            unequip(player, category);
        } else {
            switch (category) {
                case KILL_EFFECTS -> data.selectedKillEffect = cosmetic.id();
                case TRAILS -> data.selectedTrail = cosmetic.id();
                case TAGS -> {
                    data.selectedTag = cosmetic.id();
                    data.customPrefix = null;
                }
                default -> {
                    return;
                }
            }
            save(data);
            if (category == Category.TAGS) applyPrefix(player);
            msg(player, "&aEquipped " + cosmetic.display() + "&a.");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .6f, 1.4f);
        }
        openCosmeticList(player, category, page);
    }

    private void unequip(Player player, Category category) {
        PlayerData data = data(player);
        switch (category) {
            case KILL_EFFECTS -> data.selectedKillEffect = null;
            case TRAILS -> data.selectedTrail = null;
            case TAGS -> {
                clearPrefix(player);
                msg(player, "&eTitle removed.");
                return;
            }
            default -> {
                return;
            }
        }
        save(data);
        msg(player, "&eUnequipped.");
    }

    private ItemStack customTitleItem(Player player, PlayerData data) {
        if (!has(player, Category.TITLE, "all")) {
            return item(Material.WRITABLE_BOOK, "&b&lCustom Title", "&7Write your own title.", "",
                "&c✖ Locked", "&7Included with the " + perkRank("title", "&bDiamond") + " &7rank.");
        }
        if (data.savedCustomPrefix != null) {
            boolean equipped = data.savedCustomPrefix.equals(data.customPrefix);
            return glow(item(Material.NAME_TAG, "&b&lCustom Title",
                "&7Saved: &r" + data.savedCustomPrefix + "&f" + player.getName(), "",
                equipped ? "&a✔ Equipped" : "&eClick &7to equip",
                "&7Change it with &f/settitle <text>"), equipped);
        }
        return item(Material.WRITABLE_BOOK, "&b&lCustom Title", "&7Write your own title with",
            "&f/settitle <text>", "", "&7Up to " + prefixMaxLength + " characters.", "", "&eClick for instructions");
    }

    private void clickCustomTitle(Player player) {
        PlayerData data = data(player);
        if (!has(player, Category.TITLE, "all")) {
            deny(player, "&cCustom titles are included with the " + perkRank("title", "&bDiamond") + " &crank.");
        } else if (data.savedCustomPrefix == null) {
            player.closeInventory();
            msg(player, "&eWrite your title with &b/settitle <text>&e. You'll see a preview first.");
        } else {
            data.customPrefix = data.savedCustomPrefix;
            data.selectedTag = null;
            save(data);
            applyPrefix(player);
            msg(player, "&aEquipped your custom title: &r" + data.savedCustomPrefix);
            openTags(player, 1);
        }
    }

    // --- Tokens ---

    private void openTokens(Player player) {
        PlayerData data = data(player);
        Inventory inv = frame(player, Menu.TOKENS, "Cosmetics ▸ Tokens", null);
        List<Integer> slots = List.of(MenuLayout.contentSlot(1, 1), MenuLayout.contentSlot(1, 2), MenuLayout.contentSlot(1, 4), MenuLayout.contentSlot(1, 5));
        Rarity[] rarities = Rarity.values();
        for (int i = 0; i < rarities.length; i++) {
            Rarity rarity = rarities[i];
            int count = data.tokens(rarity);
            ItemStack token = glow(item(rarity.tokenIcon, rarity.color + "&l" + rarity.label + " Token",
                "&7You have: &f" + count, "",
                "&7Unlocks any one " + rarity.display() + " &7cosmetic.",
                "&7Open a category and click a locked",
                rarity.display() + " &7cosmetic to redeem it.", "",
                "&8Founder cosmetics can't be redeemed."), count > 0);
            token.setAmount(Math.max(1, Math.min(64, count)));
            inv.setItem(slots.get(i), token);
        }
        button(inv, MenuLayout.contentSlot(2, 3), item(Material.WRITABLE_BOOK, "&e&lLore Credits",
            "&7You have: &f" + data.loreCredits, "",
            "&7Each credit renames one item", "&7and gives it custom lore.", "",
            "&eClick to open Item Lore"), click -> openLoreMenu(player));
        backButton(inv, "Cosmetics", () -> openHub(player));
        storeButton(inv, player);
        player.openInventory(inv);
    }

    private void openRedeemConfirm(Player player, Category category, NamedCosmetic cosmetic, int page) {
        Rarity rarity = cosmetic.rarity();
        int have = data(player).tokens(rarity);
        Inventory inv = dialog(Menu.REDEEM_CONFIRM, "Confirm ▸ Redeem Token");
        button(inv, 11, item(Material.LIME_STAINED_GLASS_PANE, "&a&lRedeem",
            "&7Spend &f1 " + rarity.display() + " Token &7to unlock",
            cosmetic.display() + " &7forever.", "",
            "&7Tokens left afterwards: &f" + Math.max(0, have - 1)), click -> redeemToken(player, category, cosmetic, page));
        inv.setItem(13, item(cosmetic.icon(), cosmetic.display(), rarity.color + "● " + rarity.label, "", "&7" + categoryLabel(category)));
        button(inv, 15, item(Material.RED_STAINED_GLASS_PANE, "&c&lCancel", "&7Go back without spending."),
            click -> openCosmeticList(player, category, page));
        player.openInventory(inv);
    }

    private void redeemToken(Player player, Category category, NamedCosmetic cosmetic, int page) {
        UUID uuid = player.getUniqueId();
        player.closeInventory();
        runStorage("redeem a token", uuid, () -> storage.redeemToken(uuid, cosmetic.rarity(), category, cosmetic.id()), outcome -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null) return;
            switch (outcome) {
                case REDEEMED -> {
                    msg(online, "&aUnlocked " + cosmetic.display() + " &awith a " + cosmetic.rarity().display() + " Token&a!");
                    online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, .6f, 1.2f);
                }
                case ALREADY_OWNED -> msg(online, "&eYou already own that cosmetic. No token was spent.");
                case NO_TOKEN -> deny(online, "&cYou have no " + cosmetic.rarity().display() + " Tokens &cleft.");
            }
            refreshEntitlements(uuid, () -> {
                Player refreshed = Bukkit.getPlayer(uuid);
                if (refreshed != null) openCosmeticList(refreshed, category, page);
            });
        }, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) deny(online, "&cCouldn't redeem right now, and no token was spent. Try again in a moment.");
        });
    }

    // --- Kill messages ---

    private void openKillMessages(Player player) {
        PlayerData data = data(player);
        boolean unlocked = has(player, Category.KILLMSG, "all");
        Inventory inv = frame(player, Menu.KILL_MESSAGES, "Cosmetics ▸ Kill Messages", null);
        String current = data.killMessage == null ? "&7Default death message"
            : "&r" + LegacyComponentSerializer.legacySection().serialize(renderKillMessageComponent(normalizeKillMessage(data.killMessage),
                component(player.getName()), component("ExampleVictim"), hoverItemText(exampleLorePreset())));
        inv.setItem(MenuLayout.contentSlot(0, 3), item(Material.PAPER, "&6&lYour Kill Message", "&7Equipped: &f" + currentKillMessageText(data), current));
        List<Integer> slots = MenuLayout.spacedSlots(killMessagePresets.size());
        for (int i = 0; i < killMessagePresets.size() && i < slots.size(); i++) {
            KillMessagePreset preset = killMessagePresets.get(i);
            boolean equipped = preset.message.equals(data.killMessage);
            String preview = LegacyComponentSerializer.legacySection().serialize(renderKillMessageComponent(preset.message,
                component(player.getName()), component("ExampleVictim"), hoverItemText(exampleLorePreset())));
            List<String> lore = new ArrayList<>(List.of("&7Preview:", "&r" + preview, ""));
            if (!unlocked) {
                lore.add("&c✖ Locked");
                lore.add("&7Included with the " + perkRank("killmsg", "&dNetherite") + " &7rank.");
            } else {
                lore.add(equipped ? "&a✔ Equipped" : "&eClick &7to preview and equip");
            }
            button(inv, slots.get(i), glow(item(preset.icon, preset.display, lore.toArray(String[]::new)), equipped), click -> {
                if (!has(player, Category.KILLMSG, "all")) {
                    deny(player, "&cKill messages are included with the " + perkRank("killmsg", "&dNetherite") + " &crank.");
                    return;
                }
                data(player).pendingKillMessage = preset.message;
                save(data(player));
                openKillMessagePreview(player, preset.message);
            });
        }
        button(inv, SLOT_LEFT_ACTION, item(Material.WRITABLE_BOOK, "&b&lWrite Your Own",
            unlocked ? "&7Use &f/setkillmsg <message>" : "&c✖ Locked",
            unlocked ? "&7Placeholders: &f{killer} {victim} {item}" : "&7Included with the " + perkRank("killmsg", "&dNetherite") + " &7rank."),
            click -> {
                if (!has(player, Category.KILLMSG, "all")) {
                    deny(player, "&cKill messages are included with the " + perkRank("killmsg", "&dNetherite") + " &crank.");
                    return;
                }
                player.closeInventory();
                msg(player, "&eWrite one with &b/setkillmsg <message>&e. Use &f{killer}&e, &f{victim} &eand &f{item}&e.");
            });
        if (data.killMessage != null) {
            button(inv, SLOT_RIGHT_ACTION, item(Material.GRAY_DYE, "&c&lReset", "&7Go back to the default", "&7death message."), click -> {
                clearKillMessage(player);
                openKillMessages(player);
            });
        }
        backButton(inv, "Cosmetics", () -> openHub(player));
        storeButton(inv, player);
        player.openInventory(inv);
    }

    private void openKillMessagePreview(Player player, String raw) {
        raw = normalizeKillMessage(raw);
        Inventory inv = dialog(Menu.KILLMSG_PREVIEW, "Confirm ▸ Kill Message");
        String preview = LegacyComponentSerializer.legacySection().serialize(renderKillMessageComponent(raw, component(player.getName()), component("ExampleVictim"), hoverItemText(exampleLorePreset())));
        button(inv, 11, item(Material.LIME_STAINED_GLASS_PANE, "&a&lSave", "&7Use this kill message."), click -> {
            PlayerData data = data(player);
            if (data.pendingKillMessage == null) {
                msg(player, "&cThat preview expired. Try /setkillmsg <message> again.");
            } else {
                data.killMessage = data.pendingKillMessage;
                data.pendingKillMessage = null;
                save(data);
                msg(player, "&aKill message saved.");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .6f, 1.4f);
            }
            openKillMessages(player);
        });
        inv.setItem(13, item(Material.PAPER, "&6&lPreview", "&7How it will look in chat:", "&r" + preview, "", "&8Raw: &7" + raw));
        button(inv, 15, item(Material.RED_STAINED_GLASS_PANE, "&c&lCancel"), click -> {
            data(player).pendingKillMessage = null;
            save(data(player));
            openKillMessages(player);
        });
        player.openInventory(inv);
    }

    // --- Custom title preview ---

    private void openPrefixPreview(Player player, String clean) {
        Inventory inv = dialog(Menu.PREFIX_PREVIEW, "Confirm ▸ Custom Title");
        button(inv, 11, item(Material.LIME_STAINED_GLASS_PANE, "&a&lSave and Equip", "&7Save this as your custom title."), click -> {
            String pending = pendingPrefix.remove(player.getUniqueId());
            if (pending == null) {
                msg(player, "&cThat preview expired. Try /settitle <text> again.");
                player.closeInventory();
                return;
            }
            PlayerData data = data(player);
            data.customPrefix = pending;
            data.savedCustomPrefix = pending;
            data.selectedTag = null;
            save(data);
            applyPrefix(player);
            msg(player, "&aSaved and equipped your custom title: &r" + pending);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .6f, 1.4f);
            openTags(player, 1);
        });
        inv.setItem(13, item(Material.NAME_TAG, "&b&lPreview", "&7Name: &r" + clean + "&f" + player.getName(), "",
            "&7Chat: &r" + clean + "&f" + player.getName() + "&7: Hello!"));
        button(inv, 15, item(Material.RED_STAINED_GLASS_PANE, "&c&lCancel"), click -> {
            pendingPrefix.remove(player.getUniqueId());
            player.closeInventory();
            msg(player, "&eCustom title cancelled.");
        });
        player.openInventory(inv);
    }

    // --- Item lore ---

    private void openLoreMenu(Player player) {
        PlayerData data = data(player);
        Inventory inv = frame(player, Menu.LORE_MENU, "Cosmetics ▸ Item Lore", null);
        ItemStack held = player.getInventory().getItemInMainHand();
        ItemStack heldIcon = held.getType().isAir() ? new ItemStack(Material.BARRIER) : new ItemStack(held.getType());
        ItemMeta heldMeta = heldIcon.getItemMeta();
        heldMeta.displayName(component("&f&lHeld Item: &r" + heldItemName(player)));
        heldMeta.lore(List.of(component("&7Lore Credits: &e" + data.loreCredits), Component.empty(),
            component(data.loreCredits > 0 || player.isOp() ? "&7Each change uses one credit." : "&cYou have no Lore Credits.")));
        heldIcon.setItemMeta(heldMeta);
        inv.setItem(MenuLayout.contentSlot(0, 3), heldIcon);
        button(inv, MenuLayout.contentSlot(1, 1), item(Material.WRITABLE_BOOK, "&b&lWrite Lore",
            "&f/setlore <Name|Line 1|Line 2>", "&f/setlore book &7reads your offhand book", "&f/setlore draft &7builds it line by line", "",
            "&7You'll see a preview first.", "", "&eClick for instructions"), click -> {
            player.closeInventory();
            msg(player, "&eShort lore: &f/setlore <Name|Lore line 1|Lore line 2>");
            msg(player, "&eLong lore: &f/setlore book &7or &f/setlore draft start <name>");
        });
        button(inv, MenuLayout.contentSlot(1, 3), item(Material.FILLED_MAP, "&b&lWeb Editor", "&7Design lore in your browser.", "", "&eClick for the link"), click -> {
            player.closeInventory();
            sendLoreEditor(player);
        });
        button(inv, MenuLayout.contentSlot(1, 5), exampleLorePreset(), click -> applyExampleLorePreset(player));
        button(inv, MenuLayout.contentSlot(2, 2), item(Material.BOOK, "&e&lLore History", "&7Your recent lore designs.", "", "&eClick to open"),
            click -> openLoreHistory(player));
        button(inv, MenuLayout.contentSlot(2, 4), item(Material.GRINDSTONE, "&c&lClear Held Item", "&7Remove the custom name and lore", "&7from the item in your hand.", "", "&eClick to preview"),
            click -> openLoreClearConfirm(player));
        backButton(inv, "Cosmetics", () -> openHub(player));
        storeButton(inv, player);
        player.openInventory(inv);
    }

    private void openLoreHistory(Player player) {
        Inventory inv = frame(player, Menu.LORE_HISTORY, "Item Lore ▸ History", null);
        List<ItemStack> history = data(player).loreHistory;
        List<Integer> slots = MenuLayout.symmetricSlots(Math.min(history.size(), MenuLayout.CONTENT_CAPACITY), MenuLayout.CONTENT_COLUMNS);
        for (int i = 0; i < slots.size(); i++) inv.setItem(slots.get(i), history.get(i));
        if (history.isEmpty()) {
            inv.setItem(MenuLayout.contentSlot(1, 3), item(Material.PAPER, "&7No lore yet", "&7Items you customize appear here."));
        }
        inv.setItem(SLOT_LEFT_ACTION, item(Material.OAK_SIGN, "&e&lHistory", "&7Your last " + MenuLayout.CONTENT_CAPACITY + " lore designs.", "&7Hover one to read it."));
        button(inv, SLOT_RIGHT_ACTION, item(Material.GRAY_DYE, "&c&lClear History", "&7Removes this list.", "&7Your items are not changed."),
            click -> clearLoreHistory(player));
        backButton(inv, "Item Lore", () -> openLoreMenu(player));
        storeButton(inv, player);
        player.openInventory(inv);
    }

    private void openLorePreview(Player player, String raw) {
        Inventory inv = dialog(Menu.LORE_PREVIEW, "Confirm ▸ Item Lore");
        ItemStack preview = player.getInventory().getItemInMainHand().clone();
        applyLoreToItem(preview, raw);
        button(inv, 11, item(Material.LIME_STAINED_GLASS_PANE, "&a&lApply", "&7Apply this name and lore.", "&7Uses one Lore Credit."), click -> {
            String pending = pendingLore.remove(player.getUniqueId());
            if (pending == null) msg(player, "&cThat preview expired. Try /setlore again.");
            else applyLore(player, pending);
            player.closeInventory();
        });
        inv.setItem(13, preview);
        button(inv, 15, item(Material.RED_STAINED_GLASS_PANE, "&c&lCancel"), click -> {
            pendingLore.remove(player.getUniqueId());
            player.closeInventory();
            msg(player, "&eItem lore cancelled. No credit was used.");
        });
        player.openInventory(inv);
    }

    private void openLoreClearConfirm(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            deny(player, "&cHold the item you want to clear first.");
            return;
        }
        Inventory inv = dialog(Menu.LORE_CLEAR_CONFIRM, "Confirm ▸ Clear Lore");
        ItemStack preview = held.clone();
        clearLoreFromItem(preview);
        button(inv, 11, item(Material.LIME_STAINED_GLASS_PANE, "&a&lClear", "&7Remove the custom name and lore.", "&7Other item data stays the same."), click -> {
            ItemStack current = player.getInventory().getItemInMainHand();
            if (current.getType().isAir()) {
                msg(player, "&cHold an item first.");
            } else {
                clearLoreFromItem(current);
                msg(player, "&eCleared the custom name and lore from your held item.");
                player.playSound(player.getLocation(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1f, 1f);
            }
            player.closeInventory();
        });
        inv.setItem(13, preview);
        button(inv, 15, item(Material.RED_STAINED_GLASS_PANE, "&c&lCancel"), click -> openLoreMenu(player));
        player.openInventory(inv);
    }

    // --- Armor trims ---

    private void openTrimPatterns(Player player) {
        if (!has(player, Category.TRIM, "all")) {
            deny(player, "&cArmor trims are included with the " + perkRank("trim", "&bDiamond") + " &crank.");
            return;
        }
        if (!isArmor(player.getInventory().getItemInMainHand())) {
            deny(player, "&cHold the armor piece you want to trim, then try again.");
            return;
        }
        if (!player.isOp()) {
            long elapsed = System.currentTimeMillis() - data(player).lastTrim;
            long cooldown = trimCooldownHours * 60L * 60L * 1000L;
            if (elapsed < cooldown) {
                deny(player, "&cYou can trim again in " + formatDuration(cooldown - elapsed) + ".");
                return;
            }
        }
        Inventory inv = frame(player, Menu.TRIM_PATTERNS, "Armor Trims ▸ Pattern", null);
        List<Integer> slots = MenuLayout.symmetricSlots(trimPatterns.size(), MenuLayout.CONTENT_COLUMNS);
        for (int i = 0; i < trimPatterns.size() && i < slots.size(); i++) {
            String pattern = trimPatterns.get(i);
            button(inv, slots.get(i), item(trimTemplate(pattern), "&6&l" + titleCase(pattern), "&7Click to choose a material."),
                click -> openTrimMaterials(player, pattern));
        }
        button(inv, SLOT_LEFT_ACTION, item(Material.GRINDSTONE, "&c&lRemove Trim", "&7Remove the trim from the", "&7armor in your hand."), click -> {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!isArmor(held)) {
                deny(player, "&cHold the armor piece you want to reset.");
                return;
            }
            removeTrim(held);
            msg(player, "&eArmor trim removed.");
            player.closeInventory();
        });
        ItemStack held = player.getInventory().getItemInMainHand().clone();
        inv.setItem(SLOT_RIGHT_ACTION, held);
        backButton(inv, "Cosmetics", () -> openHub(player));
        storeButton(inv, player);
        player.openInventory(inv);
    }

    private Material trimTemplate(String pattern) {
        Material template = Material.matchMaterial(pattern.toUpperCase(Locale.ROOT) + "_ARMOR_TRIM_SMITHING_TEMPLATE");
        return template == null ? Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE : template;
    }

    private void openTrimMaterials(Player player, String pattern) {
        Inventory inv = frame(player, Menu.TRIM_MATERIALS, "Armor Trims ▸ " + titleCase(pattern), null);
        List<Integer> slots = MenuLayout.symmetricSlots(trimMaterials.size(), 5);
        for (int i = 0; i < trimMaterials.size() && i < slots.size(); i++) {
            String material = trimMaterials.get(i);
            button(inv, slots.get(i), item(trimMaterialIcon(material), "&e&l" + titleCase(material), "&7Pattern: &6" + titleCase(pattern), "", "&eClick to preview"),
                click -> openTrimConfirm(player, pattern, material));
        }
        backButton(inv, "Patterns", () -> openTrimPatterns(player));
        storeButton(inv, player);
        player.openInventory(inv);
    }

    private void openTrimConfirm(Player player, String pattern, String material) {
        Inventory inv = dialog(Menu.TRIM_CONFIRM, "Confirm ▸ Armor Trim");
        ItemStack preview = player.getInventory().getItemInMainHand().clone();
        applyTrim(preview, pattern, material);
        ItemMeta meta = preview.getItemMeta();
        meta.lore(List.of(component("&7Pattern: &6" + titleCase(pattern)), component("&7Material: &f" + titleCase(material))));
        preview.setItemMeta(meta);
        button(inv, 11, item(Material.LIME_STAINED_GLASS_PANE, "&a&lApply Trim", "&7Next trim available in " + trimCooldownHours + "h."), click -> {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (!isArmor(item)) {
                deny(player, "&cYou must still be holding the armor piece.");
                player.closeInventory();
                return;
            }
            applyTrim(item, pattern, material);
            data(player).lastTrim = System.currentTimeMillis();
            save(data(player));
            msg(player, "&aYour armor has been trimmed.");
            player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f);
            player.closeInventory();
        });
        inv.setItem(13, preview);
        button(inv, 15, item(Material.RED_STAINED_GLASS_PANE, "&c&lBack", "&7Pick a different material."), click -> openTrimMaterials(player, pattern));
        player.openInventory(inv);
    }

    private void openAdminPlayers(Player admin) {
        openAdminPlayers(admin, 1);
    }

    private void openAdminPlayers(Player admin, int page) {
        int requestedPage = Math.max(1, page);
        List<UUID> onlineIds = Bukkit.getOnlinePlayers().stream()
            .map(Player::getUniqueId)
            .toList();
        if (!storageHealthy || storage == null || storageExecutor == null || storageExecutor.isShutdown()) {
            List<UUID> targets = mergeManagedPlayerIds(List.of(), onlineIds);
            lastManagedPlayerIds.put(admin.getUniqueId(), targets);
            renderAdminPlayers(admin, requestedPage, targets);
            return;
        }
        msg(admin, "&eLoading cosmetics player list...");
        storageExecutor.execute(() -> {
            try {
                List<UUID> storedIds = storage.managedPlayerIds();
                Bukkit.getScheduler().runTask(this, () -> {
                    if (!admin.isOnline()) return;
                    List<UUID> targets = mergeManagedPlayerIds(storedIds, onlineIds);
                    lastManagedPlayerIds.put(admin.getUniqueId(), targets);
                    renderAdminPlayers(admin, requestedPage, targets);
                    preloadAdminPageTargets(admin, requestedPage, targets);
                });
            } catch (RuntimeException e) {
                logStorageFailure("list cosmetics players", admin.getUniqueId(), e);
            }
        });
    }

    private void renderAdminPlayers(Player admin, int page, List<UUID> targets) {
        page = Math.max(1, page);
        Inventory inv = gui(Menu.ADMIN_PLAYERS, 54, "&8Cosmetics Admin &7Page " + page, page);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        int offset = (page - 1) * ADMIN_PLAYER_SLOTS.size();
        for (int i = 0; i < ADMIN_PLAYER_SLOTS.size(); i++) {
            int targetIndex = offset + i;
            if (targetIndex >= targets.size()) break;
            UUID target = targets.get(targetIndex);
            PlayerData data = players.get(target);
            boolean online = Bukkit.getPlayer(target) != null;
            inv.setItem(ADMIN_PLAYER_SLOTS.get(i), headItem(target,
                (online ? "&a" : "&e") + playerName(target),
                "&7Status: " + (online ? "&aOnline" : "&8Offline"),
                data == null ? "&7Stored grants: &8Loading..." : "&7Stored grants: &f" + grantCount(data),
                data == null ? "&7Lore credits: &8Loading..." : "&7Lore credits: &e" + data.loreCredits,
                "",
                "&eClick to manage cosmetics."));
        }
        inv.setItem(4, item(Material.NETHER_STAR, "&b&lCosmetics Admin",
            "&7Click a player head to manage every cosmetic.",
            "&7Players appear here when online or when",
            "&7they have saved/granted cosmetic data."));
        inv.setItem(49, item(Material.BOOK, "&bAdmin Commands",
            "&7Offline players:",
            "&f/cosadmin preset <player> patreon",
            "&f/cosadmin preset <player> supporter replace",
            "",
            "&7GUI includes saved cosmetic players."));
        if (page > 1) inv.setItem(45, item(Material.ARROW, "&ePrevious Page"));
        if (offset + ADMIN_PLAYER_SLOTS.size() < targets.size()) inv.setItem(53, item(Material.ARROW, "&aNext Page"));
        admin.openInventory(inv);
    }

    private void preloadAdminPageTargets(Player admin, int page, List<UUID> targets) {
        int offset = (page - 1) * ADMIN_PLAYER_SLOTS.size();
        for (int i = 0; i < ADMIN_PLAYER_SLOTS.size(); i++) {
            int targetIndex = offset + i;
            if (targetIndex >= targets.size()) return;
            UUID target = targets.get(targetIndex);
            if (players.containsKey(target)) continue;
            loadAsync(target, ignored -> {
                if (admin.isOnline()) {
                    renderAdminPlayers(admin, page, targets);
                }
            });
        }
    }

    private List<UUID> mergeManagedPlayerIds(List<UUID> storedIds, List<UUID> onlineIds) {
        Set<UUID> ids = new LinkedHashSet<>(storedIds);
        ids.addAll(onlineIds);
        return new ArrayList<>(ids);
    }

    private void openAdminCategories(Player admin, UUID target) {
        PlayerData targetData = players.get(target);
        if (targetData == null) {
            msg(admin, "&eLoading player cosmetics...");
            loadAsync(target, ignored -> openAdminCategories(admin, target));
            return;
        }
        Inventory inv = gui(Menu.ADMIN_CATEGORIES, 54, "&8Admin > " + playerName(target), target.toString());
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(4, headItem(target, "&e" + playerName(target),
            "&7Stored grants: &f" + grantCount(targetData),
            "&7Lore credits: &e" + targetData.loreCredits,
            "",
            "&7Left-click a category to edit items.",
            "&7Right-click a category to enable/disable all.",
            "&7Shift-right-click clears that category."));
        List<Category> categories = adminCategories();
        for (int i = 0; i < categories.size() && i < ADMIN_CATEGORY_SLOTS.size(); i++) {
            inv.setItem(ADMIN_CATEGORY_SLOTS.get(i), categoryItem(targetData, categories.get(i)));
        }
        inv.setItem(31, item(Material.GOLD_INGOT, "&6Supporter Presets",
            "&7Apply supporter packages from config.",
            "&7Best for 10-20 patrons.",
            "",
            "&eClick to open."));
        inv.setItem(40, item(Material.BARRIER, "&cClear All Grants", "&7Removes every stored cosmetic grant.", "&7Does not erase lore history or saved text."));
        inv.setItem(45, item(Material.ARROW, "&eBack to Players"));
        admin.openInventory(inv);
    }

    private void openAdminPresets(Player admin, UUID target) {
        Inventory inv = gui(Menu.ADMIN_PRESETS, 27, "&8Admin > Presets", target.toString());
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(4, headItem(target, "&e" + playerName(target), "&7Click a package to add grants.", "&7Shift-click replaces old grants first."));
        List<Integer> slots = centeredRowSlots(grantPresets.size(), List.of(10, 11, 12, 13, 14, 15, 16));
        for (int i = 0; i < grantPresets.size() && i < slots.size(); i++) {
            GrantPreset preset = grantPresets.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("&7Adds a configured cosmetics package.");
            lore.add("&7Lore credits: &e+" + preset.loreCredits);
            lore.add("");
            lore.addAll(preset.summary());
            lore.add("");
            lore.add("&eClick to apply.");
            lore.add("&6Shift-click to replace grants.");
            inv.setItem(slots.get(i), item(preset.icon, preset.display, lore.toArray(String[]::new)));
        }
        if (grantPresets.isEmpty()) {
            inv.setItem(13, item(Material.BARRIER, "&cNo presets configured", "&7Add grant-presets to config.yml."));
        }
        inv.setItem(18, item(Material.ARROW, "&eBack to Categories"));
        admin.openInventory(inv);
    }

    private void openAdminCategory(Player admin, UUID target, Category category) {
        openAdminCategory(admin, target, category, 1);
    }

    private void openAdminCategory(Player admin, UUID target, Category category, int page) {
        int requestedPage = Math.max(1, page);
        PlayerData targetData = players.get(target);
        if (targetData == null) {
            msg(admin, "&eLoading player cosmetics...");
            loadAsync(target, ignored -> openAdminCategory(admin, target, category, requestedPage));
            return;
        }
        Inventory inv = gui(Menu.ADMIN_CATEGORY, 54, "&8Admin > " + categoryLabel(category) + " &7Page " + requestedPage, target + "|" + category.name() + "|" + requestedPage);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        boolean all = categoryAllEnabled(targetData, category);
        inv.setItem(4, item(all ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
            (all ? "&a&lALL ENABLED" : "&c&lALL DISABLED"),
            "&7Player: &f" + playerName(target),
            "&7Category: &f" + categoryLabel(category),
            "",
            "&eClick to toggle full category access."));

        List<NamedCosmetic> list = categoryCosmetics(category);
        if (list.isEmpty()) {
            boolean granted = targetData.hasGrant(category, "all") || targetData.hasGrant(category, categoryDefaultId(category));
            inv.setItem(22, item(granted ? Material.LIME_DYE : categoryIcon(category),
                "&e" + categoryLabel(category),
                granted ? "&aEnabled for this player." : "&cDisabled for this player.",
                "",
                "&eClick to toggle this perk."));
        } else {
            int offset = (requestedPage - 1) * ADMIN_OPTION_SLOTS.size();
            for (int i = 0; i < ADMIN_OPTION_SLOTS.size(); i++) {
                int cosmeticIndex = offset + i;
                if (cosmeticIndex >= list.size()) break;
                NamedCosmetic cosmetic = list.get(cosmeticIndex);
                boolean granted = targetData.hasGrant(category, "all") || targetData.hasGrant(category, cosmetic.id);
                inv.setItem(ADMIN_OPTION_SLOTS.get(i), item(granted ? Material.LIME_DYE : cosmetic.icon,
                    cosmetic.display,
                    granted ? "&aEnabled for this player." : "&cDisabled for this player.",
                    "&7ID: &f" + cosmetic.id,
                    "",
                    "&eClick to toggle."));
            }
            if (requestedPage > 1) inv.setItem(48, item(Material.ARROW, "&ePrevious Page"));
            if (offset + ADMIN_OPTION_SLOTS.size() < list.size()) inv.setItem(50, item(Material.ARROW, "&aNext Page"));
        }
        if (category == Category.TAGS) {
            boolean customEnabled = targetData.hasGrant(Category.TITLE, "all");
            inv.setItem(40, item(customEnabled ? Material.LIME_DYE : Material.WRITABLE_BOOK,
                "&b&lCustom Prefixes",
                customEnabled ? "&aEnabled for this player." : "&cDisabled for this player.",
                "&7Allows /settitle <text>.",
                "",
                "&eClick to toggle."));
        }
        inv.setItem(45, item(Material.ARROW, "&eBack to Player"));
        inv.setItem(49, item(Material.BARRIER, "&cClear Category", "&7Removes all stored grants for &f" + categoryLabel(category) + "&7."));
        admin.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            if (pendingTrimChecks.add(player.getUniqueId())) {
                Bukkit.getScheduler().runTask(this, () -> {
                    pendingTrimChecks.remove(player.getUniqueId());
                    enforceWornTrims(player);
                });
            }
            return;
        }
        event.setCancelled(true);
        if (holder.menu.admin && !player.hasPermission("civ.cosmetic.admin")) {
            player.closeInventory();
            msg(player, "&cNo permission!");
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory().getType() == InventoryType.PLAYER) return;
        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;
        if (!holder.menu.admin) {
            Consumer<ClickType> action = holder.actions.get(slot);
            if (action != null) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, .35f, 1f);
                action.accept(event.getClick());
            }
            return;
        }
        switch (holder.menu) {
            case ADMIN_PLAYERS -> clickAdminPlayers(player, slot, holder);
            case ADMIN_CATEGORIES -> clickAdminCategories(player, slot, event.getClick(), holder);
            case ADMIN_CATEGORY -> clickAdminCategory(player, slot, holder);
            case ADMIN_PRESETS -> clickAdminPresets(player, slot, event.getClick(), holder);
            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }

    private void clickAdminPlayers(Player admin, int slot, GuiHolder holder) {
        int page = holder.intData();
        if (slot == 45) {
            openAdminPlayers(admin, page - 1);
            return;
        }
        if (slot == 53) {
            openAdminPlayers(admin, page + 1);
            return;
        }
        int index = ADMIN_PLAYER_SLOTS.indexOf(slot);
        if (index < 0) return;
        List<UUID> targets = lastManagedPlayerIds.getOrDefault(admin.getUniqueId(), List.of());
        if (targets.isEmpty()) {
            openAdminPlayers(admin, page);
            return;
        }
        int targetIndex = (page - 1) * ADMIN_PLAYER_SLOTS.size() + index;
        if (targetIndex >= targets.size()) return;
        openAdminCategories(admin, targets.get(targetIndex));
    }

    private void clickAdminCategories(Player admin, int slot, ClickType click, GuiHolder holder) {
        UUID target = UUID.fromString(holder.data);
        PlayerData data = players.get(target);
        if (data == null) {
            msg(admin, "&eLoading player cosmetics...");
            loadAsync(target, ignored -> openAdminCategories(admin, target));
            return;
        }
        if (slot == 31) {
            openAdminPresets(admin, target);
            return;
        }
        if (slot == 40) {
            clearAllGrants(data);
            saveWithGrants(data);
            Player online = Bukkit.getPlayer(target);
            if (online != null) validateSelections(online);
            openAdminCategories(admin, target);
            msg(admin, "&cCleared all grants for &f" + playerName(target));
            return;
        }
        if (slot == 45) {
            openAdminPlayers(admin);
            return;
        }
        List<Category> categories = adminCategories();
        for (int i = 0; i < ADMIN_CATEGORY_SLOTS.size() && i < categories.size(); i++) {
            if (slot != ADMIN_CATEGORY_SLOTS.get(i)) continue;
            Category category = categories.get(i);
            if (click.isRightClick()) {
                if (click.isShiftClick()) clearCategoryGrants(data, category);
                else toggleCategoryAll(data, category);
                saveWithGrants(data);
                Player online = Bukkit.getPlayer(target);
                if (online != null) validateSelections(online);
                openAdminCategories(admin, target);
            } else {
                openAdminCategory(admin, target, category);
            }
            return;
        }
    }

    private void clickAdminCategory(Player admin, int slot, GuiHolder holder) {
        String[] parts = holder.data.split("\\|", 3);
        if (parts.length < 2) return;
        UUID target = UUID.fromString(parts[0]);
        Category category = Category.valueOf(parts[1]);
        int page = parts.length > 2 ? parseInt(parts[2], 1) : 1;
        PlayerData data = players.get(target);
        if (data == null) {
            msg(admin, "&eLoading player cosmetics...");
            loadAsync(target, ignored -> openAdminCategory(admin, target, category, page));
            return;
        }
        if (slot == 45) {
            openAdminCategories(admin, target);
            return;
        }
        if (slot == 48) {
            openAdminCategory(admin, target, category, page - 1);
            return;
        }
        if (slot == 50) {
            openAdminCategory(admin, target, category, page + 1);
            return;
        }
        if (slot == 49) {
            clearCategoryGrants(data, category);
            saveWithGrants(data);
            Player online = Bukkit.getPlayer(target);
            if (online != null) validateSelections(online);
            openAdminCategory(admin, target, category, page);
            msg(admin, "&cCleared &f" + categoryLabel(category) + " &cfor &f" + playerName(target));
            return;
        }
        if (slot == 4) {
            toggleCategoryAll(data, category);
            saveWithGrants(data);
            Player online = Bukkit.getPlayer(target);
            if (online != null) validateSelections(online);
            openAdminCategory(admin, target, category, page);
            return;
        }
        if (category == Category.TAGS && slot == 40) {
            toggleAll(data, Category.TITLE);
            saveWithGrants(data);
            Player online = Bukkit.getPlayer(target);
            if (online != null) validateSelections(online);
            openAdminCategory(admin, target, category, page);
            return;
        }
        List<NamedCosmetic> list = category == Category.KILL_EFFECTS ? killEffects : category == Category.TRAILS ? trails : category == Category.TAGS ? tags : List.of();
        if (list.isEmpty()) {
            if (slot != 22) return;
            String id = categoryDefaultId(category);
            if (data.hasGrant(category, "all") || data.hasGrant(category, id)) {
                data.revoke(category, "all");
                data.revoke(category, id);
            } else {
                data.grant(category, id);
            }
            saveWithGrants(data);
            Player online = Bukkit.getPlayer(target);
            if (online != null) validateSelections(online);
            openAdminCategory(admin, target, category, page);
            return;
        }
        int index = ADMIN_OPTION_SLOTS.indexOf(slot);
        int cosmeticIndex = (page - 1) * ADMIN_OPTION_SLOTS.size() + index;
        if (index >= 0 && cosmeticIndex < list.size()) {
            String id = list.get(cosmeticIndex).id;
            if (data.hasGrant(category, "all")) {
                data.revoke(category, "all");
                for (NamedCosmetic cosmetic : list) data.grant(category, cosmetic.id);
            }
            if (data.hasGrant(category, id)) data.revoke(category, id);
            else data.grant(category, id);
            saveWithGrants(data);
            Player online = Bukkit.getPlayer(target);
            if (online != null) validateSelections(online);
            openAdminCategory(admin, target, category, page);
        }
    }

    private void clickAdminPresets(Player admin, int slot, ClickType click, GuiHolder holder) {
        UUID target = UUID.fromString(holder.data);
        PlayerData data = players.get(target);
        if (data == null) {
            msg(admin, "&eLoading player cosmetics...");
            loadAsync(target, ignored -> openAdminPresets(admin, target));
            return;
        }
        if (slot == 18) {
            openAdminCategories(admin, target);
            return;
        }
        List<Integer> slots = centeredRowSlots(grantPresets.size(), List.of(10, 11, 12, 13, 14, 15, 16));
        int index = slots.indexOf(slot);
        if (index < 0 || index >= grantPresets.size()) return;
        GrantPreset preset = grantPresets.get(index);
        boolean replace = click.isShiftClick();
        if (replace) clearAllGrants(data);
        applyPreset(data, preset);
        saveWithGrants(data);
        Player online = Bukkit.getPlayer(target);
        if (online != null) validateSelections(online);
        openAdminCategories(admin, target);
        msg(admin, "&aApplied &e" + preset.id + " &ato &f" + playerName(target) + (replace ? " &7(replaced old grants)" : ""));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerData data = players.get(uuid);
        if (data != null) {
            save(data);
            players.remove(uuid, data);
            if (storageExecutor != null && !storageExecutor.isShutdown()) {
                storageExecutor.execute(() -> {
                    if (!players.containsKey(uuid)) storage.forget(uuid);
                });
            }
        }
        lastEffectPreview.remove(uuid);
        linkCooldowns.clear(uuid);
        pendingTrimChecks.remove(uuid);
        pendingPrefix.remove(uuid);
        pendingLore.remove(uuid);
        loreDrafts.remove(uuid);
        activeTrailsByPlayer.remove(uuid);
        loadingPlayers.remove(uuid);
        loadCallbacks.remove(uuid);
        lastManagedPlayerIds.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        recordWeaponKill(killer, event.getEntity());
        if (!civChatKillProviderRegistered) {
            ItemStack weapon = killer.getInventory().getItemInMainHand();
            Component killMessage = getKillMessage(killer, event.getEntity(), weapon, defaultWeaponDescription(weapon));
            if (killMessage != null) {
                event.deathMessage(killMessage);
            }
        }
        PlayerData data = data(killer);
        if (data.selectedKillEffect == null) return;
        if (!has(killer, Category.KILL_EFFECTS, data.selectedKillEffect)) {
            data.selectedKillEffect = null;
            save(data);
            return;
        }
        playKillEffect(data.selectedKillEffect, event.getEntity().getLocation().add(0, 1, 0), killer, false);
    }

    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        ItemStack result = event.getInventory().getResult();
        ItemStack marked = markCraftedWeapon(result, player, null);
        if (marked != null) {
            event.getInventory().setResult(marked);
        }
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        ItemStack marked = markCraftedWeapon(event.getResult(), player, event.getInventory().getInputEquipment());
        if (marked != null) {
            event.setResult(marked);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)) return;
        PlayerData data = data(player);
        if (data.selectedTrail == null) return;
        if (!has(player, Category.TRAILS, data.selectedTrail)) {
            data.selectedTrail = null;
            save(data);
            return;
        }
        String trail = data.selectedTrail;
        UUID shooter = player.getUniqueId();
        if (!tryStartTrail(shooter)) return;
        new BukkitRunnable() {
            int ticks;
            int stuckTicks;
            boolean finished;
            @Override
            public void run() {
                if (ticks++ >= maxTrailTicks || projectile.isDead() || !projectile.isValid() || (projectile instanceof Projectile p && p.isOnGround())) {
                    stop();
                    return;
                }
                Location loc = projectile.getLocation();
                if (isUnsafeTrailBlock(loc)) {
                    stop();
                    return;
                }
                if (projectile.getVelocity().lengthSquared() <= stuckVelocityThreshold * stuckVelocityThreshold) {
                    stuckTicks++;
                    if (stuckTicks >= stuckTicksBeforeStop) {
                        stop();
                        return;
                    }
                } else {
                    stuckTicks = 0;
                }
                drawTrail(trail, loc);
            }

            private void stop() {
                if (finished) return;
                finished = true;
                finishTrail(shooter);
                cancel();
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        Bukkit.getScheduler().runTask(this, () -> enforceWornTrims(event.getPlayer()));
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Bukkit.getScheduler().runTask(this, () -> enforceWornTrims(event.getPlayer()));
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        enforceWornTrims(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            Bukkit.getScheduler().runTaskLater(this, () -> enforceWornTrims(player), 2L);
        }
    }

    private void enforceWornTrims(Player player) {
        if (!trimEnforcement || has(player, Category.TRIM, "all")) return;
        PlayerInventory inv = player.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        boolean changed = false;
        for (ItemStack item : armor) {
            if (removeTrim(item)) changed = true;
        }
        if (changed) {
            inv.setArmorContents(armor);
            msg(player, "&eArmor trims are cosmetic perks, so trims on worn armor were removed.");
        }
    }

    private boolean tryStartTrail(UUID shooter) {
        if (activeTrailTasks >= maxActiveTrailsGlobal) return false;
        int active = activeTrailsByPlayer.getOrDefault(shooter, 0);
        if (active >= maxActiveTrailsPerPlayer) return false;
        activeTrailTasks++;
        activeTrailsByPlayer.put(shooter, active + 1);
        return true;
    }

    private void finishTrail(UUID shooter) {
        activeTrailTasks = Math.max(0, activeTrailTasks - 1);
        int active = activeTrailsByPlayer.getOrDefault(shooter, 0) - 1;
        if (active <= 0) activeTrailsByPlayer.remove(shooter);
        else activeTrailsByPlayer.put(shooter, active);
    }

    private boolean isUnsafeTrailBlock(Location loc) {
        Material material = loc.getBlock().getType();
        return material == Material.WATER
            || material == Material.LAVA
            || material == Material.BUBBLE_COLUMN
            || material == Material.COBWEB
            || material == Material.POWDER_SNOW;
    }

    private void validateSelections(Player player) {
        PlayerData data = data(player);
        boolean changed = false;
        if (data.selectedKillEffect != null && !has(player, Category.KILL_EFFECTS, data.selectedKillEffect)) {
            data.selectedKillEffect = null;
            changed = true;
        }
        if (data.selectedTrail != null && !has(player, Category.TRAILS, data.selectedTrail)) {
            data.selectedTrail = null;
            changed = true;
        }
        if (data.selectedTag != null && !has(player, Category.TAGS, data.selectedTag)) {
            data.selectedTag = null;
            changed = true;
        }
        if (data.customPrefix != null && !has(player, Category.TITLE, "all")) {
            data.customPrefix = null;
            changed = true;
        }
        if (changed) {
            save(data);
            applyPrefix(player);
        }
    }

    private void applyPrefix(Player player) {
        PlayerData data = data(player);
        Component name = Component.text(player.getName());
        Component custom = null;
        if (data.selectedTag != null) {
            NamedCosmetic tag = find(tags, data.selectedTag);
            if (tag != null) custom = component(premadeTagDisplay(tag)).append(name);
        } else if (data.customPrefix != null) {
            custom = component(data.customPrefix + "&r ").append(name);
        }
        Component rendered = custom == null ? baseDisplayName(player) : custom;
        player.displayName(rendered);
        player.playerListName(rendered);
        applyCivChatName(player, custom);
        applyTabName(player, custom);
    }

    private void applyCivChatName(Player player, @Nullable Component custom) {
        if (!Bukkit.getPluginManager().isPluginEnabled("CivChat2")) return;
        try {
            Object manager = civChatManager();
            if (custom == null) {
                manager.getClass().getMethod("removeCustomName", UUID.class).invoke(manager, player.getUniqueId());
            } else {
                manager.getClass().getMethod("setCustomName", UUID.class, Component.class).invoke(manager, player.getUniqueId(), custom);
            }
        } catch (Throwable t) {
            getLogger().warning("Failed to apply CivChat2 custom name for " + player.getName() + ": " + t.getMessage());
        }
    }

    private void applyTabName(Player player, @Nullable Component custom) {
        if (!Bukkit.getPluginManager().isPluginEnabled("TAB")) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                Object api = Class.forName("me.neznamy.tab.api.TabAPI").getMethod("getInstance").invoke(null);
                Object tabPlayer = api.getClass().getMethod("getPlayer", UUID.class).invoke(api, player.getUniqueId());
                if (tabPlayer == null) return;
                Object manager = api.getClass().getMethod("getTabListFormatManager").invoke(api);
                String serialized = custom == null ? null : LegacyComponentSerializer.legacySection().serialize(custom);
                for (var method : manager.getClass().getMethods()) {
                    if (!method.getName().equals("setName") || method.getParameterCount() != 2) continue;
                    method.invoke(manager, tabPlayer, serialized);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }, 20L);
    }

    public @Nullable Component getKillMessage(Player killer, Player victim, @Nullable ItemStack weapon, Component defaultWeaponDescription) {
        PlayerData data = data(killer);
        if (data.killMessage == null || !has(killer, Category.KILLMSG, "all")) return null;
        Component item = weapon == null || weapon.getType().isAir() ? defaultWeaponDescription : hoverItemText(weapon);
        return renderKillMessageComponent(normalizeKillMessage(data.killMessage), killer.displayName(), victim.displayName(), item);
    }

    private Component baseDisplayName(Player player) {
        String vaultPrefix = vaultPrefix(player);
        if (vaultPrefix.isBlank()) {
            return Component.text(player.getName());
        }
        return component(vaultPrefix.stripTrailing() + "&r ").append(Component.text(player.getName()));
    }

    private String vaultPrefix(Player player) {
        if (vaultChat == null) return "";
        try {
            String value = vaultChat.getPlayerPrefix(player);
            return value == null ? "" : value;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String normalizeKillMessage(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.toLowerCase(Locale.ROOT).contains("{item}")) return trimmed;
        return trimmed + " &7{item}";
    }

    private Component renderKillMessageComponent(String raw, Component killer, Component victim, Component item) {
        Component result = Component.empty();
        int index = 0;
        while (index < raw.length()) {
            int next = raw.length();
            String token = null;
            for (String candidate : List.of("{killer}", "{victim}", "{item}")) {
                int found = raw.indexOf(candidate, index);
                if (found >= 0 && found < next) {
                    next = found;
                    token = candidate;
                }
            }
            if (token == null) {
                result = result.append(component(raw.substring(index)));
                break;
            }
            if (next > index) result = result.append(component(raw.substring(index, next)));
            result = result.append(switch (token) {
                case "{killer}" -> killer;
                case "{victim}" -> victim;
                case "{item}" -> item;
                default -> Component.empty();
            });
            index = next + token.length();
        }
        return result.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private Component hoverItemText(ItemStack item) {
        Component display;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            display = meta.displayName();
        } else {
            display = Component.text(itemName(item), NamedTextColor.GRAY);
        }
        Component withDisplay = Component.text("with ", NamedTextColor.DARK_GRAY).append(display);
        try {
            return withDisplay.hoverEvent(item.asHoverEvent());
        } catch (Throwable ignored) {
            Component hover = display;
            if (meta != null && meta.lore() != null) {
                for (Component line : meta.lore()) hover = hover.append(Component.newline()).append(line);
            }
            return withDisplay.hoverEvent(HoverEvent.showText(hover));
        }
    }

    private Component defaultWeaponDescription(ItemStack weapon) {
        return weapon == null || weapon.getType().isAir()
            ? Component.text("their fists", NamedTextColor.GRAY)
            : hoverItemText(weapon);
    }

    private ItemStack markCraftedWeapon(@Nullable ItemStack result, Player crafter, @Nullable ItemStack source) {
        if (!weaponLinkEnabled || !weaponLinkCraftedByEnabled || !isLinkableWeapon(result)) return null;
        ItemStack marked = result.clone();
        ItemMeta meta = marked.getItemMeta();
        if (meta == null) return null;
        WeaponCrafter existing = readWeaponCrafter(meta);
        if (existing == null && source != null && source.hasItemMeta()) {
            existing = readWeaponCrafter(source.getItemMeta());
        }
        if (existing == null) {
            writeWeaponCrafter(meta, new WeaponCrafter(crafter.getUniqueId(), crafter.getName()));
        } else {
            writeWeaponCrafter(meta, existing);
        }
        rebuildWeaponLore(meta);
        marked.setItemMeta(meta);
        return marked;
    }

    private void recordWeaponKill(Player killer, Player victim) {
        if (!weaponLinkEnabled || !weaponLinkSlayerEnabled) return;
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (!isLinkableWeapon(weapon)) return;
        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return;
        Map<UUID, String> kills = readWeaponKills(meta);
        kills.put(victim.getUniqueId(), victim.getName());
        writeWeaponKills(meta, kills);
        rebuildWeaponLore(meta);
        weapon.setItemMeta(meta);
    }

    private void linkHeldItem(Player player) {
        if (!weaponLinkEnabled) {
            msg(player, "&cWeapon linking is disabled.");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isLinkableWeapon(item)) {
            msg(player, "&cHold a sword first.");
            return;
        }
        long now = System.currentTimeMillis();
        long wait = linkCooldowns.remainingMillis(player.getUniqueId(), now, linkCooldownMillis);
        if (wait > 0L) {
            msg(player, "&cCooldown: Wait " + formatDuration(wait) + " before linking again.");
            return;
        }
        linkCooldowns.mark(player.getUniqueId(), now);
        ItemMeta meta = item.getItemMeta();
        Map<UUID, String> kills = meta == null ? Map.of() : readWeaponKills(meta);
        Component name = meta != null && meta.hasDisplayName()
            ? meta.displayName()
            : Component.text(itemName(item), NamedTextColor.GRAY);
        Component linked = Component.text("[", NamedTextColor.DARK_GRAY)
            .append(name)
            .append(Component.text("]", NamedTextColor.DARK_GRAY))
            .hoverEvent(HoverEvent.showText(linkHover(item, kills)));
        Component message = Component.text("linked ", NamedTextColor.GRAY)
            .append(linked)
            .append(Component.text(" (" + (weaponLinkSlayerEnabled ? kills.size() : 0) + " slayers)", NamedTextColor.DARK_GRAY));
        sendToCurrentChat(player, message);
    }

    private Component linkHover(ItemStack item, Map<UUID, String> kills) {
        ItemMeta meta = item.getItemMeta();
        Component hover = meta != null && meta.hasDisplayName()
            ? meta.displayName()
            : Component.text(itemName(item), NamedTextColor.GRAY);
        List<String> customLore = meta == null ? List.of() : customLoreLines(meta);
        for (String line : customLore) {
            hover = hover.append(Component.newline()).append(component(line));
        }
        WeaponCrafter crafter = meta == null ? null : readWeaponCrafter(meta);
        if (weaponLinkCraftedByEnabled && weaponLinkCraftedByShowInHover && crafter != null) {
            hover = hover.append(Component.newline()).append(formatWeaponLine(weaponLinkCraftedByLineFormat, crafter.name(), 0));
        }
        if (weaponLinkSlayerEnabled && weaponLinkSlayerShowInHover && !kills.isEmpty()) {
            hover = hover.append(Component.newline()).append(Component.text("Slayers", NamedTextColor.GOLD));
            int shown = 0;
            for (String name : kills.values()) {
                if (shown++ >= weaponLinkSlayerHoverLimit) break;
                hover = hover.append(Component.newline()).append(formatWeaponLine(weaponLinkSlayerLineFormat, name, 0));
            }
            int hidden = kills.size() - Math.min(kills.size(), weaponLinkSlayerHoverLimit);
            if (hidden > 0) {
                hover = hover.append(Component.newline()).append(formatWeaponLine(weaponLinkSlayerMoreFormat, "", hidden));
            }
        }
        return hover.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private void sendToCurrentChat(Player player, Component message) {
        if (Bukkit.getPluginManager().isPluginEnabled("CivChat2")) {
            try {
                Object manager = civChatManager();
                Object group = manager.getClass().getMethod("getGroupChatting", Player.class).invoke(manager, player);
                if (group != null) {
                    for (var method : manager.getClass().getMethods()) {
                        if (!method.getName().equals("sendGroupMsg") || method.getParameterCount() != 3) continue;
                        method.invoke(manager, player, group, message);
                        return;
                    }
                }
                Object format = Class.forName("vg.civcraft.mc.civchat2.ChatStrings").getField("localChatFormat").get(null);
                for (var method : manager.getClass().getMethods()) {
                    if (!method.getName().equals("broadcastMessage") || method.getParameterCount() != 4) continue;
                    method.invoke(manager, player, message, format, new HashSet<>(Bukkit.getOnlinePlayers()));
                    return;
                }
            } catch (Throwable t) {
                getLogger().warning("Falling back to direct local /link message: " + t.getMessage());
            }
        }
        Location origin = player.getLocation();
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (!recipient.getWorld().equals(origin.getWorld())) continue;
            if (weaponLinkFallbackRange > 0 && recipient.getLocation().distance(origin) > weaponLinkFallbackRange) continue;
            recipient.sendMessage(player.displayName().append(Component.text(" ")).append(message));
        }
    }

    private Object civChatManager() throws ReflectiveOperationException {
        Object plugin = Class.forName("vg.civcraft.mc.civchat2.CivChat2").getMethod("getInstance").invoke(null);
        if (plugin == null) throw new IllegalStateException("CivChat2 instance is not available");
        return plugin.getClass().getMethod("getCivChat2Manager").invoke(plugin);
    }

    private boolean isLinkableWeapon(ItemStack item) {
        return item != null && !item.getType().isAir() && weaponLinkMaterials.contains(item.getType());
    }

    private @Nullable WeaponCrafter readWeaponCrafter(ItemMeta meta) {
        String name = meta.getPersistentDataContainer().get(weaponCrafterNameKey, PersistentDataType.STRING);
        if (name == null || name.isBlank()) return null;
        String rawUuid = meta.getPersistentDataContainer().get(weaponCrafterUuidKey, PersistentDataType.STRING);
        UUID uuid = null;
        if (rawUuid != null && !rawUuid.isBlank()) {
            try {
                uuid = UUID.fromString(rawUuid);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new WeaponCrafter(uuid, name);
    }

    private void writeWeaponCrafter(ItemMeta meta, WeaponCrafter crafter) {
        if (crafter.uuid() != null) {
            meta.getPersistentDataContainer().set(weaponCrafterUuidKey, PersistentDataType.STRING, crafter.uuid().toString());
        } else {
            meta.getPersistentDataContainer().remove(weaponCrafterUuidKey);
        }
        meta.getPersistentDataContainer().set(weaponCrafterNameKey, PersistentDataType.STRING, crafter.name());
    }

    private Map<UUID, String> readWeaponKills(ItemMeta meta) {
        String raw = meta.getPersistentDataContainer().get(weaponKillsKey, PersistentDataType.STRING);
        Map<UUID, String> kills = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return kills;
        for (String entry : raw.split("\n")) {
            int split = entry.indexOf('|');
            if (split <= 0 || split >= entry.length() - 1) continue;
            try {
                kills.put(UUID.fromString(entry.substring(0, split)), entry.substring(split + 1));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return kills;
    }

    private void writeWeaponKills(ItemMeta meta, Map<UUID, String> kills) {
        if (kills.isEmpty()) {
            meta.getPersistentDataContainer().remove(weaponKillsKey);
            return;
        }
        List<String> entries = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : kills.entrySet()) {
            entries.add(entry.getKey() + "|" + entry.getValue());
        }
        meta.getPersistentDataContainer().set(weaponKillsKey, PersistentDataType.STRING, String.join("\n", entries));
    }

    /**
     * Re-renders the lore lines CivCosmetics owns (custom lore, crafted-by, slayer-of). Lines added by vanilla or
     * other plugins are preserved: the exact lines this plugin rendered last time are recorded in {@code ownedLoreKey}
     * and only those are replaced.
     */
    private void rebuildWeaponLore(ItemMeta meta) {
        List<Component> foreign = foreignLore(meta);
        List<Component> owned = renderOwnedLore(meta);
        if (!foreign.isEmpty() && !owned.isEmpty()) {
            owned.add(0, Component.empty());
        }
        List<Component> lore = new ArrayList<>(foreign);
        lore.addAll(owned);
        if (owned.isEmpty() && !hasCosmeticLoreData(meta)) {
            meta.getPersistentDataContainer().remove(ownedLoreKey);
        } else {
            // An empty list still marks the item as tracked, so it is never mistaken for a pre-tracking item.
            List<String> keys = new ArrayList<>(owned.size());
            for (Component line : owned) keys.add(loreKey(line));
            meta.getPersistentDataContainer().set(ownedLoreKey, PersistentDataType.LIST.strings(), keys);
        }
        meta.lore(lore.isEmpty() ? null : lore);
    }

    private boolean hasCosmeticLoreData(ItemMeta meta) {
        var pdc = meta.getPersistentDataContainer();
        return pdc.has(customLoreLinesKey, PersistentDataType.STRING)
            || pdc.has(weaponCrafterNameKey, PersistentDataType.STRING)
            || pdc.has(weaponKillsKey, PersistentDataType.STRING);
    }

    private static String loreKey(Component line) {
        return LORE_KEY_SERIALIZER.serialize(line);
    }

    /** Current lore minus the lines CivCosmetics rendered last time. */
    private List<Component> foreignLore(ItemMeta meta) {
        List<Component> current = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        if (current.isEmpty()) return current;
        List<String> owned = meta.getPersistentDataContainer().get(ownedLoreKey, PersistentDataType.LIST.strings());
        if (owned == null) {
            // Items written before ownership tracking had lore rendered entirely by this plugin.
            return hasCosmeticLoreData(meta) ? new ArrayList<>() : current;
        }
        if (owned.isEmpty()) return current;
        List<String> keys = new ArrayList<>(current.size());
        for (Component line : current) keys.add(loreKey(line));
        // Prefer removing our lines as one contiguous block (the normal layout), searching from the end.
        for (int start = keys.size() - owned.size(); start >= 0; start--) {
            if (keys.subList(start, start + owned.size()).equals(owned)) {
                current.subList(start, start + owned.size()).clear();
                return current;
            }
        }
        // Another plugin interleaved lines; remove each of ours once, from the end.
        for (String key : owned) {
            int index = keys.lastIndexOf(key);
            if (index >= 0) {
                keys.remove(index);
                current.remove(index);
            }
        }
        return current;
    }

    private List<Component> renderOwnedLore(ItemMeta meta) {
        List<Component> lore = new ArrayList<>();
        for (String line : customLoreLines(meta)) {
            lore.add(component(line));
        }
        boolean weaponSectionStarted = false;
        WeaponCrafter crafter = readWeaponCrafter(meta);
        if (weaponLinkCraftedByEnabled && weaponLinkCraftedByShowInLore && crafter != null) {
            if (!lore.isEmpty() && !weaponSectionStarted) {
                lore.add(Component.empty());
                weaponSectionStarted = true;
            }
            lore.add(formatWeaponLine(weaponLinkCraftedByLineFormat, crafter.name(), 0));
        }
        Map<UUID, String> kills = readWeaponKills(meta);
        if (weaponLinkSlayerEnabled && weaponLinkSlayerShowInLore && !kills.isEmpty() && weaponLinkSlayerVisibleLines > 0) {
            if (!lore.isEmpty() && !weaponSectionStarted) {
                lore.add(Component.empty());
                weaponSectionStarted = true;
            }
            int shown = 0;
            for (String name : kills.values()) {
                if (shown++ >= weaponLinkSlayerVisibleLines) break;
                lore.add(formatWeaponLine(weaponLinkSlayerLineFormat, name, 0));
            }
            int hidden = kills.size() - Math.min(kills.size(), weaponLinkSlayerVisibleLines);
            if (hidden > 0) {
                lore.add(formatWeaponLine(weaponLinkSlayerMoreFormat, "", hidden));
            }
        }
        return lore;
    }

    private Component formatWeaponLine(String format, String name, int count) {
        return component(format
            .replace("{name}", name)
            .replace("{count}", Integer.toString(count)))
            .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private List<String> customLoreLines(ItemMeta meta) {
        String raw = meta.getPersistentDataContainer().get(customLoreLinesKey, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return List.of();
        return Arrays.asList(raw.split("\n", -1));
    }

    private void playKillEffect(String id, Location loc, Player viewer, boolean preview) {
        switch (id) {
            case "pig" -> {
                Consumer<Pig> setup = spawned -> {
                    prepareEffectEntity(spawned);
                    spawned.customName(component("&d&lASCENDING"));
                };
                Pig pig = loc.getWorld().spawn(loc, Pig.class, setup);
                effectEntities.add(pig);
                killSound(loc, Sound.ENTITY_PIG_DEATH, 2f, 1.2f, preview);
                new BukkitRunnable() {
                    int ticks;
                    @Override public void run() {
                        if (ticks++ >= EFFECT_ENTITY_LIFETIME_TICKS || !pig.isValid()) {
                            if (pig.isValid()) firework(loc, Color.FUCHSIA, preview);
                            effectEntities.remove(pig);
                            pig.remove();
                            cancel();
                            return;
                        }
                        Location next = loc.clone().add(0, ticks * 0.15, 0);
                        pig.teleport(next);
                        particles(next, Particle.HEART, 10, .4, .4, .4, 0);
                        particles(next, Particle.CLOUD, 10, .3, .3, .3, 0);
                    }
                }.runTaskTimer(this, 1L, 1L);
            }
            case "dragon" -> {
                killSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 3f, 1f, preview);
                particles(loc, Particle.DRAGON_BREATH, 200, 1.2, 1.2, 1.2, .1);
                particles(loc, Particle.PORTAL, 150, 1.5, 1.5, 1.5, .5);
                particles(loc, Particle.END_ROD, 80, 1, 2, 1, .05);
            }
            case "thunder" -> loc.getWorld().strikeLightningEffect(loc);
            case "flame" -> {
                killSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 2f, preview);
                particles(loc, Particle.FLAME, 300, 1, 2, 1, .1);
                particles(loc, Particle.LARGE_SMOKE, 80, .5, 1, .5, 0);
                particles(loc, Particle.LAVA, 60, 1, 1, 1, 0);
            }
            case "heartbeat" -> repeating(6, 4, i -> {
                killSound(loc, Sound.ENTITY_PLAYER_HURT, 1f, .5f, preview);
                particles(loc, Particle.HEART, 40, .7, 1.2, .7, 0);
                particles(loc, Particle.DAMAGE_INDICATOR, 30, .5, 1, .5, 0);
            });
            case "warden" -> {
                killSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1f, preview);
                particles(loc, Particle.SONIC_BOOM, 15, .5, 2, .5, 0);
            }
            case "firework" -> firework(loc, Color.RED, preview);
            case "snow" -> {
                killSound(loc, Sound.BLOCK_POWDER_SNOW_BREAK, 1f, 1f, preview);
                repeating(8, 1, i -> {
                    ringDust(loc.clone().add(0, .15, 0), Color.fromRGB(120, 220, 255), 1.0f, .35 + i * .14, 12);
                    particles(loc.clone().add(0, .2 + i * .12, 0), Particle.SNOWFLAKE, 18, .65, .25, .65, .025);
                });
                dust(loc, Color.WHITE, 1.2f, 70, .9, 1.3, .9);
            }
            case "trident" -> {
                killSound(loc, Sound.ENTITY_GENERIC_SPLASH, 2f, 1f, preview);
                particles(loc, Particle.SPLASH, 300, 1, 2, 1, .5);
                particles(loc, Particle.BUBBLE_POP, 150, 1.2, 2.2, 1.2, 0);
            }
            case "crystal" -> {
                killSound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, .5f, preview);
                particles(loc, Particle.WITCH, 300, 1.2, 2, 1.2, .1);
                particles(loc, Particle.ENCHANTED_HIT, 150, 1, 1, 1, .2);
            }
            case "blood" -> {
                killSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, .5f, preview);
                dust(loc, Color.RED, 1.8f, 500, 1, 1.5, 1);
                dust(loc, Color.BLACK, 1.2f, 200, .6, 1.2, .6);
            }
            case "explosion" -> {
                killSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2f, 1f, preview);
                particles(loc, Particle.EXPLOSION_EMITTER, 5, 0, 0, 0, 0);
                particles(loc, Particle.LARGE_SMOKE, 200, 1.2, 1.2, 1.2, .1);
            }
            case "void" -> {
                killSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, .5f, preview);
                particles(loc, Particle.REVERSE_PORTAL, 350, 1.2, 2.5, 1.2, .1);
                particles(loc, Particle.SQUID_INK, 150, 1, 1.5, 1, 0);
            }
            case "wealth" -> {
                killSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, .5f, preview);
                particles(loc, Particle.TOTEM_OF_UNDYING, 250, 1.2, 2.5, 1.2, .3);
                particles(loc, Particle.HAPPY_VILLAGER, 150, 1.2, 1.8, 1.2, 0);
            }
            case "squid" -> {
                Consumer<Squid> setup = this::prepareEffectEntity;
                Squid squid = loc.getWorld().spawn(loc, Squid.class, setup);
                effectEntities.add(squid);
                killSound(loc, Sound.ENTITY_SQUID_SQUIRT, 2f, .8f, preview);
                new BukkitRunnable() {
                    int ticks;
                    @Override public void run() {
                        if (ticks++ >= EFFECT_ENTITY_LIFETIME_TICKS || !squid.isValid()) {
                            if (squid.isValid()) firework(loc, Color.BLUE, preview);
                            effectEntities.remove(squid);
                            squid.remove();
                            cancel();
                            return;
                        }
                        Location next = loc.clone().add(0, ticks * 0.18, 0);
                        squid.teleport(next);
                        particles(next, Particle.SQUID_INK, 20, .4, .4, .4, 0);
                        particles(next, Particle.BUBBLE, 10, .3, .3, .3, 0);
                    }
                }.runTaskTimer(this, 1L, 1L);
            }
            case "tornado" -> {
                killSound(loc, Sound.ENTITY_PHANTOM_FLAP, 2f, 1f, preview);
                repeating(16, 1, i -> {
                    double y = .15 + i * .15;
                    double radius = .25 + i * .045;
                    for (int point = 0; point < 3; point++) {
                        double angle = i * .75 + point * (Math.PI * 2 / 3);
                        Location next = loc.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                        particles(next, Particle.CLOUD, 5, .05, .05, .05, .04);
                        particles(next, Particle.REVERSE_PORTAL, 2, .03, .03, .03, .02);
                    }
                    if (i % 4 == 0) particles(loc.clone().add(0, y, 0), Particle.POOF, 18, .35, .1, .35, 0);
                });
            }
            case "lava" -> {
                killSound(loc, Sound.BLOCK_LAVA_POP, 2f, 1f, preview);
                particles(loc, Particle.LAVA, 300, 1, 1.2, 1, 0);
                particles(loc, Particle.DRIPPING_LAVA, 150, .8, 2.5, .8, 0);
            }
            case "music" -> {
                killSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 2f, 1f, preview);
                particles(loc, Particle.NOTE, 250, 1.2, 2.5, 1.2, 1);
            }
            case "cloud" -> {
                killSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f, preview);
                Location above = loc.clone().add(0, 2.3, 0);
                particles(above, Particle.END_ROD, 90, .45, .2, .45, .08);
                particles(loc, Particle.TOTEM_OF_UNDYING, 120, .9, 1.4, .9, .18);
                particles(loc, Particle.FIREWORK, 80, .7, 1.0, .7, .12);
                ringDust(loc.clone().add(0, .15, 0), Color.fromRGB(255, 245, 190), 1.1f, 1.0, 24);
            }
            case "slime" -> {
                killSound(loc, Sound.ENTITY_SLIME_DEATH, 2f, 1f, preview);
                dust(loc, Color.LIME, 1.8f, 400, 1, 1.5, 1);
                dust(loc, Color.GREEN, 1.4f, 150, .8, 1.2, .8);
            }
            case "cookies" -> {
                killSound(loc, Sound.ENTITY_PLAYER_BURP, 1f, 1f, preview);
                dust(loc, Color.fromRGB(101, 67, 33), 1.5f, 350, 1, 1.8, 1);
                dust(loc, Color.fromRGB(210, 180, 140), 1.2f, 150, 1, 1.8, 1);
            }
            case "soul_rift" -> {
                killSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, .65f, preview);
                particles(loc, Particle.SOUL_FIRE_FLAME, 80, .7, 1.2, .7, .03);
                particles(loc, Particle.SOUL, 65, .8, 1.6, .8, .04);
                particles(loc, Particle.REVERSE_PORTAL, 110, .85, 1.5, .85, .08);
                ringDust(loc.clone().add(0, .15, 0), Color.fromRGB(40, 180, 190), 1.0f, .85, 18);
            }
            case "meteor" -> {
                killSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1f, .75f, preview);
                repeating(8, 1, i -> {
                    Location next = loc.clone().add(.9 - i * .11, 3.0 - i * .35, .9 - i * .11);
                    particles(next, Particle.FLAME, 14, .12, .12, .12, .04);
                    particles(next, Particle.LARGE_SMOKE, 8, .1, .1, .1, .02);
                });
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    killSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.2f, preview);
                    particles(loc, Particle.EXPLOSION_EMITTER, 2, 0, 0, 0, 0);
                    particles(loc, Particle.LAVA, 55, .75, .7, .75, 0);
                    particles(loc, Particle.LARGE_SMOKE, 70, .8, .8, .8, .06);
                }, 8L);
            }
            case "shatter" -> {
                killSound(loc, Sound.BLOCK_GLASS_BREAK, 1.1f, .8f, preview);
                killSound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, .8f, 1.5f, preview);
                particles(loc, Particle.ENCHANTED_HIT, 130, .95, 1.3, .95, .16);
                dust(loc, Color.fromRGB(170, 235, 255), 1.1f, 110, .9, 1.2, .9);
                dust(loc, Color.WHITE, .8f, 70, .8, 1.4, .8);
            }
            case "founder" -> {
                killSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, .7f, 1.2f, preview);
                ringDust(loc.clone().add(0, .15, 0), Color.fromRGB(255, 176, 0), 1.6f, 1.2, 32);
                ringDust(loc.clone().add(0, 2.1, 0), Color.fromRGB(255, 215, 90), 1.2f, .55, 16);
                particles(loc.clone().add(0, 1.2, 0), Particle.END_ROD, 40, .5, .8, .5, .05);
                particles(loc.clone().add(0, 1.6, 0), Particle.TOTEM_OF_UNDYING, 90, .6, .6, .6, .25);
                firework(loc, Color.fromRGB(255, 176, 0), preview);
            }
            case "crown" -> {
                killSound(loc, Sound.BLOCK_BELL_USE, .8f, 1.4f, preview);
                killSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.8f, preview);
                ringDust(loc.clone().add(0, .15, 0), Color.fromRGB(255, 190, 35), 1.4f, 1.05, 28);
                particles(loc.clone().add(0, 1.4, 0), Particle.TOTEM_OF_UNDYING, 120, .75, .7, .75, .2);
                particles(loc.clone().add(0, 2.0, 0), Particle.FIREWORK, 75, .55, .35, .55, .12);
            }
            case "glitch" -> {
                repeating(6, 2, i -> {
                    killSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, .45f, .55f + i * .08f, preview);
                    Location next = loc.clone().add((i % 2 == 0 ? .35 : -.35), .15 + i * .18, (i % 3 - 1) * .25);
                    particles(next, Particle.REVERSE_PORTAL, 35, .25, .45, .25, .08);
                    particles(next, Particle.PORTAL, 18, .2, .35, .2, .12);
                });
            }
            case "execution" -> {
                killSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, .8f, preview);
                killSound(loc, Sound.BLOCK_ANVIL_LAND, .45f, 1.6f, preview);
                particles(loc.clone().add(0, 1.0, 0), Particle.SWEEP_ATTACK, 8, .7, .3, .7, 0);
                particles(loc, Particle.ENCHANTED_HIT, 110, .85, 1.1, .85, .14);
                dust(loc, Color.fromRGB(120, 0, 0), 1.4f, 90, .8, 1.1, .8);
            }
        }
    }

    /** Configures a kill-effect mob before it is added to the world so it can never be farmed or left behind. */
    private void prepareEffectEntity(LivingEntity entity) {
        entity.getPersistentDataContainer().set(effectEntityKey, PersistentDataType.BYTE, (byte) 1);
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setGravity(false);
        entity.setAI(false);
        entity.setCollidable(false);
        entity.setCanPickupItems(false);
        entity.setRemoveWhenFarAway(true);
        if (entity instanceof Lootable lootable) {
            lootable.setLootTable(null);
        }
    }

    private boolean isEffectEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(effectEntityKey, PersistentDataType.BYTE);
    }

    private void removeEffectEntities() {
        for (Entity entity : new ArrayList<>(effectEntities)) {
            entity.remove();
        }
        effectEntities.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEffectEntityDamage(EntityDamageEvent event) {
        if (isEffectEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEffectEntityDeath(EntityDeathEvent event) {
        if (!isEffectEntity(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @EventHandler
    public void onEffectEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if ((entity instanceof Pig || entity instanceof Squid) && isEffectEntity(entity)) {
                effectEntities.remove(entity);
                entity.remove();
            }
        }
    }

    @EventHandler
    public void onEffectEntitiesLoad(EntitiesLoadEvent event) {
        // Safety net for mobs left over from older versions or a crash mid-effect.
        for (Entity entity : event.getEntities()) {
            if ((entity instanceof Pig || entity instanceof Squid) && isEffectEntity(entity)) {
                entity.remove();
            }
        }
    }

    /** Per-player cooldown shared by kill-effect and trail previews. Returns false (and tells the player) if cooling down. */
    private boolean tryPreview(Player player) {
        if (effectPreviewCooldownMillis <= 0) return true;
        long now = System.currentTimeMillis();
        Long last = lastEffectPreview.get(player.getUniqueId());
        if (last != null && now - last < effectPreviewCooldownMillis) {
            long remaining = Math.max(1, (effectPreviewCooldownMillis - (now - last) + 999) / 1000);
            msg(player, "&cPlease wait " + remaining + "s before previewing again.");
            return false;
        }
        lastEffectPreview.put(player.getUniqueId(), now);
        return true;
    }

    private void previewTrail(Player player, String trail) {
        Location base = player.getLocation().add(player.getLocation().getDirection().multiply(1.5)).add(0, 1.2, 0);
        new BukkitRunnable() {
            int ticks;
            @Override public void run() {
                if (ticks++ > 35) {
                    cancel();
                    return;
                }
                drawTrail(trail, base.clone().add(player.getLocation().getDirection().multiply(ticks * .18)));
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void drawTrail(String trail, Location loc) {
        switch (trail) {
            case "hearts" -> particles(loc, Particle.HEART, 1, .1, .1, .1, 0);
            case "flame" -> particles(loc, Particle.FLAME, 2, .05, .05, .05, .01);
            case "magic" -> particles(loc, Particle.WITCH, 2, .1, .1, .1, 0);
            case "music" -> particles(loc, Particle.NOTE, 1, .2, .2, .2, 1);
            case "smoke" -> particles(loc, Particle.LARGE_SMOKE, 2, .1, .1, .1, .01);
            case "crit" -> particles(loc, Particle.ENCHANTED_HIT, 2, .1, .1, .1, .1);
            case "slime" -> loc.getWorld().spawnParticle(Particle.ITEM, loc, 2, .1, .1, .1, 0, new ItemStack(Material.SLIME_BALL));
            case "soul" -> {
                particles(loc, Particle.SOUL_FIRE_FLAME, 1, .05, .05, .05, .01);
                particles(loc, Particle.SOUL, 1, .08, .08, .08, .01);
            }
            case "ender" -> {
                particles(loc, Particle.PORTAL, 2, .08, .08, .08, .05);
                particles(loc, Particle.REVERSE_PORTAL, 1, .06, .06, .06, .02);
            }
            case "gold" -> dust(loc, Color.fromRGB(255, 190, 35), .8f, 2, .08, .08, .08);
            case "founder" -> {
                dust(loc, Color.fromRGB(255, 176, 0), .9f, 2, .06, .06, .06);
                particles(loc, Particle.END_ROD, 1, .03, .03, .03, 0);
            }
            case "frost" -> {
                particles(loc, Particle.SNOWFLAKE, 1, .08, .08, .08, .01);
                dust(loc, Color.fromRGB(120, 220, 255), .7f, 1, .05, .05, .05);
            }
            case "blood" -> dust(loc, Color.fromRGB(150, 0, 0), .8f, 2, .08, .08, .08);
            case "totem" -> particles(loc, Particle.TOTEM_OF_UNDYING, 2, .08, .08, .08, .04);
            case "bubble" -> {
                particles(loc, Particle.BUBBLE, 1, .07, .07, .07, 0);
                particles(loc, Particle.BUBBLE_POP, 1, .08, .08, .08, 0);
            }
        }
    }

    private void firework(Location loc, Color color, boolean preview) {
        loc.getWorld().spawnParticle(Particle.FIREWORK, loc, 80, .5, .5, .5, .2);
        killSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f, preview);
    }

    private void killSound(Location loc, Sound sound, float volumeMultiplier, float pitch, boolean preview) {
        if (!killEffectSoundsEnabled) return;
        float baseVolume = preview ? killEffectPreviewSoundVolume : killEffectSoundVolume;
        if (baseVolume <= 0) return;
        loc.getWorld().playSound(loc, sound, Math.max(0f, baseVolume * volumeMultiplier), pitch);
    }

    private void particles(Location loc, Particle particle, int count, double ox, double oy, double oz, double extra) {
        loc.getWorld().spawnParticle(particle, loc, count, ox, oy, oz, extra);
    }

    private void dust(Location loc, Color color, float size, int count, double ox, double oy, double oz) {
        loc.getWorld().spawnParticle(Particle.DUST, loc, count, ox, oy, oz, .1, new Particle.DustOptions(color, size));
    }

    private void ringDust(Location loc, Color color, float size, double radius, int points) {
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            Location point = loc.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
        }
    }

    private void repeating(int times, int period, Consumer<Integer> consumer) {
        new BukkitRunnable() {
            int i;
            @Override public void run() {
                if (i >= times) {
                    cancel();
                    return;
                }
                consumer.accept(++i);
            }
        }.runTaskTimer(this, 0L, period);
    }

    private boolean has(Player player, Category category, String id) {
        PlayerData data = data(player);
        String normalized = id.toLowerCase(Locale.ROOT);
        if (player.hasPermission("civ.cosmetic.admin")) return true;
        NamedCosmetic cosmetic = find(categoryCosmetics(category), normalized);
        boolean explicit = player.hasPermission("civ.cosmetic." + category.id + "." + normalized)
            || (category == Category.KILL_EFFECTS && player.hasPermission("killeffect." + normalized))
            || (category == Category.TRAILS && player.hasPermission("civ.cosmetic.trail." + normalized))
            || data.hasGrant(category, normalized);
        if (cosmetic != null && cosmetic.founder()) {
            // Founder cosmetics are never part of a category-wide or rarity unlock.
            return explicit || player.hasPermission("civ.cosmetic.founder");
        }
        return explicit
            || player.hasPermission("civ.cosmetic." + category.id)
            || player.hasPermission("civ.cosmetic." + category.id + ".all")
            || data.hasGrant(category, "all")
            || (cosmetic != null && player.hasPermission(cosmetic.rarity().permission()));
    }

    /** Item lore is open to anyone with lore access or a lore credit to spend. */
    private boolean canUseLore(Player player) {
        return player.isOp() || has(player, Category.LORE, "item") || data(player).loreCredits > 0;
    }

    private PlayerData data(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = players.get(uuid);
        if (data != null) return data;
        loadAsync(uuid, loaded -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                validateSelections(online);
                applyPrefix(online);
                enforceWornTrims(online);
            } else {
                players.remove(uuid);
            }
        });
        return new PlayerData(uuid);
    }

    private PlayerData load(UUID uuid) {
        PlayerData existing = players.get(uuid);
        if (existing != null) return existing;
        if (Bukkit.isPrimaryThread()) {
            loadAsync(uuid, null);
            return new PlayerData(uuid);
        }
        return storage.load(uuid);
    }

    private void save(PlayerData data) {
        queueSave(data, false);
    }

    private void saveWithGrants(PlayerData data) {
        queueSave(data, true);
    }

    private void queueSave(PlayerData data, boolean includeGrants) {
        if (!storageHealthy || storage == null) return;
        if (players.get(data.uuid) != data) {
            // data(player) hands out a blank placeholder while a load is pending or failed; saving it would
            // overwrite the player's real row with defaults.
            return;
        }
        PlayerData snapshot = data.copy();
        if (shuttingDown || storageExecutor == null || storageExecutor.isShutdown()) {
            writeSnapshot(snapshot, includeGrants, false);
            return;
        }
        storageExecutor.execute(() -> writeSnapshot(snapshot, includeGrants, true));
    }

    /**
     * Writes one snapshot (storage thread, or the main thread during shutdown). On failure the snapshot is kept in
     * {@link #failedSaves} and retried periodically, so a transient DB error does not lose credits or grants.
     */
    private void writeSnapshot(PlayerData snapshot, boolean includeGrants, boolean retry) {
        PendingSave pending = failedSaves.get(snapshot.uuid);
        boolean grants = includeGrants || (pending != null && pending.includeGrants());
        Supplier<Void> write = () -> {
            if (grants) storage.save(snapshot);
            else storage.saveState(snapshot);
            return null;
        };
        try {
            if (retry) withRetry(write);
            else write.get();
            if (pending != null) failedSaves.remove(snapshot.uuid, pending);
        } catch (RuntimeException e) {
            failedSaves.put(snapshot.uuid, new PendingSave(snapshot, grants));
            logStorageFailure("save cosmetics", snapshot.uuid, e);
        }
    }

    /** Main thread, periodic: re-attempts saves that failed, using the freshest in-memory data. */
    private void retryFailedSaves() {
        if (failedSaves.isEmpty() || shuttingDown || storageExecutor == null || storageExecutor.isShutdown()) return;
        if (!failedSaveRetryInFlight.compareAndSet(false, true)) return;
        List<PlayerData> snapshots = new ArrayList<>();
        for (PendingSave pending : failedSaves.values()) {
            PlayerData live = players.get(pending.snapshot().uuid);
            snapshots.add(live != null ? live.copy() : pending.snapshot());
        }
        storageExecutor.execute(() -> {
            try {
                for (PlayerData snapshot : snapshots) {
                    writeSnapshot(snapshot, false, false);
                }
                if (failedSaves.isEmpty()) getLogger().info("CivCosmetics storage recovered; queued saves written.");
            } finally {
                failedSaveRetryInFlight.set(false);
            }
        });
    }

    /** Runs a storage operation, retrying transient failures with a short backoff (not while shutting down). */
    private <T> T withRetry(Supplier<T> operation) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= STORAGE_RETRY_DELAYS_MS.length; attempt++) {
            try {
                return operation.get();
            } catch (RuntimeException e) {
                last = e;
                if (attempt == STORAGE_RETRY_DELAYS_MS.length || shuttingDown) break;
                try {
                    Thread.sleep(STORAGE_RETRY_DELAYS_MS[attempt]);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw last;
    }

    /**
     * Main thread, periodic. One indexed query per poll for all loaded online players; only players whose version
     * changed (a purchase, refund, redemption or admin grant on any server) are reloaded.
     */
    private void pollEntitlements() {
        if (shuttingDown || !storageHealthy || storage == null || storageExecutor == null || storageExecutor.isShutdown()) return;
        Map<UUID, Long> known = new HashMap<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            PlayerData data = players.get(online.getUniqueId());
            if (data != null) known.put(online.getUniqueId(), data.entitlementsVersion);
        }
        if (known.isEmpty() || !entitlementPollInFlight.compareAndSet(false, true)) return;
        storageExecutor.execute(() -> {
            try {
                Map<UUID, Store.Entitlements> changed = new HashMap<>();
                for (Map.Entry<UUID, Long> stored : storage.entitlementVersions(known.keySet()).entrySet()) {
                    if (stored.getValue().longValue() != known.get(stored.getKey()).longValue()) {
                        changed.put(stored.getKey(), storage.loadEntitlements(stored.getKey()));
                    }
                }
                if (!changed.isEmpty() && isEnabled()) {
                    Bukkit.getScheduler().runTask(this, () -> changed.forEach(this::applyEntitlements));
                }
            } catch (RuntimeException e) {
                logStorageFailure("refresh store entitlements", null, e);
            } finally {
                entitlementPollInFlight.set(false);
            }
        });
    }

    private void refreshEntitlements(UUID uuid, @Nullable Runnable then) {
        if (!players.containsKey(uuid)) {
            if (then != null) then.run();
            return;
        }
        runStorage("refresh store entitlements", uuid, () -> storage.loadEntitlements(uuid), entitlements -> {
            applyEntitlements(uuid, entitlements);
            if (then != null) then.run();
        }, then);
    }

    private void applyEntitlements(UUID uuid, Store.Entitlements entitlements) {
        PlayerData data = players.get(uuid);
        if (data == null || entitlements.version() < data.entitlementsVersion) return;
        data.applyEntitlements(entitlements);
        // Move the storage-side grant baseline only after the cached copy changed. The executor runs tasks in order, so
        // saves copied before this point still diff against the old baseline and can't delete or revive grants.
        Map<Category, Set<String>> baseline = new EnumMap<>(Category.class);
        for (Category category : Category.values()) baseline.put(category, new HashSet<>(data.grants.get(category)));
        if (storageExecutor != null && !storageExecutor.isShutdown()) {
            storageExecutor.execute(() -> storage.setGrantBaseline(uuid, baseline));
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            validateSelections(online);
            applyPrefix(online);
        }
    }

    /**
     * Runs a storage call off the main thread with retries, then hands the result back on the main thread.
     * Callbacks are skipped once the plugin is disabled.
     */
    private <T> void runStorage(String action, @Nullable UUID uuid, Supplier<T> work,
                                @Nullable Consumer<T> onSuccess, @Nullable Runnable onFailure) {
        if (!storageHealthy || storage == null || storageExecutor == null || storageExecutor.isShutdown()) {
            if (onFailure != null) onFailure.run();
            return;
        }
        storageExecutor.execute(() -> {
            try {
                T result = withRetry(work);
                if (onSuccess != null && isEnabled()) Bukkit.getScheduler().runTask(this, () -> onSuccess.accept(result));
            } catch (RuntimeException e) {
                logStorageFailure(action, uuid, e);
                if (onFailure != null && isEnabled()) Bukkit.getScheduler().runTask(this, onFailure);
            }
        });
    }

    /**
     * Staff credit grants go through the purchase ledger under a generated id, so they are retried durably if the
     * database is down and can never apply twice. The cached balance updates once the write lands.
     */
    private void adjustLoreCredits(PlayerData data, int delta) {
        if (delta <= 0) return;
        String id = "staff-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        submitStoreOp(Bukkit.getConsoleSender(), new StoreOp(Store.KIND_LORE, data.uuid, null, delta, id, "staff"));
    }

    private boolean isLoaded(Player player) {
        return players.containsKey(player.getUniqueId());
    }

    private boolean requireLoaded(Player player) {
        if (isLoaded(player)) return true;
        loadAsync(player.getUniqueId(), null);
        msg(player, "&eYour cosmetics are still loading. Try again in a moment.");
        return false;
    }

    private boolean requireLoaded(CommandSender sender, UUID uuid, Runnable continuation) {
        if (players.containsKey(uuid)) return true;
        loadAsync(uuid, ignored -> continuation.run());
        msg(sender, "&eLoading cosmetics data. Run again in a moment if needed.");
        return false;
    }

    private void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        loadAsync(uuid, data -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null) {
                players.remove(uuid);
                return;
            }
            validateSelections(online);
            applyPrefix(online);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Player delayed = Bukkit.getPlayer(uuid);
                if (delayed != null) enforceWornTrims(delayed);
            }, 2L);
        });
    }

    private void loadAsync(UUID uuid, @Nullable Consumer<PlayerData> callback) {
        if (!Bukkit.isPrimaryThread()) {
            if (isEnabled()) Bukkit.getScheduler().runTask(this, () -> loadAsync(uuid, callback));
            return;
        }
        PlayerData existing = players.get(uuid);
        if (existing != null) {
            if (callback != null) callback.accept(existing);
            return;
        }
        if (!storageHealthy || storage == null || storageExecutor == null || storageExecutor.isShutdown()) {
            return;
        }
        if (callback != null) {
            loadCallbacks.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(callback);
        }
        if (!loadingPlayers.add(uuid)) return;
        storageExecutor.execute(() -> {
            try {
                PlayerData loaded = withRetry(() -> storage.load(uuid));
                Bukkit.getScheduler().runTask(this, () -> {
                    loadingPlayers.remove(uuid);
                    players.put(uuid, loaded);
                    List<Consumer<PlayerData>> callbacks = loadCallbacks.remove(uuid);
                    if (callbacks != null) {
                        for (Consumer<PlayerData> next : callbacks) next.accept(loaded);
                    }
                });
            } catch (RuntimeException e) {
                logStorageFailure("load cosmetics", uuid, e);
                if (shuttingDown) return;
                Bukkit.getScheduler().runTask(this, () -> {
                    loadingPlayers.remove(uuid);
                    loadCallbacks.remove(uuid);
                });
                // Keep trying for online players instead of disabling the plugin; they run with defaults meanwhile.
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null && !players.containsKey(uuid)) loadPlayer(online);
                }, LOAD_RETRY_TICKS);
            }
        });
    }

    private void logStorageFailure(String action, @Nullable UUID uuid, RuntimeException e) {
        getLogger().warning("Failed to " + action + (uuid == null ? "" : " for " + uuid) + ": " + e.getMessage()
            + " (will retry; plugin stays enabled)");
    }

    private Inventory gui(Menu menu, int size, String title, @Nullable Object data) {
        GuiHolder holder = new GuiHolder(menu, data == null ? "" : data.toString());
        Inventory inventory = Bukkit.createInventory(holder, size, color(title));
        holder.inventory = inventory;
        return inventory;
    }

    private List<Integer> centeredRowSlots(int itemCount, List<Integer> rowSlots) {
        int shown = Math.min(Math.max(0, itemCount), rowSlots.size());
        int start = Math.max(0, (rowSlots.size() - shown) / 2);
        return rowSlots.subList(start, start + shown);
    }

    private void fill(Inventory inv, Material material) {
        ItemStack pane = item(material, "&7");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(component(name));
        if (lore.length > 0) {
            List<Component> components = new ArrayList<>();
            for (String line : lore) components.add(component(line));
            meta.lore(components);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack headItem(UUID uuid, String name, String... lore) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        meta.displayName(component(name));
        if (lore.length > 0) {
            List<Component> components = new ArrayList<>();
            for (String line : lore) components.add(component(line));
            meta.lore(components);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack exampleLorePreset() {
        ItemStack stack = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(component("&b&lBlade"));
        meta.lore(List.of(
            component("&7A cosmetic lore preset."),
            component("&7Forged for event champions."),
            Component.empty(),
            component("&eClick to apply to held item.")));
        stack.setItemMeta(meta);
        return stack;
    }

    private void applyExampleLorePreset(Player player) {
        setLore(player, "&b&lBlade|&7Forged for event champions.|&7A cosmetic blade from CIVPVP.");
    }

    private boolean isArmor(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return item.getItemMeta() instanceof ArmorMeta;
    }

    private boolean removeTrim(ItemStack item) {
        if (!isArmor(item)) return false;
        ArmorMeta meta = (ArmorMeta) item.getItemMeta();
        if (meta.getTrim() == null) return false;
        meta.setTrim(null);
        item.setItemMeta(meta);
        return true;
    }

    private void applyTrim(ItemStack item, String patternId, String materialId) {
        if (!isArmor(item)) return;
        TrimPattern pattern = Registry.TRIM_PATTERN.get(org.bukkit.NamespacedKey.minecraft(patternId.toLowerCase(Locale.ROOT)));
        TrimMaterial material = Registry.TRIM_MATERIAL.get(org.bukkit.NamespacedKey.minecraft(materialId.toLowerCase(Locale.ROOT)));
        if (pattern == null || material == null) return;
        ArmorMeta meta = (ArmorMeta) item.getItemMeta();
        meta.setTrim(new ArmorTrim(material, pattern));
        item.setItemMeta(meta);
    }

    private Material trimMaterialIcon(String material) {
        return switch (material) {
            case "redstone" -> Material.REDSTONE;
            case "lapis" -> Material.LAPIS_LAZULI;
            case "amethyst" -> Material.AMETHYST_SHARD;
            case "emerald" -> Material.EMERALD;
            case "diamond" -> Material.DIAMOND;
            case "gold" -> Material.GOLD_INGOT;
            case "iron" -> Material.IRON_INGOT;
            case "copper" -> Material.COPPER_INGOT;
            case "netherite" -> Material.NETHERITE_INGOT;
            case "quartz" -> Material.QUARTZ;
            default -> Material.PAPER;
        };
    }

    private void clearPrefix(Player player) {
        PlayerData data = data(player);
        data.selectedTag = null;
        data.customPrefix = null;
        save(data);
        applyPrefix(player);
    }

    private String currentPrefixText(PlayerData data) {
        if (data.selectedTag != null) {
            NamedCosmetic tag = find(tags, data.selectedTag);
            if (tag != null) return tag.display;
        }
        if (data.customPrefix != null) return data.customPrefix;
        return "&7Rank Default";
    }

    private String premadeTagDisplay(NamedCosmetic tag) {
        return tag.display.stripTrailing() + " ";
    }

    private String currentKillMessageText(PlayerData data) {
        if (data.killMessage == null || data.killMessage.isBlank()) return "&7Default";
        for (KillMessagePreset preset : killMessagePresets) {
            if (normalizeKillMessage(preset.message).equals(normalizeKillMessage(data.killMessage))) {
                return preset.display;
            }
        }
        return "&bCustom";
    }

    private String heldItemName(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) return "Nothing";
        ItemMeta meta = held.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return LegacyComponentSerializer.legacySection().serialize(meta.displayName());
        }
        return itemName(held);
    }

    private NamedCosmetic find(List<NamedCosmetic> cosmetics, String id) {
        for (NamedCosmetic cosmetic : cosmetics) if (cosmetic.id.equals(id)) return cosmetic;
        return null;
    }

    private GrantPreset findPreset(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        for (GrantPreset preset : grantPresets) if (preset.id.equals(normalized)) return preset;
        return null;
    }

    private String presetIds() {
        if (grantPresets.isEmpty()) return "none";
        List<String> ids = new ArrayList<>();
        for (GrantPreset preset : grantPresets) ids.add(preset.id);
        return String.join(", ", ids);
    }

    private void applyPreset(PlayerData data, GrantPreset preset) {
        for (Map.Entry<Category, Set<String>> entry : preset.grants.entrySet()) {
            for (String id : entry.getValue()) {
                data.grant(entry.getKey(), id);
                if (entry.getKey() == Category.TAGS && id.equals("all")) data.grant(Category.TITLE, "all");
            }
        }
        adjustLoreCredits(data, preset.loreCredits);
    }

    private void grantAll(PlayerData data) {
        for (Category category : adminCategories()) {
            data.grant(category, categoryDefaultId(category));
        }
        data.grant(Category.TITLE, "all");
    }

    private void clearAllGrants(PlayerData data) {
        for (Set<String> grants : data.grants.values()) grants.clear();
    }

    private List<Category> adminCategories() {
        return List.of(Category.KILL_EFFECTS, Category.TRAILS, Category.TAGS, Category.TRIM, Category.LORE, Category.KILLMSG);
    }

    private boolean categoryAllEnabled(PlayerData data, Category category) {
        if (category == Category.TAGS) {
            return data.hasGrant(Category.TAGS, "all") && data.hasGrant(Category.TITLE, "all");
        }
        return data.hasGrant(category, "all");
    }

    private void clearCategoryGrants(PlayerData data, Category category) {
        data.grants.get(category).clear();
        if (category == Category.TAGS) {
            data.grants.get(Category.TITLE).clear();
        }
    }

    private void toggleCategoryAll(PlayerData data, Category category) {
        if (category != Category.TAGS) {
            toggleAll(data, category);
            return;
        }
        if (categoryAllEnabled(data, category)) {
            data.grants.get(Category.TAGS).clear();
            data.grants.get(Category.TITLE).clear();
        } else {
            data.grants.get(Category.TAGS).clear();
            data.grants.get(Category.TAGS).add("all");
            data.grants.get(Category.TITLE).clear();
            data.grants.get(Category.TITLE).add("all");
        }
    }

    private void toggleAll(PlayerData data, Category category) {
        Set<String> grants = data.grants.get(category);
        if (grants.contains("all")) {
            grants.clear();
        } else {
            grants.clear();
            grants.add("all");
        }
    }

    private int grantCount(PlayerData data) {
        int count = 0;
        for (Set<String> grants : data.grants.values()) count += grants.size();
        return count;
    }

    private String playerName(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() == null ? uuid.toString().substring(0, 8) : player.getName();
    }

    private List<NamedCosmetic> categoryCosmetics(Category category) {
        return switch (category) {
            case KILL_EFFECTS -> killEffects;
            case TRAILS -> trails;
            case TAGS -> tags;
            default -> List.of();
        };
    }

    private Material categoryIcon(Category category) {
        return switch (category) {
            case KILL_EFFECTS -> Material.TNT;
            case TRAILS -> Material.ARROW;
            case TITLE -> Material.NAME_TAG;
            case TAGS -> Material.NAME_TAG;
            case TRIM -> Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE;
            case LORE -> Material.DIAMOND_SWORD;
            case KILLMSG -> Material.PAPER;
        };
    }

    private String categoryLabel(Category category) {
        return switch (category) {
            case KILL_EFFECTS -> "Kill Effects";
            case TRAILS -> "Projectile Trails";
            case TITLE -> "Custom Prefixes";
            case TAGS -> "Prefixes";
            case TRIM -> "Armor Trims";
            case LORE -> "Item Lore";
            case KILLMSG -> "Kill Messages";
        };
    }

    private String categoryDefaultId(Category category) {
        return category == Category.LORE ? "item" : "all";
    }

    private ItemStack categoryItem(PlayerData data, Category category) {
        if (category == Category.TAGS) {
            Set<String> tagGrants = data.grants.get(Category.TAGS);
            Set<String> titleGrants = data.grants.get(Category.TITLE);
            boolean tagsAll = tagGrants.contains("all");
            boolean titleAll = titleGrants.contains("all");
            int tagIndividual = Math.max(0, tagGrants.size() - (tagsAll ? 1 : 0));
            int titleIndividual = Math.max(0, titleGrants.size() - (titleAll ? 1 : 0));
            boolean full = tagsAll && titleAll;
            boolean partial = tagsAll || titleAll || tagIndividual > 0 || titleIndividual > 0;
            String state = full ? "&aEnabled" : partial ? "&ePartial" : "&cDisabled";
            Material icon = full ? Material.EMERALD_BLOCK : partial ? Material.YELLOW_DYE : Material.NAME_TAG;
            return item(icon,
                state + " &fPrefixes",
                "&7Premade tag grants: &f" + (tagsAll ? "all" : tagIndividual),
                "&7Custom prefixes: " + (titleAll ? "&aEnabled" : "&cDisabled"),
                "",
                "&eLeft-click to edit.",
                "&6Right-click to toggle all.",
                "&cShift-right-click to clear.");
        }
        Set<String> grants = data.grants.get(category);
        boolean all = grants.contains("all");
        int individual = Math.max(0, grants.size() - (all ? 1 : 0));
        String state = all ? "&aEnabled" : individual > 0 ? "&ePartial" : "&cDisabled";
        Material icon = all ? Material.EMERALD_BLOCK : individual > 0 ? Material.YELLOW_DYE : categoryIcon(category);
        return item(icon,
            state + " &f" + categoryLabel(category),
            "&7Category: &f" + category.id,
            "&7Individual grants: &f" + individual,
            "",
            "&eLeft-click to edit.",
            "&6Right-click to toggle all.",
            "&cShift-right-click to clear.");
    }

    private Material material(String raw) {
        if (raw == null) return Material.PAPER;
        Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        return material == null ? Material.PAPER : material;
    }

    private Set<Material> parseMaterials(List<String> raw, Set<Material> fallback) {
        if (raw == null || raw.isEmpty()) return new HashSet<>(fallback);
        Set<Material> materials = new HashSet<>();
        for (String entry : raw) {
            Material material = Material.matchMaterial(entry.toUpperCase(Locale.ROOT));
            if (material != null) materials.add(material);
        }
        return materials.isEmpty() ? new HashSet<>(fallback) : materials;
    }

    private String cleanPrefix(String raw) {
        return LoreGuard.stripObfuscated(raw.replace("\"", ""));
    }

    private int visibleLength(String raw) {
        return ChatColor.stripColor(color(raw)).length();
    }

    private Component component(String raw) {
        return LegacyComponentSerializer.legacySection()
            .deserialize(color(raw))
            .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private String color(String raw) {
        if (raw == null) return "";
        Matcher matcher = HEX_COLOR_PATTERN.matcher(raw);
        StringBuilder text = new StringBuilder(raw.length());
        while (matcher.find()) {
            String replacement = net.md_5.bungee.api.ChatColor.of("#" + matcher.group(1)).toString();
            matcher.appendReplacement(text, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(text);
        return ChatColor.translateAlternateColorCodes('&', text.toString());
    }

    private String itemName(ItemStack item) {
        if (item == null || item.getType().isAir()) return "Air";
        String[] words = item.getType().name().toLowerCase(Locale.ROOT).split("_");
        List<String> formatted = new ArrayList<>();
        for (String word : words) {
            if (word.isEmpty()) continue;
            formatted.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return String.join(" ", formatted);
    }

    private void msg(CommandSender sender, String text) {
        sender.sendMessage(color(prefix + " &r" + text));
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(1, millis / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("cosmetics") && args.length == 1) return List.of("help");
        if (name.equals("cosadmin")) {
            if (args.length == 1) {
                List<String> subs = new ArrayList<>(List.of("grant", "revoke", "clear", "preset", "grantall", "patreon", "ledger", "help"));
                if (isConsole(sender)) subs.addAll(STORE_SUBCOMMANDS);
                return subs;
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("token")) return Arrays.stream(Rarity.values()).map(r -> r.id).toList();
            if (args.length == 3 && args[0].equalsIgnoreCase("preset")) return grantPresets.stream().map(p -> p.id).toList();
            if (args.length == 3 && args[0].equalsIgnoreCase("grantall")) return List.of("0", "1", "3", "10");
            if (args.length == 3) return adminCategories().stream().map(c -> c.id).toList();
            if (args.length == 4 && args[0].equalsIgnoreCase("preset")) return List.of("replace");
            if (args.length == 4) return allIds();
        }
        if (name.equals("titleadmin") && args.length == 1) return List.of("set", "clear");
        if (name.equals("settitle")) return List.of("menu", "tags", "reset", "clear", "none");
        if (name.equals("setkillmsg")) return List.of("{victim} was deleted by {killer} {item}", "{killer} sent {victim} to spawn {item}");
        if (name.equals("setlore")) {
            if (args.length == 1) return List.of("book", "draft", "editor");
            if (args.length == 2 && args[0].equalsIgnoreCase("draft")) return List.of("start", "add", "setname", "remove", "clear", "preview", "apply");
        }
        return List.of();
    }

    private List<String> allIds() {
        List<String> ids = new ArrayList<>();
        ids.add("all");
        tags.forEach(c -> ids.add(c.id));
        killEffects.forEach(c -> ids.add(c.id));
        trails.forEach(c -> ids.add(c.id));
        return ids;
    }

    /** Thread-safe, side-effect-free lookup for placeholder callbacks; never starts a load. */
    private PlayerData cachedData(Player player) {
        PlayerData data = players.get(player.getUniqueId());
        return data != null ? data : new PlayerData(player.getUniqueId());
    }

    public String placeholderPrefix(Player player) {
        PlayerData data = cachedData(player);
        if (data.selectedTag != null) {
            NamedCosmetic tag = find(tags, data.selectedTag);
            if (tag != null) return color(premadeTagDisplay(tag));
        }
        if (data.customPrefix != null) return color(data.customPrefix + "&r ");
        return color(vaultPrefix(player));
    }

    public String placeholderName(Player player) {
        return placeholderPrefix(player) + player.getName();
    }

    /** {@code founder} cosmetics are exclusive: category-wide unlocks, rarity permissions and tokens never include them. */
    private record NamedCosmetic(String id, String display, Material icon, Rarity rarity, boolean founder) {
        NamedCosmetic(String id, String display, Material icon, Rarity rarity) {
            this(id, display, icon, rarity, false);
        }
    }

    private record KillMessagePreset(String id, String display, Material icon, String message) {
    }

    private record WeaponCrafter(@Nullable UUID uuid, String name) {
    }

    private record PendingSave(PlayerData snapshot, boolean includeGrants) {
    }

    private static class GrantPreset {
        private final String id;
        private final String display;
        private final Material icon;
        private final int loreCredits;
        private final Map<Category, Set<String>> grants = new EnumMap<>(Category.class);

        private GrantPreset(String id, String display, Material icon, int loreCredits) {
            this.id = id;
            this.display = display;
            this.icon = icon;
            this.loreCredits = loreCredits;
            for (Category category : Category.values()) grants.put(category, new HashSet<>());
        }

        private void grant(Category category, String id) {
            grants.get(category).add(id.toLowerCase(Locale.ROOT));
        }

        private List<String> summary() {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<Category, Set<String>> entry : grants.entrySet()) {
                if (entry.getValue().isEmpty()) continue;
                lines.add("&8- &f" + entry.getKey().id + ": &7" + String.join(", ", entry.getValue()));
            }
            return lines;
        }
    }

    private enum Menu {
        HUB, COSMETIC_LIST, TOKENS, REDEEM_CONFIRM, PREFIX_PREVIEW, LORE_MENU, LORE_PREVIEW, LORE_CLEAR_CONFIRM, KILL_MESSAGES,
        KILLMSG_PREVIEW, LORE_HISTORY, TRIM_PATTERNS, TRIM_MATERIALS, TRIM_CONFIRM,
        ADMIN_PLAYERS(true), ADMIN_CATEGORIES(true), ADMIN_CATEGORY(true), ADMIN_PRESETS(true);

        /** Admin menus re-check civ.cosmetic.admin on every click, not just when they are opened. */
        private final boolean admin;

        Menu() {
            this(false);
        }

        Menu(boolean admin) {
            this.admin = admin;
        }
    }

    private static class GuiHolder implements InventoryHolder {
        private final Menu menu;
        private final String data;
        /** Player menus register a click action per slot; admin menus still dispatch by slot number. */
        private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
        private Inventory inventory;

        private GuiHolder(Menu menu, String data) {
            this.menu = menu;
            this.data = data;
        }

        private int intData() {
            try {
                return Integer.parseInt(data);
            } catch (NumberFormatException e) {
                return 1;
            }
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    enum Category {
        KILL_EFFECTS("killeffects"), TRAILS("trails"), TITLE("title"), TAGS("tags"), TRIM("trim"), LORE("lore"), KILLMSG("killmsg");

        final String id;
        private static final Map<String, Category> ALIASES = new HashMap<>();

        static {
            for (Category category : values()) ALIASES.put(category.id, category);
            for (String alias : List.of("killeffect", "kill-effects", "kill_effects")) ALIASES.put(alias, KILL_EFFECTS);
            for (String alias : List.of("trail", "projectile", "projectiles", "projectiletrails", "projectile-trails", "projectile_trails")) ALIASES.put(alias, TRAILS);
            for (String alias : List.of("prefix", "prefixes", "tag", "premadetag", "premadetags", "premade-tags")) ALIASES.put(alias, TAGS);
            ALIASES.put("titles", TITLE);
            for (String alias : List.of("armortrim", "armortrims", "trims")) ALIASES.put(alias, TRIM);
            for (String alias : List.of("killmessage", "killmessages", "kill-msg")) ALIASES.put(alias, KILLMSG);
            for (String alias : List.of("itemlore", "item-lore")) ALIASES.put(alias, LORE);
        }

        Category(String id) {
            this.id = id;
        }

        static Category normal(String raw) {
            if (raw == null) return null;
            return ALIASES.get(raw.toLowerCase(Locale.ROOT));
        }
    }

    static class PlayerData {
        final UUID uuid;
        final Map<Category, Set<String>> grants = new EnumMap<>(Category.class);
        String selectedKillEffect;
        String selectedTrail;
        String selectedTag;
        String customPrefix;
        String savedCustomPrefix;
        String killMessage;
        String pendingKillMessage;
        int loreCredits;
        long lastTrim;
        /** Unspent store tokens per rarity. Derived from the purchase ledger; never saved from this copy. */
        final Map<Rarity, Integer> tokens = Store.emptyTokens();
        /** Last entitlements_version read from storage; a higher stored value means another server changed something. */
        long entitlementsVersion;
        final List<ItemStack> loreHistory = new ArrayList<>();

        PlayerData(UUID uuid) {
            this.uuid = uuid;
            for (Category category : Category.values()) grants.put(category, new HashSet<>());
        }

        void grant(Category category, String id) {
            grants.get(category).add(id.toLowerCase(Locale.ROOT));
        }

        void revoke(Category category, String id) {
            grants.get(category).remove(id.toLowerCase(Locale.ROOT));
        }

        boolean hasGrant(Category category, String id) {
            return grants.get(category).contains(id.toLowerCase(Locale.ROOT));
        }

        int tokens(Rarity rarity) {
            return tokens.getOrDefault(rarity, 0);
        }

        void applyEntitlements(Store.Entitlements entitlements) {
            for (Category category : Category.values()) {
                Set<String> current = grants.get(category);
                current.clear();
                current.addAll(entitlements.grants().getOrDefault(category, Set.of()));
            }
            loreCredits = entitlements.loreCredits();
            tokens.putAll(entitlements.tokens());
            entitlementsVersion = entitlements.version();
        }

        PlayerData copy() {
            PlayerData copy = new PlayerData(uuid);
            for (Map.Entry<Category, Set<String>> entry : grants.entrySet()) {
                copy.grants.get(entry.getKey()).addAll(entry.getValue());
            }
            copy.selectedKillEffect = selectedKillEffect;
            copy.selectedTrail = selectedTrail;
            copy.selectedTag = selectedTag;
            copy.customPrefix = customPrefix;
            copy.savedCustomPrefix = savedCustomPrefix;
            copy.killMessage = killMessage;
            copy.pendingKillMessage = pendingKillMessage;
            copy.loreCredits = loreCredits;
            copy.lastTrim = lastTrim;
            copy.tokens.putAll(tokens);
            copy.entitlementsVersion = entitlementsVersion;
            for (ItemStack item : loreHistory) {
                copy.loreHistory.add(item.clone());
            }
            return copy;
        }
    }

    private static class LoreDraft {
        private String name;
        private final List<String> lines = new ArrayList<>();

        private LoreDraft(String name) {
            this.name = name;
        }

        private String raw() {
            List<String> parts = new ArrayList<>();
            parts.add(name);
            parts.addAll(lines);
            return String.join("|", parts);
        }
    }

    public static class CivCosmeticsPlaceholders extends PlaceholderExpansion {
        private final CivCosmeticsPlugin plugin;

        CivCosmeticsPlaceholders(CivCosmeticsPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
            if (player == null) return "";
            return switch (params.toLowerCase(Locale.ROOT)) {
                case "prefix" -> plugin.placeholderPrefix(player);
                case "name" -> plugin.placeholderName(player);
                case "selected_tag" -> {
                    String tag = plugin.cachedData(player).selectedTag;
                    yield tag == null ? "" : tag;
                }
                case "lore_credits" -> Integer.toString(plugin.cachedData(player).loreCredits);
                default -> null;
            };
        }

        @Override
        public @NotNull String getIdentifier() {
            return "civcosmetics";
        }

        @Override
        public @NotNull String getAuthor() {
            return "Civ";
        }

        @Override
        public @NotNull String getVersion() {
            return plugin.getPluginMeta().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }
    }
}
