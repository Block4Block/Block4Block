package hasjamon.block4block.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;

public class LavaCasting implements Listener {
    // Prevents lavacasting (i.e., combinations of lava and water that form cobblestone).
    // Accidentally disables obsidian formation... Oh well.
    @EventHandler
    public void onCobbleFormation(BlockFormEvent e) {
        Block b = e.getBlock();

        b.setType(Material.AIR);
        e.setCancelled(true);
    }
}
