package fr.noltox.hcplugins.translationkey;

import com.github.retrooper.packetevents.PacketEvents;
import fr.noltox.hcplugins.core.api.HCPluginsCore;
import fr.noltox.hcplugins.core.api.command.CoreCommandRegistration;
import fr.noltox.hcplugins.core.api.config.HCPluginFiles;
import fr.noltox.hcplugins.placeholdersextra.api.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Level;

/** Lifecycle only. The catalog and parser do not access server state. */
public final class HCTranslationKey extends JavaPlugin implements Listener {
    static final String PERMISSION = "hcplugins.tkey.admin";
    private CatalogStore catalogs;
    private Path configPath;
    private CoreCommandRegistration commandRegistration;
    private PlaceholderProviderRegistration providerRegistration;
    private PacketTranslator packets;
    private MainThreadQueue handoff;
    private EffectDispatcher effects;
    private ExecutorService loader;
    private final AtomicBoolean loading = new AtomicBoolean();
    private final AtomicLong lastWarning = new AtomicLong(Long.MIN_VALUE);
    private volatile boolean active;

    @Override public void onEnable() {
        try {
            configPath = HCPluginFiles.singleConfiguration(this);
            HCPluginFiles.copyDefault(this, "config.yml", configPath);
            catalogs = new CatalogStore(CatalogLoader.load(configPath));
            loader = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("HCTranslationKey-config").factory());
            effects = new EffectDispatcher(this);
            handoff = new MainThreadQueue(task -> getServer().getScheduler().runTask(this, task),
                    ex -> warn("Traitement différé interrompu : " + ex.getMessage()));
            active = true;
            commandRegistration = HCPluginsCore.require(this).register(this, "tkey",
                    "Catalogue de contenus HeavenCube", List.of(PERMISSION), new TranslationCommand(this));
            providerRegistration = HCPlaceholders.require(this).register(this, "tkey", new PlaceholderProvider() {
                @Override public String resolve(OfflinePlayer player, String expression) {
                    if (!active) return null;
                    Catalog snapshot = catalog();
                    try { return snapshot.render(expression, PapiBridge.context(player, snapshot.allowedPlaceholders()), false).miniMessage(); }
                    catch (RuntimeException ex) { warn("Placeholder tkey non résolu : " + ex.getMessage()); return null; }
                }
            });
            packets = new PacketTranslator(this);
            PacketEvents.getAPI().getEventManager().registerListener(packets);
            getServer().getPluginManager().registerEvents(this, this);
            reportAliases();
            getLogger().info(catalog().keys().size() + " clés/alias disponibles dans " + configPath.getFileName() + ".");
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "Impossible d'activer HCTranslationKey : " + ex.getMessage(), ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    @Override public void onDisable() {
        active = false;
        if (packets != null) { packets.close(); PacketEvents.getAPI().getEventManager().unregisterListener(packets); }
        if (handoff != null) handoff.close();
        if (loader != null) loader.shutdownNow();
        if (effects != null) effects.close();
        if (providerRegistration != null) providerRegistration.close();
        if (commandRegistration != null) commandRegistration.close();
        getServer().getScheduler().cancelTasks(this);
        HandlerList.unregisterAll((Listener) this);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { effects.remove(event.getPlayer().getUniqueId()); }
    boolean active() { return active; }
    Catalog catalog() { return catalogs.current(); }
    MainThreadQueue handoff() { return handoff; }
    EffectDispatcher effects() { return effects; }

    void load(CommandSender sender, boolean publish) {
        if (!loading.compareAndSet(false, true)) {
            sender.sendMessage(Component.text("Une validation est déjà en cours.", NamedTextColor.YELLOW)); return;
        }
        long started = System.nanoTime();
        loader.submit(() -> {
            Catalog candidate = null; RuntimeException failure = null;
            try { candidate = CatalogLoader.load(configPath); }
            catch (RuntimeException ex) { failure = ex; }
            if (!active) { loading.set(false); return; }
            Catalog result = candidate; RuntimeException error = failure;
            if (!handoff.submit(() -> {
                try {
                    if (!active) return;
                    if (error != null) {
                        sender.sendMessage(HCPluginsCore.translations(this).reloadFailure(this));
                        sender.sendMessage(Component.text(error.getMessage(), NamedTextColor.RED));
                        getLogger().log(Level.WARNING, "Catalogue rejeté, génération précédente conservée.", error);
                    } else if (publish) {
                        catalogs.reload(() -> result); reportAliases();
                        sender.sendMessage(HCPluginsCore.translations(this).reloadSuccess(this, System.nanoTime() - started));
                    } else sender.sendMessage(Component.text("Catalogue valide : " + result.keys().size() + " clés/alias.", NamedTextColor.GREEN));
                } finally { loading.set(false); }
            })) { loading.set(false); warn("File pleine : candidat de configuration non publié."); }
        });
    }
    private void reportAliases() {
        if (!catalog().aliases().isEmpty())
            getLogger().warning(catalog().aliases().size() + " alias déprécié(s) du catalogue (10 premiers, une fois par chargement) : "
                    + catalog().aliases().entrySet().stream().limit(10).toList());
    }
    void warn(String message) {
        long now = System.nanoTime(), previous = lastWarning.get();
        if ((previous == Long.MIN_VALUE || now - previous >= TimeUnit.SECONDS.toNanos(10))
                && lastWarning.compareAndSet(previous, now)) getLogger().warning(message);
    }
}
