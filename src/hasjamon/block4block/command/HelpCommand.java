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
        inv = Bukkit.createInventory(null, 36, "Block4Block Help");

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
        ItemStack pigHead = createPlayerHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDFlZTc2ODFhZGYwMDA2N2YwNGJmNDI2MTFjOTc2NDEwNzVhNDRhZTJiMWMwMzgxZDVhYzZiMzI0NjIxMWJmZSJ9fX0=");
        ItemStack polarbearHead = createPlayerHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2Q4NzAyOTExZTYxNmMwZDMyZmJlNzc4ZDE5NWYyMWVjY2U5MDI1YmNiZDA5MTUxZTNkOTdhZjMxOTJhYTdlYyJ9fX0=");
        inv.setItem(3,createItem(Material.GRASS_BLOCK,
                "§bBasics: §eWhy can't I break blocks?",
                "§aMost blocks require you to have them in your off-hand or 1-9 hotbar/quickslots when breaking them.",
                "§bIt costs a block to break them.",
                "§cThis both creates more meaningful progression and reduces griefing significantly.",
                "§dConsider it Veteran Mode, if it was in the base game."
        ));
        inv.setItem(4,createItem(Material.OAK_LOG,
                "§bBasics: §eBlocks that can be broken.",
                "§fSome blocks can still be broken normally.",
                "§fTrees can be taken down and the wood will be the most basic building material",
                "§fUtilities such as crafting tables and beds are also exempt.",
                "§fSee a full list by clicking this block and following the link that pops up in the chat."
        ));
        inv.setItem(5,createItem(Material.STONE,
                "§bBasics: §eAdvanced Mining",
                "§7Stone still drops cobblestone and have a chance of dropping more than one.",
                "§7Smelting cobblestone back into stone makes it reusable.",
                "§7Use ravines and caves to go deep underground, before you have a proper collection of stone.",
                "§7Build ladders and platforms to reach ores in ravines.",
                "§7No more straight corridors! You will have to navigate around pockets of diorite and granite.",
                "§7Drop down signs and help yourself back to the exit. No tunneling out without the right blocks."
        ));
        inv.setItem(9,createItem(Material.SKELETON_SKULL,
                "§aLoot: §eSkeletons drop stone!",
                "§fStone is easiest obtained from skeletons.",
                "§fThey can be tough to fight and it's recommended to get a shield.",
                "§fCreating a farm for animals that drop leather may help you boost your defenses.",
                "§fIf its hard to get night time, go find dark areas or make one for them to spawn in.",
                "§fThe easiest way to gather lots of stone is to create a mob farm."
        ));
        inv.setItem(10,createItem(Material.SPAWNER,
                "§aLoot: §eSpawners drop",
                "§fYou can break and obtain spawners with any tool.",
                "§fNo silk-touch needed."
        ));
        inv.setItem(11,createItem(pigHead,
                "§aLoot: §eDirt from Pigs",
                "§fDirt can be gained from pigs.",
                "§fFarm it to terraform your area.",
                "§fGrassblocks can be changed into dirt with simple tricks.",
                "§fTry placing a block on top or using a hoe."
        ));
        inv.setItem(12,createItem(polarbearHead,
                "§aLoot: §eMore to discover!",
                "§fThere are more changes for you to seek out and discover.",
                "§fWhy not share your discoveries on Discord?"
        ));
        inv.setItem(15,createItem(Material.SAND,
                "§6Tricks: §eObtaining blocks affected by gravity.",
                "§fBlocks affected by gravity become drops, if they fall on torches.",
                "§fYou will have to find a way to break a block to make the blocks fall.",
                "§fIf you are swift you can place a torch in place as soon as they begin falling.",
                "§fMake sure they can't fall beyond the torch."
        ));
        inv.setItem(16,createItem(Material.WATER_BUCKET,
                "§6Tricks: §eObtaining blocks using water.",
                "§fFlowers, redstone wire and more become drops when interacting with water."
        ));
        inv.setItem(17,createItem(Material.PISTON,
                "§6Tricks: §eObtaining blocks using a piston",
                "§fWatermelons, pumpkins and bamboo will drop when pushed by a piston.",
                "§fIf a piston doesn't work, try with TNT."
        ));
        inv.setItem(19,createItem(Material.LECTERN,
                "§cClaim: §ePlacing a claim.",
                "§fUse F3+G to see chunk borders.",
                "§fPlace the lectern in the chunk you wish to claim.",
                "§fPut a claim book in the lectern to place your claim.",
                "§fYou cannot place a claim next to bedrock."
        ));
        inv.setItem(20,createItem(Material.BARRIER,
                "§cClaim: §eWhat does a claim do?",
                "§fClaims prevent non-members from placing blocks.",
                "§fPlayers can still break blocks inside your claim.",
                "§fBy holding TAB you can see how many claims you and others have.",
                "§fYou can steal a claim by taking the book or breaking the lectern."
        ));
        inv.setItem(21,createItem(Material.IRON_BLOCK,
                "§cClaim: §eProtecting a claim.",
                "§fProtect your claim by surrounding your lectern with blocks.",
                "§fChoose blocks you believe infiltrators won't expect.",
                "§fUse redstone traps and doors to stop them from reaching your lectern.",
                "§fNametag strong mobs and have them guard the lectern for you."
        ));
        inv.setItem(22,createItem(Material.WRITABLE_BOOK,
                "§cClaim: §eHow to make a claim book.",
                "§fUse a book and quill and follow the below format.",
                "§fYou need to include yourself in the list to be a part of the claim.",
                "§fYou can place a claim for others.",
                "§fYou will get a confirmation in chat when placing a proper claim book in the lectern.",
                "§cClaim Book format:",
                "§fclaim",
                "§fmembername1",
                "§fmembername2",
                "§fmembername3"
        ));
        inv.setItem(23,createItem(Material.WRITTEN_BOOK,
                "§cClaim: §eUsing a signed book for claiming.",
                "§fYou can claim using a signed book as well.",
                "§fSigned books identify the original author.",
                "§fCopy the signed book to easily create many claims.",
                "§fYou can't change the memberlist of signed books.",
                "§fYou can tell if it's the original or a copy.",
                "§fUse others signed books to create conflicts."
        ));
        inv.setItem(24,createItem(Material.REDSTONE_BLOCK,
                "§cClaim: §eBlocks that can be broken inside owned claim.",
                "§fWhile inside your claim, you will be able to break more blocks normally.",
                "§fThis applies mostly to redstone related items.",
                "§fSee a full list by clicking this block and following the link that pops up in the chat."
        ));
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
