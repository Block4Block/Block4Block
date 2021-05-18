package hasjamon.block4block.files;

import hasjamon.block4block.Block4Block;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ConfigManager {
    private final Block4Block plugin = Block4Block.getPlugin(Block4Block.class);
    private final ConsoleCommandSender consoleSender = Bukkit.getServer().getConsoleSender();
    private final File claimDataFile = new File(this.plugin.getDataFolder(), "claimdata.yml");
    private final FileConfiguration claimDataCfg = YamlConfiguration.loadConfiguration(claimDataFile);

    public ConfigManager(){
        if (!this.plugin.getDataFolder().exists())
            if(!this.plugin.getDataFolder().mkdir())
                consoleSender.sendMessage("Failed to create data folder.");

        saveDefaultConfig();
        saveClaimData();
    }

    // Saves the default config; always overwrites. This file is purely for ease of reference; it is never loaded.
    private void saveDefaultConfig() {
        File defaultFile = new File(this.plugin.getDataFolder(), "default.yml");
        InputStream cfgStream = plugin.getResource("config.yml");

        if(cfgStream != null) {
            try {
                Files.copy(cfgStream, defaultFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                consoleSender.sendMessage(ChatColor.RED + "Failed to save default.yml");
            }
        }
    }

    public FileConfiguration getClaimData() {
        return this.claimDataCfg;
    }

    public void saveClaimData() {
        try {
            this.claimDataCfg.save(this.claimDataFile);
            consoleSender.sendMessage(ChatColor.AQUA + "Claim data has been saved to claimdata.yml");
        } catch (IOException e) {
            consoleSender.sendMessage(ChatColor.RED + "Failed to save claim data to claimdata.yml");
        }
    }
}
