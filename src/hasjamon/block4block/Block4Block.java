package hasjamon.block4block;

import hasjamon.block4block.Listeners.*;
import hasjamon.block4block.files.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;

public class Block4Block extends JavaPlugin{
    public HashMap<UUID, Boolean> notify = new HashMap<UUID, Boolean>();
    public ConfigManager cfg;
    public static Block4Block instance;

    @Override
    public void onEnable() {
        instance = this; //creates instance of the plugin
        registerEvents(); //registers all the listeners
        loadconfigmanager(); //setup config
    }

    private void registerEvents() {
        getServer().getPluginManager().registerEvents(new BlockBreak(), this);
        getServer().getPluginManager().registerEvents(new LecternPlace(), this);
        getServer().getPluginManager().registerEvents(new LecternBreak(), this);
        getServer().getPluginManager().registerEvents(new LecternInteract(), this);
        getServer().getPluginManager().registerEvents(new BlockPlace(), this);
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
