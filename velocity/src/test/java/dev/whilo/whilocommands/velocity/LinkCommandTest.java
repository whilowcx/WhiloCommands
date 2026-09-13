package dev.whilo.whilocommands.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import dev.whilo.whilocommands.CommandDefinition;
import dev.whilo.whilocommands.PluginConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LinkCommandTest {
    private final Player player = mock(Player.class);
    private final SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);

    private LinkCommand command(String message, long seconds) throws IOException {
        when(invocation.source()).thenReturn(player);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return new LinkCommand(new CommandDefinition("vote", List.of("votes"), "vote.use",
            message, "&7Open link", "https://example.com/vote", seconds * 1_000_000_000L),
            PluginConfig.defaultMessages());
    }

    @Test
    void denialStaysOnProxyAndDoesNotConsumeCooldown() throws IOException {
        LinkCommand command = command("Vote", 60);
        assertTrue(command.hasPermission(invocation));
        command.execute(invocation);
        verify(player).sendMessage(LinkCommand.color(PluginConfig.defaultMessages().get("no-permission")));
        when(player.hasPermission("vote.use")).thenReturn(true);
        command.execute(invocation);
        ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
        verify(player, times(2)).sendMessage(sent.capture());
        assertEquals(ClickEvent.openUrl("https://example.com/vote"), sent.getValue().clickEvent());
    }

    @Test
    void rendersHexColorHoverAndClick() throws IOException {
        LinkCommand command = command("&#12ab34Vote", 0);
        when(player.hasPermission("vote.use")).thenReturn(true);
        command.execute(invocation);
        ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(sent.capture());
        assertEquals(TextColor.color(0x12ab34), sent.getValue().color());
        assertEquals(ClickEvent.openUrl("https://example.com/vote"), sent.getValue().clickEvent());
        assertNotNull(sent.getValue().hoverEvent());
    }

    @Test
    void parsesDocumentedRgbFormatAndPreservesLegacyColors() {
        assertEquals(Component.text("Hello", TextColor.color(0x55ff55)),
            LinkCommand.color("&#55ff55Hello"));
        assertEquals(Component.text("Hello", net.kyori.adventure.text.format.NamedTextColor.GREEN),
            LinkCommand.color("&aHello"));
    }

    @Test
    void enforcesCooldownAndClearsItOnDisconnect() throws IOException {
        LinkCommand command = command("Vote", 60);
        when(player.hasPermission("vote.use")).thenReturn(true);
        command.execute(invocation);
        command.execute(invocation);
        ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
        verify(player, times(2)).sendMessage(sent.capture());
        assertNull(sent.getValue().clickEvent());
        command.forget(player.getUniqueId());
        command.execute(invocation);
        verify(player, times(3)).sendMessage(sent.capture());
        assertNotNull(sent.getValue().clickEvent());
    }

    @Test
    void handlesConsoleAndUnconfiguredMessages() throws IOException {
        LinkCommand command = command("", 0);
        when(player.hasPermission("vote.use")).thenReturn(true);
        command.execute(invocation);
        verify(player).sendMessage(LinkCommand.color(PluginConfig.defaultMessages().get("not-configured")));
        CommandSource console = mock(CommandSource.class);
        when(invocation.source()).thenReturn(console);
        command.execute(invocation);
        verify(console).sendMessage(LinkCommand.color(PluginConfig.defaultMessages().get("players-only")));
    }
}
