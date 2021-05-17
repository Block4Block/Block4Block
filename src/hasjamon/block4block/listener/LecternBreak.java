package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public class LecternBreak implements Listener {
    Block4Block plugin = Block4Block.getInstance();

    // Event called if a block is broken
    @EventHandler
    public void onBreak(BlockBreakEvent e){
        if(plugin.cfg.getclaimdata().contains(utils.getChunkID(e.getBlock().getChunk()))) { //if the block broken is in a claimed chunk
            Player p = e.getPlayer();
            if (e.getBlock().getType() == Material.LECTERN){ //if the block is a lectern
                if(utils.isClaimBlock(e.getBlock()))
                    utils.unclaimChunk(p,e.getBlock(),false);
            }
        }
    }


    @EventHandler
    public void onExplosion(EntityExplodeEvent e) {
        for (Block block : e.blockList()) {
            if (block.getType().equals(Material.LECTERN) && utils.isClaimBlock(block)) {
                utils.unclaimChunk(null, block, true);
                String[] members = utils.getMembers(block.getChunk());
                for (Player p : Bukkit.getOnlinePlayers())
                    if (members != null)
                        for (String member : members)
                            if (member.equalsIgnoreCase(p.getName()))
                                p.sendMessage(utils.chat("&cYour claim has been destroyed!"));
            }
        }
    }
}
