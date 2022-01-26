package hasjamon.block4block.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerClaimsCountedEvent extends Event {
    public final Player player;
    public final int numClaims;
    private static final HandlerList handlers = new HandlerList();

    public PlayerClaimsCountedEvent(Player player, int numClaims) {
        this.player = player;
        this.numClaims = numClaims;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
