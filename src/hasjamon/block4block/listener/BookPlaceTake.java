package hasjamon.block4block.listener;

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

public class BookPlaceTake implements Listener {
    private final Block4Block plugin;

    public BookPlaceTake(Block4Block plugin){
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e){
        Block b = e.getBlock();
        Player p = e.getPlayer();

        // If you're placing something on a lectern
        if(b.getType() == Material.LECTERN){
            // If the lectern is in a claimed chunk
            if(plugin.cfg.getClaimData().contains(utils.getChunkID(b.getChunk()))){
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
                }else{ // If you're placing a book
                    BookMeta bookmeta = (BookMeta) e.getItemInHand().getItemMeta();
                    if(bookmeta != null && bookmeta.getPageCount() > 0){
                        if(bookmeta.getPage(1).substring(0, 5).equalsIgnoreCase("claim")) {
                            e.setCancelled(true);
                            p.sendMessage(utils.chat("&cThis chunk is already claimed! Find the claim book or remove \"claim\" from your book to place it."));
                        }
                    }
                }
            }else if(e.getItemInHand().getType() == Material.WRITABLE_BOOK || e.getItemInHand().getType() == Material.WRITTEN_BOOK){
                boolean canPlaceBook = utils.claimChunk(b, p, e.getItemInHand());

                if(!canPlaceBook)
                    e.setCancelled(true);
            }
        }
    }

    // Runs if a player takes a book from a lectern, then unclaims the chunk if it's a claim book
    @EventHandler
    public void onBookTake(PlayerTakeLecternBookEvent e){
        if(plugin.cfg.getClaimData().contains(utils.getChunkID(e.getLectern().getChunk())))
            if(utils.isClaimBlock(e.getLectern().getBlock()))
                utils.unclaimChunk(e.getPlayer(),e.getLectern().getBlock(), false);
    }
}
