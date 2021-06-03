package hasjamon.block4block.listener;

import hasjamon.block4block.utils.utils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class EggLay implements Listener {
    @EventHandler
    public void onItemSpawn(ItemSpawnEvent e) {
        Item item = e.getEntity();
        ItemStack itemStack = item.getItemStack();

        // If it's an egg and wasn't dropped by a player: 1% chance to lay a random spawn egg instead
        if (itemStack.getType() == Material.EGG && item.getThrower() == null && item.getPickupDelay() == 10) {
            List<Entity> nearbyEntities = item.getNearbyEntities(5, 5, 5);
            Set<String> namedChickensPos = new HashSet<>();
            Map<Character, Integer> letterBonuses = new HashMap<>();

            for(Entity ne : nearbyEntities){
                if(ne.getType() == EntityType.CHICKEN){
                    String chickenName = ne.getCustomName();

                    if(chickenName != null) {
                        Location loc = ne.getLocation();
                        String pos = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();

                        // If not other named chicken has been found at that location
                        if (!namedChickensPos.contains(pos)) {
                            namedChickensPos.add(pos);
                            letterBonuses.merge(chickenName.toLowerCase().charAt(0), 1, Integer::sum);
                        }
                    }
                }
            }

            if (Math.random() <= 0.01 * calcSpawnChanceBonus(namedChickensPos.size())) {
                itemStack.setType(utils.getRandomSpawnEgg(letterBonuses));
            }
        }
    }

    // Returns log2(n + 2)
    private double calcSpawnChanceBonus(double numNamedChickens){
        // log2(x) = log(x) / log(2)
        return Math.log(numNamedChickens + 2) / Math.log(2);
    }
}
