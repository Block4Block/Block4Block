package hasjamon.block4block.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ClaimContestOverEvent extends Event {
    public final Player winner;
    private static final HandlerList handlers = new HandlerList();

    public ClaimContestOverEvent(Player winner) {
        this.winner = winner;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
