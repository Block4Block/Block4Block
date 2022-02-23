package hasjamon.block4block.listener;

import hasjamon.block4block.utils.utils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFertilizeEvent;

public class BlockFertilize implements Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent e) {
        Player p = e.getPlayer();
        String chunkID = utils.getChunkID(e.getBlock().getLocation());
        String[] members = utils.getMembers(chunkID);

        if (members != null && !utils.isMemberOfClaim(members, p)) {
            e.setCancelled(true);
        }
    }
}
