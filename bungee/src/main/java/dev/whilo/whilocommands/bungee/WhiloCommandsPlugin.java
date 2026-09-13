package dev.whilo.whilocommands.bungee;

import dev.whilo.whilocommands.CommandDefinition;
import dev.whilo.whilocommands.PluginConfig;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class WhiloCommandsPlugin extends Plugin implements Listener {
    private volatile List<LinkCommand> links = List.of();
    private volatile Map<String, String> messages;
    private ReloadCommand reloadCommand;

    @Override
    public void onEnable() {
        try {
            messages = PluginConfig.defaultMessages();
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Cannot read bundled messages", e);
            return;
        }
        var commands = getProxy().getPluginManager();
        boolean conflict = commands.getCommands().stream()
            .anyMatch(command -> command.getKey().equalsIgnoreCase("whilocommands")
                || command.getKey().equalsIgnoreCase("wc"));
        if (conflict) {
            getLogger().severe("Cannot enable WhiloCommands: /whilocommands or /wc is already registered");
            return;
        }
        reloadCommand = new ReloadCommand();
        commands.registerCommand(this, reloadCommand);
        commands.registerListener(this, this);
        reload();
    }

    synchronized boolean reload() {
        try {
            PluginConfig config = PluginConfig.load(getDataFolder().toPath());
            var commands = getProxy().getPluginManager();
            Set<String> occupied = new HashSet<>();
            commands.getCommands().stream()
                .filter(command -> links.stream().noneMatch(link -> link == command.getValue()))
                .forEach(command -> occupied.add(command.getKey().toLowerCase(Locale.ROOT)));
            List<LinkCommand> replacements = new ArrayList<>();
            for (CommandDefinition definition : config.commands()) {
                for (String name : definition.names().toList()) {
                    if (occupied.contains(name)) {
                        throw new IOException("/" + name + " is already registered by another plugin");
                    }
                }
                replacements.add(new LinkCommand(definition, config.messages()));
            }
            links.forEach(commands::unregisterCommand);
            replacements.forEach(command -> commands.registerCommand(this, command));
            links = List.copyOf(replacements);
            messages = config.messages();
            reloadCommand.updatePermissionMessage();
            getLogger().info("Loaded " + links.size() + " link commands");
            return true;
        } catch (IOException e) {
            getLogger().severe("Configuration not applied: " + e.getMessage());
            return false;
        }
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        links.forEach(command -> command.forget(event.getPlayer().getUniqueId()));
    }

    @Override
    public synchronized void onDisable() {
        getProxy().getPluginManager().unregisterCommands(this);
        getProxy().getPluginManager().unregisterListeners(this);
        links = List.of();
    }

    private final class ReloadCommand extends Command {
        private ReloadCommand() {
            super("whilocommands", "whilocommands.reload", "wc");
            updatePermissionMessage();
        }

        private void updatePermissionMessage() {
            setPermissionMessage(LinkCommand.color(messages.get("no-permission")).toLegacyText());
        }

        @Override
        public void execute(CommandSender sender, String[] arguments) {
            if (arguments.length != 1 || !arguments[0].equalsIgnoreCase("reload")) {
                sender.sendMessage(LinkCommand.color(messages.get("reload-usage")));
                return;
            }
            boolean applied = reload();
            sender.sendMessage(LinkCommand.color(messages.get(applied ? "reload-success" : "reload-failed")));
        }
    }
}
