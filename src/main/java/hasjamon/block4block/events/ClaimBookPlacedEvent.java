package hasjamon.block4block.events;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ClaimBookPlacedEvent extends Event {
    public final Player player;
    public final boolean isMasterBook;
    public final Block lectern;
    public final boolean isMember;
    private static final HandlerList handlers = new HandlerList();

    public ClaimBookPlacedEvent(Player player, Block lectern, boolean isMasterBook, boolean isMember) {
        this.player = player;
        this.lectern = lectern;
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
