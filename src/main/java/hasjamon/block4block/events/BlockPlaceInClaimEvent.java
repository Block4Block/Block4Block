package hasjamon.block4block.events;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class BlockPlaceInClaimEvent extends Event {
    public final Player player;
    public final Block block;
    public final boolean success;
    private static final HandlerList handlers = new HandlerList();

    public BlockPlaceInClaimEvent(Player player, Block block, boolean success) {
        this.player = player;
        this.block = block;
        this.success = success;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}