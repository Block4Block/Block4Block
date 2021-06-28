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
import oshi.util.tuples.Pair;

public class BlockPlace implements Listener {
    private final Block4Block plugin;

    public BlockPlace(Block4Block plugin){
        this.plugin = plugin;
    }

    // Prevent blocks from being placed in someone else's claim
    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();

        if(e.getBlockReplacedState().getType() == Material.AIR)
            utils.b4bGracePeriods.put(b, new Pair<>(System.nanoTime(), b.getType().name()));

        if(b.getType() == Material.LECTERN)
            return;

        // If the block was placed in a claimed chunk
        if (plugin.cfg.getClaimData().contains(utils.getChunkID(e.getBlockPlaced().getLocation()))) {
            String[] members = utils.getMembers(b.getLocation());

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
    public void onBucketEmpty(PlayerBucketEmptyEvent e){
        Block b = e.getBlock();
        Player p = e.getPlayer();
        String chunkID = utils.getChunkID(b.getLocation());

        if(plugin.cfg.getClaimData().contains(chunkID)){
            String[] members = utils.getMembers(b.getLocation());

            if (members != null) {
                for (String member : members)
                    if (member.equalsIgnoreCase(p.getName()))
                        return;

                p.sendMessage(utils.chat("&cYou cannot empty buckets in this claim"));
                e.setCancelled(true);
            }
        }
    }
}

