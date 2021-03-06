package hasjamon.block4block.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;

public class LavaCasting implements Listener {
    // Modifies lavacasting to form andesite instead of cobblestone.
    @EventHandler
    public void onBlockForm(BlockFormEvent e) {
        Block b = e.getBlock();
        Material newType = e.getNewState().getType();

        if(newType == Material.COBBLESTONE || newType == Material.STONE) {
            b.setType(Material.ANDESITE);
            e.setCancelled(true);
        }
    }
}
