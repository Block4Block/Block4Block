package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

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
            ItemStack item = e.getItemInHand();
            Material type = item.getType();
            boolean isBook = (type == Material.WRITTEN_BOOK || type == Material.WRITABLE_BOOK);
            boolean canPlace = true;
            Location bLoc = b.getLocation();

            String chunkID = utils.getChunkID(bLoc);
            FileConfiguration claimData = plugin.cfg.getClaimData();
            boolean isClaimed = claimData.contains(chunkID);

            // If you're placing a book
            if(isBook){
                BookMeta meta = (BookMeta) item.getItemMeta();

                if(meta != null){
                    // If it's a copy of a master book, update its pages
                    if(meta.getLore() != null){
                        FileConfiguration masterBooks = plugin.cfg.getMasterBooks();
                        String bookID = String.join("", meta.getLore()).substring(17);

                        if(masterBooks.contains(bookID + ".pages")) {
                            meta.setPages(masterBooks.getStringList(bookID + ".pages"));
                        }
                    }

                    // If it's a claim book
                    if(meta.getPageCount() > 0 && utils.isClaimPage(meta.getPage(1))){
                        if(isClaimed) {
                            canPlace = false;
                            p.sendMessage(utils.chat("&cThis chunk is already claimed! Find the claim book or remove \"claim\" from your book to place it."));
                        }else {
                            canPlace = utils.claimChunk(b, item, p::sendMessage);
                        }
                    }
                }
            }else{
                String[] members = utils.getMembers(chunkID);

                if(members != null){
                    boolean isMember = false;

                    for (String member : members) {
                        if (member.equalsIgnoreCase(p.getName())) {
                            isMember = true;
                            break;
                        }
                    }

                    if(!isMember) {
                        canPlace = false;
                        p.sendMessage(utils.chat("&cYou cannot place blocks in this claim"));
                    }
                }
            }

            if(canPlace) {
                // If it's a (copy of a) master book, add it to the list of copies on lecterns
                if(isBook) {
                    ItemStack book = e.getItemInHand();
                    BookMeta meta = (BookMeta) book.getItemMeta();

                    if (meta != null) {
                        List<String> lore = meta.getLore();

                        if (lore != null) {
                            FileConfiguration masterBooks = plugin.cfg.getMasterBooks();
                            String bookID = String.join("", lore).substring(17);
                            String xyz = bLoc.getBlockX() + "," + bLoc.getBlockY() + "," + bLoc.getBlockZ();
                            List<String> copies = new ArrayList<>();

                            if(masterBooks.contains(bookID + ".copies-on-lecterns"))
                                copies = masterBooks.getStringList(bookID + ".copies-on-lecterns");
                            copies.add(chunkID + "!" + xyz);

                            masterBooks.set(bookID + ".copies-on-lecterns", copies);
                            plugin.cfg.saveMasterBooks();
                        }
                    }
                }
            } else {
                e.setCancelled(true);

                Lectern lectern = (Lectern) b.getState();
                lectern.getInventory().clear();
            }
        }
    }

    // Runs if a player takes a book from a lectern, then unclaims the chunk if it's a claim book
    @EventHandler
    public void onBookTake(PlayerTakeLecternBookEvent e){
        Block lecternBlock = e.getLectern().getBlock();
        String chunkID = utils.getChunkID(e.getLectern().getLocation());
        ItemStack book = e.getBook();

        // If it's a (copy of a) master book, remove it from the list of copies on lecterns
        if(book != null){
            BookMeta meta = (BookMeta) book.getItemMeta();

            if(meta != null){
                List<String> lore = meta.getLore();

                if(lore != null){
                    FileConfiguration masterBooks = plugin.cfg.getMasterBooks();
                    String bookID = String.join("", lore).substring(17);

                    if(masterBooks.contains(bookID + ".copies-on-lecterns")) {
                        List<String> copies = masterBooks.getStringList(bookID + ".copies-on-lecterns");
                        Location bLoc = lecternBlock.getLocation();
                        String xyz = bLoc.getBlockX() + "," + bLoc.getBlockY() + "," + bLoc.getBlockZ();

                        copies.remove(chunkID + "!" + xyz);

                        masterBooks.set(bookID + ".copies-on-lecterns", copies);
                        plugin.cfg.saveMasterBooks();
                    }
                }
            }
        }

        if(plugin.cfg.getClaimData().contains(chunkID))
            if(utils.isClaimBlock(lecternBlock))
                utils.unclaimChunk(lecternBlock, false, e.getPlayer()::sendMessage);
    }
}
