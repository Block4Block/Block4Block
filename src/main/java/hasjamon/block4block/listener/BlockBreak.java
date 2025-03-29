package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.events.B4BlockBreakWithinGracePeriodEvent;
import hasjamon.block4block.events.BlockBreakInClaimEvent;
import hasjamon.block4block.utils.utils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static hasjamon.block4block.utils.utils.canOpenChest;

public class BlockBreak implements Listener {

    private final Block4Block plugin;
    private long andesiteLatestBreak = 0;

    public BlockBreak(Block4Block plugin) {
        this.plugin = plugin;
    }

    // Helper: Determines if the item is considered "placeable" for marking purposes.
    private boolean isPlaceable(ItemStack stack) {
        if (stack == null) return false;
        Material mat = stack.getType();
        // If it is a block, it is placeable.
        if (mat.isBlock()) return true;
        // Add non-block items that are still placeable.
        return (mat == Material.PAINTING ||
                mat == Material.ITEM_FRAME ||
                mat == Material.GLOW_ITEM_FRAME);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        FileConfiguration cfg = plugin.getConfig();
        boolean isInsideClaim = plugin.cfg.getClaimData().contains(utils.getClaimID(b.getLocation()));

        plugin.getLogger().info("DEBUG: onBreak triggered for block " + b.getType() +
                " by player " + p.getName() + " at " + b.getLocation() +
                ", isInsideClaim=" + isInsideClaim);

        // Prevent breaking chests in someone else's claim
        if (isInsideClaim && b.getType() == Material.CHEST) {
            String[] members = utils.getMembers(b.getLocation());
            boolean isMember = utils.isMemberOfClaim(members, p);
            boolean canOpen = canOpenChest(b, p);
            plugin.getLogger().info("DEBUG: Attempting to break chest in claim. isMember=" + isMember + ", canOpen=" + canOpen);
            if (!isMember && !canOpen) {
                e.setCancelled(true);
                plugin.getLogger().info("DEBUG: Breaking chest cancelled because player not allowed to open it.");
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(utils.chat("&cThe chest is shielded by the blocks above it.")));
                return;
            }
        }

        // Lecterns are exempt from B4B rules
        if (b.getType() == Material.LECTERN) {
            plugin.getLogger().info("DEBUG: Block is a lectern, skipping B4B logic.");
            return;
        }

        // No drops in creative
        if (p.getGameMode() == GameMode.CREATIVE) {
            plugin.getLogger().info("DEBUG: Player is in Creative mode, skipping B4B logic.");
            return;
        }

        List<?> claimBlacklist = cfg.getList("blacklisted-claim-blocks");

        // Check claim logic
        if (isInsideClaim) {
            if (utils.isAdjacentToClaimBlock(b)) {
                String claimID = utils.getClaimID(b.getLocation());
                plugin.getLogger().info("DEBUG: Block is adjacent to a claim block. Updating boss bar for claim " + claimID);
                utils.updateBossBar(p, claimID);
            }
            if (!utils.isClaimBlock(b)) {
                String[] members = utils.getMembers(b.getLocation());
                if (members != null) {
                    boolean isMember = utils.isMemberOfClaim(members, p);
                    plugin.getLogger().info("DEBUG: Checking claim membership. isMember=" + isMember);
                    if (isMember) {
                        if (cfg.getBoolean("only-apply-b4b-to-intruders")) {
                            plugin.getLogger().info("DEBUG: B4B only applies to intruders; skipping for claim member.");
                            plugin.pluginManager.callEvent(new BlockBreakInClaimEvent(p, b, true));
                            return;
                        }
                        if (claimBlacklist != null && claimBlacklist.contains(b.getType().toString())) {
                            plugin.getLogger().info("DEBUG: Block is in claim blacklist; using free in claim marker.");
                            String label = getBlockMarkerLabel(p, b);
                            markDroppedItems(p, b, label);
                            return;
                        }
                    } else if (!cfg.getBoolean("can-break-in-others-claims")) {
                        plugin.getLogger().info("DEBUG: Non-member breaking block in claim is not allowed. Cancelling.");
                        e.setCancelled(true);
                        p.sendMessage(utils.chat("&cYou cannot break blocks in this claim"));
                        plugin.pluginManager.callEvent(new BlockBreakInClaimEvent(p, b, false));
                        return;
                    }
                }
            }
        }

