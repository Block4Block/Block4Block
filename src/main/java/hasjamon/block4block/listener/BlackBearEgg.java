package hasjamon.block4block.listener;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.PolarBear;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

public class BlackBearEgg implements Listener {
    private final NamespacedKey eggKey;

    public BlackBearEgg(NamespacedKey eggKey) {
        this.eggKey = eggKey;
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg)) return;

        Egg egg = (Egg) event.getEntity();
        if (!egg.getPersistentDataContainer().has(eggKey, PersistentDataType.BYTE)) return;

        ProjectileSource source = egg.getShooter();
        if (source instanceof Player) {
            // Spawn a Polar Bear but with a custom name ("Albino Black Bear")
            PolarBear bear = (PolarBear) egg.getWorld().spawnEntity(egg.getLocation(), EntityType.POLAR_BEAR);
            bear.setCustomName(ChatColor.WHITE + "Albino Black Bear");
            bear.setCustomNameVisible(true);
        }
    }
}
