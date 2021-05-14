package hasjamon.block4block.Listeners;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.inventory.meta.BookMeta;

public class LecternPlace implements Listener {
    Block4Block plugin = Block4Block.getInstance();

    @EventHandler
    public void onPlace(BlockPlaceEvent e){
        Block b = e.getBlock();
        Player p = e.getPlayer();

        // If you're placing something on a lectern
        if(b.getType() == Material.LECTERN){
            // If the lectern is in a claimed chunk
            if(plugin.cfg.getclaimdata().contains(utils.getChunkID(b.getChunk()))){
                String[] members = utils.getMembers(b.getChunk());
                // If you're placing something other than a book
                if(e.getItemInHand().getType() != Material.WRITTEN_BOOK && e.getItemInHand().getType() != Material.WRITABLE_BOOK){
                    if(members != null){
                        for (String member : members)
                            if (member.equalsIgnoreCase(p.getName()))
                                return;

                        e.setCancelled(true);
                        p.sendMessage(utils.chat("&cYou cannot place blocks in this claim"));
                    }
                // If you're placing a book
                }else{
                    BookMeta bookmeta = (BookMeta) e.getItemInHand().getItemMeta();
                    if(bookmeta != null && bookmeta.getPageCount() > 0){
                        if(bookmeta.getPage(1).substring(0, 5).equalsIgnoreCase("claim")) {
                            e.setCancelled(true);
                            p.sendMessage(utils.chat("&cThis chunk is already claimed! Find the claim book or remove \"claim\" from your book to place it."));
                        }
                    }
                }
            }else if(e.getItemInHand().getType() == Material.WRITABLE_BOOK || e.getItemInHand().getType() == Material.WRITTEN_BOOK){
                utils.claimChunk(b, p, e.getItemInHand());
            }
        }
    }

    // Runs if a player takes the book out of the lectern
    @EventHandler
    public void onBookTake(PlayerTakeLecternBookEvent e){
        if(plugin.cfg.getclaimdata().contains(utils.getChunkID(e.getLectern().getChunk()))) // If the chunk is claimed
            if(utils.isClaimBlock(e.getLectern().getBlock())) // Checks if the lectern is the claim block
                utils.unclaimChunk(e.getPlayer(),e.getLectern().getBlock(), false); // Unclaims the chunk
    }
}
