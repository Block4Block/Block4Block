package hasjamon.block4block.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class HelpCmdSucceededEvent extends Event {
    public final Player player;
    private static final HandlerList handlers = new HandlerList();

    public HelpCmdSucceededEvent(Player player) {
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
