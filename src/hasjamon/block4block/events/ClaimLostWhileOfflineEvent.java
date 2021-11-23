package hasjamon.block4block.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ClaimLostWhileOfflineEvent extends Event {
    public final Player player;
    private static final HandlerList handlers = new HandlerList();

    public ClaimLostWhileOfflineEvent(Player player) {
        this.player = player;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
