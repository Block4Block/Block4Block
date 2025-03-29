package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

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

        // Check if the broken block is a spawner
        if (b.getType() == Material.SPAWNER) {
            handleSpawnerBreak(e, p, b);
            return;
        }

        // Apply lore to natural block drops
        applyLoreToBlockDrops(b, p, e);
    }

    // ----- Handle Spawner Break -----
    private void handleSpawnerBreak(BlockBreakEvent e, Player p, Block b) {
        CreatureSpawner spawner = (CreatureSpawner) b.getState();
        EntityType spawnType = spawner.getSpawnedType();
        String expectedSpawnerType = spawnType.name();
        ItemStack spawnerItem = new ItemStack(Material.SPAWNER);
        ItemMeta itemMeta = spawnerItem.getItemMeta();
        boolean isInsideClaim = plugin.cfg.getClaimData().contains(utils.getClaimID(b.getLocation()));

        // Check if player is allowed to break inside a claim
        if (isInsideClaim) {
            String[] members = utils.getMembers(b.getLocation());
            boolean isMember = utils.isMemberOfClaim(members, p);

            if (!isMember) {
                if (!hasValidSpawnerItem(p, expectedSpawnerType)) {
                    e.setCancelled(true);
                    String message = utils.chat("&aSpend &c" + expectedSpawnerType + "_SPAWNER &afrom your hotbar to break this!");
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                    return;
                }
            }
        }

        // Apply spawner metadata and lore
        if (itemMeta != null) {
            itemMeta.setDisplayName(utils.prettifyEnumName(spawnType) + " Spawner");
            itemMeta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "spawnType"), PersistentDataType.STRING, spawnType.name());
            spawnerItem.setItemMeta(itemMeta);

            // Determine and apply the correct lore marker based on config
            String loreLabel = getBlockMarkerLabel(p, b);
            applyLoreMark(spawnerItem, loreLabel);

            // Drop spawner with metadata and lore
            b.getWorld().dropItemNaturally(b.getLocation(), spawnerItem);
        }
    }

    // ----- Check for Valid Spawner Item in Hand or Hotbar -----
    private boolean hasValidSpawnerItem(Player p, String expectedSpawnerType) {
        for (int i = -2; i < 9; i++) {
            ItemStack item = (i == -2) ? p.getInventory().getItemInOffHand() : (i == -1) ? p.getInventory().getItemInMainHand() : p.getInventory().getItem(i);

            if (item != null && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "spawnType"), PersistentDataType.STRING)) {
                    String storedType = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "spawnType"), PersistentDataType.STRING);
                    if (storedType != null && storedType.equals(expectedSpawnerType)) {
                        removeItem(p, i);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ----- Remove Item After Valid Check -----
    private void removeItem(Player p, int slot) {
        if (slot == -2) {
            ItemStack offhandItem = p.getInventory().getItemInOffHand();
            offhandItem.setAmount(offhandItem.getAmount() - 1);
            p.getInventory().setItemInOffHand(offhandItem.getAmount() > 0 ? offhandItem : null);
        } else if (slot >= 0) {
            ItemStack itemToRemove = p.getInventory().getItem(slot);
            itemToRemove.setAmount(itemToRemove.getAmount() - 1);
            p.getInventory().setItem(slot, itemToRemove.getAmount() > 0 ? itemToRemove : null);
        }
    }

    // ----- Apply Lore to Block Drops -----
    private void applyLoreToBlockDrops(Block b, Player p, BlockBreakEvent e) {
        Collection<ItemStack> drops = b.getDrops(p.getInventory().getItemInMainHand());
        String loreLabel = getBlockMarkerLabel(p, b);

        // Cancel default drops to prevent duplication
        e.setDropItems(false);

        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR) {
                continue;
            }

            cleanLore(drop);
            applyLoreMark(drop, loreLabel);
            dropItemNaturally(b.getLocation(), drop);
        }
    }

    // ----- Drop Item with Metadata -----
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

    // ----- Apply Lore Marker to Item -----
    private void applyLoreMark(ItemStack stack, String label) {
        if (stack == null || !isPlaceable(stack)) return;

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        String normalizedLabel = "§7" + label;

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

    // ----- Determine Lore Marker Label -----
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

    // ----- Handle Bucket Fill -----
    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        String claimID = utils.getClaimID(b.getLocation());

        // Allow milking
        if (utils.isAir(b.getType())) return;

        // Prevent bucket fill if not a claim member
        if (plugin.cfg.getClaimData().contains(claimID)) {
            String[] members = utils.getMembers(b.getLocation());
            if (members != null && !utils.isMemberOfClaim(members, p)) {
                e.setCancelled(true);
                p.sendMessage(utils.chat("&cYou cannot fill buckets in this claim"));
            }
        }
    }
}
