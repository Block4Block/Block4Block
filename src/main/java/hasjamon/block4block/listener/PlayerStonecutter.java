package hasjamon.block4block.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class PlayerStonecutter implements Listener {

    private final JavaPlugin plugin;

    public PlayerStonecutter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerUseStonecutter(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("block-stonecutter")) return; // Check if enabled

        if (event.getClickedBlock() == null) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock().getType() != Material.STONECUTTER) return;

        Player player = event.getPlayer();
        event.setCancelled(true);

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cStonecutters are Disabled!"));
    }
}