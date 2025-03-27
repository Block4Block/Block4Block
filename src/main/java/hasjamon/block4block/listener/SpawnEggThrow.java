package hasjamon.block4block.listener;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

public class SpawnEggThrow implements Listener {
    private final NamespacedKey eggKey;

    public SpawnEggThrow(NamespacedKey eggKey) {
        this.eggKey = eggKey;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getItem() == null || !event.getItem().getType().name().endsWith("_SPAWN_EGG")) return;

        ItemStack item = event.getItem();
        Material spawnEggType = item.getType();

        event.setCancelled(true);
        Player player = event.getPlayer();

        // Launch the egg as a projectile
        Egg egg = player.launchProjectile(Egg.class);
        egg.getPersistentDataContainer().set(eggKey, PersistentDataType.STRING, spawnEggType.name());

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
        if (!egg.getPersistentDataContainer().has(eggKey, PersistentDataType.STRING)) return;

        String spawnEggName = egg.getPersistentDataContainer().get(eggKey, PersistentDataType.STRING);
        if (spawnEggName == null) return;

        EntityType entityType = getEntityTypeFromEgg(spawnEggName);
        if (entityType != null) {
            egg.getWorld().spawnEntity(egg.getLocation(), entityType);
        }
    }

    // Converts spawn egg name to EntityType
    private EntityType getEntityTypeFromEgg(String eggName) {
        try {
            return EntityType.valueOf(eggName.replace("_SPAWN_EGG", ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
