package hasjamon.block4block.listener;

import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftFallingBlock;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.inventory.ItemStack;

public class EntityDropItem implements Listener {
    @EventHandler
    public void onEntityDropItem(EntityDropItemEvent e){
        if(e.getEntityType() == EntityType.FALLING_BLOCK)
            if(((CraftFallingBlock) e.getEntity()).getMaterial() == Material.GRAVEL)
                e.getItemDrop().setItemStack(new ItemStack(Material.FLINT, 1));
    }
}
