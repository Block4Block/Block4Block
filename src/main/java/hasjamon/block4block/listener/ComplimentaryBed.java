package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class ComplimentaryBed implements Listener {
    private final Block4Block plugin;

    public ComplimentaryBed(Block4Block plugin) {
        this.plugin = plugin;
    }

    private void giveComplimentaryBed(Player player) {
        String pID = player.getUniqueId().toString();
        FileConfiguration bedUsage = plugin.cfg.getComplimentaryBedUsage();

        // 🛠 Migrate old format if necessary
        Object existing = bedUsage.get(pID);
        if (existing instanceof Number) {
            long legacyNextAvailable = ((Number) existing).longValue();
            bedUsage.set(pID, null); // Remove old flat value
            bedUsage.set(pID + ".nextAvailable", legacyNextAvailable);
            bedUsage.set(pID + ".bedCount", 0); // Assume 0 if unknown
            bedUsage.set(pID + ".name", player.getName());
            plugin.getLogger().info("Migrated old complimentary bed data for " + player.getName());
        }

        long now = System.currentTimeMillis();
        long nextAvailable = bedUsage.getLong(pID + ".nextAvailable", 0);

        int limit = plugin.getConfig().getInt("complimentary-bed.limit", 1);
        Object intervalConfig = plugin.getConfig().get("complimentary-bed.interval");
        long cooldownDuration = getCooldownDuration(intervalConfig);

        int bedCount = bedUsage.getInt(pID + ".bedCount", 0) + 1;
        int bedsInInventory = player.getInventory().all(Material.WHITE_BED).size();

        plugin.getLogger().info("Bed count: " + bedCount + " | Next available: " + nextAvailable + " | Current time: " + now);

        if (now < nextAvailable || bedCount > limit) {
            double diff = (nextAvailable - now) / (60.0 * 60 * 1000);
            DecimalFormat decimals = new DecimalFormat("#.#");
            player.sendMessage(ChatColor.YELLOW + "Next Complimentary Bed available in "
                    + decimals.format(diff) + " hours.");
            return;
        }

        ItemStack bed = new ItemStack(Material.WHITE_BED, 1);
        ItemMeta meta = bed.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Complimentary Bed");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Received " + bedCount + " times");
        meta.setLore(lore);
        bed.setItemMeta(meta);

        player.getInventory().addItem(bed);
        player.sendMessage(ChatColor.GRAY + "Complimentary Bed #" + bedCount + " collected.");

        bedUsage.set(pID + ".name", player.getName());
        bedUsage.set(pID + ".nextAvailable", now + cooldownDuration);
        bedUsage.set(pID + ".bedCount", bedCount);
        plugin.cfg.saveComplimentaryBedUsage();
    }

    private long getCooldownDuration(Object intervalConfig) {
        if (intervalConfig instanceof Number) {
            long minutes = ((Number) intervalConfig).longValue();
            return minutes * 60 * 1000;
        } else if (intervalConfig instanceof String) {
            String interval = ((String) intervalConfig).toLowerCase();
            switch (interval) {
                case "hourly":
                    return 60 * 60 * 1000;
                case "daily":
                    return 24 * 60 * 60 * 1000;
                case "weekly":
                    return 7 * 24 * 60 * 60 * 1000;
                case "monthly":
                    return 30L * 24 * 60 * 60 * 1000;
                default:
                    plugin.getLogger().warning("Invalid interval '" + interval + "'. Defaulting to 1 day.");
                    return 24 * 60 * 60 * 1000;
            }
        }
        plugin.getLogger().warning("Invalid interval format in config. Defaulting to 1 day.");
        return 24 * 60 * 60 * 1000;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getLogger().info("Respawn event triggered for: " + player.getName());

        // Only check if the player has no bed set
        if (player.getBedSpawnLocation() == null) {
            String pID = player.getUniqueId().toString();
            FileConfiguration bedUsage = plugin.cfg.getComplimentaryBedUsage();
            long now = System.currentTimeMillis();
            long nextAvailable = bedUsage.getLong(pID + ".nextAvailable", 0);

            int limit = plugin.getConfig().getInt("complimentary-bed.limit", 1);
            int count = player.getInventory().all(Material.WHITE_BED).size() + 1;

            plugin.getLogger().info("Checking conditions for Complimentary Bed: count=" + count
                    + ", nextAvailable=" + nextAvailable + ", now=" + now);

            if (now < nextAvailable || count > limit) {
                double diff = (nextAvailable - now) / (60.0 * 60 * 1000);
                DecimalFormat decimals = new DecimalFormat("#.#");
                player.sendMessage(ChatColor.GRAY + "You did not receive a Complimentary Bed. Next (Bed #"
                        + count + ") available in " + decimals.format(diff) + " hours.");
            } else {
                giveComplimentaryBed(player);
            }
        }
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String pID = player.getUniqueId().toString();
        FileConfiguration bedUsage = plugin.cfg.getComplimentaryBedUsage();
        long nextAvailable = bedUsage.getLong(pID + ".nextAvailable", 0);

        if (!player.hasPlayedBefore()) {
            plugin.getLogger().info("Player joining for the first time: " + player.getName());
            if (player.getBedSpawnLocation() == null) {
                giveComplimentaryBed(player);
            }
        }
    }
}
