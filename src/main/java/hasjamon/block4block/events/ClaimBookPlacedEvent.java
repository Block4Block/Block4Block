package hasjamon.block4block.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ClaimBookPlacedEvent extends Event {
    public final Player player;
    public final boolean isMasterBook;
    public final boolean isMember;
    private static final HandlerList handlers = new HandlerList();

    public ClaimBookPlacedEvent(Player player, boolean isMasterBook, boolean isMember) {
        this.player = player;
        this.isMasterBook = isMasterBook;
        this.isMember = isMember;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
