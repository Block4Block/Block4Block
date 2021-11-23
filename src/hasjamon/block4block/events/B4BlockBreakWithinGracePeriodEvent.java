package hasjamon.block4block.events;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class B4BlockBreakWithinGracePeriodEvent extends Event {
    public final Player player;
    public final Block block;
    public final boolean normallyRequiresBlock;
    private static final HandlerList handlers = new HandlerList();

    public B4BlockBreakWithinGracePeriodEvent(Player player, Block block, boolean normallyRequiresBlock) {
        this.player = player;
        this.block = block;
        this.normallyRequiresBlock = normallyRequiresBlock;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
