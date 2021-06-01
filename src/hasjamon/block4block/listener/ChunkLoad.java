package hasjamon.block4block.listener;

import hasjamon.block4block.utils.utils;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class ChunkLoad implements Listener {
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e){
        for(Entity ent : e.getChunk().getEntities())
            if(ent.getType() == EntityType.IRON_GOLEM)
                utils.ironGolems.put((IronGolem) ent, null);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e){
        for(Entity ent : e.getChunk().getEntities())
            if(ent.getType() == EntityType.IRON_GOLEM)
                utils.ironGolems.remove((IronGolem) ent);
    }
}
