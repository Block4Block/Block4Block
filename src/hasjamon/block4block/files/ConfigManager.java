package hasjamon.block4block.files;

import hasjamon.block4block.Block4Block;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;

public class ConfigManager {
    public static ConfigManager conf;
    private Block4Block plugin = (Block4Block) Block4Block.getPlugin(Block4Block.class);
    public FileConfiguration cfg;
    public File configfile;
    public FileConfiguration claimdatacfg;
    public File claimdatafile;

    public void setupConfig() {
        if (!this.plugin.getDataFolder().exists())
            this.plugin.getDataFolder().mkdir();
        this.configfile = new File(this.plugin.getDataFolder(), "config.yml");
        if (!this.configfile.exists()) {
            try {
                this.configfile.createNewFile();
            } catch (IOException e) {
                Bukkit.getServer().getConsoleSender().sendMessage("not create config.yml file.");
            }
        }
        this.cfg = (FileConfiguration) YamlConfiguration.loadConfiguration(this.configfile);
        Bukkit.getServer().getConsoleSender().sendMessage("created!");
    }

    public FileConfiguration getConfig() {
        return this.cfg;
    }

    public void saveConfig() {
        this.plugin.saveConfig();
        Bukkit.getServer().getConsoleSender().sendMessage("has been saved!");
    }

    public void reloadConfig() {
        this.plugin.saveConfig();
        this.cfg = (FileConfiguration)YamlConfiguration.loadConfiguration(this.configfile);
        Bukkit.getServer().getConsoleSender().sendMessage("has been reloaded!");
    }

    public void setupclaimdata() {
        if (!this.plugin.getDataFolder().exists())
            this.plugin.getDataFolder().mkdir();
        this.claimdatafile = new File(this.plugin.getDataFolder(), "claimdata.yml");
        if (!this.claimdatafile.exists())
            try {
                this.claimdatafile.createNewFile();
                Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.GREEN + "The claimdata.yml file has been created");
            } catch (IOException e) {
                Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.RED + "Could not create the claimdata.yml file");
            }
        this.claimdatacfg = (FileConfiguration)YamlConfiguration.loadConfiguration(this.claimdatafile);
    }

    public FileConfiguration getclaimdata() {
        return this.claimdatacfg;
    }

    public void saveclaimdata() {
        try {
            this.claimdatacfg.save(this.claimdatafile);
            Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "The claimdata.yml file has been saved");
        } catch (IOException e) {
            Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.RED + "Could not save the claimdata.yml file");
        }
    }

    public void reloadclaimdata() {
        saveclaimdata();
        this.claimdatacfg = (FileConfiguration)YamlConfiguration.loadConfiguration(this.claimdatafile);
        Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.BLUE + "The claimdata.yml file has been reload");
    }
}
