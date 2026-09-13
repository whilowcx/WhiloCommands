package dev.whilo.whilocommands.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.whilo.whilocommands.CommandDefinition;
import dev.whilo.whilocommands.PluginConfig;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Plugin(id = "whilocommands", name = "WhiloCommands", version = "1.0", authors = {"whilo"})
public final class WhiloCommandsPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path directory;
    private volatile Map<CommandMeta, LinkCommand> links = Map.of();
    private volatile Map<String, String> messages;
    private CommandMeta reloadCommand;

    @Inject
    public WhiloCommandsPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path directory) {
        this.proxy = proxy;
        this.logger = logger;
        this.directory = directory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        try {
            messages = PluginConfig.defaultMessages();
        } catch (IOException e) {
            logger.error("Cannot read bundled messages", e);
            return;
        }
        var commands = proxy.getCommandManager();
        if (commands.hasCommand("whilocommands") || commands.hasCommand("wc")) {
            logger.error("Cannot enable WhiloCommands: /whilocommands or /wc is already registered");
            return;
        }
        reloadCommand = commands.metaBuilder("whilocommands").aliases("wc").plugin(this).build();
        commands.register(reloadCommand, new ReloadCommand());
        reload();
    }

    synchronized boolean reload() {
        try {
            PluginConfig config = PluginConfig.load(directory);
            var commands = proxy.getCommandManager();
            Map<CommandMeta, LinkCommand> replacements = new LinkedHashMap<>();
            for (CommandDefinition definition : config.commands()) {
                for (String name : definition.names().toList()) {
                    CommandMeta registered = commands.getCommandMeta(name);
                    if (registered != null && !links.containsKey(registered)) {
                        throw new IOException("/" + name + " is already registered by another plugin");
                    }
                }
                CommandMeta meta = commands.metaBuilder(definition.name())
                    .aliases(definition.aliases().toArray(String[]::new)).plugin(this).build();
                replacements.put(meta, new LinkCommand(definition, config.messages()));
            }
            links.keySet().forEach(commands::unregister);
            replacements.forEach(commands::register);
            links = Map.copyOf(replacements);
            messages = config.messages();
            logger.info("Loaded {} link commands", links.size());
            return true;
        } catch (IOException e) {
            logger.error("Configuration not applied: {}", e.getMessage());
            return false;
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        links.values().forEach(command -> command.forget(event.getPlayer().getUniqueId()));
    }

    @Subscribe
    public synchronized void onShutdown(ProxyShutdownEvent event) {
        links.keySet().forEach(proxy.getCommandManager()::unregister);
        links = Map.of();
        if (reloadCommand != null) {
            proxy.getCommandManager().unregister(reloadCommand);
        }
    }

    private final class ReloadCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            var source = invocation.source();
            if (!source.hasPermission("whilocommands.reload")) {
                source.sendMessage(LinkCommand.color(messages.get("no-permission")));
                return;
            }
            String[] arguments = invocation.arguments();
            if (arguments.length != 1 || !arguments[0].equalsIgnoreCase("reload")) {
                source.sendMessage(LinkCommand.color(messages.get("reload-usage")));
                return;
            }
            boolean applied = reload();
            source.sendMessage(LinkCommand.color(messages.get(applied ? "reload-success" : "reload-failed")));
        }
    }
}
