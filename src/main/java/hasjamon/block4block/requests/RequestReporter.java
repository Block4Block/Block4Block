package hasjamon.block4block.requests;

import hasjamon.block4block.Block4Block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tells the Ko-fi request service when the owner finishes a sign or names an animal.
 * No commands, no messages and no items: the plugin only reports what happened.
 * The service decides whether it matches the active request.
 */
public class RequestReporter implements Listener {
    private final Block4Block plugin;
    private final String url;
    private final String secret;
    private final List<String> players;

    private RequestReporter(Block4Block plugin, String url, String secret, List<String> players) {
        this.plugin = plugin;
        this.url = url;
        this.secret = secret;
        this.players = players;
    }

    /** Returns null when the feature is off or not set up. */
    public static RequestReporter create(Block4Block plugin) {
        if (!plugin.getConfig().getBoolean("kofi-requests.enabled", false)) return null;
        String url = plugin.getConfig().getString("kofi-requests.url", "");
        String file = plugin.getConfig().getString("kofi-requests.secret-file", "");
        List<String> players = plugin.getConfig().getStringList("kofi-requests.players");
        try {
            String secret = Files.readString(Path.of(file), StandardCharsets.UTF_8).trim();
            if (url.isEmpty() || secret.isEmpty() || players.isEmpty()) throw new IllegalStateException("incomplete settings");
            return new RequestReporter(plugin, url, secret, players);
        } catch (Exception e) {
            plugin.getLogger().warning("Ko-fi requests are enabled but not set up (" + e.getMessage() + "); skipping.");
            return null;
        }
    }

    private boolean watched(String name) {
        for (String p : players) if (p.equalsIgnoreCase(name)) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSign(SignChangeEvent e) {
        if (!watched(e.getPlayer().getName())) return;
        StringBuilder text = new StringBuilder();
        for (String line : e.getLines()) {
            if (text.length() > 0 || !line.isEmpty()) text.append(line).append('\n');
        }
        String joined = text.toString().replaceAll("\\n+$", "");
        if (joined.isEmpty()) return;
        report("sign", joined, e.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNameTag(PlayerInteractEntityEvent e) {
        if (!watched(e.getPlayer().getName())) return;
        EquipmentSlot hand = e.getHand();
        ItemStack item = e.getPlayer().getInventory().getItem(hand);
        if (item == null || item.getType() != Material.NAME_TAG || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;
        Entity target = e.getRightClicked();
        if (!(target instanceof LivingEntity)) return;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
        report("animal", name, target.getLocation());
    }

    private void report(String kind, String text, Location loc) {
        String body = "{\"kind\":" + q(kind) + ",\"text\":" + q(text) + ",\"world\":" + q(loc.getWorld().getName())
                + ",\"x\":" + loc.getBlockX() + ",\"y\":" + loc.getBlockY() + ",\"z\":" + loc.getBlockZ() + "}";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(3000);
                c.setReadTimeout(3000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("X-B4B-Secret", secret);
                try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
                c.getResponseCode();
                c.disconnect();
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not reach the request service: " + ex.getMessage());
            }
        });
    }

    private static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': break;
                case '\t': b.append(' '); break;
                default: if (ch < 0x20) b.append(' '); else b.append(ch);
            }
        }
        return b.append('"').toString();
    }
}
