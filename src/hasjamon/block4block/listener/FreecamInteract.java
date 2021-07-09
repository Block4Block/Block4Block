package hasjamon.block4block.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class FreecamInteract implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e){
        if(e.getAction() == Action.RIGHT_CLICK_BLOCK)
            if(!lookingAtBlock(e.getClickedBlock(), e.getPlayer()))
                e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent e){
        Block b = e.getBlock();

        if(b.getType() != Material.ANDESITE && !lookingAtBlock(b, e.getPlayer()))
            e.setCancelled(true);
    }

    private boolean lookingAtBlock(Block b, Player p){
        if(b != null){
            Location pEyeLoc = p.getEyeLocation();
            Vector direction = pEyeLoc.getDirection();

            RayTraceResult result = p.getWorld().rayTraceBlocks(pEyeLoc, direction, 6.0);

            if(result == null || result.getHitBlock() == null || !result.getHitBlock().equals(b))
                return false;
        }

        return true;
    }
}
