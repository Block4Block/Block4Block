package hasjamon.block4block.listener;

import hasjamon.block4block.utils.utils;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

public class EggLay implements Listener {
    @EventHandler
    public void onItemSpawn(ItemSpawnEvent e) {
        Item item = e.getEntity();
        ItemStack itemStack = item.getItemStack();

        // If it's an egg and wasn't dropped by a player: 1% chance to lay a random spawn egg instead
        if (itemStack.getType() == Material.EGG && item.getThrower() == null && item.getPickupDelay() == 10)
            if(Math.random() <= 0.01)
                itemStack.setType(utils.getRandomSpawnEgg());
    }
}
