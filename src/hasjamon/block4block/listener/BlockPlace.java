package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

public class BlockPlace implements Listener {
    // Prevent blocks from being placed in someone else's claim
    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();

        if(b.getType() == Material.LECTERN)
            return;

        // If the block was placed in a claimed chunk
        if (Block4Block.getInstance().cfg.getClaimData().contains(utils.getChunkID(e.getBlockPlaced().getChunk()))) {
            String[] members = utils.getMembers(b.getChunk());

            if (!utils.isClaimBlock(b)){
                // If the player placing the block is a member: Don't prevent block placement
                if (members != null)
                    for (String member : members)
                        if (member.equalsIgnoreCase(p.getName()))
                            return;

                e.setCancelled(true);
                p.sendMessage(utils.chat("&cYou cannot place blocks in this claim"));
            }
        }
    }

    @EventHandler
    public void onEmpty(PlayerBucketEmptyEvent e){
        Block b = e.getBlock();
        Player p = e.getPlayer();
        Material bucket = e.getBucket();

        if(Block4Block.getInstance().cfg.getClaimData().contains(utils.getChunkID(b.getChunk()))){
            String[] members = utils.getMembers(b.getChunk());
            if (members != null) {
                for (String member : members)
                    if (member.equalsIgnoreCase(p.getName()))
                        if (bucket.toString().contains("LAVA") || bucket.toString().contains("WATER"))
                            return;

                p.sendMessage(utils.chat("&cYou cannot place Lava/Water inside this claim"));
                e.setCancelled(true);
            }
        }
    }
}

