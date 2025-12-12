package hasjamon.block4block.listener;

import hasjamon.block4block.utils.utils;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

public class EntityDeath implements Listener {
    @EventHandler
    public void onEntityDeath(EntityDeathEvent e){
        if(e.getEntityType() == EntityType.IRON_GOLEM)
            utils.ironGolems.remove((IronGolem) e.getEntity());

        // Drop gunpowder when a ghast is killed by a deflected fireball
        if(e.getEntityType() == EntityType.GHAST) {
            Ghast ghast = (Ghast) e.getEntity();

            // Check if the last damage was from an entity
            if(ghast.getLastDamageCause() instanceof EntityDamageByEntityEvent damageEvent) {
                // Check if damage was from a fireball shot by this same ghast
                if(damageEvent.getDamager() instanceof Fireball fireball) {
                    // If the fireball was shot by the ghast itself, it must have been deflected
                    if(fireball.getShooter() instanceof Ghast && fireball.getType() == EntityType.FIREBALL) {
                        // Drop gunpowder
                        e.getEntity().getWorld().dropItemNaturally(
                                e.getEntity().getLocation(),
                                new ItemStack(Material.GUNPOWDER)
                        );

                        // Remove firework rockets from drops
                        Iterator<ItemStack> iterator = e.getDrops().iterator();
                        while(iterator.hasNext()) {
                            ItemStack item = iterator.next();
                            if(item.getType() == Material.FIREWORK_ROCKET) {
                                iterator.remove();
                            }
                        }
                    }
                }
            }
        }
    }
}