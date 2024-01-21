package hasjamon.block4block.listener;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ExplosionPrimeEvent;

import java.util.List;

public class ExplosionPrime implements Listener {
    private final double range = 32.5;

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {

        if (event.getEntityType() == EntityType.CREEPER) {
            Entity entity = event.getEntity();
            List<Entity> nearbyCats = entity.getNearbyEntities(range, range, range).stream()
                    .filter(ent -> ent.getType() == EntityType.CAT)
                    .toList();

            if (!nearbyCats.isEmpty()) {
                event.setCancelled(true);
                nearbyCats.forEach(cat -> cat.getNearbyEntities(range + 10, range + 10, range + 10).stream()
                        .filter(ent -> ent.getType() == EntityType.PLAYER)
                        .forEach(player -> {
                            double volume = Math.max(0, 1 - (player.getLocation().distance(cat.getLocation()) / (range * 3)));
                            ((Player) player).playSound(player, Sound.ENTITY_CAT_HISS, SoundCategory.HOSTILE, (float) volume, (float) 1.0);
                        }));
            }
        }
    }
}
