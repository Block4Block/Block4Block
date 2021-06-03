package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import java.util.ArrayList;
import java.util.List;

public class BookEdit implements Listener {
    private final Block4Block plugin;

    public BookEdit(Block4Block plugin){
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e){
        Player p = e.getPlayer();

        // If player right clicks air
        if(e.getAction().equals(Action.RIGHT_CLICK_AIR)){
            ItemStack item = e.getItem();

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
                        if(!meta.hasGeneration() || meta.getGeneration() == BookMeta.Generation.ORIGINAL){
                            item.setType(Material.WRITABLE_BOOK);
                            item.setItemMeta(meta);
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
        List<String> lore = meta.getLore();

        if(lore == null) {
            /*if (e.isSigning()) {
                List<String> lore = new ArrayList<>();
                lore.add(utils.chat("&6Master Book &7#" + getNextMasterBookID()));
                meta.setLore(lore);
                e.setNewBookMeta(meta);
            }*/
        }else{
            String allLore = String.join("", lore);
            String bookID = allLore.substring(17);

            masterBooks.set(bookID + ".pages", meta.getPages());

            /*
            TODO: 1. When a copy is placed on a lectern (whether it's currently a claim book or not), add it to a list (config file). When it's removed from the lectern, remove it from the list.
            TODO: 2. When a copy is opened, update its content before it's shown.
            TODO: 3. When a master book is saved or signed, save its pages (config file) and update claims by copies already placed on lecterns.
            TODO: Make sure claims are not overwritten if another claim book already exists in a chunk. Destroy copy instead?
            TODO: (unrelated) reduce number of getChunk calls by using the getChunkID function that takes coordinates whenever possible.
             */
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
