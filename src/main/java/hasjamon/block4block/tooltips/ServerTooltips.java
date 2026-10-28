package hasjamon.block4block.tooltips;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.protocol.component.ComponentType;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.HashedComponentPatchMap;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore;
import com.github.retrooper.packetevents.protocol.item.HashedStack;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.recipe.data.MerchantOffer;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMerchantOffers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import hasjamon.block4block.Block4Block;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adds a client-only "Block for Block / Free to Break / Free in Claims" lore line to block items, by rewriting the
 * packets that carry item stacks to the player. The server's real items are never changed, so stacking, hoppers and
 * item sorters behave exactly as in vanilla.
 * <p>
 * Three things keep it consistent with the server:
 * <ul>
 *   <li>every clientbound packet that shows an item to the player is decorated (inventory, slot, cursor, trades);</li>
 *   <li>on every container click the client's hash of our line is replaced by the hash of the real item (see
 *       {@link LoreHasher}), so the server accepts the click instead of re-sending the slot;</li>
 *   <li>creative clients, which send whole stacks back, have our line stripped.</li>
 * </ul>
 * Items that already carry lore of their own are left alone: their client hash would differ from the server's in a way
 * only the real item can resolve, and block items with custom lore are rare.
 * <p>
 * Failure is safe: if the server's hash code cannot be reached (a Minecraft version this was not built for), nothing is
 * decorated and a single warning is logged.
 */
public final class ServerTooltips implements PacketListener {
    private enum Kind { NONE, BREAK_COST, FREE_TO_BREAK, FREE_IN_CLAIMS }

    /** Everything that depends on config; replaced as a whole on reload. */
    private static final class Snapshot {
        final Set<String> freeToBreak = new HashSet<>();
        final Set<String> freeInClaims = new HashSet<>();
        final Map<Kind, Component> lines = new EnumMap<>(Kind.class);
        final Set<String> texts = new HashSet<>();
        final Map<ItemType, Kind> kindOf = new ConcurrentHashMap<>();
        final Map<Kind, Integer> hashOf = new ConcurrentHashMap<>();
    }

    private final Block4Block plugin;
    private final LoreHasher hasher = new LoreHasher();
    private volatile Snapshot snap;
    private volatile boolean broken;
    private PacketListenerCommon registered;

    public ServerTooltips(Block4Block plugin) {
        this.plugin = plugin;
        reload(plugin.getConfig());
    }

