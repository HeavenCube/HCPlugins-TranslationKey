package fr.noltox.hcplugins.translationkey;

import com.github.retrooper.packetevents.*;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.*;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.chat.*;
import com.github.retrooper.packetevents.protocol.packettype.*;
import com.github.retrooper.packetevents.protocol.player.*;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import io.github.retrooper.packetevents.impl.netty.NettyManagerImpl;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PacketSurfaceTest {
    private PacketEventsAPI<?> previous;
    private User user;
    @BeforeEach void setup() {
        previous = PacketEvents.getAPI();
        PacketEvents.setAPI(new Api());
        user = new User(new Object(), ConnectionState.PLAY, ClientVersion.V_26_3, new UserProfile(UUID.randomUUID(), "Test"));
    }
    @AfterEach void restore() { PacketEvents.setAPI(previous); }
    @Test void playTextWrappersRoundTripOn263WithoutRetainingEventBuffers() {
        var source = Component.text("[[hctkey:a]]");
        var packets = List.of(
                new WrapperPlayServerSystemChatMessage(false, source),
                new WrapperPlayServerSystemChatMessage(true, source),
                new WrapperPlayServerActionBar(source),
                new WrapperPlayServerSetTitleText(source),
                new WrapperPlayServerSetTitleSubtitle(source),
                new WrapperPlayServerDisguisedChat(source, new ChatType.Bound(ChatTypes.CHAT, Component.text("Test"), null)),
                new WrapperPlayServerPlayerListHeaderAndFooter(source, source),
                new WrapperPlayServerDisconnect(source));
        for (PacketWrapper<?> packet : packets) {
            Object buffer = PacketEvents.getAPI().getNettyManager().getByteBufAllocationOperator().buffer();
            try {
                var event = new Send(packet.getPacketTypeData().getPacketType(), user, buffer);
                event.setLastUsedWrapper(packet);
                PacketSurface surface = PacketSurface.read(event);
                assertNotNull(surface);
                assertNull(surface.packet().getBuffer(), "deferred copy must not own Netty data");
                var replacements = surface.components().stream().map(c -> Component.text("Remplacé")).map(Component.class::cast).toList();
                surface.update().accept(replacements);
                surface.commit().accept(event);
                assertSame(buffer, event.getLastUsedWrapper().getBuffer(), "direct rewrite must keep event buffer");
                event.getLastUsedWrapper().write();
                ByteBufHelper.readerIndex(buffer, 0);
                PacketSurface decoded = PacketSurface.read(new Send(packet.getPacketTypeData().getPacketType(), user, buffer));
                assertEquals(replacements, decoded.components(), packet.getClass().getSimpleName());
            } finally { ByteBufHelper.release(buffer); }
        }
    }
    @Test void bossbarControlAndTitleTimingMetadataArePreserved() {
        var bossbar = new WrapperPlayServerBossBar(UUID.randomUUID(), WrapperPlayServerBossBar.Action.UPDATE_TITLE);
        bossbar.setTitle(Component.text("[[hctkey:a]]"));
        for (PacketWrapper<?> packet : List.of(bossbar, new WrapperPlayServerBossBar(bossbar.getUUID(), WrapperPlayServerBossBar.Action.REMOVE),
                new WrapperPlayServerSetTitleTimes(10, 60, 20), new WrapperPlayServerClearTitles(true))) {
            Object buffer = PacketEvents.getAPI().getNettyManager().getByteBufAllocationOperator().buffer();
            try {
                var event = new Send(packet.getPacketTypeData().getPacketType(), user, buffer);
                event.setLastUsedWrapper(packet);
                var surface = PacketSurface.read(event);
                assertNotNull(surface);
                surface.commit().accept(event); event.getLastUsedWrapper().write();
                ByteBufHelper.readerIndex(buffer, 0);
                var decoded = PacketSurface.read(new Send(packet.getPacketTypeData().getPacketType(), user, buffer));
                assertEquals(surface.components(), decoded.components());
                if (packet instanceof WrapperPlayServerBossBar b) {
                    var actual = (WrapperPlayServerBossBar) decoded.packet();
                    assertEquals(b.getAction(), actual.getAction()); assertEquals(b.getUUID(), actual.getUUID());
                } else if (packet instanceof WrapperPlayServerSetTitleTimes t) {
                    var actual = (WrapperPlayServerSetTitleTimes) decoded.packet();
                    assertEquals(t.getStayTicks(), actual.getStayTicks());
                    assertEquals(t.getFadeInTicks(), actual.getFadeInTicks());
                    assertEquals(t.getFadeOutTicks(), actual.getFadeOutTicks());
                } else assertTrue(((WrapperPlayServerClearTitles) decoded.packet()).getReset());
            } finally { ByteBufHelper.release(buffer); }
        }
    }
    @Test void inventoryAndSignedPlayerChatAreNotIntercepted() {
        for (var type : List.of(PacketType.Play.Server.WINDOW_ITEMS, PacketType.Play.Server.CHAT_MESSAGE)) {
            Object buffer = PacketEvents.getAPI().getNettyManager().getByteBufAllocationOperator().buffer();
            try { assertNull(PacketSurface.read(new Send(type, user, buffer))); }
            finally { ByteBufHelper.release(buffer); }
        }
    }
    private static final class Send extends PacketSendEvent {
        Send(PacketTypeCommon type, User user, Object buffer) {
            super(type.getId(ClientVersion.V_26_3), type, ServerVersion.V_26_3, user.getChannel(), user, null, buffer);
        }
    }
    private static final class Api extends PacketEventsAPI<Void> {
        private final NettyManager netty = new NettyManagerImpl();
        @Override public boolean isLoaded() { return true; }
        @Override public void init() { throw new UnsupportedOperationException(); }
        @Override public boolean isInitialized() { return true; }
        @Override public boolean isTerminated() { return false; }
        @Override public Void getPlugin() { return null; }
        @Override public ServerManager getServerManager() { return () -> ServerVersion.V_26_3; }
        @Override public ProtocolManager getProtocolManager() { throw new UnsupportedOperationException(); }
        @Override public PlayerManager getPlayerManager() { throw new UnsupportedOperationException(); }
        @Override public NettyManager getNettyManager() { return netty; }
        @Override public ChannelInjector getInjector() { throw new UnsupportedOperationException(); }
    }
}
