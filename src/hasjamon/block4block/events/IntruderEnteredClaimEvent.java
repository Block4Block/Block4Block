package hasjamon.block4block.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class IntruderEnteredClaimEvent extends Event {
    public final Player claimOwner;
    private static final HandlerList handlers = new HandlerList();

    public IntruderEnteredClaimEvent(Player claimOwner) {
        this.claimOwner = claimOwner;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
