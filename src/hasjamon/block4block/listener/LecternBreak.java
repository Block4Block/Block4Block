package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public class LecternBreak implements Listener {
    private final Block4Block plugin;

    public LecternBreak(Block4Block plugin){
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent e){
        if(!e.isCancelled()) {
            Block b = e.getBlock();
            String chunkID = utils.getChunkID(b.getLocation());

            // If the block is in a claimed chunk
            if (plugin.cfg.getClaimData().contains(chunkID)) {
                Player p = e.getPlayer();

                // LECTERN is exempt from B4B - otherwise having it on noloot would cause problems
                if (b.getType() == Material.LECTERN)
                    if (utils.isClaimBlock(b))
                        utils.unclaimChunk(b, false, p::sendMessage);
            }
        }
    }

    // If a lectern is destroyed in an explosion: Inform the members of the claim
    @EventHandler
    public void onExplosion(EntityExplodeEvent e) {
        for (Block block : e.blockList()) {
            if (block.getType().equals(Material.LECTERN) && utils.isClaimBlock(block)) {
                utils.unclaimChunk(block, true, (msg) -> {});
                String[] members = utils.getMembers(block.getLocation());
                for (Player p : Bukkit.getOnlinePlayers())
                    if (members != null)
                        for (String member : members)
                            if (member.equalsIgnoreCase(p.getName()))
                                p.sendMessage(utils.chat("&cYour claim has been destroyed!"));
            }
        }
    }
}
