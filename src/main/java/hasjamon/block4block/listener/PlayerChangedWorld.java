package hasjamon.block4block.listener;

import hasjamon.block4block.utils.utils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.util.Optional;

public class PlayerChangedWorld implements Listener {
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World newWorld = player.getWorld();
        Optional<Boolean> fireTickEnabled = Optional.ofNullable(newWorld.getGameRuleValue(GameRule.DO_FIRE_TICK));

        fireTickEnabled.ifPresent(enabled -> sendFireTickMessage(player, enabled, newWorld.getEnvironment()));
    }

    private void sendFireTickMessage(Player player, Boolean enabled, World.Environment dimension) {
        String worldName = utils.getWorldName(dimension);
        String msg = utils.chat("Fire " + (enabled ? "&cspreads" : "&adoes not spread") + " &fin " + worldName);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }
}
