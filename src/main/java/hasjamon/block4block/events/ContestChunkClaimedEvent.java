package hasjamon.block4block.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ContestChunkClaimedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    public final String claimant;

    public ContestChunkClaimedEvent(String claimant) {
        this.claimant = claimant;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}