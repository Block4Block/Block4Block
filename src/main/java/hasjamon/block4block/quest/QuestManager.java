package hasjamon.block4block.quest;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.events.ClaimBookPlacedEvent;
import hasjamon.block4block.utils.utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Config-defined questlines shown on a boss bar. A step states what to do and never why; the reason is the reveal.
 * Progress is kept per player in questprogress.yml.
 *
 * Step completion kinds ({@code complete.on}):
 * <ul>
 *   <li>{@code pickup}: picks up {@code item}, optionally only if it dropped from {@code source_entity}</li>
 *   <li>{@code craft}: has crafted {@code item} {@code count} times in total (server statistic)</li>
 *   <li>{@code has_items}: has every material in {@code items} in the inventory</li>
 *   <li>{@code write_book}: saves a book containing every word of {@code contains} ({@code {player}} is their name)</li>
 *   <li>{@code claim_book_placed}: places a claim book on a lectern</li>
 * </ul>
 */
public class QuestManager implements Listener {
    private static final long CHECK_TICKS = 40;
    private static final long SAVE_TICKS = 20 * 60;
    private static final long ITEM_SOURCE_TTL_MS = 10 * 60 * 1000;

    private final Block4Block plugin;
    private final File file;
    private final YamlConfiguration progress;
    private boolean dirty = false;

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, ItemSource> itemSources = new LinkedHashMap<>();
    private long deathTick = -1;
    private org.bukkit.Location deathLoc;
    private EntityType deathType;

    private static final class ItemSource {
        final EntityType type;
        final long at;
        ItemSource(EntityType type, long at) { this.type = type; this.at = at; }
    }

