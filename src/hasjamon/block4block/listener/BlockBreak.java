package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import oshi.util.tuples.Pair;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class BlockBreak implements Listener {
    private final Block4Block plugin;
    private final int gracePeriod;
    private long andesiteLatestBreak = 0;

    public BlockBreak(Block4Block plugin){
        this.plugin = plugin;
        this.gracePeriod = plugin.getConfig().getInt("b4b-grace-period");
    }

    // This Class is for the block break event (This runs every time a player breaks a block)
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        FileConfiguration cfg = plugin.getConfig();

        // Lecterns are exempt from B4B rules. Changing this would require refactoring of LecternBreak's onBreak.
        if (b.getType() == Material.LECTERN) return;
        if (p.getGameMode() == GameMode.CREATIVE) return;

        if (plugin.cfg.getClaimData().contains(utils.getChunkID(b.getLocation()))) { //if claimed
            if (!utils.isClaimBlock(b)) {
                String[] members = utils.getMembers(b.getLocation());
                List<?> claimBlacklist = cfg.getList("blacklisted-claim-blocks");

                // If the player is a member of the claim and the block is claim-blacklisted: Don't apply B4B rules
                if (members != null && claimBlacklist != null) {
                    if (claimBlacklist.contains(b.getType().toString()))
                        if(utils.isMemberOfClaim(members, p))
                            return;

                    // If the chunk is claimed, you're not a member, and 'can-break-in-others-claims' isn't on
                    if (!cfg.getBoolean("can-break-in-others-claims")) {
                        // Cancel BlockBreakEvent, i.e., prevent block from breaking
                        e.setCancelled(true);
                        p.sendMessage(utils.chat("&cYou cannot break blocks in this claim"));
                        return;
                    }
                }
            }
        }

        if(plugin.getConfig().getBoolean("andesite-splash-on")) {
            if(b.getType() == Material.ANDESITE) {
                // Add splash if it's been at least 0.1 second since the last time andesite was broken (to avoid chain reaction)
                if(System.nanoTime() - andesiteLatestBreak > 1E8) {
                    andesiteLatestBreak = System.nanoTime();
                    for (int x = -1; x <= 1; x++)
                        for (int y = -1; y <= 1; y++)
                            for (int z = -1; z <= 1; z++)
                                if(!(x == 0 && y == 0 && z == 0))
                                    if (b.getRelative(x, y, z).getType() == Material.ANDESITE)
                                        if(plugin.getConfig().getBoolean("andesite-splash-reduce-durability"))
                                            p.breakBlock(b.getRelative(x, y, z));
                                        else
                                            b.getRelative(x, y, z).breakNaturally(p.getInventory().getItemInMainHand());
                }
                return;
            }
        }

        List<?> blacklistedBlocks = cfg.getList("blacklisted-blocks");
        List<?> lootDisabled = cfg.getList("no-loot-on-break");

        if(blacklistedBlocks != null && lootDisabled != null) {
            // Does Block4Block apply, i.e., has the block type not been exempted from Block4Block through the blacklist
            boolean requiresBlock = !blacklistedBlocks.contains(b.getType().toString());

            // Are drops disabled for this block type
            boolean noloot = lootDisabled.contains(b.getType().toString());

            Set<Block> expiredGracePeriods = new HashSet<>();

            // Grace periods count as expired if x seconds have passed or the block's material has changed
            for(Map.Entry<Block, Pair<Long, String>> entry : utils.b4bGracePeriods.entrySet())
                if (System.nanoTime() - entry.getValue().getA() >= gracePeriod * 1e9 || !entry.getValue().getB().equals(entry.getKey().getType().name()))
                    expiredGracePeriods.add(entry.getKey());

            for(Block expired : expiredGracePeriods)
                utils.b4bGracePeriods.remove(expired);

            // If the block is still covered by the grace period, do not apply B4B rules
            if(utils.b4bGracePeriods.containsKey(b))
                return;

            utils.b4bCheck(p, b, e, noloot, requiresBlock);
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        String chunkID = utils.getChunkID(b.getLocation());

        // Allow milking
        if(b.getType() == Material.AIR)
            return;

        // Disallow filling buckets with anything other than milk
        if (plugin.cfg.getClaimData().contains(chunkID)) {
            String[] members = utils.getMembers(b.getLocation());

            if (members != null) {
                if(utils.isMemberOfClaim(members, p))
                    return;

                p.sendMessage(utils.chat("&cYou cannot fill buckets in this claim"));
                e.setCancelled(true);
            }
        }
    }
}
