package hasjamon.block4block.listener;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerHarvestBlock implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onPlayerHarvestBlock(PlayerHarvestBlockEvent e){
        Material blockType = e.getHarvestedBlock().getType();

        if(blockType == Material.CAVE_VINES || blockType == Material.CAVE_VINES_PLANT){
            if(Math.random() > 0.7){
                e.getItemsHarvested().add(new ItemStack(Material.GLOW_LICHEN, 1));
            }
        }
    }
}
