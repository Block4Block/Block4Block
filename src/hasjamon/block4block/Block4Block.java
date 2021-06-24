package hasjamon.block4block;

import hasjamon.block4block.command.*;
import hasjamon.block4block.files.ConfigManager;
import hasjamon.block4block.listener.*;
import hasjamon.block4block.utils.utils;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

public class Block4Block extends JavaPlugin{
    public PluginManager pluginManager = getServer().getPluginManager();
    public ConfigManager cfg;
    private static Block4Block instance;
    private List<?> hints;
    private int nextHint = 0;

    @Override
    public void onEnable() {
        instance = this; // Creates instance of the plugin
        cfg = new ConfigManager(); // Initializes config
        registerEvents(); // Registers all the listeners
        setCommandExecutors(); // Registers all the commands
        setupHints(); // Prepares hints and starts broadcasting them
        if(this.getConfig().getBoolean("golems-guard-claims"))
            getServer().getScheduler().scheduleSyncRepeatingTask(this, utils::updateGolemHostility, 0, 20);
        utils.minSecBetweenAlerts = this.getConfig().getInt("seconds-between-intruder-alerts");
    }

    private void setupHints() {
        hints = getConfig().getList("hints");
        long interval = getConfig().getLong("seconds-between-hints");
        boolean hintsEnabled = getConfig().getBoolean("hints-enabled");

        // Shuffle hints, then show a hint every 10 minutes (20 ticks/second * 600 seconds)
        if(hints != null && hintsEnabled){
            Collections.shuffle(hints);
            getServer().getScheduler().scheduleSyncRepeatingTask(this, this::showHint, 0, 20 * interval);
        }
    }

    private void showHint() {
        if(++nextHint >= hints.size())
            nextHint = 0;

        String hint = (String) hints.get(nextHint);
        FileConfiguration hintSettings = this.cfg.getHintSettings();

        for(Player p : Bukkit.getOnlinePlayers()) {
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


        if(dieCmd != null) dieCmd.setExecutor(new DieCommand());
        if(hintsCmd != null) hintsCmd.setExecutor(new HintsCommand(this));
        if(helpCmd != null) helpCmd.setExecutor(new HelpCommand(this));
        if(ignoreCmd != null && unignoreCmd != null){
            IgnoreCommand cmd = new IgnoreCommand(this);
            ignoreCmd.setExecutor(cmd);
            unignoreCmd.setExecutor(cmd);
        }
        if(claimContestCmd != null) claimContestCmd.setExecutor(new ClaimContestCommand(this));
        if(bedCmd != null) bedCmd.setExecutor(new BedCommand(this));
        if(welcomeCmd != null) welcomeCmd.setExecutor(new WelcomeCommand(this));
    }

    private void registerEvents() {
        pluginManager.registerEvents(new BlockBreak(this), this);
        pluginManager.registerEvents(new BookPlaceTake(this), this);
        pluginManager.registerEvents(new LecternBreak(this), this);
        pluginManager.registerEvents(new BookEdit(this), this);
        pluginManager.registerEvents(new BlockPlace(this), this);
        if(this.getConfig().getBoolean("balance-lavacasting"))
            pluginManager.registerEvents(new LavaCasting(), this);
        if(this.getConfig().getBoolean("chickens-lay-spawn-eggs"))
            pluginManager.registerEvents(new EggLay(), this);
        if(this.getConfig().getBoolean("destroy-fishing-rods"))
            pluginManager.registerEvents(new PlayerFish(this), this);
        pluginManager.registerEvents(new EntityDropItem(), this);
        pluginManager.registerEvents(new CreatureSpawn(), this);
        pluginManager.registerEvents(new PlayerChat(this), this);
        pluginManager.registerEvents(new PlayerMove(), this);
        pluginManager.registerEvents(new PlayerQuit(), this);
        pluginManager.registerEvents(new PlayerJoin(this), this);
        pluginManager.registerEvents(new PlayerDeath(), this);
        pluginManager.registerEvents(new PlayerRespawn(), this);
        pluginManager.registerEvents(new ChunkLoad(), this);
        if(this.getConfig().getBoolean("disable-freecam-interactions"))
            pluginManager.registerEvents(new FreecamInteract(), this);
        if(this.getConfig().getBoolean("enable-lava-immunity"))
            pluginManager.registerEvents(new PlayerLavaDamage(this), this);
        pluginManager.registerEvents(new TNTExplode(this), this);
    }

    public static Block4Block getInstance(){
        return instance;
    }
}
