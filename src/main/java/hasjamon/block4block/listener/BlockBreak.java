package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BlockBreak implements Listener {
    private final Block4Block plugin;

    public BlockBreak(Block4Block plugin) {
        this.plugin = plugin;
    }

    // ----- Main Block Break Event -----
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        FileConfiguration cfg = plugin.getConfig();
        boolean isInsideClaim = plugin.cfg.getClaimData().contains(utils.getClaimID(b.getLocation()));

        // Prevent breaking chests in someone else's claim
        if (isInsideClaim && b.getType() == Material.CHEST) {
            String[] members = utils.getMembers(b.getLocation());
            boolean isMember = utils.isMemberOfClaim(members, p);
            boolean canOpen = utils.canOpenChest(b, p);

            if (!isMember && !canOpen) {
                e.setCancelled(true);
                String message = utils.chat("&cThe chest is shielded by the blocks above it.");
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                return;
            }
        }

        // Ignore lecterns and creative mode
        if (b.getType() == Material.LECTERN || p.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // Apply lore to natural block drops
        applyLoreToBlockDrops(b, p, e);
    }

    // ----- Apply Lore to Block Drops -----
    private void applyLoreToBlockDrops(Block b, Player p, BlockBreakEvent e) {
        Collection<ItemStack> drops = b.getDrops(p.getInventory().getItemInMainHand()); // Natural drops
        String loreLabel = getBlockMarkerLabel(p, b);

        // Cancel the default drop to prevent double drops
        e.setDropItems(false);

        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR) {
                continue; // Skip invalid drops
            }

            // Clean old lore and apply new lore
            cleanLore(drop);
            applyLoreMark(drop, loreLabel);

            // Drop the modified item naturally
            dropItemNaturally(b.getLocation(), drop);
        }
    }

    // ----- Drop Correct Item with Metadata -----
    private void dropItemNaturally(Location loc, ItemStack item) {
        loc.getWorld().dropItemNaturally(loc, item);
    }

    // ----- Clean Lore from Item -----
    private void cleanLore(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return;

        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        List<String> lore = meta.getLore();
        if (lore == null) return;

        // Remove any existing markers
        lore.removeIf(line -> {
            String clean = normalizeLore(line);
            return clean.equals("free to break") ||
                    clean.equals("free in claim") ||
                    clean.equals("block for block") ||
                    clean.equals("f2b") ||
                    clean.equals("finc") ||
                    clean.equals("b4b");
        });

        meta.setLore(lore);
        stack.setItemMeta(meta);
    }

    // ----- Apply Lore Label to Item -----
    private void applyLoreMark(ItemStack stack, String label) {
        if (stack == null || !isPlaceable(stack)) return;

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        String normalizedLabel = "§7" + label;

        // Avoid duplicate lore entries
        for (String line : lore) {
            if (normalizeLore(line).equals(normalizeLore(label))) {
                return;
            }
        }

        lore.add(normalizedLabel);
        meta.setLore(lore);
        stack.setItemMeta(meta);
    }

    // ----- Normalize Lore String -----
    private String normalizeLore(String loreLine) {
        return loreLine.replace("§7", "").trim().toLowerCase();
    }

    // ----- Determine Lore Label for Block -----
    private String getBlockMarkerLabel(Player p, Block b) {
        FileConfiguration cfg = plugin.getConfig();
        boolean useLong = cfg.getBoolean("use-long-form");
        List<?> freeInClaim = cfg.getList("blacklisted-claim-blocks");
        List<?> freeBlocks = cfg.getList("blacklisted-blocks");
        boolean isInClaim = plugin.cfg.getClaimData().contains(utils.getClaimID(b.getLocation()));

        if (isInClaim && freeInClaim != null && freeInClaim.contains(b.getType().toString())) {
            return useLong ? "Free in Claim" : "FINC";
        } else if (freeBlocks != null && freeBlocks.contains(b.getType().toString())) {
            return useLong ? "Free to Break" : "F2B";
        } else {
            return useLong ? "Block for Block" : "B4B";
        }
    }

    // ----- Check if Item is Placeable -----
    private boolean isPlaceable(ItemStack stack) {
        if (stack == null) return false;
        Material mat = stack.getType();
        return mat.isBlock() ||
                mat == Material.PAINTING ||
                mat == Material.ITEM_FRAME ||
                mat == Material.GLOW_ITEM_FRAME;
    }
}
