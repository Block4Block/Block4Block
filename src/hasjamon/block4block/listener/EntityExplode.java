package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

public class EntityExplode implements Listener {
    private final Block4Block plugin;

    public EntityExplode(Block4Block plugin){
        this.plugin = plugin;
    }

    @EventHandler
    public void onTNTExplode(EntityExplodeEvent e){
        World.Environment environment = e.getEntity().getWorld().getEnvironment();
        String dimension = switch(environment){
            case NORMAL -> "overworld";
            case NETHER -> "nether";
            case THE_END -> "end";
            case CUSTOM -> "custom";
        };

        List<?> claimImmunity = plugin.getConfig().getList("claim-explosion-immunity." + dimension);

        if(claimImmunity != null && claimImmunity.contains(e.getEntityType().toString())){
            FileConfiguration claimData = plugin.cfg.getClaimData();

            e.blockList().removeIf(b -> claimData.contains(utils.getChunkID(b.getLocation())));
        }

        // Remove sand drops
        for(Block block : e.blockList())
            if (block.getType() == Material.SAND)
                block.setType(Material.AIR);
    }
}
