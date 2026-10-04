package fr.noltox.hcplugins.translationkey;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.player.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import java.util.*;

/** Detached wrappers only cross the thread boundary; no reference-counted event/buffer is retained. */
final class PacketTranslator extends PacketListenerAbstract {
    private final HCTranslationKey plugin;
    private volatile boolean active = true;
    PacketTranslator(HCTranslationKey plugin) { super(PacketListenerPriority.HIGHEST); this.plugin = plugin; }
    void close() { active = false; }
    @Override public void onPacketSend(PacketSendEvent event) {
        if (!active || event.isCancelled()) return;
        var previous = event.getLastUsedWrapper();
        try {
            PacketSurface surface = PacketSurface.read(event);
            if (surface == null) return;
            boolean marked = surface.components().stream().anyMatch(ComponentRenderer::contains);
            boolean pending = plugin.handoff().pending();
            if (!marked && !pending) { event.setLastUsedWrapper(previous); return; }
            Catalog catalog = plugin.catalog();
            User user = event.getUser();
            if (surface.disconnect()) {
                if (marked) {
                    // The channel may close immediately: never defer a disconnect or dispatch its effects.
                    transform(surface, catalog, TextContext.IDENTITY, false);
                    surface.commit().accept(event); event.markForReEncode(true);
                }
                return;
            }
            if (!pending) {
                try {
                    TextContext context = Bukkit.isPrimaryThread()
                            ? PapiBridge.context(event.getPlayer(), catalog.allowedPlaceholders())
                            : value -> { if (PapiBridge.contains(value)) throw new NeedsServerThread(); return value; };
                    var effects = transform(surface, catalog, context, marked);
                    if (marked) {
                        surface.commit().accept(event); event.markForReEncode(true);
                        if (!effects.isEmpty()) event.getTasksAfterSend().add(() -> enqueueEffects(user, effects));
                    }
                    return;
                } catch (NeedsServerThread ignored) { /* Defer only genuinely dynamic work. */ }
            }
            // Preserve order across the intercepted text/control surfaces while a dynamic packet is pending.
            // Each wrapper owns Java data, not the event's Netty buffer.
            if (plugin.handoff().submit(() -> sendDeferred(user, surface, catalog, marked))) event.setCancelled(true);
            else { event.setLastUsedWrapper(previous); plugin.warn("File réseau pleine : paquet original conservé."); }
        } catch (RuntimeException ex) {
            event.setLastUsedWrapper(previous);
            plugin.warn("Marqueur réseau conservé : " + ex.getMessage());
        }
    }
    private static List<Effects.Effect> transform(PacketSurface surface, Catalog catalog, TextContext context, boolean dispatch) {
        var output = new ArrayList<Component>(surface.components().size());
        var effects = new LinkedHashSet<Effects.Effect>();
        var renderer = new ComponentRenderer(catalog);
        for (Component component : surface.components()) {
            var result = renderer.replace(component, context, dispatch);
            output.add(result.component()); effects.addAll(result.effects());
        }
        surface.update().accept(output);
        return List.copyOf(effects);
    }
    private Player player(User user) {
        if (!active || plugin.getServer().isStopping()) return null;
        Player player = plugin.getServer().getPlayer(user.getUUID());
        return player != null && player.isOnline() && PacketEvents.getAPI().getPlayerManager().getUser(player) == user ? player : null;
    }
    private void sendDeferred(User user, PacketSurface surface, Catalog catalog, boolean marked) {
        Player player = player(user);
        if (player == null) return;
        List<Effects.Effect> effects = List.of();
        try {
            if (marked) effects = transform(surface, catalog, PapiBridge.context(player, catalog.allowedPlaceholders()), true);
        } catch (RuntimeException ex) { plugin.warn("Marqueur différé conservé : " + ex.getMessage()); }
        // Silent avoids intercepting the same delayed packet again. The connection/session is revalidated above.
        user.sendPacketSilently(surface.packet());
        plugin.effects().dispatch(player, effects);
    }
    private void enqueueEffects(User user, List<Effects.Effect> effects) {
        if (active && !plugin.handoff().submit(() -> {
            Player player = player(user);
            if (player != null) plugin.effects().dispatch(player, effects);
        })) plugin.warn("File d'effets pleine : effets ignorés.");
    }
    private static final class NeedsServerThread extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