        // Andesite splash logic
        if (cfg.getBoolean("andesite-splash-on") && b.getType() == Material.ANDESITE) {
            plugin.getLogger().info("DEBUG: Andesite-splash logic triggered.");
            if (System.nanoTime() - andesiteLatestBreak > 1E8) {
                andesiteLatestBreak = System.nanoTime();
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            if (!(x == 0 && y == 0 && z == 0)) {
                                Block neighbor = b.getRelative(x, y, z);
                                if (neighbor.getType() == Material.ANDESITE) {
                                    if (cfg.getBoolean("andesite-splash-reduce-durability")) {
                                        p.breakBlock(neighbor);
                                    } else {
                                        neighbor.breakNaturally(p.getInventory().getItemInMainHand());
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return;
        }

        // If not inside claim and only-apply-b4b-to-intruders is true, skip
        if (!isInsideClaim && cfg.getBoolean("only-apply-b4b-to-intruders")) {
            plugin.getLogger().info("DEBUG: B4B only applies to intruders in claims, skipping because block is not in a claim.");
            return;
        }

        List<?> blacklistedBlocks = cfg.getList("blacklisted-blocks");
        List<?> lootDisabledTypes = cfg.getList("no-loot-on-break");

        if (blacklistedBlocks != null && lootDisabledTypes != null) {
            boolean requiresBlock = !blacklistedBlocks.contains(b.getType().toString());
            plugin.getLogger().info("DEBUG: Checking B4B logic. requiresBlock=" + requiresBlock);
            utils.removeExpiredB4BGracePeriods();
            if (utils.b4bGracePeriods.containsKey(b)) {
                plugin.getLogger().info("DEBUG: This block is still in grace period, skipping B4B logic.");
                plugin.pluginManager.callEvent(new B4BlockBreakWithinGracePeriodEvent(p, b, requiresBlock));
                return;
            }
            plugin.getLogger().info("DEBUG: Calling b4bCheck(...) for block " + b.getType());
            utils.b4bCheck(p, b, e, lootDisabledTypes, requiresBlock, false);
        } else {
            plugin.getLogger().info("DEBUG: blacklisted-blocks or no-loot-on-break is null, skipping b4bCheck logic.");
        }

        if (!e.isCancelled()) {
            plugin.getLogger().info("DEBUG: Event is not cancelled; proceeding with custom drop logic.");
            e.setDropItems(false);
            String label = getBlockMarkerLabel(p, b);
            markDroppedItems(p, b, label);
        } else {
            plugin.getLogger().info("DEBUG: Event got cancelled by b4bCheck or another process; no custom drops.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnerBreak(BlockBreakEvent e) {
        // ... existing spawner logic ...
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        // ... existing bucket fill logic ...
    }

    // Inventory events – update immediately so that picked-up items are labelled before stacking.
    @EventHandler(ignoreCancelled = true)
    public void onInventoryPickupItem(InventoryPickupItemEvent e) {
        if (e.getItem() != null) {
            ItemStack stack = e.getItem().getItemStack();
            updateItemMarker(stack);
        }
        updateInventoryMarks(e.getInventory());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (e.getItem() != null) {
            ItemStack stack = e.getItem().getItemStack();
            updateItemMarker(stack);
        }
        updateInventoryMarks(p.getInventory());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent e) {
        updateInventoryMarks(e.getInventory());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        updateInventoryMarks(p.getInventory());
        plugin.getLogger().info("DEBUG: Updated inventory marks for player " + p.getName());
    }

    // ***** Helper Methods for Marking Items *****

    private void markDroppedItems(Player p, Block b, String label) {
        String method = plugin.getConfig().getString("marking-method");
        ItemStack toolInHand = p.getInventory().getItemInMainHand();
        Collection<ItemStack> drops = b.getDrops(toolInHand, p);
        for (ItemStack drop : drops) {
            if (drop == null) continue;
            // Only label if the item is placeable.
            if (!isPlaceable(drop)) {
                plugin.getLogger().info("DEBUG: Skipping " + drop.getType() + " as it's not considered placeable.");
                continue;
            }
            if (method.equals("lore")) {
                applyLoreMark(drop, label);
            } else if (method.equals("none")) {
                removeMarkersFromItem(drop);
            }
            b.getWorld().dropItemNaturally(b.getLocation(), drop);
        }
    }

    private void applyLoreMark(ItemStack stack, String label) {
        if (stack == null) return;
        if (!isPlaceable(stack)) {
            plugin.getLogger().info("DEBUG: Skipping " + stack.getType() + " in applyLoreMark as it's not considered placeable.");
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        lore.add("§7" + label);
        meta.setLore(lore);
        stack.setItemMeta(meta);
        plugin.getLogger().info("DEBUG: Lore marking applied to " + stack.getType() + " with label " + label);
    }

    /**
     * Removes any allowed markers from an item's lore.
     */
    private void removeMarkersFromItem(ItemStack stack) {
        if (stack == null) return;
        if (!isPlaceable(stack)) return;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasLore()) return;
        List<String> lore = meta.getLore();
        Iterator<String> iter = lore.iterator();
        while (iter.hasNext()) {
            String line = iter.next();
            String clean = line.replace("§7", "").trim();
            if (clean.equalsIgnoreCase("Free to Break") ||
                    clean.equalsIgnoreCase("Free in Claim") ||
                    clean.equalsIgnoreCase("Block for Block") ||
                    clean.equalsIgnoreCase("F2B") ||
                    clean.equalsIgnoreCase("FINC") ||
                    clean.equalsIgnoreCase("B4B")) {
                iter.remove();
            }
        }
        meta.setLore(lore);
        stack.setItemMeta(meta);
        plugin.getLogger().info("DEBUG: Removed markers from " + stack.getType());
    }

    /**
     * Computes the expected marker for an item during inventory updates based on its type.
     * If the item is not considered placeable, returns a sentinel value ("SKIP-LABEL").
     * If the item type is in the blacklisted-claim-blocks list, returns "Free in Claim" (or "FINC");
     * else if in the blacklisted-blocks list, returns "Free to Break" (or "F2B");
     * otherwise returns "Block for Block" (or "B4B").
     */
    private String getInventoryMarkerLabel(ItemStack stack) {
        if (!isPlaceable(stack)) {
            return "SKIP-LABEL";
        }
        FileConfiguration cfg = plugin.getConfig();
        boolean useLong = cfg.getBoolean("use-long-form");
        List<?> freeInClaim = cfg.getList("blacklisted-claim-blocks");
        List<?> freeBlocks = cfg.getList("blacklisted-blocks");
        if (freeInClaim != null && freeInClaim.contains(stack.getType().toString())) {
            return useLong ? "Free in Claim" : "FINC";
        } else if (freeBlocks != null && freeBlocks.contains(stack.getType().toString())) {
            return useLong ? "Free to Break" : "F2B";
        }
        return useLong ? "Block for Block" : "B4B";
    }

    /**
     * Updates an item's marker in its lore based on its type.
     * If the marking-method is "none", it removes all allowed markers.
     */
    private void updateItemMarker(ItemStack stack) {
        if (stack == null) return;
        if (!isPlaceable(stack)) {
            plugin.getLogger().info("DEBUG: Skipping " + stack.getType() + " in updateItemMarker as it's not considered placeable.");
            return;
        }
        String method = plugin.getConfig().getString("marking-method");
        if (method.equals("none")) {
            removeMarkersFromItem(stack);
            return;
        }
        String expectedLabel = getInventoryMarkerLabel(stack);
        if (expectedLabel.equals("SKIP-LABEL")) {
            plugin.getLogger().info("DEBUG: getInventoryMarkerLabel returned 'SKIP-LABEL' for " + stack.getType());
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        Iterator<String> iter = lore.iterator();
        while (iter.hasNext()) {
            String line = iter.next();
            String clean = line.replace("§7", "").trim();
            if (clean.equalsIgnoreCase("Free to Break") ||
                    clean.equalsIgnoreCase("Free in Claim") ||
                    clean.equalsIgnoreCase("Block for Block") ||
                    clean.equalsIgnoreCase("F2B") ||
                    clean.equalsIgnoreCase("FINC") ||
                    clean.equalsIgnoreCase("B4B")) {
                iter.remove();
            }
        }
        lore.add("§7" + expectedLabel);
        meta.setLore(lore);
        stack.setItemMeta(meta);
    }

    /**
     * Iterates over an inventory and updates each item's marker based on its type.
     */
    public void updateInventoryMarks(Inventory inv) {
        if (inv == null) return;
        for (ItemStack item : inv.getContents()) {
            if (item != null) {
                updateItemMarker(item);
            }
        }
        plugin.getLogger().info("DEBUG: Updated inventory marks.");
    }

    /**
     * Determines the proper marker label for a block break event based on exemption lists.
     * - If the block's type is in blacklisted-claim-blocks (and if the block is within a claim): use "Free in Claim" (or "FINC")
     * - Else if the block's type is in blacklisted-blocks: use "Free to Break" (or "F2B")
     * - Otherwise: use "Block for Block" (or "B4B")
     */
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
}
