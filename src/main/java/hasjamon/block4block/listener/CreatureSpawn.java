package hasjamon.block4block.listener;

import hasjamon.block4block.utils.utils;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public class CreatureSpawn implements Listener {
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        LivingEntity entity = e.getEntity();
        boolean isNamed = entity.getCustomName() != null;

        entity.setRemoveWhenFarAway(!isNamed);

        if (e.getEntityType() == EntityType.IRON_GOLEM) {
            IronGolem golem = (IronGolem) entity;
            String chunkID = utils.getChunkID(golem.getLocation());

            // Make sure it can attack players
            golem.setPlayerCreated(false);

            // Add it to the list of tracked golems
            utils.ironGolems.put(golem, chunkID);
        }
    }
}
