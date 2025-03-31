package hasjamon.block4block.listener;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;

public class SpawnEggThrow implements Listener {
    private final NamespacedKey eggKey;
    private final Random random = new Random(); // Create an instance of Random

    public SpawnEggThrow(NamespacedKey eggKey) {
        this.eggKey = eggKey;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getItem() == null || !event.getItem().getType().name().endsWith("_SPAWN_EGG")) return;

        ItemStack item = event.getItem();
        Material spawnEggType = item.getType();

        Player player = event.getPlayer();

        if (!(player.getGameMode() == GameMode.CREATIVE)) {

            //Cancel regular behaviour.
            event.setCancelled(true);

            // Launch the egg as a snowball projectile to prevent natural egg hatching
            Snowball snowball = player.launchProjectile(Snowball.class);
            snowball.getPersistentDataContainer().set(eggKey, PersistentDataType.STRING, spawnEggType.name());

            // Make the snowball visually look like the correct spawn egg
            ItemStack eggVisual = new ItemStack(spawnEggType);
            snowball.setItem(eggVisual);

            // Adjustable velocity // not necessary default matches Egg.
            //snowball.setVelocity(snowball.getVelocity().multiply(0.6)); // Reduces velocity to ~60% of normal

            float pitch = 0.2f + (random.nextFloat() * 0.2f);

            // Play egg throw sound with randomized pitch
            player.playSound(player.getLocation(), Sound.ENTITY_EGG_THROW, 0.5f, pitch);

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
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball)) return;

        Snowball snowball = (Snowball) event.getEntity();
        if (!snowball.getPersistentDataContainer().has(eggKey, PersistentDataType.STRING)) return;

        String spawnEggName = snowball.getPersistentDataContainer().get(eggKey, PersistentDataType.STRING);
        if (spawnEggName == null) return;

        EntityType entityType = getEntityTypeFromEgg(spawnEggName);
        if (entityType != null) {
            snowball.getWorld().spawnEntity(snowball.getLocation(), entityType);
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
