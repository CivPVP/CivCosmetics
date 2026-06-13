package net.civmc.civcosmetics;

import java.io.File;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CivCosmeticsPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private static final List<Integer> TAG_SLOTS = List.of(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34);
    private static final List<Integer> TRAIL_SLOTS = List.of(
        10, 11, 12, 13, 14, 15, 16,
        20, 21, 22, 23, 24);
    private static final List<Integer> TRIM_PATTERN_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31);
    private static final List<Integer> TRIM_MATERIAL_SLOTS = List.of(11, 12, 13, 14, 15, 20, 21, 22, 23, 24);
    private static final List<Integer> ADMIN_PLAYER_SLOTS = List.of(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43);
    private static final List<Integer> EFFECT_SLOTS = ADMIN_PLAYER_SLOTS;
    private static final List<Integer> ADMIN_OPTION_SLOTS = TAG_SLOTS;
    private static final List<Integer> ADMIN_CATEGORY_SLOTS = List.of(11, 13, 15, 20, 22, 24);

    private final Map<UUID, PlayerData> players = new HashMap<>();
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
    private final Set<UUID> loadingPlayers = new HashSet<>();
    private final Map<UUID, List<Consumer<PlayerData>>> loadCallbacks = new HashMap<>();
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
    private boolean shuttingDown;
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
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        if (expansion != null) {
            expansion.unregister();
        }
        Bukkit.getServicesManager().unregisterAll(this);
        if (storage != null && storageExecutor != null && storageHealthy) {
            for (PlayerData data : new ArrayList<>(players.values())) {
                PlayerData snapshot = data.copy();
                storageExecutor.execute(() -> {
                    try {
                        storage.saveState(snapshot);
                    } catch (RuntimeException e) {
                        getLogger().warning("Failed to flush cosmetics for " + snapshot.uuid + ": " + e.getMessage());
                    }
                });
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
            storage.initialize();
            if (getConfig().getBoolean("mysql.import-yaml-on-enable", true)) {
                storageExecutor.execute(() -> {
                    try {
                        YamlCosmeticsImporter.importOnce(storage, playerFolder, getLogger());
                    } catch (RuntimeException e) {
                        failStorage("import old YAML cosmetics", null, e);
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
        storeUrl = getConfig().getString("store-url", "https://www.patreon.com/c/CivPVP");
        loreEditorUrl = getConfig().getString("lore-editor-url", "https://civpvp.net/lore-editor");
        prefixMaxLength = getConfig().getInt("prefix-max-length", 16);
        loreMaxLines = Math.max(1, getConfig().getInt("lore.max-lines", 30));
        loreMaxTotalCharacters = Math.max(1, getConfig().getInt("lore.max-total-characters", 4096));
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
        tags.add(new NamedCosmetic("champion", "&#FFB000&l✦ ᴄʜᴀᴍᴘɪᴏɴ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("legend", "&#FFD166&l★ ʟᴇɢᴇɴᴅ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("knight", "&#9AD0FF&l⚔ ᴋɴɪɢʜᴛ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("royal", "&#B388FF&l♛ ʀᴏʏᴀʟ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("veteran", "&#A8A8A8&l✚ ᴠᴇᴛᴇʀᴀɴ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("builder", "&#F4A261&l▣ ʙᴜɪʟᴅᴇʀ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("hunter", "&#7ED957&l⌖ ʜᴜɴᴛᴇʀ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("titan", "&#56CCF2&l◆ ᴛɪᴛᴀɴ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("ember", "&#FF6B35&l🔥 ᴇᴍʙᴇʀ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("frost", "&#7FDBFF&l❄ ꜰʀᴏꜱᴛ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("void", "&#8E44AD&l✧ ᴠᴏɪᴅ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("crimson", "&#D7263D&l✦ ᴄʀɪᴍꜱᴏɴ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("lucky", "&#2ECC71&l☘ ʟᴜᴄᴋʏ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("celestial", "&#F8F7FF&l☾ ᴄᴇʟᴇꜱᴛɪᴀʟ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("rogue", "&#95A5A6&l⌁ ʀᴏɢᴜᴇ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("guardian", "&#00B894&l⬟ ɢᴜᴀʀᴅɪᴀɴ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("alchemist", "&#C3FF00&l⚗ ᴀʟᴄʜᴇᴍɪꜱᴛ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("voyager", "&#4D96FF&l✈ ᴠᴏʏᴀɢᴇʀ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("phantom", "&#BFC0FF&l☁ ᴘʜᴀɴᴛᴏᴍ &f", Material.NAME_TAG));
        tags.add(new NamedCosmetic("reaper", "&#2D3436&l☠ ʀᴇᴀᴘᴇʀ &f", Material.NAME_TAG));

        killEffects.add(new NamedCosmetic("pig", "&d&lPIG ASCENSION", Material.PORKCHOP));
        killEffects.add(new NamedCosmetic("dragon", "&5&lDRAGON", Material.DRAGON_EGG));
        killEffects.add(new NamedCosmetic("thunder", "&b&lLIGHTNING", Material.LIGHTNING_ROD));
        killEffects.add(new NamedCosmetic("flame", "&6&lFLAME", Material.CAMPFIRE));
        killEffects.add(new NamedCosmetic("heartbeat", "&c&lHEARTBEAT", Material.RED_DYE));
        killEffects.add(new NamedCosmetic("warden", "&3&lWARDEN", Material.WARDEN_SPAWN_EGG));
        killEffects.add(new NamedCosmetic("firework", "&f&lFIREWORK", Material.FIREWORK_ROCKET));
        killEffects.add(new NamedCosmetic("snow", "&b&lFROSTBITE", Material.POWDER_SNOW_BUCKET));
        killEffects.add(new NamedCosmetic("trident", "&9&lTRIDENT", Material.TRIDENT));
        killEffects.add(new NamedCosmetic("crystal", "&d&lCRYSTAL", Material.AMETHYST_CLUSTER));
        killEffects.add(new NamedCosmetic("blood", "&4&lBLOOD EXPLOSION", Material.REDSTONE_BLOCK));
        killEffects.add(new NamedCosmetic("explosion", "&4&lEXPLOSION", Material.TNT));
        killEffects.add(new NamedCosmetic("void", "&d&lVOID", Material.ENDER_EYE));
        killEffects.add(new NamedCosmetic("wealth", "&6&lWEALTH", Material.GOLD_INGOT));
        killEffects.add(new NamedCosmetic("squid", "&0&lSQUID MISSILE", Material.INK_SAC));
        killEffects.add(new NamedCosmetic("tornado", "&3&lVORTEX", Material.SCULK));
        killEffects.add(new NamedCosmetic("lava", "&c&lLAVA", Material.MAGMA_CREAM));
        killEffects.add(new NamedCosmetic("music", "&a&lMUSIC", Material.NOTE_BLOCK));
        killEffects.add(new NamedCosmetic("cloud", "&f&lJUDGEMENT", Material.END_ROD));
        killEffects.add(new NamedCosmetic("slime", "&2&lSLIME", Material.SLIME_BALL));
        killEffects.add(new NamedCosmetic("cookies", "&6&lCOOKIE FOUNTAIN", Material.COOKIE));
        killEffects.add(new NamedCosmetic("soul_rift", "&3&lSOUL RIFT", Material.SOUL_LANTERN));
        killEffects.add(new NamedCosmetic("meteor", "&6&lMETEOR", Material.FIRE_CHARGE));
        killEffects.add(new NamedCosmetic("shatter", "&b&lSHATTER", Material.BLUE_STAINED_GLASS));
        killEffects.add(new NamedCosmetic("crown", "&e&lCROWN", Material.GOLDEN_HELMET));
        killEffects.add(new NamedCosmetic("glitch", "&d&lGLITCH", Material.ENDER_PEARL));
        killEffects.add(new NamedCosmetic("execution", "&4&lEXECUTION", Material.IRON_AXE));

        trails.add(new NamedCosmetic("hearts", "&c&lHEART", Material.RED_DYE));
        trails.add(new NamedCosmetic("flame", "&6&lFLAME", Material.BLAZE_POWDER));
        trails.add(new NamedCosmetic("magic", "&d&lMAGIC", Material.GHAST_TEAR));
        trails.add(new NamedCosmetic("music", "&a&lMUSIC", Material.NOTE_BLOCK));
        trails.add(new NamedCosmetic("smoke", "&8&lSMOKE", Material.GUNPOWDER));
        trails.add(new NamedCosmetic("crit", "&b&lCRIT", Material.DIAMOND_SWORD));
        trails.add(new NamedCosmetic("slime", "&2&lSLIME", Material.SLIME_BALL));
        trails.add(new NamedCosmetic("soul", "&3&lSOUL", Material.SOUL_TORCH));
        trails.add(new NamedCosmetic("ender", "&5&lENDER", Material.ENDER_PEARL));
        trails.add(new NamedCosmetic("gold", "&6&lGOLD", Material.GOLD_NUGGET));
        trails.add(new NamedCosmetic("frost", "&b&lFROST", Material.SNOWBALL));
        trails.add(new NamedCosmetic("totem", "&a&lTOTEM", Material.TOTEM_OF_UNDYING));

        killMessagePresets.add(new KillMessagePreset("classic", "&e&lClassic", Material.PAPER, "&8{victim} &7was killed by &8{killer} &7{item}"));
        killMessagePresets.add(new KillMessagePreset("deleted", "&c&lDeleted", Material.TNT, "&c{victim} &7was deleted by &6{killer} &7{item}"));
        killMessagePresets.add(new KillMessagePreset("sent_spawn", "&a&lSent To Spawn", Material.COMPASS, "&6{killer} &7sent &c{victim} &7back to spawn &8{item}"));
        killMessagePresets.add(new KillMessagePreset("outplayed", "&b&lOutplayed", Material.DIAMOND_SWORD, "&b{victim} &7got outplayed by &f{killer} &8{item}"));
        killMessagePresets.add(new KillMessagePreset("vanished", "&5&lVanished", Material.ENDER_PEARL, "&5{victim} &7vanished after meeting &d{killer} &8{item}"));
        killMessagePresets.add(new KillMessagePreset("royal", "&6&lRoyal", Material.GOLDEN_HELMET, "&e{killer} &6claimed victory over &e{victim} &8{item}"));

        trimPatterns.addAll(List.of("coast", "dune", "eye", "rib", "sentry", "snout", "spire", "tide", "vex", "ward", "wild", "wayfinder", "raiser", "shaper", "host", "silence", "bolt", "flow"));
        trimMaterials.addAll(List.of("amethyst", "copper", "diamond", "emerald", "gold", "iron", "lapis", "netherite", "quartz", "redstone"));
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
            msg(sender, "&e/cosadmingui");
            return true;
        }
        if (args.length < 2) {
            msg(sender, "&cUsage: /cosadmin <grant|revoke|clear|preset> <player> <category|preset> [id|replace]");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!requireLoaded(sender, target.getUniqueId(), () -> cosAdmin(sender, args))) return true;
        PlayerData data = load(target.getUniqueId());
        if (args[0].equalsIgnoreCase("grantall") || args[0].equalsIgnoreCase("patreon")) {
            int loreCredits = args[0].equalsIgnoreCase("patreon") ? 1 : (args.length > 2 ? parseInt(args[2], 0) : 0);
            grantAll(data);
            data.loreCredits += Math.max(0, loreCredits);
            saveWithGrants(data);
            Player online = target.getPlayer();
            if (online != null) validateSelections(online);
            msg(sender, "&aGranted all cosmetics to &f" + target.getName() + " &7(+" + Math.max(0, loreCredits) + " lore credits)");
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
            msg(sender, "&aApplied &e" + preset.id + " &ato &f" + target.getName() + (replace ? " &7(replaced old grants)" : ""));
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
            msg(sender, "&aGranted &e" + category.id + ":" + id + " &ato &f" + target.getName());
        } else if (args[0].equalsIgnoreCase("revoke")) {
            data.revoke(category, id);
            if (category == Category.TAGS && id.equals("all")) data.revoke(Category.TITLE, "all");
            saveWithGrants(data);
            Player online = target.getPlayer();
            if (online != null) validateSelections(online);
            msg(sender, "&cRevoked &e" + category.id + ":" + id + " &cfrom &f" + target.getName());
        } else if (args[0].equalsIgnoreCase("clear")) {
            clearCategoryGrants(data, category);
            saveWithGrants(data);
            Player online = target.getPlayer();
            if (online != null) validateSelections(online);
            msg(sender, "&cCleared " + category.id + " grants from " + target.getName());
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
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!requireLoaded(sender, target.getUniqueId(), () -> titleAdmin(sender, args))) return true;
        PlayerData data = load(target.getUniqueId());
        if (args[0].equalsIgnoreCase("clear")) {
            data.customPrefix = null;
            data.selectedTag = null;
            save(data);
            Player online = target.getPlayer();
            if (online != null) applyPrefix(online);
            msg(sender, "&eCleared title for " + target.getName());
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
            msg(sender, "&aSet title for " + target.getName() + ": &r" + raw);
        }
        return true;
    }

    private boolean addLoreCredit(CommandSender sender, String[] args) {
        if (!sender.hasPermission("civ.cosmetic.admin")) {
            msg(sender, "&cNo permission!");
            return true;
        }
        if (args.length < 1) {
            msg(sender, "&cUsage: /addlorecredit <player> [amount]");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        int amount = args.length > 1 ? parseInt(args[1], 0) : 1;
        if (amount <= 0) {
            msg(sender, "&cAmount must be positive.");
            return true;
        }
        if (!requireLoaded(sender, target.getUniqueId(), () -> addLoreCredit(sender, args))) return true;
        PlayerData data = load(target.getUniqueId());
        data.loreCredits += amount;
        save(data);
        msg(sender, "&aAdded &e" + amount + " &alore credits to &f" + target.getName());
        return true;
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
        msg(sender, "&eStore/Patreon: &b" + storeUrl);
        if (sender.hasPermission("civ.cosmetic.admin")) {
            msg(sender, "&6&lAdmin");
            msg(sender, "&e/cosadmin help &7- grant/revoke/preset commands.");
            msg(sender, "&e/cosadmingui &7- admin GUI.");
            msg(sender, "&e/cosadmin grantall <player> [loreCredits] &7- grant all cosmetics.");
            msg(sender, "&e/cosadmin patreon <player> &7- grant all cosmetics + one lore credit.");
            msg(sender, "&e/titleadmin set|clear <player> [prefix] &7- manage player title.");
            msg(sender, "&e/addlorecredit <player> [amount] &7- add lore credits.");
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
        if (!has(player, Category.LORE, "item")) {
            msg(player, "&cYou do not have access to custom item lore.");
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
        String validation = validateLore(raw);
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

    private String validateLore(String raw) {
        String[] parts = raw.split("\\|", -1);
        int loreLines = Math.max(0, parts.length - 1);
        if (loreLines > loreMaxLines) {
            return "&cThat lore has too many lines. Max " + loreMaxLines + ".";
        }
        if (raw.length() > loreMaxTotalCharacters) {
            return "&cThat lore is too long. Max " + loreMaxTotalCharacters + " stored characters.";
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
        String validation = validateLore(raw);
        if (validation != null) {
            msg(player, validation);
            return false;
        }
        applyLoreToItem(item, raw);
        data.loreHistory.add(item.clone());
        while (data.loreHistory.size() > 27) {
            data.loreHistory.remove(0);
        }
        if (!player.isOp()) {
            data.loreCredits--;
        }
        save(data);
        msg(player, "&aItem updated. &eLore Credit consumed.");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
        return true;
    }

    private void applyLoreToItem(ItemStack item, String raw) {
        String[] parts = raw.split("\\|", -1);
        ItemMeta meta = item.getItemMeta();
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

    private void clearLoreFromItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.displayName(null);
        meta.getPersistentDataContainer().remove(customLoreNameKey);
        meta.getPersistentDataContainer().remove(customLoreLinesKey);
        rebuildWeaponLore(meta);
        item.setItemMeta(meta);
    }

    private void openHub(Player player) {
        PlayerData data = data(player);
        Inventory inv = gui(Menu.HUB, 27, "&8> &6Main Cosmetics Hub", null);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(4, headItem(player.getUniqueId(), "&6&lYour Profile", "&7Lore Credits: &e" + data.loreCredits));
        inv.setItem(10, item(Material.TNT, "&6&lKill Effects", "&7Customize your finishing moves.", "", "&eClick to open."));
        inv.setItem(11, item(Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, "&b&lArmor Trims", "&7Style your armor.", "", "&eClick to open."));
        inv.setItem(12, item(Material.NAME_TAG, "&d&lPrefixes", "&7Current: &f" + currentPrefixText(data), "&7Choose a preset or create a custom prefix.", "", "&eClick to open."));
        inv.setItem(13, item(Material.DIAMOND_SWORD, "&f&lCustom Lore", "&7Credits: &e" + data.loreCredits, "&7Includes examples, history, and item cleanup.", "", "&eClick to open."));
        inv.setItem(14, item(Material.ARROW, "&e&lProjectile Trails", "&7Customize arrows and pearls.", "", "&eClick to open."));
        inv.setItem(15, item(Material.PAPER, "&c&lKill Messages", "&7Current: &f" + currentKillMessageText(data), "&7Select preset death messages.", "", "&eClick to open."));
        inv.setItem(16, item(Material.GOLD_INGOT, "&d&lPATREON", "&7Support the server.", "", "&eClick for link."));
        player.openInventory(inv);
    }

    private void openKillEffects(Player player) {
        Inventory inv = gui(Menu.KILL_EFFECTS, 54, "&6&lKill Effects &8- &eCIVPVP", null);
        fill(inv, Material.GRAY_STAINED_GLASS_PANE);
        PlayerData data = data(player);
        for (int i = 0; i < killEffects.size() && i < EFFECT_SLOTS.size(); i++) {
            NamedCosmetic effect = killEffects.get(i);
            boolean selected = effect.id.equals(data.selectedKillEffect);
            boolean owned = has(player, Category.KILL_EFFECTS, effect.id);
            inv.setItem(EFFECT_SLOTS.get(i), item(selected ? Material.LIME_DYE : effect.icon, effect.display + (selected ? " &7(&aSelected&7)" : owned ? " &7(&eUnlocked&7)" : " &7(&cLocked&7)"),
                owned ? "&7Left click to select." : "&cLocked.",
                owned ? "&7Right click to preview." : "&7Requires civ.cosmetic.killeffects." + effect.id));
        }
        inv.setItem(49, item(Material.BARRIER, "&c&lCLEAR EFFECT"));
        player.openInventory(inv);
    }

    private void openTrails(Player player) {
        Inventory inv = gui(Menu.TRAILS, 36, "&8> &6Projectile Trails", null);
        fill(inv, Material.GRAY_STAINED_GLASS_PANE);
        PlayerData data = data(player);
        for (int i = 0; i < trails.size() && i < TRAIL_SLOTS.size(); i++) {
            NamedCosmetic trail = trails.get(i);
            boolean selected = trail.id.equals(data.selectedTrail);
            boolean owned = has(player, Category.TRAILS, trail.id);
            inv.setItem(TRAIL_SLOTS.get(i), item(selected ? Material.LIME_DYE : trail.icon, trail.display + " TRAIL" + (selected ? " &7(&aSelected&7)" : owned ? " &7(&eUnlocked&7)" : " &7(&cLocked&7)"),
                owned ? "&7Left click to select." : "&cLocked.",
                owned ? "&7Right click to preview." : "&7Requires civ.cosmetic.trails." + trail.id));
        }
        inv.setItem(31, item(Material.BARRIER, "&c&lCLEAR TRAIL"));
        player.openInventory(inv);
    }

    private void openTags(Player player, int page) {
        page = Math.max(1, page);
        Inventory inv = gui(Menu.TAGS, 54, "&8> &6Prefixes &8Page " + page, page);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        int offset = (page - 1) * TAG_SLOTS.size();
        PlayerData data = data(player);
        for (int i = 0; i < TAG_SLOTS.size() && offset + i < tags.size(); i++) {
            NamedCosmetic tag = tags.get(offset + i);
            boolean selected = tag.id.equals(data.selectedTag);
            boolean owned = has(player, Category.TAGS, tag.id);
            inv.setItem(TAG_SLOTS.get(i), item(Material.NAME_TAG, tag.display + (selected ? "&aSelected" : owned ? "&7Prefix Tag" : "&cLocked"),
                owned ? "&aUnlocked." : "&cLocked.",
                owned ? "&7Click to preview and equip." : "&7Requires civ.cosmetic.tags." + tag.id));
        }
        inv.setItem(45, item(Material.ARROW, "&e< Back to Cosmetics"));
        if (page > 1) inv.setItem(46, item(Material.ARROW, "&e< Previous Page"));
        inv.setItem(48, data.savedCustomPrefix == null
            ? item(Material.WRITABLE_BOOK, "&b&lCUSTOM PREFIX", "&7Create one with /settitle <text>")
            : item(Material.NAME_TAG, data.savedCustomPrefix + "&7Saved Custom", "&7Click to equip it again."));
        inv.setItem(49, item(Material.BARRIER, "&c&lCLEAR PERSONAL PREFIX"));
        inv.setItem(50, item(Material.WRITABLE_BOOK, "&e&lCREATE CUSTOM", "&7Use /settitle <text>"));
        if (offset + TAG_SLOTS.size() < tags.size()) inv.setItem(53, item(Material.ARROW, "&aNext Page >"));
        player.openInventory(inv);
    }

    private void openLorePreview(Player player, String raw) {
        Inventory inv = gui(Menu.LORE_PREVIEW, 27, "&8Item Lore > Preview", null);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        ItemStack preview = player.getInventory().getItemInMainHand().clone();
        applyLoreToItem(preview, raw);
        inv.setItem(11, item(Material.LIME_STAINED_GLASS_PANE, "&a&lSAVE ITEM LORE", "&7Apply this name and lore.", "&7Consumes one Lore Credit."));
        inv.setItem(13, preview);
        inv.setItem(15, item(Material.RED_STAINED_GLASS_PANE, "&c&lCANCEL"));
        player.openInventory(inv);
    }

    private void openLoreMenu(Player player) {
        PlayerData data = data(player);
        Inventory inv = gui(Menu.LORE_MENU, 27, "&8Item Lore", null);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(4, item(Material.DIAMOND_SWORD, "&f&lItem Lore",
            "&7Credits: &e" + data.loreCredits,
            "&7Held item: &f" + heldItemName(player),
            "",
            has(player, Category.LORE, "item") ? "&aUnlocked." : "&cLocked."));
        inv.setItem(10, item(Material.WRITABLE_BOOK, "&b&lCreate Custom Lore",
            "&7Use the command:",
            "&f/setlore <Name|Lore line 1|Lore line 2>",
            "&f/setlore book &7for long book input",
            "&f/setlore editor &7for the web editor",
            "",
            "&7A preview and confirm menu will open."));
        inv.setItem(12, exampleLorePreset());
        inv.setItem(14, item(Material.BOOK, "&e&lLore History", "&7View your previous lore previews.", "", "&eClick to open."));
        inv.setItem(16, item(Material.BARRIER, "&c&lClear Held Item Lore", "&7Remove custom name and lore from", "&7the item in your main hand.", "", "&eClick to preview."));
        inv.setItem(22, item(Material.ARROW, "&e< Back to Cosmetics"));
        player.openInventory(inv);
    }

    private void openLoreClearConfirm(Player player) {
        if (!has(player, Category.LORE, "item")) {
            msg(player, "&cYou do not have access to custom item lore.");
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            msg(player, "&cHold an item first.");
            return;
        }
        Inventory inv = gui(Menu.LORE_CLEAR_CONFIRM, 27, "&8Clear Lore > Confirm", null);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        ItemStack preview = held.clone();
        clearLoreFromItem(preview);
        inv.setItem(11, item(Material.LIME_STAINED_GLASS_PANE, "&a&lCONFIRM CLEAR", "&7Remove custom name and lore.", "&7Other item data stays unchanged."));
        inv.setItem(13, preview);
        inv.setItem(15, item(Material.RED_STAINED_GLASS_PANE, "&c&lCANCEL"));
        player.openInventory(inv);
    }

    private void openPrefixPreview(Player player, String clean) {
        Inventory inv = gui(Menu.PREFIX_PREVIEW, 27, "&8Custom Prefix > Preview", null);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(11, item(Material.LIME_STAINED_GLASS_PANE, "&a&lSAVE AND EQUIP", "&7Save this custom prefix."));
        inv.setItem(13, item(Material.NAME_TAG, clean + "&7Preview", "&7Name preview:", "&f" + clean + "&f" + player.getName(), "", "&7Chat preview:", clean + "&f" + player.getName() + "&7: Hello!", "", "&8Raw: &7" + clean));
        inv.setItem(15, item(Material.RED_STAINED_GLASS_PANE, "&c&lCANCEL"));
        player.openInventory(inv);
    }

    private void openKillMessagePreview(Player player, String raw) {
        raw = normalizeKillMessage(raw);
        Inventory inv = gui(Menu.KILLMSG_PREVIEW, 27, "&8Kill Message > Preview", null);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        String preview = LegacyComponentSerializer.legacySection().serialize(renderKillMessageComponent(raw, component(player.getName()), component("ExampleVictim"), hoverItemText(exampleLorePreset())));
        inv.setItem(11, item(Material.LIME_STAINED_GLASS_PANE, "&a&lSAVE KILL MESSAGE", "&7Use this kill message."));
        inv.setItem(13, item(Material.PAPER, "&e&lPREVIEW", "&7Final broadcast ending:", "&r" + preview, "", "&8Raw: &7" + raw, "&8Placeholders: {killer}, {victim}, {item}"));
        inv.setItem(15, item(Material.RED_STAINED_GLASS_PANE, "&c&lCANCEL"));
        player.openInventory(inv);
    }

    private void openKillMessages(Player player) {
        Inventory inv = gui(Menu.KILL_MESSAGES, 45, "&8Kill Messages", null);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        PlayerData data = data(player);
        inv.setItem(4, item(Material.PAPER, "&c&lSelected Kill Message",
            "&7Current: &f" + currentKillMessageText(data),
            "",
            data.killMessage == null ? "&7Default Paper death message is active." : "&7Your selected message appears below.",
            data.killMessage == null ? "" : "&r" + LegacyComponentSerializer.legacySection().serialize(renderKillMessageComponent(normalizeKillMessage(data.killMessage), component(player.getName()), component("ExampleVictim"), hoverItemText(exampleLorePreset())))));
        List<Integer> slots = centeredRowSlots(killMessagePresets.size(), List.of(10, 11, 12, 13, 14, 15, 16));
        for (int i = 0; i < killMessagePresets.size() && i < slots.size(); i++) {
            KillMessagePreset preset = killMessagePresets.get(i);
            boolean selected = preset.message.equals(data.killMessage);
            String preview = LegacyComponentSerializer.legacySection().serialize(renderKillMessageComponent(preset.message, component(player.getName()), component("ExampleVictim"), hoverItemText(exampleLorePreset())));
            inv.setItem(slots.get(i), item(selected ? Material.LIME_DYE : preset.icon, preset.display + (selected ? " &7(&aSelected&7)" : ""),
                has(player, Category.KILLMSG, "all") ? "&aUnlocked." : "&cLocked.",
                "&7Preview:",
                "&r" + preview,
                "",
                "&eClick to preview and select."));
        }
        if (data.killMessage != null) {
            String preview = LegacyComponentSerializer.legacySection().serialize(renderKillMessageComponent(normalizeKillMessage(data.killMessage), component(player.getName()), component("ExampleVictim"), hoverItemText(exampleLorePreset())));
            inv.setItem(22, item(Material.WRITABLE_BOOK, "&b&lCurrent Custom", "&7Preview:", "&r" + preview, "", "&8Raw: &7" + data.killMessage));
        } else {
            inv.setItem(22, item(Material.WRITABLE_BOOK, "&b&lCustom Message", "&7Create one with:", "&f/setkillmsg <message>", "", "&8Use {killer}, {victim}, {item}"));
        }
        inv.setItem(36, item(Material.ARROW, "&e< Back to Cosmetics"));
        inv.setItem(40, item(Material.BARRIER, "&c&lCLEAR KILL MESSAGE"));
        inv.setItem(44, item(Material.OAK_SIGN, "&e&lCUSTOM COMMAND", "&7Use:", "&f/setkillmsg <message>", "", "&8Placeholders: {killer}, {victim}, {item}"));
        player.openInventory(inv);
    }

    private void openLoreHistory(Player player) {
        Inventory inv = gui(Menu.LORE_HISTORY, 36, "&8Lore History > Preview", null);
        fill(inv, Material.GRAY_STAINED_GLASS_PANE);
        int slot = 0;
        for (ItemStack item : data(player).loreHistory) {
            inv.setItem(slot++, item);
            if (slot > 26) break;
        }
        inv.setItem(27, exampleLorePreset());
        inv.setItem(31, item(Material.ARROW, "&e< Back to Lore Menu"));
        inv.setItem(35, item(Material.BARRIER, "&c&lCLEAR HISTORY", "&7Clears saved lore preview items.", "&7Does not edit your held item."));
        player.openInventory(inv);
    }

    private void openTrimPatterns(Player player) {
        if (!has(player, Category.TRIM, "all")) {
            msg(player, "&cYou do not have access to armor trims.");
            return;
        }
        if (!isArmor(player.getInventory().getItemInMainHand())) {
            msg(player, "&cYou must be holding a piece of armor.");
            return;
        }
        if (!player.isOp()) {
            long elapsed = System.currentTimeMillis() - data(player).lastTrim;
            long cooldown = trimCooldownHours * 60L * 60L * 1000L;
            if (elapsed < cooldown) {
                msg(player, "&cCooldown: Wait " + formatDuration(cooldown - elapsed) + ".");
                return;
            }
        }
        Inventory inv = gui(Menu.TRIM_PATTERNS, 45, "&8Cosmetic > Select Pattern", null);
        fill(inv, Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < trimPatterns.size(); i++) {
            inv.setItem(TRIM_PATTERN_SLOTS.get(i), item(Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, "&6&l" + trimPatterns.get(i) + " &ePattern", "&7Click to select this design."));
        }
        inv.setItem(40, item(Material.BARRIER, "&c&lRESET TRIM", "&7Click to remove existing trim."));
        player.openInventory(inv);
    }

    private void openTrimMaterials(Player player, String pattern) {
        Inventory inv = gui(Menu.TRIM_MATERIALS, 36, "&8Material > " + pattern, pattern);
        fill(inv, Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < trimMaterials.size(); i++) {
            String material = trimMaterials.get(i);
            inv.setItem(TRIM_MATERIAL_SLOTS.get(i), item(trimMaterialIcon(material), "&e&l" + material + " &7Material", "&7Apply to pattern: &e" + pattern));
        }
        inv.setItem(31, item(Material.ARROW, "&e< Back to Patterns"));
        player.openInventory(inv);
    }

    private void openTrimConfirm(Player player, String pattern, String material) {
        Inventory inv = gui(Menu.TRIM_CONFIRM, 27, "&8Confirming Selection", pattern + "|" + material);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        ItemStack preview = player.getInventory().getItemInMainHand().clone();
        applyTrim(preview, pattern, material);
        ItemMeta meta = preview.getItemMeta();
        meta.lore(List.of(component("&8&m-----------------"), component("&7Pattern: &6" + pattern), component("&7Material: &f" + material), component("&8&m-----------------"), Component.empty(), component("&a&lCLICK TO APPLY")));
        preview.setItemMeta(meta);
        inv.setItem(11, item(Material.LIME_STAINED_GLASS_PANE, "&a&lCONFIRM APPLICATION"));
        inv.setItem(13, preview);
        inv.setItem(15, item(Material.RED_STAINED_GLASS_PANE, "&c&lCANCEL / BACK"));
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
                failStorage("list cosmetics players", admin.getUniqueId(), e);
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
        inv.setItem(31, item(Material.GOLD_INGOT, "&6Patreon Presets",
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
            Bukkit.getScheduler().runTask(this, () -> enforceWornTrims(player));
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory().getType() == InventoryType.PLAYER) return;
        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;
        switch (holder.menu) {
            case HUB -> clickHub(player, slot);
            case KILL_EFFECTS -> clickKillEffects(player, slot, event.getClick());
            case TRAILS -> clickTrails(player, slot, event.getClick());
            case TAGS -> clickTags(player, slot, holder);
            case PREFIX_PREVIEW -> clickPrefixPreview(player, slot);
            case LORE_MENU -> clickLoreMenu(player, slot);
            case LORE_PREVIEW -> clickLorePreview(player, slot);
            case LORE_CLEAR_CONFIRM -> clickLoreClearConfirm(player, slot);
            case KILL_MESSAGES -> clickKillMessages(player, slot);
            case KILLMSG_PREVIEW -> clickKillMessagePreview(player, slot);
            case LORE_HISTORY -> {
                if (slot == 27) applyExampleLorePreset(player);
                else if (slot == 31) openLoreMenu(player);
                else if (slot == 35) clearLoreHistory(player);
            }
            case TRIM_PATTERNS -> clickTrimPatterns(player, slot, clicked);
            case TRIM_MATERIALS -> clickTrimMaterials(player, slot, clicked, holder);
            case TRIM_CONFIRM -> clickTrimConfirm(player, slot, holder);
            case ADMIN_PLAYERS -> clickAdminPlayers(player, slot, holder);
            case ADMIN_CATEGORIES -> clickAdminCategories(player, slot, event.getClick(), holder);
            case ADMIN_CATEGORY -> clickAdminCategory(player, slot, holder);
            case ADMIN_PRESETS -> clickAdminPresets(player, slot, event.getClick(), holder);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }

    private void clickHub(Player player, int slot) {
        if (slot == 10) openKillEffects(player);
        else if (slot == 11) openTrimPatterns(player);
        else if (slot == 12) openTags(player, 1);
        else if (slot == 13) openLoreMenu(player);
        else if (slot == 14) openTrails(player);
        else if (slot == 15) openKillMessages(player);
        else if (slot == 16) {
            player.closeInventory();
            msg(player, " ");
            msg(player, "&a&lSTORE: &b&n" + storeUrl);
            msg(player, " ");
        }
    }

    private void clickKillEffects(Player player, int slot, ClickType click) {
        if (slot == 49) {
            data(player).selectedKillEffect = null;
            save(data(player));
            player.closeInventory();
            msg(player, "&cKill effect cleared.");
            return;
        }
        int index = EFFECT_SLOTS.indexOf(slot);
        if (index < 0 || index >= killEffects.size()) return;
        NamedCosmetic effect = killEffects.get(index);
        if (!has(player, Category.KILL_EFFECTS, effect.id)) {
            msg(player, "&cYou do not own this kill effect.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (click.isRightClick()) {
            playKillEffect(effect.id, player.getLocation().add(0, 1, 0), player, true);
            return;
        }
        data(player).selectedKillEffect = effect.id;
        save(data(player));
        player.closeInventory();
        msg(player, "&aYou selected " + effect.display + "&a.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    private void clickTrails(Player player, int slot, ClickType click) {
        if (slot == 31) {
            data(player).selectedTrail = null;
            save(data(player));
            player.closeInventory();
            msg(player, "&cProjectile trail cleared.");
            return;
        }
        int index = TRAIL_SLOTS.indexOf(slot);
        if (index < 0 || index >= trails.size()) return;
        NamedCosmetic trail = trails.get(index);
        if (!has(player, Category.TRAILS, trail.id)) {
            msg(player, "&cYou do not own this projectile trail.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (click.isRightClick()) {
            previewTrail(player, trail.id);
            return;
        }
        data(player).selectedTrail = trail.id;
        save(data(player));
        player.closeInventory();
        msg(player, "&aYou selected the " + trail.display + "&a trail.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    private void clickTags(Player player, int slot, GuiHolder holder) {
        int page = holder.intData();
        if (slot == 45) openHub(player);
        else if (slot == 46) openTags(player, page - 1);
        else if (slot == 48) {
            PlayerData data = data(player);
            if (data.savedCustomPrefix == null) {
                player.closeInventory();
                msg(player, "&eCreate one with: &b/settitle <text>");
            } else if (!has(player, Category.TITLE, "all")) {
                msg(player, "&cYou do not have access to custom prefixes.");
            } else {
                data.customPrefix = data.savedCustomPrefix;
                data.selectedTag = null;
                save(data);
                applyPrefix(player);
                player.closeInventory();
                msg(player, "&aEquipped saved custom prefix: &r" + data.savedCustomPrefix);
            }
        } else if (slot == 49) {
            clearPrefix(player);
            player.closeInventory();
            msg(player, "&ePersonal prefix cleared. Your rank prefix will show again.");
        } else if (slot == 50) {
            player.closeInventory();
            msg(player, "&eCreate a custom prefix with: &b/settitle <text>");
        } else if (slot == 53) {
            openTags(player, page + 1);
        } else {
            int index = TAG_SLOTS.indexOf(slot);
            int tagIndex = (page - 1) * TAG_SLOTS.size() + index;
            if (index < 0 || tagIndex >= tags.size()) return;
            NamedCosmetic tag = tags.get(tagIndex);
            if (!has(player, Category.TAGS, tag.id)) {
                msg(player, "&cYou do not own this prefix tag.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            PlayerData data = data(player);
            data.selectedTag = tag.id;
            data.customPrefix = null;
            save(data);
            applyPrefix(player);
            player.closeInventory();
            msg(player, "&aEquipped prefix: &r" + tag.display);
        }
    }

    private void clickPrefixPreview(Player player, int slot) {
        if (slot == 11) {
            String pending = pendingPrefix.remove(player.getUniqueId());
            if (pending == null) {
                msg(player, "&cThat preview expired. Try /settitle <text> again.");
            } else {
                PlayerData data = data(player);
                data.customPrefix = pending;
                data.savedCustomPrefix = pending;
                data.selectedTag = null;
                save(data);
                applyPrefix(player);
                msg(player, "&aSaved and equipped custom prefix: &r" + pending);
            }
            player.closeInventory();
        } else if (slot == 15) {
            pendingPrefix.remove(player.getUniqueId());
            player.closeInventory();
            msg(player, "&eCustom prefix preview cancelled.");
        }
    }

    private void clickLoreMenu(Player player, int slot) {
        if (slot == 10) {
            msg(player, "&eShort lore: &f/setlore <Name|Lore line 1|Lore line 2>");
            msg(player, "&eLong lore: &f/setlore book &7or &f/setlore draft start <name>");
            msg(player, "&eEditor: &b" + loreEditorUrl);
        } else if (slot == 12) {
            applyExampleLorePreset(player);
        } else if (slot == 14) {
            openLoreHistory(player);
        } else if (slot == 16) {
            openLoreClearConfirm(player);
        } else if (slot == 22) {
            openHub(player);
        }
    }

    private void clickLorePreview(Player player, int slot) {
        if (slot == 11) {
            String pending = pendingLore.remove(player.getUniqueId());
            if (pending == null) {
                msg(player, "&cThat preview expired. Try /setlore again.");
            } else {
                applyLore(player, pending);
            }
            player.closeInventory();
        } else if (slot == 15) {
            pendingLore.remove(player.getUniqueId());
            player.closeInventory();
            msg(player, "&eItem lore preview cancelled.");
        }
    }

    private void clickLoreClearConfirm(Player player, int slot) {
        if (slot == 11) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType().isAir()) {
                msg(player, "&cHold an item first.");
            } else {
                clearLoreFromItem(held);
                msg(player, "&eCleared the custom name and lore from your held item.");
                player.playSound(player.getLocation(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1f, 1f);
            }
            player.closeInventory();
        } else if (slot == 15) {
            player.closeInventory();
            msg(player, "&eItem lore clear cancelled.");
        }
    }


    private void clickKillMessages(Player player, int slot) {
        if (slot == 36) {
            openHub(player);
            return;
        }
        if (slot == 40) {
            clearKillMessage(player);
            player.closeInventory();
            return;
        }
        List<Integer> slots = centeredRowSlots(killMessagePresets.size(), List.of(10, 11, 12, 13, 14, 15, 16));
        for (int i = 0; i < slots.size() && i < killMessagePresets.size(); i++) {
            if (slot != slots.get(i)) continue;
            if (!has(player, Category.KILLMSG, "all")) {
                msg(player, "&cYou do not have access to custom kill messages.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            KillMessagePreset preset = killMessagePresets.get(i);
            data(player).pendingKillMessage = preset.message;
            save(data(player));
            openKillMessagePreview(player, preset.message);
            return;
        }
    }

    private void clickKillMessagePreview(Player player, int slot) {
        PlayerData data = data(player);
        if (slot == 11) {
            if (data.pendingKillMessage == null) {
                msg(player, "&cThat preview expired. Try /setkillmsg <message> again.");
            } else {
                data.killMessage = data.pendingKillMessage;
                data.pendingKillMessage = null;
                save(data);
                msg(player, "&aKill message saved.");
            }
            player.closeInventory();
        } else if (slot == 15) {
            data.pendingKillMessage = null;
            save(data);
            player.closeInventory();
            msg(player, "&eKill message preview cancelled.");
        }
    }

    private void clickTrimPatterns(Player player, int slot, ItemStack clicked) {
        if (slot == 40) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (!isArmor(item)) {
                msg(player, "&cHold the armor piece you want to reset.");
                player.closeInventory();
                return;
            }
            removeTrim(item);
            msg(player, "&eArmor trim removed.");
            player.closeInventory();
            return;
        }
        int index = TRIM_PATTERN_SLOTS.indexOf(slot);
        if (index >= 0 && index < trimPatterns.size()) {
            openTrimMaterials(player, trimPatterns.get(index));
        }
    }

    private void clickTrimMaterials(Player player, int slot, ItemStack clicked, GuiHolder holder) {
        if (slot == 31) {
            openTrimPatterns(player);
            return;
        }
        int index = TRIM_MATERIAL_SLOTS.indexOf(slot);
        if (index >= 0 && index < trimMaterials.size()) {
            openTrimConfirm(player, holder.data, trimMaterials.get(index));
        }
    }

    private void clickTrimConfirm(Player player, int slot, GuiHolder holder) {
        String[] parts = holder.data.split("\\|", 2);
        if (parts.length < 2) return;
        if (slot == 11) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (!isArmor(item)) {
                msg(player, "&cYou must still be holding the armor piece.");
                player.closeInventory();
                return;
            }
            applyTrim(item, parts[0], parts[1]);
            data(player).lastTrim = System.currentTimeMillis();
            save(data(player));
            msg(player, "&a&lSUCCESS! &7Your armor has been trimmed.");
            player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f);
            player.closeInventory();
        } else if (slot == 15) {
            openTrimMaterials(player, parts[0]);
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
        PlayerData data = players.remove(uuid);
        if (data != null) {
            save(data);
        }
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
        snapshotExistingLore(meta);
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
        snapshotExistingLore(meta);
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

    private void rebuildWeaponLore(ItemMeta meta) {
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
        meta.lore(lore.isEmpty() ? null : lore);
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

    private void snapshotExistingLore(ItemMeta meta) {
        if (meta.getPersistentDataContainer().has(customLoreLinesKey, PersistentDataType.STRING)) return;
        List<Component> existing = meta.lore();
        if (existing == null || existing.isEmpty()) return;
        List<String> raw = new ArrayList<>();
        for (Component line : existing) {
            raw.add(LegacyComponentSerializer.legacySection().serialize(line));
        }
        meta.getPersistentDataContainer().set(customLoreLinesKey, PersistentDataType.STRING, String.join("\n", raw));
    }

    private void playKillEffect(String id, Location loc, Player viewer, boolean preview) {
        switch (id) {
            case "pig" -> {
                Pig pig = loc.getWorld().spawn(loc, Pig.class);
                pig.customName(component("&d&lASCENDING"));
                pig.setGravity(false);
                pig.setAI(false);
                killSound(loc, Sound.ENTITY_PIG_DEATH, 2f, 1.2f, preview);
                new BukkitRunnable() {
                    int ticks;
                    @Override public void run() {
                        if (ticks++ >= 25 || pig.isDead()) {
                            firework(loc, Color.FUCHSIA, preview);
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
                Squid squid = loc.getWorld().spawn(loc, Squid.class);
                squid.setGravity(false);
                squid.setAI(false);
                killSound(loc, Sound.ENTITY_SQUID_SQUIRT, 2f, .8f, preview);
                new BukkitRunnable() {
                    int ticks;
                    @Override public void run() {
                        if (ticks++ >= 25 || squid.isDead()) {
                            firework(loc, Color.BLUE, preview);
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
        return player.hasPermission("civ.cosmetic.admin")
            || player.hasPermission("civ.cosmetic." + category.id)
            || player.hasPermission("civ.cosmetic." + category.id + ".all")
            || player.hasPermission("civ.cosmetic." + category.id + "." + normalized)
            || (category == Category.KILL_EFFECTS && player.hasPermission("killeffect." + normalized))
            || (category == Category.TRAILS && player.hasPermission("civ.cosmetic.trail." + normalized))
            || data.hasGrant(category, "all")
            || data.hasGrant(category, normalized);
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
        PlayerData snapshot = data.copy();
        if (shuttingDown || storageExecutor == null || storageExecutor.isShutdown()) {
            try {
                if (includeGrants) storage.save(snapshot);
                else storage.saveState(snapshot);
            } catch (RuntimeException e) {
                failStorage("save cosmetics", snapshot.uuid, e);
            }
            return;
        }
        storageExecutor.execute(() -> {
            try {
                if (includeGrants) storage.save(snapshot);
                else storage.saveState(snapshot);
            } catch (RuntimeException e) {
                failStorage("save cosmetics", snapshot.uuid, e);
            }
        });
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
                PlayerData loaded = storage.load(uuid);
                Bukkit.getScheduler().runTask(this, () -> {
                    loadingPlayers.remove(uuid);
                    players.put(uuid, loaded);
                    List<Consumer<PlayerData>> callbacks = loadCallbacks.remove(uuid);
                    if (callbacks != null) {
                        for (Consumer<PlayerData> next : callbacks) next.accept(loaded);
                    }
                });
            } catch (RuntimeException e) {
                Bukkit.getScheduler().runTask(this, () -> {
                    loadingPlayers.remove(uuid);
                    loadCallbacks.remove(uuid);
                });
                failStorage("load cosmetics", uuid, e);
            }
        });
    }

    private void failStorage(String action, @Nullable UUID uuid, RuntimeException e) {
        if (!storageHealthy) return;
        storageHealthy = false;
        getLogger().warning("Failed to " + action + (uuid == null ? "" : " for " + uuid) + ": " + e.getMessage());
        if (!shuttingDown) {
            Bukkit.getScheduler().runTask(this, () -> getServer().getPluginManager().disablePlugin(this));
        }
    }

    private Inventory gui(Menu menu, int size, String title, @Nullable Object data) {
        return Bukkit.createInventory(new GuiHolder(menu, data == null ? "" : data.toString()), size, color(title));
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
        data.loreCredits += preset.loreCredits;
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
        return raw.replace("\"", "").replace("&k", "").replace("&K", "");
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
            if (args.length == 1) return List.of("grant", "revoke", "clear", "preset", "grantall", "patreon", "help");
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

    public String placeholderPrefix(Player player) {
        PlayerData data = data(player);
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

    private record NamedCosmetic(String id, String display, Material icon) {
    }

    private record KillMessagePreset(String id, String display, Material icon, String message) {
    }

    private record WeaponCrafter(@Nullable UUID uuid, String name) {
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
        HUB, KILL_EFFECTS, TRAILS, TAGS, PREFIX_PREVIEW, LORE_MENU, LORE_PREVIEW, LORE_CLEAR_CONFIRM, KILL_MESSAGES, KILLMSG_PREVIEW, LORE_HISTORY,
        TRIM_PATTERNS, TRIM_MATERIALS, TRIM_CONFIRM, ADMIN_PLAYERS, ADMIN_CATEGORIES, ADMIN_CATEGORY, ADMIN_PRESETS
    }

    private static class GuiHolder implements InventoryHolder {
        private final Menu menu;
        private final String data;

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
            throw new UnsupportedOperationException();
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
                case "selected_tag" -> plugin.data(player).selectedTag == null ? "" : plugin.data(player).selectedTag;
                case "lore_credits" -> Integer.toString(plugin.data(player).loreCredits);
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
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }
    }
}
