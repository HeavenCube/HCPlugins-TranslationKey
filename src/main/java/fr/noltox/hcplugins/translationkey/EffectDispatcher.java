package fr.noltox.hcplugins.translationkey;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Main-thread owner of finite-lived bossbars; at most one HCTranslationKey bar per player. */
final class EffectDispatcher implements AutoCloseable {
    private record ActiveBar(BossBar bar, BukkitTask task) {}
    private final Plugin plugin;
    private final Map<UUID, ActiveBar> bars = new HashMap<>();
    EffectDispatcher(Plugin plugin) { this.plugin = plugin; }
    void dispatch(Player player, List<Effects.Effect> effects) {
        if (!player.isOnline()) return;
        for (Effects.Effect effect : new LinkedHashSet<>(effects)) switch (effect) {
            case Effects.Audio s -> player.playSound(Sound.sound(s.key(), s.source(), s.volume(), s.pitch()));
            case Effects.Actionbar a -> player.sendActionBar(a.text());
            case Effects.Title t -> player.showTitle(Title.title(t.title(), t.subtitle(), Title.Times.times(t.fadeIn(), t.stay(), t.fadeOut())));
            case Effects.Subtitle s -> {
                player.sendTitlePart(TitlePart.TIMES, Title.Times.times(s.fadeIn(), s.stay(), s.fadeOut()));
                player.sendTitlePart(TitlePart.SUBTITLE, s.text());
            }
            case Effects.Bar b -> {
                remove(player.getUniqueId());
                BossBar bar = BossBar.bossBar(b.text(), b.progress(), b.color(), b.overlay());
                UUID id = player.getUniqueId();
                player.showBossBar(bar);
                BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> remove(id), b.ticks());
                bars.put(id, new ActiveBar(bar, task));
            }
        }
    }
    void remove(UUID id) {
        ActiveBar old = bars.remove(id);
        if (old == null) return;
        old.task().cancel();
        Player player = plugin.getServer().getPlayer(id);
        if (player != null) player.hideBossBar(old.bar());
    }
    @Override public void close() { for (UUID id : List.copyOf(bars.keySet())) remove(id); }
}
