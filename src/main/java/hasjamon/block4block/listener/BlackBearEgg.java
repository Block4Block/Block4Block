package hasjamon.block4block.listener;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Egg;
import org.bukkit.entity.PolarBear;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

public class BlackBearEgg implements Listener {
    private final NamespacedKey eggKey;

    public BlackBearEgg(NamespacedKey eggKey) {
        this.eggKey = eggKey;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getItem() == null || event.getItem().getType() != Material.COAL) return;

        ItemStack item = event.getItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(eggKey, PersistentDataType.BYTE)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // Launch the egg and attach the custom data
        Egg egg = player.launchProjectile(Egg.class);
        egg.getPersistentDataContainer().set(eggKey, PersistentDataType.BYTE, (byte) 1);

        // Play egg throw sound
        player.playSound(player.getLocation(), Sound.ENTITY_EGG_THROW, 1.0f, 1.0f);

        // Remove one item from player's hand
        ItemStack handItem = player.getInventory().getItem(event.getHand());
        if (handItem != null) {
            if (handItem.getAmount() > 1) {
                handItem.setAmount(handItem.getAmount() - 1);
            } else {
                player.getInventory().setItem(event.getHand(), null);
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg)) return;

        Egg egg = (Egg) event.getEntity();
        if (!egg.getPersistentDataContainer().has(eggKey, PersistentDataType.BYTE)) return;

        ProjectileSource source = egg.getShooter();
        if (source instanceof Player) {
            PolarBear bear = (PolarBear) egg.getWorld().spawnEntity(egg.getLocation(), EntityType.POLAR_BEAR);
            bear.setCustomName(ChatColor.WHITE + "Albino Black Bear");
            bear.setCustomNameVisible(true);
        }
    }
}
