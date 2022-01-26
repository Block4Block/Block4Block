package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.ChickenBonuses;
import hasjamon.block4block.utils.utils;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

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
            ChickenBonuses bonuses = utils.calcChickenBonuses(item);
            Map<Character, Integer> letterBonuses = bonuses.letterBonuses;
            int numNamedChickens = bonuses.numNamedChickens;

            double spawnChance = plugin.getConfig().getDouble("spawn-egg-chance");
            if (Math.random() <= spawnChance * utils.calcGeneralChickenBonus(numNamedChickens)) {
                itemStack.setType(utils.getRandomSpawnEgg(letterBonuses));
            }
        }
    }
}
