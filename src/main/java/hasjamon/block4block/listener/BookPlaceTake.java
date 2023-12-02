package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.events.ClaimBookPlacedEvent;
import hasjamon.block4block.events.ClaimRemovedEvent;
import hasjamon.block4block.utils.utils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

public class BookPlaceTake implements Listener {
    private final Block4Block plugin;

    public BookPlaceTake(Block4Block plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();

        // If you're placing something on a lectern
        if (b.getType() == Material.LECTERN) {
            ItemStack item = e.getItemInHand();
            Material type = item.getType();
            boolean isBook = (type == Material.WRITTEN_BOOK || type == Material.WRITABLE_BOOK);
            boolean canPlace = true;
            Location bLoc = b.getLocation();
            String claimID = utils.getClaimID(bLoc);
            FileConfiguration claimData = plugin.cfg.getClaimData();
            boolean isClaimed = claimData.contains(claimID);

            // If you're placing a book
            if (isBook) {
                BookMeta meta = (BookMeta) item.getItemMeta();

                if (meta != null) {
                    // If it's a copy of a master book, update its pages
                    if (meta.getLore() != null) {
                        FileConfiguration masterBooks = plugin.cfg.getMasterBooks();
                        String bookID = String.join("", meta.getLore()).substring(17);

                        if (plugin.getConfig().getBoolean("enable-master-books"))
                            if (masterBooks.contains(bookID + ".pages"))
                                meta.setPages(masterBooks.getStringList(bookID + ".pages"));
                    }

                    // If it's a claim book
                    if (meta.getPageCount() > 0 && utils.isClaimPage(meta.getPage(1))) {
                        if (isClaimed) {
                            canPlace = false;
                            p.sendMessage(utils.chat("&cThis chunk is already claimed! Find the claim book or remove \"claim\" from your book to place it."));
                        } else {
                            canPlace = utils.claimChunk(b, utils.findMembersInBook(meta), p::sendMessage);
                        }
                    }
                }
            }

            if (canPlace) {
                // If it's a (copy of a) master book, add it to the list of copies on lecterns
                if (isBook) {
                    ItemStack book = e.getItemInHand();
                    BookMeta meta = (BookMeta) book.getItemMeta();
                    boolean isMasterBook = false;
                    boolean isMember = utils.isMemberOfClaim(utils.getMembers(claimID), p);

                    if (meta != null) {
                        List<String> lore = meta.getLore();

                        if (lore != null) {
                            isMasterBook = true;
                            FileConfiguration masterBooks = plugin.cfg.getMasterBooks();
                            String bookID = String.join("", lore).substring(17);
                            String xyz = bLoc.getBlockX() + "," + bLoc.getBlockY() + "," + bLoc.getBlockZ();
                            List<String> copies = new ArrayList<>();

                            if (masterBooks.contains(bookID + ".copies-on-lecterns"))
                                copies = masterBooks.getStringList(bookID + ".copies-on-lecterns");
                            copies.add(claimID + "!" + xyz);

                            masterBooks.set(bookID + ".copies-on-lecterns", copies);
                            plugin.cfg.saveMasterBooks();
                        }
                    }

                    plugin.pluginManager.callEvent(new ClaimBookPlacedEvent(p, b, isMasterBook, isMember));
                }
            } else {
                e.setCancelled(true);

                Lectern lectern = (Lectern) b.getState();
                lectern.getInventory().clear();
            }
        }
    }

    // Runs if a player takes a book from a lectern, then unclaims the chunk if it's a claim book
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBookTake(PlayerTakeLecternBookEvent e) {
        Block lecternBlock = e.getLectern().getBlock();
        String claimID = utils.getClaimID(e.getLectern().getLocation());

        if (plugin.cfg.getClaimData().contains(claimID)) {
            if (utils.isClaimBlock(lecternBlock)) {
                Player player = e.getPlayer();
                boolean isMember = utils.isMemberOfClaim(utils.getMembers(claimID), player);
                long numProtectedSides = utils.countProtectedSides(lecternBlock);
                boolean isInvulnerable = utils.isClaimInvulnerable(lecternBlock);

                if (isMember || numProtectedSides == 0 && !isInvulnerable) {
                    utils.unclaimChunk(lecternBlock, true, player::sendMessage);
                    plugin.pluginManager.callEvent(new ClaimRemovedEvent(player, lecternBlock, isMember));
                } else {
                    if (!isInvulnerable) {
                        String msg = utils.chat("&aLectern is still protected from &c" + numProtectedSides + " &asides");
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
                    }
                    e.setCancelled(true);
                }
            }
        }
    }
}
