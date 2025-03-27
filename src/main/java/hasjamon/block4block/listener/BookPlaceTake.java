package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.events.ClaimBookPlacedEvent;
import hasjamon.block4block.events.ClaimRemovedEvent;
import hasjamon.block4block.utils.utils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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
    public void onPlace(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        Player p = e.getPlayer();

        // If you're placing something on a lectern
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && b != null && b.getType() == Material.LECTERN && e.hasItem()) {
            ItemStack item = e.getItem();
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
                if (isBook) {
                    BookMeta meta = (BookMeta) item.getItemMeta();

                    if (meta != null) {
                        List<String> lore = meta.getLore();
                        boolean isMasterBook = false;
                        boolean isMember = utils.findMembersInBook(meta).stream()
                                .anyMatch(member -> member.equalsIgnoreCase(p.getName()));

                        if (isMember)
                            p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.RECORDS, 1, 1f);
                        plugin.pluginManager.callEvent(new ClaimBookPlacedEvent(p, b, isMasterBook, isMember));

                        // If it's a (copy of a) master book, add it to the list of copies on lecterns
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
                }

                // After a book is placed, update the boss bar for the player
                utils.updateBossBar(p, claimID);
            } else {
                e.setCancelled(true);
            }
        }
    }

    // Runs if a player takes a book from a lectern, then unclaims the chunk if it's a claim book
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBookTake(PlayerTakeLecternBookEvent e) {
        Block lecternBlock = e.getLectern().getBlock();
        Lectern lectern = (Lectern) lecternBlock.getState();
        String claimID = utils.getClaimID(lectern.getLocation());
        ItemStack book = e.getBook();
        BookMeta meta = (BookMeta) book.getItemMeta();
        Player p = e.getPlayer();

        if (book.getType() == Material.WRITTEN_BOOK) {
            if (meta != null && meta.getLore() != null) {
                FileConfiguration masterBooks = plugin.cfg.getMasterBooks();
                String bookID = String.join("", meta.getLore()).substring(17);

                if (masterBooks.contains(bookID + ".pages")) {
                    List<String> masterBookPages = masterBooks.getStringList(bookID + ".pages");
                    // Check if it's a claim book, then check if it's the one that claimed the chunk
                    if (!masterBookPages.isEmpty() && utils.isClaimPage(masterBookPages.getFirst())) {
                        FileConfiguration claimData = plugin.cfg.getClaimData();
                        Location bLoc = lectern.getLocation();
                        boolean isCorrupted =
                                !(claimData.contains(claimID + ".location")
                                        && claimData.get(claimID + ".location.X").equals(bLoc.getX())
                                        && claimData.get(claimID + ".location.Y").equals(bLoc.getY())
                                        && claimData.get(claimID + ".location.Z").equals(bLoc.getZ())) ;

                        if (isCorrupted) {
                            lectern.getInventory().clear();

                            List<String> copies = masterBooks.getStringList(bookID + ".copies-on-lecterns");
                            String xyz = bLoc.getBlockX() + "," + bLoc.getBlockY() + "," + bLoc.getBlockZ();
                            copies.remove(claimID + "!" + xyz);
                            masterBooks.set(bookID + ".copies-on-lecterns", copies);
                            plugin.cfg.saveMasterBooks();

                            p.sendMessage(ChatColor.GRAY + "The book was corrupted and turns to dust in your hands.");

                            e.setCancelled(true);
                            return;
                        }
                    }

                    if (plugin.getConfig().getBoolean("enable-master-books"))
                        meta.setPages(masterBooks.getStringList(bookID + ".pages"));
                    book.setItemMeta(meta);
                }
            }
        } else if (book.getType() == Material.WRITABLE_BOOK) {
            if (meta != null && plugin.getConfig().getBoolean("enable-claim-takeovers")) {
                FileConfiguration claimTakeovers = plugin.cfg.getClaimTakeovers();
                List<String> replacements = claimTakeovers.getStringList(claimID);
                List<String> pages = new ArrayList<>(meta.getPages());

                for (String replacement : replacements) {
                    String[] parts = replacement.split("\\|");
                    utils.replaceInClaimPages(pages, parts[0], parts[1]);
                }

                meta.setPages(pages);
                book.setItemMeta(meta);
                claimTakeovers.set(claimID, null);
                plugin.cfg.saveClaimTakeovers();
            }
        }

        if (plugin.cfg.getClaimData().contains(claimID)) {
            if (utils.isClaimBlock(lecternBlock)) {
                Player player = e.getPlayer();
                boolean isMember = utils.isMemberOfClaim(utils.getMembers(claimID), player);

                // Get the list of protected block faces from the config
                List<String> protectedBlockFaces = plugin.cfg.getClaimData().getStringList("claim-protection.protective-block-faces");

                // Pass both lecternBlock and the protectedBlockFaces list to countProtectedSides
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

        // After a book is taken and a claim is removed, update the boss bar
        utils.updateBossBar(p, claimID);
    }
}
