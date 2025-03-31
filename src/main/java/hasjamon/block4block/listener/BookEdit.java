package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.events.MasterBookCreatedEvent;
import hasjamon.block4block.utils.utils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.*;

public class BookEdit implements Listener {
    private final Block4Block plugin;

    public BookEdit(Block4Block plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onOpenBook(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        Block clickedBlock = e.getClickedBlock();

        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (clickedBlock == null || clickedBlock.getType() != Material.LECTERN) {
                if (item != null) {
                    // If player is holding a book and quill
                    if (item.getType() == Material.WRITABLE_BOOK) {
                        // If the player isn't a member of any claims
                        if (!utils.hasClaims(p)) {
                            // Send the player instruction on what to do with the book
                            p.sendMessage(utils.chat("&cNOTE: &7To make a claim book, type &a\"claim\" &7at the top of the book!"));
                            p.sendMessage(utils.chat("&aThen write a player's ign on each line to add a member!"));
                            p.spigot().sendMessage(new ComponentBuilder(utils.chat("&aCLICK HERE to see an example"))
                                    .event(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://hasjamon.github.io/b4block/lists.html"))
                                    .create());
                        }
                    } else if (item.getType() == Material.WRITTEN_BOOK) {
                        BookMeta meta = (BookMeta) item.getItemMeta();

                        if (meta != null && meta.getLore() != null) {
                            // If the book is not a copy
                            if (!meta.hasGeneration() || meta.getGeneration() == BookMeta.Generation.ORIGINAL) {
                                item.setType(Material.WRITABLE_BOOK);
                                item.addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1); // Just to make it glow
                                BookMeta newMeta = (BookMeta) item.getItemMeta();
                                newMeta.setPages(meta.getPages());
                                newMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                                item.setItemMeta(newMeta);
                            } else {
                                FileConfiguration masterBooks = plugin.cfg.getMasterBooks();
                                String bookID = String.join("", meta.getLore()).substring(17);

                                if (masterBooks.contains(bookID + ".pages")) {
                                    if (plugin.getConfig().getBoolean("enable-master-books"))
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
    }

    @EventHandler
    public void onSaveBook(PlayerEditBookEvent e) {
        Player p = e.getPlayer();
        FileConfiguration masterBooks = plugin.cfg.getMasterBooks();
        BookMeta meta = e.getNewBookMeta();
        BookMeta prevMeta = e.getPreviousBookMeta();
        List<String> lore = meta.getLore();

        if (!plugin.getConfig().getBoolean("enable-master-books"))
            return;

        if (lore == null) {
            if (e.isSigning()) {
                // Check if the first page contains "claim"
                if (meta.getPages().size() > 0 && meta.getPages().get(0).contains("claim")) {
                    long nextID = getNextMasterBookID(false);
                    List<String> newLore = new ArrayList<>();
                    newLore.add(utils.chat("&6Master Book &7#" + nextID));
                    meta.setLore(newLore);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    e.setNewBookMeta(meta);
                    masterBooks.set(nextID + ".pages", meta.getPages());

                    plugin.cfg.saveMasterBooks();
                    plugin.pluginManager.callEvent(new MasterBookCreatedEvent(p));
                } else {
                    // If the first page does not have "claim", sign as a regular book
                    e.setNewBookMeta(meta);
                }
            }
        } else {
            String bookID = String.join("", lore).substring(17);
            boolean isClaimBook = utils.isClaimBook(meta);
            boolean wasClaimBook = utils.isClaimBook(prevMeta);

            masterBooks.set(bookID + ".pages", meta.getPages());
            plugin.cfg.saveMasterBooks();

            if (isClaimBook || wasClaimBook) {
                FileConfiguration claimData = plugin.cfg.getClaimData();
                Set<Block> toBeUnclaimed = new HashSet<>();
                Set<Block> toBeClaimed = new HashSet<>();
                Set<String> chunksToBeClaimed = new HashSet<>();

                for (String copy : masterBooks.getStringList(bookID + ".copies-on-lecterns")) {
                    String[] parts = copy.split("!");
                    String claimID = parts[0];
                    String[] xyz = parts[1].split(",");
                    String environment = claimID.split("\\|")[0];
                    World.Environment env = World.Environment.valueOf(environment);

                    // If the book is turned into a claim book, but the chunk is already claimed
                    if (!wasClaimBook && (claimData.contains(claimID) || chunksToBeClaimed.contains(claimID))) {
                        if (!plugin.getConfig().getBoolean("hide-coords-globally") && utils.showCoordsInMsgs(p)) {
                            p.sendMessage(ChatColor.GRAY + "A copy of the master book at (" +
                                    String.join(", ", xyz) + ") in " +
                                    utils.getWorldName(env) + " was in a claimed chunk and has been corrupted!");
                        } else {
                            p.sendMessage(ChatColor.GRAY + "A copy of the master book at [hidden] in " +
                                    utils.getWorldName(env) + " was in a claimed chunk and has been corrupted!");
                        }
                    } else {
                        int x = Integer.parseInt(xyz[0]);
                        int y = Integer.parseInt(xyz[1]);
                        int z = Integer.parseInt(xyz[2]);

                        List<World> worlds = Bukkit.getWorlds();
                        Optional<World> world = worlds.stream().filter(w -> w.getEnvironment().name().equals(environment)).findFirst();

                        if (world.isPresent()) {
                            Block lectern = world.get().getBlockAt(x, y, z);

                            // If the copy is corrupted / a fake claim book, do nothing
                            if (claimData.contains(claimID) || chunksToBeClaimed.contains(claimID))
                                if (Math.round(claimData.getDouble(claimID + ".location.X")) != x ||
                                        Math.round(claimData.getDouble(claimID + ".location.Y")) != y ||
                                        Math.round(claimData.getDouble(claimID + ".location.Z")) != z)
                                    continue;

                            toBeUnclaimed.add(lectern);
                            if (isClaimBook) {
                                chunksToBeClaimed.add(claimID);
                                toBeClaimed.add(lectern);
                            }
                        } else {
                            p.sendMessage("Something went wrong. Please contact a server admin.");
                        }
                    }
                }

                if (toBeUnclaimed.size() > 0)
                    utils.unclaimChunkBulk(toBeUnclaimed, bookID, meta);
                if (toBeClaimed.size() > 0)
                    utils.claimChunkBulk(toBeClaimed, meta, bookID);
            }
        }
    }

    private long getNextMasterBookID(boolean saveConfig) {
        FileConfiguration masterBooks = plugin.cfg.getMasterBooks();
        long nextID = masterBooks.getLong("next-id", 0);

        masterBooks.set("next-id", nextID + 1);

        if (saveConfig)
            plugin.cfg.saveMasterBooks();

        return nextID;
    }
}
