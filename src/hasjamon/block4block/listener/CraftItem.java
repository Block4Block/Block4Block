package hasjamon.block4block.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

public class CraftItem implements Listener {
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent e) {
        if(e.getWhoClicked() instanceof Player player) {
            ItemStack result = e.getInventory().getResult();

            if (result != null && result.getType() == Material.WRITABLE_BOOK) {
                var meta = (BookMeta) result.getItemMeta();

                if(meta != null) {
                    meta.setPages("claim\n" + player.getName() + "\n\n" +
                            "Place this book on a lectern to claim the chunk it's in.\n\n"+
                            "If you have teammates, write each of their names on their own line.\n\n"+
                            "Press F3+G to view chunk borders.");
                    result.setItemMeta(meta);
                }
            }
        }
    }
}