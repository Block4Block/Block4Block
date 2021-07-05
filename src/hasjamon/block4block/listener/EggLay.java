package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
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
    private final Block4Block plugin;

    public EggLay(Block4Block plugin){
        this.plugin = plugin;
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent e) {
        Item item = e.getEntity();
        ItemStack itemStack = item.getItemStack();

        // If it's an egg and wasn't dropped by a player: Chance to lay a random spawn egg instead
        if (itemStack.getType() == Material.EGG && item.getThrower() == null && item.getPickupDelay() == 10) {
            int radius = plugin.getConfig().getInt("named-chicken-radius");
            List<Entity> nearbyEntities = item.getNearbyEntities(radius, radius, radius);
            Set<String> namedChickensPos = new HashSet<>();
            Map<Character, Integer> letterBonuses = new HashMap<>();

            for(Entity ne : nearbyEntities){
                if(ne.getType() == EntityType.CHICKEN){
                    String chickenName = ne.getCustomName();

                    if(chickenName != null) {
                        Location loc = ne.getLocation();
                        String pos = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();

                        // If no other named chicken has been found at that location
                        if (!namedChickensPos.contains(pos)) {
                            namedChickensPos.add(pos);
                            letterBonuses.merge(chickenName.toLowerCase().charAt(0), 1, Integer::sum);
                        }
                    }
                }
            }

            double spawnChance = plugin.getConfig().getDouble("spawn-egg-chance");
            if (Math.random() <= spawnChance * calcSpawnChanceBonus(namedChickensPos.size())) {
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
