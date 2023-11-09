package hasjamon.block4block.listener;

import hasjamon.block4block.utils.utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockSpreadEvent;

public class BlockSpread implements Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        Material sourceType = event.getSource().getType();

        if (sourceType == Material.SCULK_CATALYST) {
            Block block = event.getBlock();
            String claimID = utils.getClaimID(block.getLocation());
            String[] members = utils.getMembers(claimID);

            if (members != null) {
                event.setCancelled(true);
            }
        }
    }
}
