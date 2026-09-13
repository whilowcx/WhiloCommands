package dev.whilo.whilocommands.bungee;

import dev.whilo.whilocommands.CommandDefinition;
import dev.whilo.whilocommands.PluginConfig;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LinkCommandTest {
    private final ProxyServer proxy = mock(ProxyServer.class, RETURNS_DEEP_STUBS);
    private final PluginManager commands = new PluginManager(proxy, null, null, null);
    private final ProxiedPlayer player = mock(ProxiedPlayer.class);

    private LinkCommand register(String message, long seconds) throws IOException {
        when(proxy.getDisabledCommands()).thenReturn(List.of());
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        LinkCommand command = new LinkCommand(new CommandDefinition("vote", List.of("votes"), "vote.use",
            message, "&7Open link", "https://example.com/vote", seconds * 1_000_000_000L),
            PluginConfig.defaultMessages());
        commands.registerCommand(mock(Plugin.class), command);
        return command;
    }

    @Test
    void nativeDispatcherDeniesBothNamesWithoutConsumingCooldown() throws IOException {
        register("Vote", 60);
        assertTrue(commands.dispatchCommand(player, "vote"));
        assertTrue(commands.dispatchCommand(player, "votes"));
        verify(player, times(2)).sendMessage(anyString());
        verify(player, never()).sendMessage(any(BaseComponent.class));
        when(player.hasPermission("vote.use")).thenReturn(true);
        assertTrue(commands.dispatchCommand(player, "votes"));
        ArgumentCaptor<BaseComponent> sent = ArgumentCaptor.forClass(BaseComponent.class);
        verify(player).sendMessage(sent.capture());
        assertNotNull(sent.getValue().getClickEvent());
    }

    @Test
    void configuredClickOverridesUrlsDetectedInMessage() throws IOException {
        register("&#12ab34https://other.example.com", 0);
        when(player.hasPermission("vote.use")).thenReturn(true);
        commands.dispatchCommand(player, "vote");
        ArgumentCaptor<BaseComponent> sent = ArgumentCaptor.forClass(BaseComponent.class);
        verify(player).sendMessage(sent.capture());
        assertEquals("https://other.example.com", sent.getValue().toPlainText());
        assertClick(sent.getValue());
        assertNotNull(sent.getValue().getHoverEvent());
        assertTrue(sent.getValue().toLegacyText().contains("§x§1§2§a§b§3§4"));
    }

    @Test
    void aliasesShareCooldownAndDisconnectClearsIt() throws IOException {
        LinkCommand command = register("Vote", 60);
        when(player.hasPermission("vote.use")).thenReturn(true);
        commands.dispatchCommand(player, "vote");
        commands.dispatchCommand(player, "votes");
        ArgumentCaptor<BaseComponent> sent = ArgumentCaptor.forClass(BaseComponent.class);
        verify(player, times(2)).sendMessage(sent.capture());
        assertNull(sent.getValue().getClickEvent());
        command.forget(player.getUniqueId());
        commands.dispatchCommand(player, "votes");
        verify(player, times(3)).sendMessage(sent.capture());
        assertNotNull(sent.getValue().getClickEvent());
    }

    @Test
    void sendingDoesNotExposeTheCachedMutableComponent() throws IOException {
        register("Vote", 0);
        when(player.hasPermission("vote.use")).thenReturn(true);
        commands.dispatchCommand(player, "vote");
        commands.dispatchCommand(player, "vote");
        ArgumentCaptor<BaseComponent> sent = ArgumentCaptor.forClass(BaseComponent.class);
        verify(player, times(2)).sendMessage(sent.capture());
        assertNotSame(sent.getAllValues().get(0), sent.getAllValues().get(1));
    }

    private void assertClick(BaseComponent component) {
        assertEquals(ClickEvent.Action.OPEN_URL, component.getClickEvent().getAction());
        assertEquals("https://example.com/vote", component.getClickEvent().getValue());
        if (component.getExtra() != null) {
            component.getExtra().forEach(this::assertClick);
        }
    }
}
