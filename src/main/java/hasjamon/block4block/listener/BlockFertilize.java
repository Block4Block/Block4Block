package hasjamon.block4block.listener;

import hasjamon.block4block.utils.utils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFertilizeEvent;

import java.util.Optional;

public class BlockFertilize implements Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Optional<Material> newTypeOpt = event.getBlocks().stream().findFirst().map(BlockState::getType);
        String claimID = utils.getClaimID(event.getBlock().getLocation());
        String[] members = utils.getMembers(claimID);

        if (members != null && !utils.isMemberOfClaim(members, player)) {
            event.setCancelled(true);
        } else if (newTypeOpt.isPresent()) {
            if (block.getType() == Material.NETHERRACK) {
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            Block toTransform = block.getRelative(x, y, z);
                            Block above = toTransform.getRelative(BlockFace.UP);

                            if (toTransform.getType() == Material.NETHERRACK && utils.isAir(above.getType()))
                                toTransform.setType(newTypeOpt.get());
                        }
                    }
                }
            }
        }
    }
}
