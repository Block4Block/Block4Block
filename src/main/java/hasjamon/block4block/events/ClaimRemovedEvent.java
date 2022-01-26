package hasjamon.block4block.events;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ClaimRemovedEvent extends Event {
    public final Player player;
    public final Block block;
    public final boolean wasMember;
    private static final HandlerList handlers = new HandlerList();

    public ClaimRemovedEvent(Player player, Block block, boolean wasMember) {
        this.player = player;
        this.block = block;
        this.wasMember = wasMember;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}