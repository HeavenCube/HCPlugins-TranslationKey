package fr.noltox.hcplugins.translationkey;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import net.kyori.adventure.text.Component;
import java.util.List;
import java.util.function.Consumer;

/** Only PLAY components; never signed chat bodies, inventory packets or raw protocol buffers. */
record PacketSurface(PacketWrapper<?> packet, List<Component> components, Consumer<List<Component>> update, boolean disconnect, Consumer<PacketSendEvent> commit) {
    static PacketSurface read(PacketSendEvent event) {
        if (!(event.getPacketType() instanceof PacketType.Play.Server type)) return null;
        return switch (type) {
            case SYSTEM_CHAT_MESSAGE -> {
                var original = new WrapperPlayServerSystemChatMessage(event);
                var copy = new WrapperPlayServerSystemChatMessage(original.isOverlay(), original.getMessage());
                yield new PacketSurface(copy, List.of(copy.getMessage()), c -> copy.setMessage(c.getFirst()), false, e -> new WrapperPlayServerSystemChatMessage(e).copy(copy));
            }
            case DISGUISED_CHAT -> {
                var original = new WrapperPlayServerDisguisedChat(event);
                var copy = new WrapperPlayServerDisguisedChat(original.getMessage(), original.getChatFormatting());
                yield new PacketSurface(copy, List.of(copy.getMessage()), c -> copy.setMessage(c.getFirst()), false, e -> new WrapperPlayServerDisguisedChat(e).copy(copy));
            }
            case ACTION_BAR -> {
                var copy = new WrapperPlayServerActionBar(new WrapperPlayServerActionBar(event).getActionBarText());
                yield new PacketSurface(copy, List.of(copy.getActionBarText()), c -> copy.setActionBarText(c.getFirst()), false, e -> new WrapperPlayServerActionBar(e).copy(copy));
            }
            case SET_TITLE_TEXT -> {
                var copy = new WrapperPlayServerSetTitleText(new WrapperPlayServerSetTitleText(event).getTitle());
                yield new PacketSurface(copy, List.of(copy.getTitle()), c -> copy.setTitle(c.getFirst()), false, e -> new WrapperPlayServerSetTitleText(e).copy(copy));
            }
            case SET_TITLE_SUBTITLE -> {
                var copy = new WrapperPlayServerSetTitleSubtitle(new WrapperPlayServerSetTitleSubtitle(event).getSubtitle());
                yield new PacketSurface(copy, List.of(copy.getSubtitle()), c -> copy.setSubtitle(c.getFirst()), false, e -> new WrapperPlayServerSetTitleSubtitle(e).copy(copy));
            }
            case BOSS_BAR -> {
                var original = new WrapperPlayServerBossBar(event);
                var copy = new WrapperPlayServerBossBar(original.getUUID(), original.getAction());
                copy.copy(original);
                boolean title = copy.getAction() == WrapperPlayServerBossBar.Action.ADD || copy.getAction() == WrapperPlayServerBossBar.Action.UPDATE_TITLE;
                yield new PacketSurface(copy, title ? List.of(copy.getTitle()) : List.of(),
                        c -> { if (title) copy.setTitle(c.getFirst()); }, false, e -> new WrapperPlayServerBossBar(e).copy(copy));
            }
            case PLAYER_LIST_HEADER_AND_FOOTER -> {
                var original = new WrapperPlayServerPlayerListHeaderAndFooter(event);
                var copy = new WrapperPlayServerPlayerListHeaderAndFooter(original.getHeader(), original.getFooter());
                yield new PacketSurface(copy, List.of(copy.getHeader(), copy.getFooter()),
                        c -> { copy.setHeader(c.get(0)); copy.setFooter(c.get(1)); }, false, e -> new WrapperPlayServerPlayerListHeaderAndFooter(e).copy(copy));
            }
            case DISCONNECT -> {
                var copy = new WrapperPlayServerDisconnect(new WrapperPlayServerDisconnect(event).getReason());
                yield new PacketSurface(copy, List.of(copy.getReason()), c -> copy.setReason(c.getFirst()), true, e -> new WrapperPlayServerDisconnect(e).copy(copy));
            }
            // These must follow a deferred title in the same queue even though they have no text.
            case SET_TITLE_TIMES -> {
                var original = new WrapperPlayServerSetTitleTimes(event);
                var copy = new WrapperPlayServerSetTitleTimes(original.getFadeInTicks(), original.getStayTicks(), original.getFadeOutTicks());
                yield new PacketSurface(copy, List.of(), ignored -> {}, false, e -> new WrapperPlayServerSetTitleTimes(e).copy(copy));
            }
            case CLEAR_TITLES -> {
                var copy = new WrapperPlayServerClearTitles(new WrapperPlayServerClearTitles(event).getReset());
                yield new PacketSurface(copy, List.of(), ignored -> {}, false, e -> new WrapperPlayServerClearTitles(e).copy(copy));
            }
            default -> null;
        };
    }
}
