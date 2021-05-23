package hasjamon.block4block.command;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.sun.istack.internal.NotNull;
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
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.UUID;

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
        ItemStack whiteSheepHead = createPlayerHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmRmZTdjYzQ2ZDc0OWIxNTMyNjFjMWRjMTFhYmJmMmEzMTA4ZWExYmEwYjI2NTAyODBlZWQxNTkyZGNmYzc1YiJ9fX0=");

        inv.addItem(createItem(Material.GRASS_BLOCK, "Grass Block", "§aFirst line of the lore", "§bSecond line of the lore"));
        inv.addItem(createItem(Material.LECTERN, "Lectern", "§aFirst line of the lore", "§bSecond line of the lore"));
        inv.addItem(createItem(Material.WRITABLE_BOOK, "Book & Quill", "§aFirst line of the lore", "§bSecond line of the lore"));
        inv.addItem(createItem(Material.STONE, "Stone", "§aFirst line of the lore", "§bSecond line of the lore"));
        inv.addItem(createItem(Material.NAME_TAG, "Name Tag", "§aFirst line of the lore", "§bSecond line of the lore"));
        inv.addItem(createItem(whiteSheepHead, "Player Head", "§aFirst line of the lore", "§bSecond line of the lore"));
    }

    // Helper method for creating an item with a custom name and description
    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);

        return createItem(item, name, lore);
    }

    private ItemStack createItem(ItemStack item, String name, String... lore) {
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

    // Hack to create a custom PLAYER_HEAD from a base64-encoded texture
    @NotNull
    private ItemStack createPlayerHead(String base64EncodedString) {
        final ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        assert meta != null;
        GameProfile profile = new GameProfile(UUID.randomUUID(), null);
        profile.getProperties().put("textures", new Property("textures", base64EncodedString));
        try {
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        skull.setItemMeta(meta);
        return skull;
    }
}
