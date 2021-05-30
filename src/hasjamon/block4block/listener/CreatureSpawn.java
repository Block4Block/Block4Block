package hasjamon.block4block.listener;

import hasjamon.block4block.utils.utils;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public class CreatureSpawn implements Listener {
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent e){
        if(e.getEntityType() == EntityType.IRON_GOLEM) {
            IronGolem golem = (IronGolem) e.getEntity();

            // Add it to the list of tracked golems
            utils.ironGolems.put(golem, golem.getLocation().getChunk());

            // Make sure it can attack players
            golem.setPlayerCreated(false);

            // Make it hostile to all intruders in chunk
            for(Player intruder : utils.intruders.get(golem.getLocation().getChunk()))
                golem.damage(0, intruder);
        }
    }
}
