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
        String claimID = utils.getClaimID(p.getLocation());

        utils.onIntruderLeaveClaim(p, claimID);

        if(plugin.canUseReflection) {
            utils.restorePlayerSkin(p);
            utils.onLoseDisguise(p);
        }

        Player killer = p.getKiller();
        if(plugin.getConfig().getBoolean("enable-claim-takeovers") && killer != null && p != killer) {
            FileConfiguration claimData = plugin.cfg.getClaimData();
            FileConfiguration masterBooks = plugin.cfg.getMasterBooks();
            FileConfiguration claimTakeovers = plugin.cfg.getClaimTakeovers();
            String victimName = p.getName().toLowerCase();
            String killerName = killer.getName().toLowerCase();

            for(String claim : claimData.getKeys(false)){
                String members = claimData.getString(claim + ".members");

                if(members != null){
                    String[] membersBefore = members.split("\\n");
                    String[] membersAfter = members.split("\\n");
                    String replacementName = utils.isMemberOfClaim(membersBefore, killer) ? "" : killerName;

                    for (int i = 0; i < membersAfter.length; i++) {
                        if (membersAfter[i].equalsIgnoreCase(victimName)) {
                            membersAfter[i] = replacementName;

                            // Save name and replacement name for next time the claim book in the claim is opened
                            String searchReplace = victimName + "|" + replacementName;
                            List<String> replacements = claimTakeovers.getStringList(claim);

                            replacements.add(searchReplace);
                            claimTakeovers.set(claim, replacements);
                        }
                    }

                    claimData.set(claim + ".members", String.join("\n", membersAfter));

                    if(!Arrays.equals(membersAfter, membersBefore)){
                        double x = claimData.getDouble(claim + ".location.X");
                        double y = claimData.getDouble(claim + ".location.Y");
                        double z = claimData.getDouble(claim + ".location.Z");
                        String xyz = x + ", " + y + ", " + z;
                        String[] membersRemoved = Arrays.stream(membersBefore)
                                                        .filter(mb -> Arrays.stream(membersAfter).anyMatch(mb::equalsIgnoreCase))
                                                        .toArray(String[]::new);

                        utils.onChunkUnclaim(claim, membersRemoved, xyz, null);
                        utils.onChunkClaim(claim, Arrays.stream(membersAfter).toList(), null, null);
                        plugin.cfg.saveOfflineClaimNotifications();
                    }
                }
            }

            for(String key : masterBooks.getKeys(false)){
                if(masterBooks.contains(key + ".pages")) {
                    List<String> pages = masterBooks.getStringList(key + ".pages");
                    String[] members = utils.findMembersInBook(pages).toArray(String[]::new);

                    if(utils.isMemberOfClaim(members, killer)){
                        utils.replaceInClaimPages(pages, victimName, "");
                    }else{
                        utils.replaceInClaimPages(pages, victimName, killerName);
                    }

                    masterBooks.set(key + ".pages", pages);
                }
            }

            plugin.cfg.saveClaimData();
            plugin.cfg.saveMasterBooks();
            plugin.cfg.saveClaimTakeovers();
            utils.updateClaimCount();
        }
    }
}
