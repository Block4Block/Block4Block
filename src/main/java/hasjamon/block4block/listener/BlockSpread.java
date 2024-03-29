package hasjamon.block4block.listener;

import hasjamon.block4block.utils.utils;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockSpreadEvent;

public class BlockSpread implements Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block source = event.getBlock();
        Block block = event.getToBlock();

        boolean shouldCancel = shouldCancelEvent(source, block);

        event.setCancelled(shouldCancel);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        Block source = event.getSource();
        Block block = event.getBlock();

        boolean shouldCancel = shouldCancelEvent(source, block);

        event.setCancelled(shouldCancel);
    }

    private boolean shouldCancelEvent(Block source, Block block) {
        boolean result = false;

        switch (source.getType()) {
            case SCULK_CATALYST:
                String claimID = utils.getClaimID(block.getLocation());
                String[] members = utils.getMembers(claimID);

                result = members != null;
                break;

            case LAVA:
                World.Environment dimension = source.getWorld().getEnvironment();
                int blockY = source.getLocation().getBlockY();
                
                result = !utils.willLavaFlowAt(blockY, dimension);
                break;
        }

        return result;
    }
}
