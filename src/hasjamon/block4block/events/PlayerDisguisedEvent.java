package hasjamon.block4block.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerDisguisedEvent extends Event {
    public final Player disguiser;
    public final OfflinePlayer disguisee;
    private static final HandlerList handlers = new HandlerList();

    public PlayerDisguisedEvent(Player disguiser, OfflinePlayer disguisee) {
        this.disguiser = disguiser;
        this.disguisee = disguisee;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
