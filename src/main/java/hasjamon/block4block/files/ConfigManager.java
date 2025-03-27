package hasjamon.block4block.files;

import hasjamon.block4block.Block4Block;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.BlockFace;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

public class ConfigManager {
    private final Block4Block plugin = Block4Block.getPlugin(Block4Block.class);
    private final ConsoleCommandSender consoleSender = Bukkit.getServer().getConsoleSender();
    private final File claimDataFile = new File(this.plugin.getDataFolder(), "claimdata.yml");
    private final FileConfiguration claimDataCfg = YamlConfiguration.loadConfiguration(claimDataFile);
    private final File hintSettingsFile = new File(this.plugin.getDataFolder(), "hintsettings.yml");
    private final FileConfiguration hintSettingsCfg = YamlConfiguration.loadConfiguration(hintSettingsFile);
    private final File ignoreListsFile = new File(this.plugin.getDataFolder(), "ignorelists.yml");
    private final FileConfiguration ignoreListsCfg = YamlConfiguration.loadConfiguration(ignoreListsFile);
    private final File claimContestFile = new File(this.plugin.getDataFolder(), "claimcontest.yml");
    private final FileConfiguration claimContestCfg = YamlConfiguration.loadConfiguration(claimContestFile);
    private final File bedCommandUsageFile = new File(this.plugin.getDataFolder(), "bedcommandusage.yml");
    private final FileConfiguration bedCommandUsageCfg = YamlConfiguration.loadConfiguration(bedCommandUsageFile);
    private final File masterBooksFile = new File(this.plugin.getDataFolder(), "masterbooks.yml");
    private final FileConfiguration masterBooksCfg = YamlConfiguration.loadConfiguration(masterBooksFile);
    private final File offlineClaimNotificationsFile = new File(this.plugin.getDataFolder(), "offlineclaimnotifications.yml");
    private final FileConfiguration offlineClaimNotificationsCfg = YamlConfiguration.loadConfiguration(offlineClaimNotificationsFile);
    private final File playerTexturesFile = new File(this.plugin.getDataFolder(), "playertextures.yml");
    private final FileConfiguration playerTexturesCfg = YamlConfiguration.loadConfiguration(playerTexturesFile);
    private final File claimTakeoversFile = new File(this.plugin.getDataFolder(), "claimtakeovers.yml");
    private final FileConfiguration claimTakeoversCfg = YamlConfiguration.loadConfiguration(claimTakeoversFile);
    private final File claimMapsFile = new File(this.plugin.getDataFolder(), "claimmaps.yml");
    private final FileConfiguration claimMapsCfg = YamlConfiguration.loadConfiguration(claimMapsFile);
    private final File coordsSettingsFile = new File(this.plugin.getDataFolder(), "coordssettings.yml");
    private final FileConfiguration coordsSettingsCfg = YamlConfiguration.loadConfiguration(coordsSettingsFile);

    public ConfigManager(){
        if (!this.plugin.getDataFolder().exists())
            if(!this.plugin.getDataFolder().mkdir())
                consoleSender.sendMessage("Failed to create data folder.");

        saveConfigAsDefault();
        saveClaimData();
        saveHintSettings();
        saveIgnoreLists();
        saveClaimContest();
        saveBedCommandUsage();
        saveMasterBooks();
        saveOfflineClaimNotifications();
        savePlayerTextures();
        saveClaimTakeovers();
        saveClaimMaps();
        saveCoordsSettings();
    }

