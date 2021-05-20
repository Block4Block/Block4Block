package hasjamon.block4block;

import hasjamon.block4block.command.*;
import hasjamon.block4block.listener.*;
import hasjamon.block4block.files.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;
import java.util.Random;

public class Block4Block extends JavaPlugin{
    public PluginManager pluginManager = getServer().getPluginManager();
    public ConfigManager cfg;
    private static Block4Block instance;

    @Override
    public void onEnable() {
        instance = this; // Creates instance of the plugin
        cfg = new ConfigManager(); // Initializes config
        registerEvents(); // Registers all the listeners
        setCommandExecutors(); // Registers all the commands

        // Show a hint every 5 minutes (20 ticks/second * 300 seconds)
        getServer().getScheduler().scheduleSyncRepeatingTask(this, this::showHint, 0, 20 * 300);
    }

    private void showHint() {
        List<?> hints = getConfig().getList("hints");

        if(hints != null) {
            Random rand = new Random();
            String hint = (String) hints.get(rand.nextInt(hints.size()));

            Bukkit.broadcastMessage(hint);
        }
    }

    private void setCommandExecutors() {
        PluginCommand cmd = this.getCommand("die");

        if(cmd != null)
            cmd.setExecutor(new DieCommand());
    }

    private void registerEvents() {
        pluginManager.registerEvents(new BlockBreak(), this);
        pluginManager.registerEvents(new BookPlaceTake(), this);
        pluginManager.registerEvents(new LecternBreak(), this);
        pluginManager.registerEvents(new EditBook(), this);
        pluginManager.registerEvents(new BlockPlace(), this);
        pluginManager.registerEvents(new LavaCasting(), this);
    }

    public static Block4Block getInstance(){
        return instance;
    }
}
