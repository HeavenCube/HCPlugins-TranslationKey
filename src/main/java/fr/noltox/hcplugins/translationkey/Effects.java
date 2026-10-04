package fr.noltox.hcplugins.translationkey;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.bossbar.BossBar;
import java.time.Duration;
import java.util.List;

final class Effects {
    private Effects() {}
    sealed interface Effect permits Audio, Actionbar, Title, Bar {}
    record Audio(Key key, Sound.Source source, float volume, float pitch) implements Effect {}
    record Actionbar(Component text) implements Effect {}
    record Title(Component title, Component subtitle, Duration fadeIn, Duration stay, Duration fadeOut) implements Effect {}
    record Bar(Component text, BossBar.Color color, BossBar.Overlay overlay, float progress, long ticks) implements Effect {}
    record Spec(Audio sound, Template actionbar, TitleSpec title, BarSpec bossbar) {
        static final Spec EMPTY = new Spec(null, null, null, null);
        List<Template> templates() {
            var result = new java.util.ArrayList<Template>();
            if (actionbar != null) result.add(actionbar);
            if (title != null) { result.add(title.title()); result.add(title.subtitle()); }
            if (bossbar != null) result.add(bossbar.text());
            return List.copyOf(result);
        }
    }
    record TitleSpec(Template title, Template subtitle, Duration fadeIn, Duration stay, Duration fadeOut) {}
    record BarSpec(Template text, BossBar.Color color, BossBar.Overlay overlay, float progress, long ticks) {}
}
