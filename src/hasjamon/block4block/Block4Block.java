package hasjamon.block4block;

import hasjamon.block4block.command.*;
import hasjamon.block4block.listener.*;
import hasjamon.block4block.files.ConfigManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;

public class Block4Block extends JavaPlugin{
    public HashMap<UUID, Boolean> notify = new HashMap<>();
    public PluginManager pluginManager = getServer().getPluginManager();
    public ConfigManager cfg;
    public static Block4Block instance;

    @Override
    public void onEnable() {
        instance = this; // Creates instance of the plugin
        registerEvents(); // Registers all the listeners
        setCommandExecutors(); // Registers all the commands
        loadconfigmanager(); // Setup config
    }

    private void setCommandExecutors() {
        PluginCommand cmd = this.getCommand("die");

        if(cmd != null)
            cmd.setExecutor(new DieCommand());
    }

    private void registerEvents() {
        pluginManager.registerEvents(new BlockBreak(), this);
        pluginManager.registerEvents(new LecternPlace(), this);
        pluginManager.registerEvents(new LecternBreak(), this);
        pluginManager.registerEvents(new LecternInteract(), this);
        pluginManager.registerEvents(new BlockPlace(), this);
        pluginManager.registerEvents(new LavaCasting(), this);
    }

    public void loadconfigmanager() {
        this.cfg = new ConfigManager();
        getConfig().options().copyDefaults(true);
        saveConfig();
        this.cfg.setupclaimdata();
        this.cfg.saveclaimdata();
    }

    public static Block4Block getInstance(){
        return instance;
    }

    @Override
    public void onDisable() {

    }
}
