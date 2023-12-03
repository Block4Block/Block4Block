package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class PlayerToggleGlide implements Listener {
    private final Block4Block plugin;
    private static final Map<Player, Long> lastGlide = new HashMap<>();
    private static final int glideCooldown = 2;

    public PlayerToggleGlide(Block4Block plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerToggleGlide(EntityToggleGlideEvent e) {
        if (e.isGliding() && e.getEntityType() == EntityType.PLAYER) {
            Player player = (Player) e.getEntity();
            ItemStack chestItem = player.getInventory().getChestplate();

            if (utils.isPhantomElytra(chestItem)) {
                if (System.nanoTime() - lastGlide.getOrDefault(player, 0L) > glideCooldown * 1e9) {
                    lastGlide.put(player, System.nanoTime());

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.setGliding(false);
                    }, 21);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                    }, glideCooldown * 20);
                } else {
                    e.setCancelled(true);
                }
            }
        }
    }
}
