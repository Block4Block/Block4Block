package hasjamon.block4block.listener;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MapCraft implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent e){
        Inventory inv = e.getClickedInventory();

        if(inv != null && inv.getType() == InventoryType.CARTOGRAPHY){
            ItemStack result = inv.getItem(2);
            ItemStack ingredient = inv.getItem(1);

            if(result != null && result.getType() == Material.FILLED_MAP && ingredient != null && ingredient.getType() == Material.PAPER){
                ItemMeta meta = result.getItemMeta();

                if(meta != null){
                    meta.setLore(null);
                    result.setItemMeta(meta);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPrepareItemCraft(PrepareItemCraftEvent e){
        if(!e.isRepair()){
            ItemStack result = e.getInventory().getResult();

            if(result != null && result.getType() == Material.FILLED_MAP){
                ItemMeta meta = result.getItemMeta();

                if(meta != null) {
                    meta.setLore(null);
                    result.setItemMeta(meta);
                }
            }
        }
    }
}
