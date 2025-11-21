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

    public EggLay(Block4Block plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent e) {
        Item item = e.getEntity();
        ItemStack itemStack = item.getItemStack();
        Material type = itemStack.getType();

        // Check if it's any type of egg (regular, blue, or brown)
        boolean isEgg = type == Material.EGG || type == Material.BLUE_EGG || type == Material.BROWN_EGG;

        // If it's an egg and wasn't dropped by a player: Chance to lay a random spawn egg instead
        if (isEgg && item.getThrower() == null && item.getPickupDelay() == 10) {
            ChickenBonuses bonuses = utils.calcChickenBonuses(item);
            Map<Character, Integer> letterBonuses = bonuses.letterBonuses;
            int numNamedChickens = bonuses.numNamedChickens;

            double spawnChance = plugin.getConfig().getDouble("spawn-egg-chance");
            if (Math.random() <= spawnChance * utils.calcGeneralChickenBonus(numNamedChickens)) {
                // Get the correct spawn egg (now returns ItemStack)
                ItemStack randomEgg = utils.SpawnEggUtils.getRandomSpawnEgg(letterBonuses);

                // Check if it's the Black Bear Egg and replace appropriately
                if (randomEgg.getType() == Material.COAL) {
                    // Replace the item with a correctly configured Black Bear Egg
                    item.setItemStack(utils.SpawnEggUtils.createBlackBearEgg());
                } else {
                    // Set the regular spawn egg
                    item.setItemStack(randomEgg);
                }
            }
        }
    }
}