    public void register() {
        registered = PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.NORMAL);
        plugin.getServer().getPluginManager().registerEvents(resyncTriggers, plugin);
    }

    public void unregister() {
        org.bukkit.event.HandlerList.unregisterAll(resyncTriggers);
        if (registered != null) PacketEvents.getAPI().getEventManager().unregisterListener(registered);
        registered = null;
    }

    public void reload(FileConfiguration cfg) {
        Snapshot s = new Snapshot();
        cfg.getStringList("blacklisted-blocks").forEach(id -> s.freeToBreak.add(norm(id)));
        cfg.getStringList("blacklisted-claim-blocks").forEach(id -> s.freeInClaims.add(norm(id)));
        s.lines.put(Kind.BREAK_COST, line(cfg.getString("server-tooltips.text.block-for-block", "Block for Block"), NamedTextColor.RED));
        s.lines.put(Kind.FREE_TO_BREAK, line(cfg.getString("server-tooltips.text.free-to-break", "Free to Break"), NamedTextColor.GREEN));
        s.lines.put(Kind.FREE_IN_CLAIMS, line(cfg.getString("server-tooltips.text.free-in-claims", "Free in Claims"), NamedTextColor.AQUA));
        s.lines.values().forEach(c -> s.texts.add(plain(c)));
        this.snap = s;
        this.broken = false;
    }

    // ---------- clientbound: add the line ----------
    @Override
    public void onPacketSend(PacketSendEvent e) {
        if (broken) return;
        var type = e.getPacketType();
        boolean touched = false;
        if (type == PacketType.Play.Server.WINDOW_ITEMS) {
            var w = new WrapperPlayServerWindowItems(e);
            Player p = playerOf(e);
            List<ItemStack> items = new ArrayList<>(w.getItems().size());
            for (ItemStack i : w.getItems()) items.add(decorate(i, p));
            w.setItems(items);
            w.getCarriedItem().ifPresent(c -> w.setCarriedItem(decorate(c, p)));
            touched = true;
        } else if (type == PacketType.Play.Server.SET_SLOT) {
            var w = new WrapperPlayServerSetSlot(e);
            w.setItem(decorate(w.getItem(), playerOf(e)));
            touched = true;
        } else if (type == PacketType.Play.Server.SET_CURSOR_ITEM) {
            var w = new WrapperPlayServerSetCursorItem(e);
            w.setStack(decorate(w.getStack(), playerOf(e)));
            touched = true;
        } else if (type == PacketType.Play.Server.SET_PLAYER_INVENTORY) {
            var w = new WrapperPlayServerSetPlayerInventory(e);
            w.setStack(decorate(w.getStack(), playerOf(e)));
            touched = true;
        } else if (type == PacketType.Play.Server.MERCHANT_OFFERS) {
            var w = new WrapperPlayServerMerchantOffers(e);
            Player p = playerOf(e);
            for (MerchantOffer offer : w.getMerchantOffers()) offer.setOutputItem(decorate(offer.getOutputItem(), p));
            touched = true;
        }
        if (touched) e.markForReEncode(true);
    }

    // ---------- serverbound: keep the server's view of the click consistent ----------
    @Override
    public void onPacketReceive(PacketReceiveEvent e) {
        if (broken) return;
        var type = e.getPacketType();
        if (type == PacketType.Play.Client.CLICK_WINDOW) {
            var w = new WrapperPlayClientClickWindow(e);
            Player p = playerOf(e);
            boolean changed = false;
            Map<Integer, Optional<HashedStack>> slots = new HashMap<>();
            for (var en : w.getHashedSlots().entrySet()) {
                Optional<HashedStack> fixed = en.getValue().map(h -> unhash(h, p));
                if (fixed.isPresent() && fixed.get() != en.getValue().get()) changed = true;
                slots.put(en.getKey(), fixed);
            }
            Optional<HashedStack> carried = w.getCarriedHashedStack().map(h -> unhash(h, p));
            if (carried.isPresent() && carried.get() != w.getCarriedHashedStack().get()) changed = true;
            if (changed) {
                w.setHashedSlots(slots);
                w.setCarriedHashedStack(carried);
                e.markForReEncode(true);
            }
        } else if (type == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) {
            var w = new WrapperPlayClientCreativeInventoryAction(e);
            Player p = playerOf(e);
            ItemStack stripped = strip(w.getItemStack());
            if (stripped != w.getItemStack()) {
                w.setItemStack(stripped);
                e.markForReEncode(true);
            }
            // The client made this stack itself, so the server never sends it back and it would stay without the
            // line. Resync the inventory once the server has applied the change (decorated by onPacketSend).
            ItemStack placed = w.getItemStack();
            if (p != null && w.getSlot() >= 0 && placed != null && !placed.isEmpty() && kindOf(placed.getType()) != Kind.NONE)
                scheduleResync(p);
        }
    }

    private final java.util.concurrent.atomic.AtomicLong rewrites = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong misses = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicInteger missesLogged = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicLong resyncs = new java.util.concurrent.atomic.AtomicLong();

    /** For /b4btooltips status: how often the client's hash was fixed, and how often it did not match. */
    public String status() {
        return "clicks fixed: " + rewrites.get() + ", hash mismatches: " + misses.get() + ", creative resyncs: " + resyncs.get()
                + (broken ? " (DISABLED: server hash code unreachable)" : "");
    }

    /** Players who put client-made stacks into their inventory in creative and have not had them decorated yet. */
    private final Set<UUID> creativeDirty = ConcurrentHashMap.newKeySet();

    /**
     * Remembers that this player's inventory needs a resync. It is done later, when they close the inventory or leave
     * creative, never mid-drag: a resync while the creative screen is in use overwrites what the client is holding.
     */
    private void scheduleResync(Player p) {
        creativeDirty.add(p.getUniqueId());
    }

    private final org.bukkit.event.Listener resyncTriggers = new org.bukkit.event.Listener() {
        @org.bukkit.event.EventHandler
        public void onClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
            if (e.getPlayer() instanceof Player p) flush(p);
        }

        @org.bukkit.event.EventHandler
        public void onMode(org.bukkit.event.player.PlayerGameModeChangeEvent e) {
            flush(e.getPlayer());
        }

        @org.bukkit.event.EventHandler
        public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
            creativeDirty.remove(e.getPlayer().getUniqueId());
        }
    };

    private void flush(Player p) {
        if (!creativeDirty.remove(p.getUniqueId())) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            resyncs.incrementAndGet();
            if (p.isOnline()) p.updateInventory();
        }, 2L);
    }

    // ---------- core ----------
    private ItemStack decorate(ItemStack item, Player player) {
        if (item == null || item.isEmpty() || player == null) return item;
        Kind kind = kindOf(item.getType());
        if (kind == Kind.NONE) return item;
        if (!currentLore(item).isEmpty()) return item;                 // real lore: leave it alone (see class comment)
        if (hashOf(kind, player) == null) return item;                 // cannot match the client's hash: do not decorate
        ItemStack copy = item.copy();
        copy.setComponent(ComponentTypes.LORE, new ItemLore(List.of(snap.lines.get(kind))));
        return copy;
    }

    private HashedStack unhash(HashedStack hs, Player player) {
        if (player == null) return hs;
        Kind kind = kindOf(hs.getItem());
        if (kind == Kind.NONE) return hs;
        Map<ComponentType<?>, Integer> added = hs.getComponents().getAddedComponents();
        Integer sent = added.get(ComponentTypes.LORE);
        if (sent == null) return hs;                                   // the client holds this stack without our line
        Integer expected = hashOf(kind, player);
        if (!sent.equals(expected)) {
            misses.incrementAndGet();
            if (missesLogged.getAndIncrement() < 20)
                plugin.getLogger().warning("Tooltip hash mismatch on " + hs.getItem().getName() + ": client sent lore hash "
                        + sent + ", expected " + expected + " (kind " + kind + ")");
            return hs;
        }
        rewrites.incrementAndGet();
        Map<ComponentType<?>, Integer> keep = new HashMap<>(added);
        keep.remove(ComponentTypes.LORE);
        return new HashedStack(hs.getItem(), hs.getCount(), new HashedComponentPatchMap(keep, hs.getComponents().getRemovedComponents()));
    }

    private ItemStack strip(ItemStack item) {
        if (item == null || item.isEmpty()) return item;
        List<Component> lines = currentLore(item);
        if (lines.size() != 1 || !isOurs(lines.get(0))) return item;
        ItemStack copy = item.copy();
        copy.unsetComponent(ComponentTypes.LORE);
        // unsetComponent leaves an explicit "lore removed" entry in the patch. The stack the server keeps would then
        // differ from the one the client holds (which never had lore), and every later click on it would mismatch.
        var old = copy.getComponents();
        Map<ComponentType<?>, Optional<?>> patches = new LinkedHashMap<>(old.getPatches());
        patches.remove(ComponentTypes.LORE);
        copy.setComponents(new com.github.retrooper.packetevents.protocol.component.PatchableComponentMap(old.getBase(), patches));
        return copy;
    }

    /**
     * Is this lore line ours? Compared by text, not by object: the line comes back from the client decoded from NBT, so
     * it is not guaranteed to equal the Component we built, and a line that slipped through would be stored as the
     * item's real lore.
     */
    private boolean isOurs(Component c) {
        Snapshot s = snap;
        return s.lines.containsValue(c) || s.texts.contains(plain(c));
    }

    private static String plain(Component c) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** The hash the client sends for a stack carrying only this kind's line, from the server's own hash code. */
    private Integer hashOf(Kind kind, Player player) {
        Snapshot s = snap;
        Integer cached = s.hashOf.get(kind);
        if (cached != null) return cached;
        Integer computed = hasher.hashOf(player, s.lines.get(kind));
        if (computed == null) {
            if (!broken) {
                broken = true;
                plugin.getLogger().warning("Server-side tooltips disabled: could not reach the server's item hash code. "
                        + "This build supports Paper 1.21.5+; check for an update. Players are shown no tooltip line.");
            }
            return null;
        }
        s.hashOf.put(kind, computed);
        return computed;
    }

    private Kind kindOf(ItemType type) {
        Snapshot s = snap;
        return s.kindOf.computeIfAbsent(type, t -> {
            String key = t.getName().toString();                       // "minecraft:dirt"
            Material m = Material.matchMaterial(key);
            if (m == null || !m.isBlock()) return Kind.NONE;           // block items only, like the mod
            String id = m.name();
            if (s.freeToBreak.contains(id)) return Kind.FREE_TO_BREAK;
            if (s.freeInClaims.contains(id)) return Kind.FREE_IN_CLAIMS;
            return Kind.BREAK_COST;
        });
    }

    private static List<Component> currentLore(ItemStack item) {
        return item.getComponent(ComponentTypes.LORE).map(ItemLore::getLines).orElse(List.of());
    }

    private static Player playerOf(PacketSendEvent e) {
        return e.getPlayer() instanceof Player p ? p : null;
    }

    private static Player playerOf(PacketReceiveEvent e) {
        return e.getPlayer() instanceof Player p ? p : null;
    }

    private static Component line(String text, NamedTextColor color) {
        // Lore is italic by default; turn it off so it reads like the mod's line.
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static String norm(String id) {
        return id.toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
    }
}
