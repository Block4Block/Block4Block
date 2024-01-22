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
import java.util.Random;

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
                            //double volume = Math.max(0, 1 - (player.getLocation().distance(cat.getLocation()) / (range * 3)));
                            //((Player) player).playSound(player, Sound.ENTITY_CAT_AMBIENT, SoundCategory.HOSTILE, (float) volume, (float) 1.0);
                            double volume = Math.max(0, 1 - (player.getLocation().distance(cat.getLocation()) / (range * 3)));
                            // Modified sound selection logic
                            Random random = new Random();
                            Sound soundToPlay;
                            if (random.nextInt(4) == 0) {
                                soundToPlay = Sound.ENTITY_CAT_AMBIENT; // 1/4 probability
                            } else {
                                soundToPlay = (random.nextInt(2) == 0) ? Sound.ENTITY_CAT_HISS :
                                        new Sound[]{Sound.ENTITY_CAT_AMBIENT, Sound.ENTITY_CAT_STRAY_AMBIENT}[random.nextInt(2)];
                            }
                            // Play the selected sound
                            ((Player) player).playSound(player, soundToPlay, SoundCategory.HOSTILE, (float) volume, (float) 1.0);
                        }));


            }
        }
    }
}