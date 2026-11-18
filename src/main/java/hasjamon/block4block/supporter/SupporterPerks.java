package hasjamon.block4block.supporter;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.map.MapPalette;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/**
 * Cosmetic perks for Block4Block Supporters (Ko-fi). Paid perks are cosmetic only (Minecraft Usage Guidelines):
 * a chat prefix, and the colour the supporter's own claims are drawn in on the claim maps they make.
 *
 * Perks live in supporters.yml, keyed by UUID: {@code <uuid>.<perk>.until} is 0 for permanent, else epoch ms.
 * An expired perk counts as gone at once and is cleared by a check every five minutes.
 * Ops grant and remove perks by hand with /b4bsupporter; the Ko-fi service will later call the same commands.
 */
public class SupporterPerks implements Listener {
    public static final String PREFIX = "prefix";
    public static final String CLAIM_COLOR = "claim-color";
    public static final List<String> PERKS = List.of(PREFIX, CLAIM_COLOR);

    private final Block4Block plugin;
    private final File file;
    private final FileConfiguration data;

    public SupporterPerks(Block4Block plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "supporters.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        // Five minutes: an expired claim colour goes back to the usual colour on the next map redraw.
        Bukkit.getScheduler().runTaskTimer(plugin, this::clearExpired, 20L * 60, 20L * 60 * 5);
    }

    // ---- reading -------------------------------------------------------------------------------------------

    private String path(UUID id, String perk) { return id + "." + perk; }

    public boolean has(UUID id, String perk) {
        ConfigurationSection s = data.getConfigurationSection(path(id, perk));
        if (s == null) return false;
        long until = s.getLong("until", 0);
        return until == 0 || until > System.currentTimeMillis();
    }

    /** The map colour the supporter chose, or null when they have no active claim colour. */
    public String claimColorName(UUID id) {
        return has(id, CLAIM_COLOR) ? data.getString(path(id, CLAIM_COLOR) + ".color") : null;
    }

    /** The palette byte for the supporter's own claims on their maps, or null for the usual colour. */
    public Byte claimColor(UUID id) {
        String name = claimColorName(id);
        if (name == null) return null;
        ConfigurationSection c = colors().getConfigurationSection(name);
        if (c == null) return null;   // a colour taken out of config.yml: fall back to the usual one
        return MapPalette.matchColor(c.getInt("r"), c.getInt("g"), c.getInt("b"));
    }

    public ConfigurationSection colors() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("supporters.claim-colors");
        return c != null ? c : new YamlConfiguration();
    }

    public List<String> colorNames() { return new ArrayList<>(colors().getKeys(false)); }

    /** The chat tag, as config.yml sets it (gold by default), the same for every supporter. */
    public String chatPrefix() {
        return utils.chat(plugin.getConfig().getString("supporters.chat-prefix", "&6[Supporter]&r"));
    }

    /** The chat colour for the supporter's name: their claim colour exactly as their map shows it, or null. */
    public String nameColor(UUID id) {
        Byte color = claimColor(id);
        if (color == null) return null;
        @SuppressWarnings("deprecation") java.awt.Color shown = MapPalette.getColor(color);
        return net.md_5.bungee.api.ChatColor.of(shown).toString();
    }

    // ---- chat ----------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        String format = e.getFormat();
        // The name (%1$s) takes the claim colour; the reset after it keeps the message itself uncoloured.
        String nameColor = nameColor(id);
        if (nameColor != null) format = format.replace("%1$s", nameColor + "%1$s" + org.bukkit.ChatColor.RESET);
        // The format is a String.format pattern: a literal % in the prefix has to be doubled.
        if (has(id, PREFIX)) format = chatPrefix().replace("%", "%%") + " " + format;
        e.setFormat(format);
    }

    // ---- writing (ops, and later the Ko-fi service) ---------------------------------------------------------

    /** Grants a perk; days 0 means permanent. Returns the stored expiry (0 = permanent). */
    public synchronized long grant(OfflinePlayer p, String perk, String color, int days, String by) {
        String base = path(p.getUniqueId(), perk);
        long until = days > 0 ? System.currentTimeMillis() + days * 86_400_000L : 0;
        data.set(p.getUniqueId() + ".name", p.getName());
        data.set(base + ".until", until);
        data.set(base + ".granted", Instant.now().toString());
        data.set(base + ".by", by);
        if (color != null) data.set(base + ".color", color);
        save();
        if (CLAIM_COLOR.equals(perk)) utils.lastClaimUpdate = System.nanoTime();   // maps redraw with the new colour
        return until;
    }

    /** Removes one perk, or all with perk == null. Returns whether anything was removed. */
    public synchronized boolean remove(UUID id, String perk) {
        boolean had;
        if (perk == null) {
            had = data.contains(id.toString());
            data.set(id.toString(), null);
        } else {
            had = data.contains(path(id, perk));
            data.set(path(id, perk), null);
            ConfigurationSection rest = data.getConfigurationSection(id.toString());
            if (rest != null && rest.getKeys(false).stream().noneMatch(PERKS::contains)) data.set(id.toString(), null);
        }
        if (had) {
            save();
            utils.lastClaimUpdate = System.nanoTime();
        }
        return had;
    }

    /** One line per perk, for /b4bsupporter info and list. */
    public List<String> describe(UUID id) {
        List<String> out = new ArrayList<>();
        for (String perk : PERKS) {
            ConfigurationSection s = data.getConfigurationSection(path(id, perk));
            if (s == null) continue;
            long until = s.getLong("until", 0);
            String when = until == 0 ? "permanent" : (until > System.currentTimeMillis() ? "until " : "expired ") + Instant.ofEpochMilli(until);
            String color = s.getString("color");
            out.add(perk + (color != null ? " " + color : "") + ", " + when);
        }
        return out;
    }

    public Map<String, UUID> supporters() {
        Map<String, UUID> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String key : data.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                out.put(data.getString(key + ".name", key), id);
            } catch (IllegalArgumentException ignored) { }
        }
        return out;
    }

    private synchronized void clearExpired() {
        boolean mapChanged = false, changed = false;
        long now = System.currentTimeMillis();
        for (String key : new ArrayList<>(data.getKeys(false))) {
            for (String perk : PERKS) {
                long until = data.getLong(key + "." + perk + ".until", 0);
                if (data.contains(key + "." + perk) && until != 0 && until <= now) {
                    data.set(key + "." + perk, null);
                    changed = true;
                    if (CLAIM_COLOR.equals(perk)) mapChanged = true;
                    plugin.getLogger().info("Supporter perk expired: " + perk + " for " + data.getString(key + ".name", key));
                }
            }
            ConfigurationSection rest = data.getConfigurationSection(key);
            if (rest != null && rest.getKeys(false).stream().noneMatch(PERKS::contains)) data.set(key, null);
        }
        if (changed) save();
        if (mapChanged) utils.lastClaimUpdate = System.nanoTime();
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save supporters.yml: " + e.getMessage());
        }
    }
}
