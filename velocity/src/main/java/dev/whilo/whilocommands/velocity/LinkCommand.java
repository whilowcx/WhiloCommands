package dev.whilo.whilocommands.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import dev.whilo.whilocommands.CommandDefinition;
import dev.whilo.whilocommands.Cooldown;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;
import java.util.UUID;

final class LinkCommand implements SimpleCommand {
    private static final LegacyComponentSerializer COLORS = LegacyComponentSerializer.legacyAmpersand();

    private final CommandDefinition definition;
    private final Map<String, String> messages;
    private final Component message;
    private final Cooldown cooldown;

    LinkCommand(CommandDefinition definition, Map<String, String> messages) {
        this.definition = definition;
        this.messages = messages;
        cooldown = new Cooldown(definition.cooldownNanos());
        Component content = color(definition.message());
        if (!definition.hover().isEmpty()) {
            content = content.hoverEvent(color(definition.hover()));
        }
        if (!definition.click().isEmpty()) {
            content = content.clickEvent(ClickEvent.openUrl(definition.click()));
        }
        message = content;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(color(messages.get("players-only")));
            return;
        }
        if (!definition.permission().isEmpty() && !player.hasPermission(definition.permission())) {
            player.sendMessage(color(messages.get("no-permission")));
            return;
        }
        if (definition.message().isEmpty()) {
            player.sendMessage(color(messages.get("not-configured")));
            return;
        }
        long remaining = cooldown.acquire(player.getUniqueId());
        if (remaining > 0) {
            player.sendMessage(color(messages.get("cooldown").replace("%time%", Long.toString(remaining))));
            return;
        }
        player.sendMessage(message);
    }

    void forget(UUID player) {
        cooldown.forget(player);
    }

    static Component color(String text) {
        return COLORS.deserialize(text);
    }
}
