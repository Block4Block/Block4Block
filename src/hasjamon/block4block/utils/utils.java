package hasjamon.block4block.utils;

import hasjamon.block4block.Block4Block;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import java.util.*;

public class utils {
    public static Block4Block plugin = Block4Block.getInstance();
    private static final Map<Material, Material> specialTypes;

    static {
        specialTypes = new HashMap<>();
        specialTypes.put(Material.REDSTONE_WIRE, Material.REDSTONE);
        specialTypes.put(Material.WALL_TORCH, Material.TORCH);
        specialTypes.put(Material.REDSTONE_WALL_TORCH, Material.REDSTONE_TORCH);
        specialTypes.put(Material.TALL_SEAGRASS, Material.SEAGRASS);
        specialTypes.put(Material.CHORUS_PLANT, Material.CHORUS_FRUIT);
        specialTypes.put(Material.KELP_PLANT, Material.KELP);
        specialTypes.put(Material.BAMBOO_SAPLING, Material.BAMBOO);
        specialTypes.put(Material.WARPED_WALL_SIGN, Material.WARPED_SIGN);
        specialTypes.put(Material.ACACIA_WALL_SIGN, Material.ACACIA_SIGN);
        specialTypes.put(Material.BIRCH_WALL_SIGN, Material.BIRCH_SIGN);
        specialTypes.put(Material.CRIMSON_WALL_SIGN, Material.CRIMSON_SIGN);
        specialTypes.put(Material.JUNGLE_WALL_SIGN, Material.JUNGLE_SIGN);
        specialTypes.put(Material.DARK_OAK_WALL_SIGN, Material.DARK_OAK_SIGN);
        specialTypes.put(Material.OAK_WALL_SIGN, Material.OAK_SIGN);
        specialTypes.put(Material.SPRUCE_WALL_SIGN, Material.SPRUCE_SIGN);
        specialTypes.put(Material.SKELETON_WALL_SKULL, Material.SKELETON_SKULL);
        specialTypes.put(Material.WITHER_SKELETON_WALL_SKULL, Material.WITHER_SKELETON_SKULL);
        specialTypes.put(Material.ZOMBIE_WALL_HEAD, Material.ZOMBIE_HEAD);
        specialTypes.put(Material.PLAYER_WALL_HEAD, Material.PLAYER_HEAD);
        specialTypes.put(Material.CREEPER_WALL_HEAD, Material.CREEPER_HEAD);
        specialTypes.put(Material.DRAGON_WALL_HEAD, Material.DRAGON_HEAD);
        specialTypes.put(Material.BRAIN_CORAL_WALL_FAN, Material.BRAIN_CORAL_FAN);
        specialTypes.put(Material.BUBBLE_CORAL_WALL_FAN, Material.BUBBLE_CORAL_FAN);
        specialTypes.put(Material.DEAD_BRAIN_CORAL_WALL_FAN, Material.DEAD_BRAIN_CORAL_FAN);
        specialTypes.put(Material.DEAD_BUBBLE_CORAL_WALL_FAN, Material.DEAD_BUBBLE_CORAL_FAN);
        specialTypes.put(Material.DEAD_FIRE_CORAL_WALL_FAN, Material.DEAD_FIRE_CORAL_FAN);
        specialTypes.put(Material.DEAD_HORN_CORAL_WALL_FAN, Material.DEAD_HORN_CORAL_FAN);
        specialTypes.put(Material.DEAD_TUBE_CORAL_WALL_FAN, Material.DEAD_TUBE_CORAL_FAN);
        specialTypes.put(Material.FIRE_CORAL_WALL_FAN, Material.FIRE_CORAL_FAN);
        specialTypes.put(Material.HORN_CORAL_WALL_FAN, Material.HORN_CORAL_FAN);
        specialTypes.put(Material.TUBE_CORAL_WALL_FAN, Material.TUBE_CORAL_FAN);
        specialTypes.put(Material.BLACK_WALL_BANNER, Material.BLACK_BANNER);
        specialTypes.put(Material.BLUE_WALL_BANNER, Material.BLUE_BANNER);
        specialTypes.put(Material.BROWN_WALL_BANNER, Material.BROWN_BANNER);
        specialTypes.put(Material.CYAN_WALL_BANNER, Material.CYAN_BANNER);
        specialTypes.put(Material.GRAY_WALL_BANNER, Material.GRAY_BANNER);
        specialTypes.put(Material.GREEN_WALL_BANNER, Material.GREEN_BANNER);
        specialTypes.put(Material.LIGHT_BLUE_WALL_BANNER, Material.LIGHT_BLUE_BANNER);
        specialTypes.put(Material.LIGHT_GRAY_WALL_BANNER, Material.LIGHT_GRAY_BANNER);
        specialTypes.put(Material.LIME_WALL_BANNER, Material.LIME_BANNER);
        specialTypes.put(Material.MAGENTA_WALL_BANNER, Material.MAGENTA_BANNER);
        specialTypes.put(Material.ORANGE_WALL_BANNER, Material.ORANGE_BANNER);
        specialTypes.put(Material.PINK_WALL_BANNER, Material.PINK_BANNER);
        specialTypes.put(Material.PURPLE_WALL_BANNER, Material.PURPLE_BANNER);
        specialTypes.put(Material.RED_WALL_BANNER, Material.RED_BANNER);
        specialTypes.put(Material.WHITE_WALL_BANNER, Material.WHITE_BANNER);
        specialTypes.put(Material.YELLOW_WALL_BANNER, Material.YELLOW_BANNER);
        specialTypes.put(Material.TRAPPED_CHEST, Material.CHEST);
        specialTypes.put(Material.SWEET_BERRY_BUSH, Material.SWEET_BERRIES);
        specialTypes.put(Material.INFESTED_STONE, Material.STONE);
        specialTypes.put(Material.INFESTED_COBBLESTONE, Material.COBBLESTONE);
        specialTypes.put(Material.INFESTED_STONE_BRICKS, Material.STONE_BRICKS);
        specialTypes.put(Material.INFESTED_CRACKED_STONE_BRICKS, Material.CRACKED_STONE_BRICKS);
        specialTypes.put(Material.INFESTED_MOSSY_STONE_BRICKS, Material.MOSSY_STONE_BRICKS);
        specialTypes.put(Material.INFESTED_CHISELED_STONE_BRICKS, Material.CHISELED_STONE_BRICKS);
    }

