package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Arrays;
import java.util.List;

public class PlayerDeath implements Listener {
    private final Block4Block plugin;

    public PlayerDeath(Block4Block plugin){
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e){
        Player p = e.getEntity();
        String chunkID = utils.getChunkID(p.getLocation());

        utils.onIntruderLeaveClaim(p, chunkID);

        utils.restorePlayerSkin(p);
        utils.onLoseDisguise(p);

        Player killer = p.getKiller();
        if(plugin.getConfig().getBoolean("enable-claim-takeovers") && killer != null && p != killer) {
            FileConfiguration claimData = plugin.cfg.getClaimData();
            FileConfiguration masterBooks = plugin.cfg.getMasterBooks();
            FileConfiguration claimTakeovers = plugin.cfg.getClaimTakeovers();
            String pName = p.getName().toLowerCase();
            String killerName = killer.getName().toLowerCase();

            for(String key : claimData.getKeys(false)){
                String members = claimData.getString(key + ".members");

                if(members != null){
                    String[] membersBefore = members.split("\\n");
                    String[] membersAfter = members.split("\\n");

                    for (int i = 0; i < membersAfter.length; i++) {
                        if (membersAfter[i].equalsIgnoreCase(pName)) {
                            membersAfter[i] = killerName;

                            // Add name and replacement name for next time the book is opened
                            String searchReplace = pName + "|" + killerName;
                            List<String> replacements = claimTakeovers.getStringList(key);

                            replacements.add(searchReplace);
                            claimTakeovers.set(key, replacements);
                        }
                    }

                    claimData.set(key + ".members", String.join("\n", membersAfter));

                    if(!Arrays.equals(membersAfter, membersBefore)){
                        double x = claimData.getDouble(key + ".location.X");
                        double y = claimData.getDouble(key + ".location.Y");
                        double z = claimData.getDouble(key + ".location.Z");
                        String xyz = x + ", " + y + ", " + z;
                        String[] membersRemoved = Arrays.stream(membersBefore)
                                                        .filter(mb -> Arrays.stream(membersAfter).anyMatch(mb::equalsIgnoreCase))
                                                        .toArray(String[]::new);

                        utils.onChunkUnclaim(key, membersRemoved, xyz, null);
                        utils.onChunkClaim(key, Arrays.stream(membersAfter).toList(), null, null);
                        plugin.cfg.saveOfflineClaimNotifications();
                    }
                }
            }

            for(String key : masterBooks.getKeys(false)){
                if(masterBooks.contains(key + ".pages")) {
                    List<String> pages = masterBooks.getStringList(key + ".pages");

                    utils.replaceInClaimPages(pages, pName, killerName);
                    masterBooks.set(key + ".pages", pages);
                }
            }

            plugin.cfg.saveClaimData();
            plugin.cfg.saveMasterBooks();
            plugin.cfg.saveClaimTakeovers();
        }
    }
}
