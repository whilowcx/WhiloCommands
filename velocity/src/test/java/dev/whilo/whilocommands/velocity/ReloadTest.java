package dev.whilo.whilocommands.velocity;

import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReloadTest {
    @TempDir
    Path directory;
    private final Map<String, CommandMeta> metadata = new HashMap<>();
    private final Map<String, Command> registered = new HashMap<>();
    private final ProxyServer proxy = mock(ProxyServer.class);
    private final CommandManager commands = mock(CommandManager.class);
    private WhiloCommandsPlugin plugin;

    @BeforeEach
    void setUp() throws IOException {
        when(proxy.getCommandManager()).thenReturn(commands);
        when(commands.getCommandMeta(anyString())).thenAnswer(call -> metadata.get(call.getArgument(0)));
        when(commands.hasCommand(anyString())).thenAnswer(call -> metadata.containsKey(call.getArgument(0)));
        when(commands.metaBuilder(anyString())).thenAnswer(call -> {
            List<String> aliases = new ArrayList<>();
            aliases.add(call.getArgument(0));
            CommandMeta.Builder builder = mock(CommandMeta.Builder.class, RETURNS_SELF);
            when(builder.aliases(any(String[].class))).thenAnswer(add -> {
                for (Object alias : add.getArguments()) {
                    aliases.add((String) alias);
                }
                return builder;
            });
            when(builder.build()).thenAnswer(build -> {
                CommandMeta meta = mock(CommandMeta.class);
                when(meta.getAliases()).thenReturn(List.copyOf(aliases));
                return meta;
            });
            return builder;
        });
        doAnswer(call -> {
            CommandMeta meta = call.getArgument(0);
            for (String alias : meta.getAliases()) {
                assertFalse(registered.containsKey(alias), "attempted to replace an existing command");
                metadata.put(alias, meta);
                registered.put(alias, call.getArgument(1));
            }
            return null;
        }).when(commands).register(any(CommandMeta.class), any(Command.class));
        doAnswer(call -> {
            CommandMeta meta = call.getArgument(0);
            for (String alias : meta.getAliases()) {
                if (metadata.remove(alias, meta)) {
                    registered.remove(alias);
                }
            }
            return null;
        }).when(commands).unregister(any(CommandMeta.class));
        Files.writeString(directory.resolve("config.yml"),
            "commands:\n  vote:\n    aliases: [votes]\n    message: Vote\n");
        plugin = new WhiloCommandsPlugin(proxy, mock(Logger.class), directory);
    }

    @Test
    void reloadCommandAndAliasSurviveRepeatedReloads() {
        plugin.onInitialize(null);
        Command control = registered.get("wc");
        assertSame(control, registered.get("whilocommands"));
        for (int i = 0; i < 3; i++) {
            CommandSource console = mock(CommandSource.class);
            when(console.hasPermission("whilocommands.reload")).thenReturn(true);
            SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
            when(invocation.source()).thenReturn(console);
            when(invocation.arguments()).thenReturn(new String[]{"reload"});
            ((SimpleCommand) registered.get("wc")).execute(invocation);
            assertSame(control, registered.get("wc"));
            assertSame(control, registered.get("whilocommands"));
            assertSame(registered.get("vote"), registered.get("votes"));
        }
    }

    @Test
    void malformedReloadPreservesWorkingCommands() throws IOException {
        plugin.onInitialize(null);
        Map<String, Command> before = Map.copyOf(registered);
        Files.writeString(directory.resolve("config.yml"), "commands: [");
        assertFalse(plugin.reload());
        assertEquals(before, registered);
    }

    @Test
    void validReloadRemovesOldAliasesAndAddsNewCommands() throws IOException {
        plugin.onInitialize(null);
        Files.writeString(directory.resolve("config.yml"),
            "commands:\n  shop:\n    aliases: [store]\n    message: Shop");
        assertTrue(plugin.reload());
        assertFalse(registered.containsKey("vote"));
        assertFalse(registered.containsKey("votes"));
        assertSame(registered.get("shop"), registered.get("store"));
        assertNotNull(registered.get("wc"));
    }

    @Test
    void conflictKeepsPreviousCommandsAndDoesNotReplaceOtherPlugins() throws IOException {
        plugin.onInitialize(null);
        Command external = mock(SimpleCommand.class);
        registered.put("shop", external);
        metadata.put("shop", mock(CommandMeta.class));
        Map<String, Command> before = Map.copyOf(registered);
        Files.writeString(directory.resolve("config.yml"), "commands:\n  shop:\n    message: Shop");
        assertFalse(plugin.reload());
        assertEquals(before, registered);
    }

    @Test
    void badStartupConfigurationCanBeFixedWithReload() throws IOException {
        Files.writeString(directory.resolve("config.yml"), "commands: [");
        plugin.onInitialize(null);
        assertNotNull(registered.get("wc"));
        Files.writeString(directory.resolve("config.yml"), "commands:\n  vote:\n    message: Vote");
        assertTrue(plugin.reload());
        assertNotNull(registered.get("vote"));
    }
}