    public static String chat(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static String getChunkID(Chunk chunk) {
        return chunk.getX() + "," + chunk.getZ();
    }

    public static String[] getMembers(Chunk chunk) {
        String members = plugin.cfg.getClaimData().getString(getChunkID(chunk) + ".members");

        if (members != null)
            return members.split("\\n");
        else
            return null;
    }

    public static boolean isClaimBlock(Block b) {
        FileConfiguration claimData = plugin.cfg.getClaimData();
        String cID = getChunkID(b.getChunk());

        if (claimData.get(cID + ".location.X").equals(b.getLocation().getX()))
            if (claimData.get(cID + ".location.Y").equals(b.getLocation().getY()))
                if (claimData.get(cID + ".location.Z").equals(b.getLocation().getZ()))
                    return true;
        return false;
    }

    public static void claimChunk(Block block, Player p, ItemStack book) {
        BookMeta bookmeta = (BookMeta) book.getItemMeta();

        if (bookmeta != null) {
            List<String> pages = bookmeta.getPages();
            List<String> members = new ArrayList<String>();

            // Collect a list of members
            for (String page : pages) {
                // If it isn't a claim page, stop looking for members
                if (!page.substring(0, 5).equalsIgnoreCase("claim"))
                    break;

                String[] lines = page.split("\\n");

                for (int i = 1; i < lines.length; i++) {
                    String member = lines[i].trim();

                    // If the member name is valid
                    if(!member.contains(" ") && !member.isEmpty())
                        members.add(member);
                }
            }

            if(members.size() > 0) {
                FileConfiguration claimData = plugin.cfg.getClaimData();
                Location blockLoc = block.getLocation();
                String chunkID = utils.getChunkID(block.getChunk());
                String membersString = String.join("\n", members);

                claimData.set(chunkID + ".location.X", blockLoc.getX());
                claimData.set(chunkID + ".location.Y", blockLoc.getY());
                claimData.set(chunkID + ".location.Z", blockLoc.getZ());
                claimData.set(chunkID + ".members", membersString);
                plugin.cfg.saveClaimData(); // Save members to claimdata.yml

                p.sendMessage(utils.chat("&aThis chunk has now been claimed!"));
                p.sendMessage(utils.chat("&aMembers who can access this chunk:"));
                for (String member : members)
                    p.sendMessage(ChatColor.GRAY + " - " + member);
            }else{
                p.sendMessage(utils.chat("&cHINT: Add \"claim\" at the top of the first page, followed by a list members, to claim this chunk!"));
            }
        }
    }

    public static void unclaimChunk(Player p, Block block, Boolean wasExploded) {
        plugin.cfg.getClaimData().set(utils.getChunkID(block.getChunk()), null);
        plugin.cfg.saveClaimData();
        if (p != null && !wasExploded)
            p.sendMessage(utils.chat("&aYou have removed this claim!"));
    }

    public static void b4bCheck(Player p, Block b, BlockBreakEvent e, Boolean noloot, boolean requiresBlock) {
        if(requiresBlock) {
            Material requiredType = b.getType();

            if (specialTypes.containsKey(requiredType))
                requiredType = specialTypes.get(requiredType);

            if (p.getInventory().getItemInOffHand().getType() == requiredType) {
                p.getInventory().getItemInOffHand().setAmount(p.getInventory().getItemInOffHand().getAmount() - 1);
            } else {
                boolean itemInInventory = false;
                for (int i = 0; i < 9; i++) {
                    ItemStack item = p.getInventory().getItem(i);
                    if (item != null) {
                        if (item.getType() == requiredType) {
                            item.setAmount(item.getAmount() - 1);
                            itemInInventory = true;
                            break;
                        }
                    }
                }

                if (!itemInInventory){
                    e.setCancelled(true);
                    p.sendMessage(utils.chat("&aYou need &c" + requiredType + " &ain your quick-slots to break this!"));
                    return;
                }
            }
        }

        if(noloot)
            b.setType(Material.AIR);
    }
}
