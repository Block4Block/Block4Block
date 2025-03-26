package hasjamon.block4block;

import com.comphenix.protocol.utility.MinecraftReflection;
import hasjamon.block4block.command.*;
import hasjamon.block4block.files.ConfigManager;
import hasjamon.block4block.listener.*;
import hasjamon.block4block.utils.utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.BlockFace;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Block4Block extends JavaPlugin {
    public PluginManager pluginManager = getServer().getPluginManager();
    public ConfigManager cfg;
    private static Block4Block instance;
    private ClaimVisual claimVisual;
    private List<?> hints;
    private int nextHint = 0;

    public static Block4Block getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        this.getConfig().options().copyDefaults(true); // Makes config work properly
        instance = this; // Creates instance of the plugin
        checkReflectionAvailability();
        cfg = new ConfigManager(); // Initializes config
        populateKnownPlayers();
        populateConfigConstants();
        registerEvents(); // Registers all the listeners
        setCommandExecutors(); // Registers all the commands
        setupHints(); // Prepares hints and starts broadcasting them
        // Initialize and register ClaimVisual
        claimVisual = new ClaimVisual(this);
        pluginManager.registerEvents(claimVisual, this);

        // Register ClaimVisualCommand
        if (getCommand("claimvisual") != null) {
            getCommand("claimvisual").setExecutor(new ClaimVisualCommand(claimVisual.getVisualEnabledPlayers()));
        }
        if (this.getConfig().getBoolean("golems-guard-claims"))
            getServer().getScheduler().scheduleSyncRepeatingTask(this, utils::updateGolemHostility, 0, 20);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, utils::updateCurrentTick, 0, 1);
        utils.minSecBetweenAlerts = this.getConfig().getInt("seconds-between-intruder-alerts");
        utils.claimWidth = this.getConfig().getInt("claim-width");
        utils.lavaFlowMaxY = this.getConfig().getInt("lava-flow-max-y");
        utils.gracePeriod = this.getConfig().getInt("b4b-grace-period");
        if (this.getConfig().getBoolean("enable-claim-maps"))
            addMapRenderers();
    }

    private void checkReflectionAvailability() {
        try {
            MinecraftReflection.getCraftPlayerClass().getDeclaredMethod("getProfile");
        } catch (NoClassDefFoundError | NoSuchMethodException e) {
            utils.canUseReflection = false;
            getServer().getConsoleSender().sendMessage(
                    ChatColor.YELLOW + "Reflection is unavailable; some features have been disabled.");
        }
    }

    private void setupHints() {
        hints = getConfig().getList("hints");
        long interval = getConfig().getLong("seconds-between-hints");
        boolean hintsEnabled = getConfig().getBoolean("hints-enabled");

        // Shuffle hints, then show a hint every 10 minutes (20 ticks/second * 600 seconds)
        if (hints != null && hintsEnabled) {
            Collections.shuffle(hints);
            getServer().getScheduler().scheduleSyncRepeatingTask(this, this::showHint, 0, 20 * interval);
        }
    }

    private void showHint() {
        if (++nextHint >= hints.size())
            nextHint = 0;

        String hint = (String) hints.get(nextHint);
        FileConfiguration hintSettings = this.cfg.getHintSettings();

        for (Player p : Bukkit.getOnlinePlayers()) {
            String pUUID = p.getUniqueId().toString();
            String pSettings = hintSettings.getString(pUUID);

            if (pSettings == null || pSettings.equals("on"))
                p.sendMessage(utils.chat(hint));
        }
    }

    private void setCommandExecutors() {
        PluginCommand dieCmd = this.getCommand("die");
        PluginCommand hintsCmd = this.getCommand("hints");
        PluginCommand helpCmd = this.getCommand("b4bhelp");
        PluginCommand ignoreCmd = this.getCommand("ignore");
        PluginCommand unignoreCmd = this.getCommand("unignore");
        PluginCommand claimContestCmd = this.getCommand("claimcontest");
        PluginCommand bedCmd = this.getCommand("bed");
        PluginCommand welcomeCmd = this.getCommand("welcome");
        PluginCommand claimLocCmd = this.getCommand("claimloc");
        PluginCommand claimFixCmd = this.getCommand("claimfix");
        PluginCommand chickenBonusCmd = this.getCommand("chickenbonus");
        PluginCommand coordsCmd = this.getCommand("coords");
        PluginCommand claimVisualCmd = this.getCommand("claimvisual");

        ClaimVisual claimVisual = new ClaimVisual(this);

        if (getCommand("claimvisual") != null) {
            getCommand("claimvisual").setExecutor(new ClaimVisualCommand(claimVisual.getVisualEnabledPlayers()));
        }


        if (dieCmd != null) dieCmd.setExecutor(new DieCommand());
        if (hintsCmd != null) hintsCmd.setExecutor(new HintsCommand(this));
        if (helpCmd != null) helpCmd.setExecutor(new HelpCommand(this));
        if (ignoreCmd != null && unignoreCmd != null) {
            IgnoreCommand cmd = new IgnoreCommand(this);
            ignoreCmd.setExecutor(cmd);
            unignoreCmd.setExecutor(cmd);
        }
        if (claimContestCmd != null) claimContestCmd.setExecutor(new ClaimContestCommand(this));
        if (bedCmd != null) bedCmd.setExecutor(new BedCommand(this));
        if (welcomeCmd != null) welcomeCmd.setExecutor(new WelcomeCommand());
        if (claimLocCmd != null) claimLocCmd.setExecutor(new ClaimLocCommand(this));
        if (claimFixCmd != null) claimFixCmd.setExecutor(new ClaimFixCommand(this));
        if (chickenBonusCmd != null) chickenBonusCmd.setExecutor(new ChickenBonusCommand(this));
        if (coordsCmd != null) coordsCmd.setExecutor(new CoordsCommand(this));
    }

    private void registerEvents() {
        pluginManager.registerEvents(new BlockBreak(this), this);
        pluginManager.registerEvents(new BookPlaceTake(this), this);
        pluginManager.registerEvents(new LecternBreak(this), this);
        pluginManager.registerEvents(new BookEdit(this), this);
        pluginManager.registerEvents(new BlockPlace(this), this);
        if (this.getConfig().getBoolean("balance-lavacasting"))
            pluginManager.registerEvents(new LavaCasting(), this);
        if (this.getConfig().getBoolean("chickens-lay-spawn-eggs"))
            pluginManager.registerEvents(new EggLay(this), this);
        if (this.getConfig().getBoolean("destroy-fishing-rods"))
            pluginManager.registerEvents(new PlayerFish(this), this);
        pluginManager.registerEvents(new EntityDropItem(), this);
        pluginManager.registerEvents(new CreatureSpawn(), this);
        pluginManager.registerEvents(new PlayerChat(this), this);
        pluginManager.registerEvents(new PlayerMove(this), this);
        pluginManager.registerEvents(new PlayerQuit(), this);
        pluginManager.registerEvents(new PlayerJoin(this), this);
        pluginManager.registerEvents(new PlayerDeath(this), this);
        pluginManager.registerEvents(new PlayerRespawn(), this);
        pluginManager.registerEvents(new ChunkLoad(), this);
        if (this.getConfig().getBoolean("disable-freecam-interactions"))
            pluginManager.registerEvents(new FreecamInteract(this), this);
        if (this.getConfig().getBoolean("enable-lava-immunity"))
            pluginManager.registerEvents(new PlayerLavaDamage(this), this);
        pluginManager.registerEvents(new Explode(this), this);
        if (this.getConfig().getBoolean("enable-disguises"))
            pluginManager.registerEvents(new EquipPlayerHead(this), this);
        if (this.getConfig().getBoolean("enable-claim-maps"))
            pluginManager.registerEvents(new MapUseOnLectern(this), this);
        pluginManager.registerEvents(new MapCraft(), this);
        if (this.getConfig().getBoolean("enable-path-grace-period"))
            pluginManager.registerEvents(new ShovelUse(), this);
        pluginManager.registerEvents(new EntityChangeBlock(this), this);
        pluginManager.registerEvents(new CraftItem(), this);
        pluginManager.registerEvents(new PlayerHarvestBlock(), this);
        pluginManager.registerEvents(new BlockFertilize(), this);
        pluginManager.registerEvents(new BlockSpread(), this);
        pluginManager.registerEvents(new PlayerToggleGlide(this), this);
        pluginManager.registerEvents(new PlayerChangedWorld(), this);
        pluginManager.registerEvents(new ExplosionPrime(), this);
    }

    private void addMapRenderers() {
        FileConfiguration claimMaps = cfg.getClaimMaps();
        Set<String> mapIDs = claimMaps.getKeys(false);

        for (String id : mapIDs) {
            MapView view = Bukkit.getMap(Integer.parseInt(id));
            String uuid = claimMaps.getString(id);

            if (view != null && uuid != null && view.getRenderers().size() == 1) {
                OfflinePlayer mapCreator = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
                view.addRenderer(utils.createClaimRenderer(mapCreator));
                view.addRenderer(utils.createIntruderRenderer(mapCreator));
            } else {
                claimMaps.set(id, null);
            }
        }

        cfg.saveClaimMaps();
    }

    // TODO: @bahm cache more config constants
    private void populateConfigConstants() {
        populateNonProtectiveBlockTypes();
        populateProtectiveBlockFaces();
    }

    private void populateKnownPlayers() {
        for (OfflinePlayer p : Bukkit.getOfflinePlayers())
            if (p != null && p.getName() != null)
                utils.knownPlayers.add(p.getName().toLowerCase());
    }

    private void populateNonProtectiveBlockTypes() {
        List<Material> blockTypes = this.getConfig()
                .getStringList("claim-protection.nonprotective-blocks")
                .stream()
                .map(Material::valueOf)
                .toList();
        utils.nonProtectiveBlockTypes.addAll(blockTypes);
    }

    private void populateProtectiveBlockFaces() {
        List<BlockFace> blockFaces = this.getConfig()
                .getStringList("claim-protection.protective-block-faces")
                .stream()
                .map(BlockFace::valueOf)
                .toList();
        utils.protectiveBlockFaces.addAll(blockFaces);
    }
}
