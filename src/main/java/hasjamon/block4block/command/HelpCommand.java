package hasjamon.block4block.command;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import hasjamon.block4block.Block4Block;
import hasjamon.block4block.events.HelpCmdSucceededEvent;
import hasjamon.block4block.utils.utils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class HelpCommand implements CommandExecutor, Listener {
    private final Block4Block plugin;
    private final Inventory inv;
    private final List<ItemStack> clickableItems = new ArrayList<>();

    public HelpCommand(Block4Block plugin) {
        this.plugin = plugin;

        plugin.pluginManager.registerEvents(this, plugin);

        // Create a new inventory with no owner (as this isn't a real inventory) and a size of nine
        inv = Bukkit.createInventory(null, 36, "Block4Block Help");

        // Add help items to the inventory
        initItems();
    }

    // Open the inventory when the b4bhelp command is sent
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if(sender instanceof Player) {
            Player p = (Player) sender;

            p.sendMessage(ChatColor.GOLD + "Hover over each item and read its description.");
            p.sendMessage(ChatColor.GRAY + "If you can't read the text; go to options -> video settings -> GUI scale (recommended: 2).");
            p.openInventory(inv);

            plugin.pluginManager.callEvent(new HelpCmdSucceededEvent(p));
            return true;
        }
        return false;
    }

    // Creates items and adds them to the help inventory
    public void initItems() {
        ItemStack whiteSheepHead = createPlayerHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmRmZTdjYzQ2ZDc0OWIxNTMyNjFjMWRjMTFhYmJmMmEzMTA4ZWExYmEwYjI2NTAyODBlZWQxNTkyZGNmYzc1YiJ9fX0=");
        ItemStack pigHead = createPlayerHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDFlZTc2ODFhZGYwMDA2N2YwNGJmNDI2MTFjOTc2NDEwNzVhNDRhZTJiMWMwMzgxZDVhYzZiMzI0NjIxMWJmZSJ9fX0=");
        ItemStack polarbearHead = createPlayerHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2Q4NzAyOTExZTYxNmMwZDMyZmJlNzc4ZDE5NWYyMWVjY2U5MDI1YmNiZDA5MTUxZTNkOTdhZjMxOTJhYTdlYyJ9fX0=");

        // Create items with clickable behaviour
        ItemStack oakLogItem = createItem(Material.OAK_LOG,
                "§bBasics: §eBlocks that can be broken.",
                "§fSome blocks can still be broken normally.",
                "§7Trees can be broken down freely.",
                "§7Use logs for any temporary structures.",
                "§7Utilities such as crafting tables and beds are also exempt.",
                "§cClick this item for a full list in chat."
        );
        ItemStack redstoneBlock = createItem(Material.REDSTONE_BLOCK,
                "§cClaim: §eBlocks that can be broken inside owned claim.",
                "§7Inside your claim, you can break more blocks normally.",
                "§7This applies mostly to redstone related items.",
                "§cClick this item for a full list in chat."
        );
        ItemStack writableBook = createItem(Material.WRITABLE_BOOK,
                "§cClaim: §eHow to make a claim book.",
                "§7Use a book and quill and follow the below format.",
                "§7Include yourself in the list to be a part of the claim.",
                "§7You can place a claim for others.",
                "§7See chat when placing your claim.",
                "§7If done correctly, your memberlist will appear.",
                "§cClick this item for a link to an example."
        );
        clickableItems.add(oakLogItem);
        clickableItems.add(redstoneBlock);
        clickableItems.add(writableBook);

        inv.setItem(1,createItem(Material.STRUCTURE_VOID,
                "§bBasics: §eGrace Period",
                "§7Within 5 seconds of placing a block you can break it freely.",
                "§7Other players can break it freely as well.",
                "§7Be sure to correct building mistakes as they happen."
        ));
        inv.setItem(3,createItem(Material.GRASS_BLOCK,
                "§bBasics: §eWhy can't I break blocks?",
                "§7Most blocks require you to have them when breaking them.",
                "§7It costs a block to break them.",
                "§7The item has to be in 1-9 or off-hand.",
                "§7Experience meaningful progression",
                "§7and significantly reduced griefing.",
                "§7Consider it a Veteran Mode. Enjoy!"
        ));
        inv.setItem(4, oakLogItem);
        inv.setItem(5,createItem(Material.STONE,
                "§bBasics: §eAdvanced Mining",
                "§7Stone still drops cobble.",
                "§710% chance of double drop.",
                "§7Smelting cobble back into stone makes it reusable.",
                "§7Use ravines and caves to reach a low y.",
                "§7Build ladders and platforms to reach ores in ravines.",
                "§7You will have to navigate around diorite and granite.",
                "§7No tunneling out without the right blocks.",
                "§7Use signs to find back. "

        ));
        inv.setItem(7,createItem(Material.RED_BED,
                "§bCommand: §e/bed",
                "§7Be given a bed.",
                "§7Has a 24 hour cooldown."
        ));
        inv.setItem(9,createItem(Material.SKELETON_SKULL,
                "§aLoot: §eSkeletons drop stone",
                "§7Stone is easiest obtained from skeletons.",
                "§7It's recommended to get a shield.",
                "§7Farming leather may help you boost your defenses.",
                "§7Seek dark areas or make one for enemies to spawn.",
                "§7Mobfarms are the easiest way to get a lot of stone."
        ));
        inv.setItem(10,createItem(Material.SPAWNER,
                "§aLoot: §eSpawners drop",
                "§7You can break and obtain spawners with any pickaxe.",
                "§7No silk-touch needed.",
                "§7Find a skeleton spawner to increase your stone production."
        ));
        inv.setItem(11,createItem(pigHead,
                "§aLoot: §ePigs drop dirt",
                "§7Dirt can be gained from pigs.",
                "§7Farm it to terraform your area.",
                "§7Use tricks to change grassblocks into dirt.",
                "§7Try placing a block on top or using a hoe."
        ));
        inv.setItem(12,createItem(polarbearHead,
                "§aLoot: §eThere is more",
                "§7There are more changes for you to seek out and discover.",
                "§7consider sharing your discoveries on Discord."
        ));
        inv.setItem(15,createItem(Material.SAND,
                "§6Tricks: §eObtain blocks affected by gravity.",
                "§7Blocks affected by gravity become drops, if they fall on torches.",
                "§7You will have to find a way to break a block to make the blocks fall.",
                "§7Place the torch as soon as they begin falling.",
                "§7Make sure they can't fall beyond the torch."
        ));
        inv.setItem(16,createItem(Material.WATER_BUCKET,
                "§6Tricks: §eObtain blocks using water.",
                "§7Flowers become drops when interacting with water.",
                "§7Redstone wire and more is affected as well."
        ));
        inv.setItem(17,createItem(Material.PISTON,
                "§6Tricks: §eObtaining blocks using a piston",
                "§7Watermelons, pumpkins and bamboo will drop when pushed by a piston.",
                "§7If a piston doesn't work, try with TNT."
        ));
        inv.setItem(19,createItem(Material.LECTERN,
                "§cClaim: §ePlacing a claim.",
                "§7Use F3+G to see chunk borders.",
                "§7Place the lectern in the chunk you wish to claim.",
                "§7Put a claim book in the lectern to place your claim.",
                "§7You cannot place a claim next to bedrock."
        ));
        inv.setItem(20,createItem(Material.BARRIER,
                "§cClaim: §eWhat does a claim do?",
                "§7Claims prevent non-members from placing blocks.",
                "§7Players can still break blocks inside your claim.",
                "§7By holding TAB you can see how many claims you and others have.",
                "§7You can steal a claim by taking the book or breaking the lectern.",
                "§7To break a lectern, remove any blocks in direct contact with it first. Bottom is excluded."
        ));
        inv.setItem(21,createItem(Material.IRON_BLOCK,
                "§cClaim: §eProtecting a claim.",
                "§7Protect your claim by surrounding your lectern with blocks.",
                "§7Choose blocks you believe infiltrators won't expect.",
                "§7Use redstone traps and doors to stop them from reaching your lectern.",
                "§7Nametag strong mobs and have them guard the lectern for you.",
                "§7Blocks affected by gravity can be pillared on each side of the lectern.",
                "§7Be aware the lectern breaks gravity blocks landing on it."
        ));
        inv.setItem(22,writableBook);
        inv.setItem(23,createItem(Material.BOOK,
                "§cClaim: §eUsing a signed book for claiming.",
                "§7You can claim using a signed book as well.",
                "§7Signed books identify the original author.",
                "§7Copy the signed book to easily create many claims.",
                "§7You can't change the memberlist of signed books.",
                "§7You can tell if it's the original or a copy.",
                "§7Use others' signed books to create conflicts."
        ));
        inv.setItem(24,redstoneBlock);
        inv.setItem(30,createItem(Material.EGG,
                "§dSecret: §eChickens lay many eggs."
        ));
        inv.setItem(32,createItem(whiteSheepHead,
                "§dSecret: §eSheep are colorful and so are blocks."
        ));
    }

    // Helper method for creating an item with a custom name and description
    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);

        return createItem(item, name, lore);
    }

    private ItemStack createItem(ItemStack item, String name, String... lore) {
        ItemMeta meta = item.getItemMeta();

        if(meta != null) {
            // Update the item's name and description
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }

        return item;
    }

    // Disable picking up or swapping items in the help inventory
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getInventory() == inv){
            HumanEntity whoClicked = e.getWhoClicked();

            if(clickableItems.contains(e.getCurrentItem()))
                whoClicked.spigot().sendMessage(new ComponentBuilder(utils.chat("&aCLICK HERE for more information"))
                        .event(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://hasjamon.github.io/b4block/lists.html" ))
                        .create());

            e.setCancelled(true);
        }
    }

    // Disable dragging items in the help inventory
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getInventory() == inv)
            e.setCancelled(true);
    }

    // Hack to create a custom PLAYER_HEAD from a base64-encoded texture
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