    // Saves the default config; always overwrites. This file is purely for ease of reference; it is never loaded.
    private void saveConfigAsDefault() {
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

    public boolean backupClaimData() {
        String backupName = "claimdata-backup-" + System.currentTimeMillis() + ".yml";
        File backupFile = new File(this.plugin.getDataFolder(), backupName);

        try {
            this.claimDataCfg.save(backupFile);
            consoleSender.sendMessage(ChatColor.AQUA + "Claim data has been backed up to " + backupName);
            return true;
        } catch (IOException e) {
            consoleSender.sendMessage(ChatColor.RED + "Failed to back up claim data to " + backupName);
            return false;
        }
    }

    public FileConfiguration getHintSettings() {
        return this.hintSettingsCfg;
    }

    public void saveHintSettings() {
        try {
            this.hintSettingsCfg.save(this.hintSettingsFile);
            consoleSender.sendMessage(ChatColor.AQUA + "Hint settings have been saved to hintsettings.yml");
        } catch (IOException e) {
            consoleSender.sendMessage(ChatColor.RED + "Failed to save hint settings to hintsettings.yml");
        }
    }

    public FileConfiguration getIgnoreLists() {
        return this.ignoreListsCfg;
    }

    public void saveIgnoreLists() {
        try {
            this.ignoreListsCfg.save(this.ignoreListsFile);
            consoleSender.sendMessage(ChatColor.AQUA + "Ignore lists have been saved to ignorelists.yml");
        } catch (IOException e) {
            consoleSender.sendMessage(ChatColor.RED + "Failed to save ignore lists to ignorelists.yml");
        }
    }

    public FileConfiguration getClaimContest() {
        return this.claimContestCfg;
    }

    public void saveClaimContest() {
        try {
            this.claimContestCfg.save(this.claimContestFile);
            consoleSender.sendMessage(ChatColor.AQUA + "Claim contest has been saved to claimcontest.yml");
        } catch (IOException e) {
            consoleSender.sendMessage(ChatColor.RED + "Failed to save claim contest to claimcontest.yml");
        }
    }

    public void clearClaimContest() {
        this.claimContestCfg.set("data", null);
        this.saveClaimContest();
    }

    public FileConfiguration getBedCommandUsage() {
        return this.bedCommandUsageCfg;
    }

    public void saveBedCommandUsage() {
        try {
            this.bedCommandUsageCfg.save(this.bedCommandUsageFile);
            consoleSender.sendMessage(ChatColor.AQUA + "Bed command usage has been saved to bedcommandusage.yml");
        } catch (IOException e) {
            consoleSender.sendMessage(ChatColor.RED + "Failed to save bed command usage to bedcommandusage.yml");
        }
    }

    public FileConfiguration getMasterBooks() {
        return this.masterBooksCfg;
    }

    public void saveMasterBooks() {
        try {
            this.masterBooksCfg.save(this.masterBooksFile);
            consoleSender.sendMessage(ChatColor.AQUA + "Master books have been saved to masterbooks.yml");
        } catch (IOException e) {
            consoleSender.sendMessage(ChatColor.RED + "Failed to save master books to masterbooks.yml");
        }
    }

    public boolean backupMasterBooks() {
        String backupName = "masterbooks-backup-" + System.currentTimeMillis() + ".yml";
        File backupFile = new File(this.plugin.getDataFolder(), backupName);

        try {
            this.masterBooksCfg.save(backupFile);
            consoleSender.sendMessage(ChatColor.AQUA + "Master books have been backed up to " + backupName);
            return true;
        } catch (IOException e) {
            consoleSender.sendMessage(ChatColor.RED + "Failed to back up master books to " + backupName);
            return false;
        }
    }

    public FileConfiguration getOfflineClaimNotifications() {
        return this.offlineClaimNotificationsCfg;
    }

    public void saveOfflineClaimNotifications() {
        try {
            this.offlineClaimNotificationsCfg.save(this.offlineClaimNotificationsFile);
            consoleSender.sendMessage(ChatColor.AQUA + "Offline claim notifications have been saved to offlineclaimnotifications.yml");
        } catch (IOException e) {
            consoleSender.sendMessage(ChatColor.RED + "Failed to save offline claim notifications to offlineclaimnotifications.yml");
        }
    }

    public boolean backupOfflineClaimNotifications() {
        String backupName = "offlineclaimnotifications-backup-" + System.currentTimeMillis() + ".yml";
        File backupFile = new File(this.plugin.getDataFolder(), backupName);

        try {
            this.offlineClaimNotificationsCfg.save(backupFile);
            consoleSender.sendMessage(ChatColor.AQUA + "Offline claim notifications have been backed up to " + backupName);
            return true;
        } catch (IOException e) {
            consoleSender.sendMessage(ChatColor.RED + "Failed to back up offline claim notifications to " + backupName);
            return false;
        }
    }

    public FileConfiguration getPlayerTextures() {
        return this.playerTexturesCfg;
    }

    public void savePlayerTextures() {
        try {
            this.playerTexturesCfg.save(this.playerTexturesFile);
            consoleSender.sendMessage(ChatColor.AQUA + "Player textures have been saved to playertextures.yml");
        } catch (IOException e) {
            consoleSender.sendMessage(ChatColor.RED + "Failed to save player textures to playertextures.yml");
        }
    }

    public FileConfiguration getClaimTakeovers() {
        return this.claimTakeoversCfg;
    }

    public void saveClaimTakeovers() {
        try {
            this.claimTakeoversCfg.save(this.claimTakeoversFile);
            consoleSender.sendMessage(ChatColor.AQUA + "Claim takeovers have been saved to claimtakeovers.yml");
        } catch (IOException e) {
            consoleSender.sendMessage(ChatColor.RED + "Failed to save claim takeovers to claimtakeovers.yml");
        }
    }

    public FileConfiguration getClaimMaps() {
        return this.claimMapsCfg;
    }

    public void saveClaimMaps() {
        try {
            this.claimMapsCfg.save(this.claimMapsFile);
            consoleSender.sendMessage(ChatColor.AQUA + "Claim maps have been saved to claimmaps.yml");
        } catch (IOException e) {
            consoleSender.sendMessage(ChatColor.RED + "Failed to save claim maps to claimmaps.yml");
        }
    }

    public FileConfiguration getCoordsSettings() {
        return this.coordsSettingsCfg;
    }

    public void saveCoordsSettings() {
        try {
            this.coordsSettingsCfg.save(this.coordsSettingsFile);
            consoleSender.sendMessage(ChatColor.AQUA + "Coords settings have been saved to coordssettings.yml");
        } catch (IOException e) {
            consoleSender.sendMessage(ChatColor.RED + "Failed to save coords settings to coordssettings.yml");
        }
    }

    public Set<BlockFace> getProtectiveBlockFaces() {
        Set<BlockFace> protectiveBlockFaces = new HashSet<>();
        FileConfiguration config = plugin.getConfig();  // Assuming plugin.cfg is your main config

        // Check if the 'protective-block-faces' is set in the config
        if (config.contains("claim-protection.protective-block-faces")) {
            // Loop through the list in the config and add the BlockFaces
            for (String face : config.getStringList("claim-protection.protective-block-faces")) {
                try {
                    // Convert the string to BlockFace and add to the set
                    BlockFace blockFace = BlockFace.valueOf(face.toUpperCase());
                    protectiveBlockFaces.add(blockFace);
                } catch (IllegalArgumentException e) {
                    // Handle invalid direction in the config, could log or just ignore
                    plugin.getLogger().warning("Invalid block face in config: " + face);
                }
            }
        }
        return protectiveBlockFaces;
    }
}
