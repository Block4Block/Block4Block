package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

public class TNTExplode implements Listener {
    private final Block4Block plugin;

    public TNTExplode(Block4Block plugin){
        this.plugin = plugin;
    }

    @EventHandler
    public void onTNTExplode(EntityExplodeEvent e){
        List<?> claimImmunity = plugin.getConfig().getList("claim-explosion-immunity");

        if(claimImmunity != null && claimImmunity.contains(e.getEntityType().toString())){
            FileConfiguration claimData = plugin.cfg.getClaimData();

            e.blockList().removeIf(b -> claimData.contains(utils.getChunkID(b.getLocation())));
        }
    }
}
