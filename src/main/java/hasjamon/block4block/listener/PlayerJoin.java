package hasjamon.block4block.listener;

import com.mojang.authlib.properties.Property;
import hasjamon.block4block.Block4Block;
import hasjamon.block4block.events.ClaimLostWhileOfflineEvent;
import hasjamon.block4block.utils.utils;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class PlayerJoin implements Listener {
    private final Block4Block plugin;

    public PlayerJoin(Block4Block plugin){
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        if(!p.hasPlayedBefore()) {
            utils.knownPlayers.add(p.getName().toLowerCase());
            utils.sendWelcomeMsg(p);
        }else {
            utils.populatePlayerClaimsIntruded(p);
        }

        utils.updateClaimCount();

        String claimID = utils.getClaimID(p.getLocation());
        if(utils.isIntruder(p, claimID))
            utils.onIntruderEnterClaim(p, claimID);


        String pName = p.getName().toLowerCase();
        FileConfiguration offlineClaimNotifications = plugin.cfg.getOfflineClaimNotifications();
        ConfigurationSection chunksLost = offlineClaimNotifications.getConfigurationSection(pName + ".chunks");
        ConfigurationSection masterBooksRemovedFrom = offlineClaimNotifications.getConfigurationSection(pName + ".masterbooks");

        if(chunksLost != null){
            Set<String> chunkIDs = chunksLost.getKeys(false);
            int i = 0;

            for(String cID : chunkIDs){
                if(++i >= 10 && chunkIDs.size() > 10){
                    p.sendMessage(ChatColor.RED + "... and " + (chunkIDs.size() - 9) + " other claims");
                    break;
                }

                String xyz = chunksLost.getString(cID);
                String worldName = utils.getWorldName(World.Environment.valueOf(claimID.split("\\|")[0]));
                p.sendMessage(ChatColor.RED + "You have lost a claim! Location: " + xyz + " in " + worldName);
            }

            plugin.pluginManager.callEvent(new ClaimLostWhileOfflineEvent(p));
        }

        if(masterBooksRemovedFrom != null){
            for(String mbID : masterBooksRemovedFrom.getKeys(false)) {
                if (masterBooksRemovedFrom.getBoolean(mbID)) {
                    p.sendMessage(ChatColor.RED + "Your name has been removed from Master Book #" + mbID + " and all related claims!");
                    plugin.pluginManager.callEvent(new ClaimLostWhileOfflineEvent(p));
                }
            }
        }

        offlineClaimNotifications.set(pName, null);
        plugin.cfg.saveOfflineClaimNotifications();

        Collection<Property> textures = utils.getTextures(p);

        if(textures != null){
            Property prop = textures.iterator().next();
            List<String> copy = new ArrayList<>();

            copy.add(prop.getName());
            copy.add(prop.getValue());
            if(prop.hasSignature())
                copy.add(prop.getSignature());

            plugin.cfg.getPlayerTextures().set(p.getUniqueId().toString(), copy);
            plugin.cfg.savePlayerTextures();
        }
    }
}
