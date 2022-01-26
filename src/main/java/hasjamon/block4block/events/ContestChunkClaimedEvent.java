package hasjamon.block4block.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ContestChunkClaimedEvent extends Event {
    public final String claimant;
    private static final HandlerList handlers = new HandlerList();

    public ContestChunkClaimedEvent(String claimant) {
        this.claimant = claimant;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
