package hasjamon.block4block.tooltips;

import com.google.common.cache.LoadingCache;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

/**
 * Computes the hash the vanilla client sends for a stack whose only added component is our lore line.
 * <p>
 * Since 1.21.5 the client sends a hash of each component on every container click and the server compares it with
 * its own item. Our line exists only in the packets we send, so without this the hashes never match and the server
 * re-sends the slot after every click (phantom items on the cursor). The hash is not computable from PacketEvents
 * (its hashing leaves lore at 0), so the server's own hash generator is asked, through reflection on Mojang-mapped
 * server classes. This is version-bound: if any step fails, {@link #hashOf} returns null and the caller must stop
 * decorating rather than risk desyncs.
 */
final class LoreHasher {
    private Method getHandle, asNMSCopy, create, components, addedComponents;
    private Field synchronizer;
    private Class<?> generatorType;
    private boolean linked;

    /** The lore hash for a stack carrying exactly this one line, or null if the server's code could not be reached. */
    synchronized Integer hashOf(Player player, Component line) {
        try {
            link(player);
            Object handle = getHandle.invoke(player);
            Object sync = synchronizer.get(handle);
            Field cacheField = sync.getClass().getDeclaredField("cache");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            LoadingCache<Object, Object> cache = (LoadingCache<Object, Object>) cacheField.get(sync);
            Object generator = Proxy.newProxyInstance(generatorType.getClassLoader(), new Class<?>[]{generatorType},
                    (proxy, method, args) -> {
                        if (method.getName().equals("apply")) return cache.getUnchecked(args[0]);
                        throw new UnsupportedOperationException(method.getName());
                    });

            ItemStack plain = new ItemStack(Material.DIRT);
            ItemMeta meta = plain.getItemMeta();
            meta.lore(List.of(line));
            plain.setItemMeta(meta);

            Object hashed = create.invoke(null, asNMSCopy.invoke(null, plain), generator);
            Object patch = components.invoke(hashed);
            Map<?, ?> added = (Map<?, ?>) addedComponents.invoke(patch);
            for (Map.Entry<?, ?> e : added.entrySet())
                if (String.valueOf(e.getKey()).contains("lore")) return (Integer) e.getValue();
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private void link(Player player) throws ReflectiveOperationException {
        if (linked) return;
        Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
        getHandle = craftPlayer.getMethod("getHandle");
        synchronizer = getHandle.getReturnType().getField("containerSynchronizer");
        Class<?> craftStack = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
        asNMSCopy = craftStack.getMethod("asNMSCopy", ItemStack.class);
        generatorType = Class.forName("net.minecraft.network.HashedPatchMap$HashGenerator");
        Class<?> hashedStack = Class.forName("net.minecraft.network.HashedStack");
        create = hashedStack.getMethod("create", asNMSCopy.getReturnType(), generatorType);
        Class<?> actual = Class.forName("net.minecraft.network.HashedStack$ActualItem");
        components = actual.getMethod("components");
        addedComponents = components.getReturnType().getMethod("addedComponents");
        linked = true;
    }
}
