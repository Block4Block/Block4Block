package hasjamon.block4block.command;

import hasjamon.block4block.Block4Block;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Arrays;

public class HelpCommand implements CommandExecutor, Listener {
    private final Inventory inv;

    public HelpCommand(Block4Block plugin) {
        plugin.pluginManager.registerEvents(this, plugin);

        // Create a new inventory with no owner (as this isn't a real inventory) and a size of nine
        inv = Bukkit.createInventory(null, 9, "Block4Block Help");

        // Add help items to the inventory
        initItems();
    }

    // Open the inventory when the b4bhelp command is sent
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if(sender instanceof Player) {
            ((Player) sender).openInventory(inv);
            return true;
        }
        return false;
    }

    // Creates items and adds them to the help inventory
    public void initItems() {
        inv.addItem(createItem(Material.GRASS_BLOCK, "Grass Block", "§aFirst line of the lore", "§bSecond line of the lore"));
        inv.addItem(createItem(Material.LECTERN, "Lectern", "§aFirst line of the lore", "§bSecond line of the lore"));
        inv.addItem(createItem(Material.WRITABLE_BOOK, "Book & Quill", "§aFirst line of the lore", "§bSecond line of the lore"));
        inv.addItem(createItem(Material.STONE, "Stone", "§aFirst line of the lore", "§bSecond line of the lore"));
        inv.addItem(createItem(Material.NAME_TAG, "Name Tag", "§aFirst line of the lore", "§bSecond line of the lore"));
    }

    // Helper method for creating an item with a custom name and description
    protected ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();

        // Update the item's name and description
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);

        return item;
    }

    // Disable picking up or swapping items in the help inventory
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getInventory() == inv)
            e.setCancelled(true);
    }

    // Disable dragging items in the help inventory
    @EventHandler
    public void onInventoryClick(InventoryDragEvent e) {
        if (e.getInventory() == inv)
            e.setCancelled(true);
    }
}