    /** What one online player is looking at right now. Not saved. */
    private static final class Session {
        final BossBar bar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_10);
        long lastProgressAt = System.currentTimeMillis();
        String shownKey = "";
        final Set<Integer> nudged = new HashSet<>();
    }

    public QuestManager(Block4Block plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "questprogress.yml");
        this.progress = YamlConfiguration.loadConfiguration(file);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, CHECK_TICKS, CHECK_TICKS);
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveIfDirty, SAVE_TICKS, SAVE_TICKS);
        utils.claimBarHidden = this::claimBarHidden;
    }

    public void shutdown() {
        utils.claimBarHidden = p -> false;
        for (Session s : sessions.values()) s.bar.removeAll();
        sessions.clear();
        saveIfDirty();
    }

    // ---- config ---------------------------------------------------------------------

    private ConfigurationSection questline(String id) {
        return plugin.getConfig().getConfigurationSection("questlines." + id);
    }

    private List<Map<?, ?>> steps(String id) {
        return plugin.getConfig().getMapList("questlines." + id + ".steps");
    }

    private Set<String> questlineIds() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("questlines");
        return s == null ? Set.of() : s.getKeys(false);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sub(Map<?, ?> m, String key) {
        Object o = m.get(key);
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    private static String str(Map<?, ?> m, String key) {
        Object o = m.get(key);
        return o == null ? null : o.toString();
    }

    // ---- progress -------------------------------------------------------------------

    private String path(Player p, String id, String leaf) {
        return p.getUniqueId() + "." + id + "." + leaf;
    }

    private boolean isDone(Player p, String id) {
        return progress.getBoolean(path(p, id, "done"), false);
    }

    private boolean isStarted(Player p, String id) {
        return progress.contains(path(p, id, "started"));
    }

    private int stepIndex(Player p, String id) {
        return progress.getInt(path(p, id, "step"), 0);
    }

    private boolean isActive(Player p, String id) {
        return isStarted(p, id) && !isDone(p, id);
    }

    private void markDirty() { dirty = true; }

    private void saveIfDirty() {
        if (!dirty) return;
        try {
            progress.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save questprogress.yml: " + e.getMessage());
        }
    }

    /** Players who finished the claim questline, or who were here before questlines existed, see the claim bar. */
    public boolean claimBarHidden(Player p) {
        String unlock = "claim_bossbar";
        return !progress.getStringList(p.getUniqueId() + ".unlocked").contains(unlock);
    }

    private void unlock(Player p, String what) {
        List<String> list = new ArrayList<>(progress.getStringList(p.getUniqueId() + ".unlocked"));
        if (!list.contains(what)) {
            list.add(what);
            progress.set(p.getUniqueId() + ".unlocked", list);
            markDirty();
        }
    }

    // ---- starting and finishing -------------------------------------------------------

    public void startQuestline(Player p, String id) {
        if (questline(id) == null || isStarted(p, id)) return;
        progress.set(path(p, id, "started"), System.currentTimeMillis());
        progress.set(path(p, id, "step"), 0);
        markDirty();
        log(p, id, "start", 0);
        refresh(p);
    }

    private void finish(Player p, String id) {
        progress.set(path(p, id, "done"), true);
        progress.set(path(p, id, "doneAt"), System.currentTimeMillis());
        markDirty();
        log(p, id, "done", stepIndex(p, id));
        ConfigurationSection q = questline(id);
        ConfigurationSection reveal = q == null ? null : q.getConfigurationSection("reveal_on_complete");
        if (reveal != null) {
            String title = reveal.getString("title", "");
            String subtitle = reveal.getString("subtitle", "");
            if (!title.isEmpty() || !subtitle.isEmpty())
                p.sendTitle(utils.chat(title), utils.chat(subtitle), 10, 70, 20);
            String actionbar = reveal.getString("actionbar");
            if (actionbar != null) p.sendActionBar(net.kyori.adventure.text.Component.text(utils.chat(actionbar)));
            for (String line : reveal.getStringList("messages")) p.sendMessage(utils.chat(line));
        }
        List<String> unlocks = q == null ? List.of() : q.getStringList("unlock_on_complete");
        for (String u : unlocks) unlock(p, u);
        refresh(p);
        if (unlocks.contains("claim_bossbar"))
            utils.updateBossBar(p, utils.getClaimID(p.getLocation()));
    }

    private void advance(Player p, String id) {
        int next = stepIndex(p, id) + 1;
        Session s = sessions.get(p.getUniqueId());
        if (s != null) {
            s.lastProgressAt = System.currentTimeMillis();
            s.nudged.clear();
        }
        // The reveal of the step just finished (for example an action-bar line) plays before the next step shows.
        List<Map<?, ?>> steps = steps(id);
        int done = next - 1;
        if (done >= 0 && done < steps.size()) {
            String actionbar = str(sub(steps.get(done), "reveal"), "actionbar");
            if (actionbar != null) p.sendActionBar(net.kyori.adventure.text.Component.text(utils.chat(actionbar)));
        }
        progress.set(path(p, id, "step"), next);
        markDirty();
        log(p, id, "step", next);
        if (next >= steps.size()) finish(p, id);
        else refresh(p);
    }

    /** One line per step so the point where players drop off is visible (success metric in the spec). */
    private void log(Player p, String id, String what, int step) {
        plugin.getLogger().info("[quest] " + p.getName() + " " + id + " " + what + " " + step);
    }

    // ---- boss bar -----------------------------------------------------------------------

    /** The questline the player should see now: the newest active high-priority one, else the oldest-started active one. */
    private String displayed(Player p) {
        String best = null;
        long bestStarted = 0;
        boolean bestHigh = false;
        for (String id : questlineIds()) {
            if (!isActive(p, id)) continue;
            boolean high = "high".equalsIgnoreCase(questline(id).getString("priority", "normal"));
            long started = progress.getLong(path(p, id, "started"));
            boolean better = best == null
                    || (high && !bestHigh)
                    || (high == bestHigh && (high ? started > bestStarted : started < bestStarted));
            if (better) { best = id; bestHigh = high; bestStarted = started; }
        }
        return best;
    }

    private void refresh(Player p) {
        Session s = sessions.computeIfAbsent(p.getUniqueId(), k -> new Session());
        String id = displayed(p);
        if (id == null) {
            s.bar.removeAll();
            s.shownKey = "";
            return;
        }
        List<Map<?, ?>> steps = steps(id);
        int idx = Math.min(stepIndex(p, id), steps.size() - 1);
        if (idx < 0) { s.bar.removeAll(); return; }
        String text = str(steps.get(idx), "text");
        String key = id + "#" + idx;
        if (!key.equals(s.shownKey)) {
            s.shownKey = key;
            s.lastProgressAt = System.currentTimeMillis();
            s.nudged.clear();
        }
        s.bar.setTitle(utils.chat("&e" + text));
        s.bar.setProgress(Math.max(0.0, Math.min(1.0, idx / (double) steps.size())));
        if (!s.bar.getPlayers().contains(p)) s.bar.addPlayer(p);
    }

    // ---- periodic checks (statistics, inventory, stuck help) -------------------------------

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (String id : questlineIds()) {
                if (isActive(p, id)) checkPolled(p, id);
            }
            String id = displayed(p);
            Session s = sessions.get(p.getUniqueId());
            if (id == null || s == null) continue;
            applyStuckHelp(p, id, s, now);
        }
    }

    private void checkPolled(Player p, String id) {
        // A step can complete several in a row (for example when the player already crafted things).
        for (int guard = 0; guard < 20 && isActive(p, id); guard++) {
            List<Map<?, ?>> steps = steps(id);
            int idx = stepIndex(p, id);
            if (idx >= steps.size()) { finish(p, id); return; }
            Map<String, Object> c = sub(steps.get(idx), "complete");
            String on = str(c, "on");
            boolean ok = false;
            if ("craft".equals(on)) {
                Material m = Material.matchMaterial(str(c, "item"));
                int count = c.get("count") instanceof Number ? ((Number) c.get("count")).intValue() : 1;
                ok = m != null && p.getStatistic(Statistic.CRAFT_ITEM, m) >= count;
            } else if ("has_items".equals(on)) {
                Object items = c.get("items");
                ok = items instanceof List && !((List<?>) items).isEmpty();
                if (ok) for (Object o : (List<?>) items) {
                    Material m = Material.matchMaterial(String.valueOf(o));
                    if (m == null || !p.getInventory().contains(m)) { ok = false; break; }
                }
            }
            if (!ok) return;
            advance(p, id);
        }
    }

    private void applyStuckHelp(Player p, String id, Session s, long now) {
        List<Map<?, ?>> steps = steps(id);
        int idx = stepIndex(p, id);
        if (idx < 0 || idx >= steps.size()) return;
        Map<String, Object> stuck = sub(steps.get(idx), "stuck");
        if (stuck.isEmpty()) return;
        TreeMap<Integer, String> byTime = new TreeMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) stuck).entrySet()) {
            try { byTime.put(Integer.parseInt(String.valueOf(e.getKey())), String.valueOf(e.getValue())); }
            catch (NumberFormatException ignored) { }
        }
        long waited = (now - s.lastProgressAt) / 1000;
        String text = str(steps.get(idx), "text");
        boolean first = true;
        for (Map.Entry<Integer, String> e : byTime.entrySet()) {
            if (waited < e.getKey()) break;
            if (first) {
                // The step text never changes; only the "where" help underneath it.
                s.bar.setTitle(utils.chat("&e" + text + " &7- " + e.getValue()));
                first = false;
            } else if (s.nudged.add(e.getKey())) {
                p.sendActionBar(net.kyori.adventure.text.Component.text(utils.chat("&7" + e.getValue())));
            }
        }
    }

    // ---- event triggers ----------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (!progress.contains(id.toString())) {
            if (p.hasPlayedBefore()) {
                // Here before questlines existed: nothing to teach, and they keep the claim bar.
                progress.set(id + ".grandfathered", true);
                unlock(p, "claim_bossbar");
                markDirty();
            } else {
                for (String q : questlineIds()) {
                    ConfigurationSection start = questline(q).getConfigurationSection("start");
                    if (start != null && "first_join".equals(start.getString("on"))) startQuestline(p, q);
                }
            }
        }
        refresh(p);
        utils.updateBossBar(p, utils.getClaimID(p.getLocation()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Session s = sessions.remove(e.getPlayer().getUniqueId());
        if (s != null) s.bar.removeAll();
        saveIfDirty();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        deathTick = Bukkit.getCurrentTick();
        deathLoc = e.getEntity().getLocation();
        deathType = e.getEntityType();
    }

    /** Drops spawn in the same tick as the death; remember what killed-off entity each item came from. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent e) {
        if (deathLoc == null || Bukkit.getCurrentTick() != deathTick) return;
        if (!Objects.equals(deathLoc.getWorld(), e.getLocation().getWorld())) return;
        if (deathLoc.distanceSquared(e.getLocation()) > 9) return;
        long now = System.currentTimeMillis();
        itemSources.put(e.getEntity().getUniqueId(), new ItemSource(deathType, now));
        itemSources.values().removeIf(src -> now - src.at > ITEM_SOURCE_TTL_MS);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        Item item = e.getItem();
        ItemStack stack = item.getItemStack();
        ItemSource src = itemSources.get(item.getUniqueId());
        for (String id : questlineIds()) {
            if (!isActive(p, id)) continue;
            List<Map<?, ?>> steps = steps(id);
            int idx = stepIndex(p, id);
            if (idx >= steps.size()) continue;
            Map<String, Object> c = sub(steps.get(idx), "complete");
            if (!"pickup".equals(str(c, "on"))) continue;
            Material m = Material.matchMaterial(String.valueOf(c.get("item")));
            if (m == null || stack.getType() != m) continue;
            String need = str(c, "source_entity");
            if (need != null && (src == null || !src.type.name().equalsIgnoreCase(need))) continue;
            advance(p, id);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        // The statistic updates after the event; check once it has.
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String id : questlineIds()) if (isActive(p, id)) checkPolled(p, id);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBookEdit(PlayerEditBookEvent e) {
        Player p = e.getPlayer();
        BookMeta meta = e.getNewBookMeta();
        StringBuilder sb = new StringBuilder();
        for (String page : meta.getPages()) sb.append(page).append(' ');
        String text = org.bukkit.ChatColor.stripColor(sb.toString()).toLowerCase(Locale.ROOT);
        for (String id : questlineIds()) {
            if (!isActive(p, id)) continue;
            List<Map<?, ?>> steps = steps(id);
            int idx = stepIndex(p, id);
            if (idx >= steps.size()) continue;
            Map<String, Object> c = sub(steps.get(idx), "complete");
            if (!"write_book".equals(str(c, "on"))) continue;
            Object words = c.get("contains");
            if (!(words instanceof List)) continue;
            boolean all = true;
            for (Object w : (List<?>) words) {
                String word = String.valueOf(w).replace("{player}", p.getName()).toLowerCase(Locale.ROOT);
                if (!text.contains(word)) { all = false; break; }
            }
            if (all) advance(p, id);
        }
    }

    @EventHandler
    public void onClaimBookPlaced(ClaimBookPlacedEvent e) {
        Player p = e.player;
        for (String id : questlineIds()) {
            if (!isActive(p, id)) continue;
            List<Map<?, ?>> steps = steps(id);
            int idx = stepIndex(p, id);
            if (idx >= steps.size()) continue;
            if ("claim_book_placed".equals(str(sub(steps.get(idx), "complete"), "on"))) advance(p, id);
        }
    }
}
