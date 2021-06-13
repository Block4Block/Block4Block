package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.*;

public class BookEdit implements Listener {
    private final Block4Block plugin;

    public BookEdit(Block4Block plugin){
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e){
        Player p = e.getPlayer();
        Block clickedBlock = e.getClickedBlock();
        ItemStack item = e.getItem();

        if(e.getAction() == Action.RIGHT_CLICK_BLOCK && clickedBlock != null && clickedBlock.getType() == Material.LECTERN) {
            Lectern lectern = (Lectern) clickedBlock.getState();
            int slot = lectern.getInventory().first(Material.WRITTEN_BOOK);

            if (slot > -1) {
                ItemStack book = lectern.getInventory().getItem(slot);
                BookMeta meta = (BookMeta) book.getItemMeta();

                if (meta != null && meta.getLore() != null) {
                    FileConfiguration masterBooks = plugin.cfg.getMasterBooks();
                    String bookID = String.join("", meta.getLore()).substring(17);

                    if (masterBooks.contains(bookID + ".pages")) {
                        // Check if it's a claim book, then check if it's the one that claimed the chunk
                        if (utils.isClaimPage(masterBooks.getStringList(bookID + ".pages").get(0))) {
                            FileConfiguration claimData = plugin.cfg.getClaimData();
                            String chunkID = utils.getChunkID(lectern.getLocation());
                            Location bLoc = lectern.getLocation();
                            boolean isCorrupted = true;

                            if (claimData.contains(chunkID + ".location"))
                                if (claimData.get(chunkID + ".location.X").equals(bLoc.getX()))
                                    if (claimData.get(chunkID + ".location.Y").equals(bLoc.getY()))
                                        if (claimData.get(chunkID + ".location.Z").equals(bLoc.getZ()))
                                            isCorrupted = false;

                            if (isCorrupted) {
                                lectern.getInventory().clear();

                                List<String> copies = masterBooks.getStringList(bookID + ".copies-on-lecterns");
                                String xyz = bLoc.getBlockX() + "," + bLoc.getBlockY() + "," + bLoc.getBlockZ();
                                copies.remove(chunkID + "!" + xyz);
                                masterBooks.set(bookID + ".copies-on-lecterns", copies);
                                plugin.cfg.saveMasterBooks();

                                p.sendMessage(ChatColor.GRAY + "The book was corrupted and turns to dust in your hands.");

                                e.setCancelled(true);
                                return;
                            }
                        }

                        if(plugin.getConfig().getBoolean("enable-master-books"))
                            meta.setPages(masterBooks.getStringList(bookID + ".pages"));
                        book.setItemMeta(meta);
                    }
                }
            }
        }else if(e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK){
            if(item != null) {
                // If player is holding a book and quill
                if (item.getType() == Material.WRITABLE_BOOK) {
                    // If the player isn't a member of any claims
                    if (!utils.countMemberClaims().containsKey(p.getName().toLowerCase())) {
                        // Send the player instruction on what to do with the book
                        p.sendMessage(utils.chat("&cNOTE: &7To make a claim book, type &a\"claim\" &7at the top of the book!"));
                        p.sendMessage(utils.chat("&aThen write a player's ign on each line to add a member!"));
                        p.spigot().sendMessage(new ComponentBuilder(utils.chat("&aCLICK HERE to see an example"))
                                .event(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://hasjamon.github.io/b4block/lists.html"))
                                .create());
                    }
                }else if(item.getType() == Material.WRITTEN_BOOK){
                    BookMeta meta = (BookMeta) item.getItemMeta();

                    if(meta != null && meta.getLore() != null) {
                        // If the book is not a copy
                        if(!meta.hasGeneration() || meta.getGeneration() == BookMeta.Generation.ORIGINAL) {
                            item.setType(Material.WRITABLE_BOOK);
                            item.setItemMeta(meta);
                            item.addUnsafeEnchantment(Enchantment.LUCK, 1);
                        }else{
                            FileConfiguration masterBooks = plugin.cfg.getMasterBooks();
                            String bookID = String.join("", meta.getLore()).substring(17);

                            if(masterBooks.contains(bookID + ".pages")) {
                                if(plugin.getConfig().getBoolean("enable-master-books"))
                                    meta.setPages(masterBooks.getStringList(bookID + ".pages"));
                                item.setItemMeta(meta);
                                p.openBook(item);
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBookSave(PlayerEditBookEvent e) {
        Player p = e.getPlayer();
        FileConfiguration masterBooks = plugin.cfg.getMasterBooks();
        BookMeta meta = e.getNewBookMeta();
        BookMeta prevMeta = e.getPreviousBookMeta();
        List<String> lore = meta.getLore();

        if(lore == null) {
            if (plugin.getConfig().getBoolean("enable-master-books") && e.isSigning()) {
                List<String> newLore = new ArrayList<>();
                newLore.add(utils.chat("&6Master Book &7#" + getNextMasterBookID()));
                meta.setLore(newLore);
                e.setNewBookMeta(meta);
            }
        }else{
            String bookID = String.join("", lore).substring(17);
            boolean isClaimBook = utils.isClaimPage(meta.getPage(1));
            boolean wasClaimBook = utils.isClaimPage(prevMeta.getPage(1));

            masterBooks.set(bookID + ".pages", meta.getPages());
            plugin.cfg.saveMasterBooks();

            if(isClaimBook || wasClaimBook) {
                FileConfiguration claimData = plugin.cfg.getClaimData();
                Set<Block> toBeUnclaimed = new HashSet<>();
                Set<Block> toBeClaimed = new HashSet<>();

                for (String copy : masterBooks.getStringList(bookID + ".copies-on-lecterns")) {
                    String[] parts = copy.split("!");
                    String chunkID = parts[0];

                    if(!wasClaimBook && claimData.contains(chunkID)){
                        p.sendMessage(ChatColor.GRAY + "A copy of the master book was in a claimed chunk and has been corrupted!");
                    }else{
                        String environment = chunkID.split("\\|")[0];
                        String[] xyz = parts[1].split(",");
                        int x = Integer.parseInt(xyz[0]);
                        int y = Integer.parseInt(xyz[1]);
                        int z = Integer.parseInt(xyz[2]);

                        List<World> worlds = Bukkit.getWorlds();
                        Optional<World> world = worlds.stream().filter(w -> w.getEnvironment().name().equals(environment)).findFirst();

                        if(world.isPresent()) {
                            Block lectern = world.get().getBlockAt(x, y, z);

                            // If the copy is corrupted / a fake claim book
                            if(claimData.contains(chunkID))
                                if(Math.round(claimData.getDouble(chunkID + ".location.X")) != x ||
                                        Math.round(claimData.getDouble(chunkID + ".location.Y")) != y ||
                                        Math.round(claimData.getDouble(chunkID + ".location.Z")) != z)
                                    continue;

                            toBeUnclaimed.add(lectern);
                            if (isClaimBook)
                                toBeClaimed.add(lectern);
                        }else{
                            p.sendMessage("Something went wrong. Please contact a server admin.");
                        }
                    }
                }

                if(toBeUnclaimed.size() > 0)
                    utils.unclaimChunkBulk(toBeUnclaimed);
                if(toBeClaimed.size() > 0)
                    utils.claimChunkBulk(toBeClaimed, meta);
            }
        }
    }

    private long getNextMasterBookID() {
        FileConfiguration masterBooks = plugin.cfg.getMasterBooks();
        long nextID = masterBooks.getLong("next-id", 0);

        masterBooks.set("next-id", nextID + 1);
        plugin.cfg.saveMasterBooks();

        return nextID;
    }
}
