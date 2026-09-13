package dev.whilo.whilocommands.bungee;

import dev.whilo.whilocommands.CommandDefinition;
import dev.whilo.whilocommands.Cooldown;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

final class LinkCommand extends Command {
    private static final Pattern HEX = Pattern.compile("&#([a-fA-F0-9]{6})");

    private final CommandDefinition definition;
    private final Map<String, String> messages;
    private final BaseComponent message;
    private final Cooldown cooldown;

    LinkCommand(CommandDefinition definition, Map<String, String> messages) {
        super(definition.name(), definition.permission(), definition.aliases().toArray(String[]::new));
        this.definition = definition;
        this.messages = messages;
        cooldown = new Cooldown(definition.cooldownNanos());
        setPermissionMessage(color(messages.get("no-permission")).toLegacyText());
        message = color(definition.message());
        if (!definition.hover().isEmpty()) {
            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(color(definition.hover()))));
        }
        if (!definition.click().isEmpty()) {
            setClick(message, new ClickEvent(ClickEvent.Action.OPEN_URL, definition.click()));
        }
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer player)) {
            sender.sendMessage(color(messages.get("players-only")));
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
        player.sendMessage(message.duplicate());
    }

    void forget(UUID player) {
        cooldown.forget(player);
    }

    static BaseComponent color(String text) {
        String hex = HEX.matcher(text).replaceAll(match -> ChatColor.of("#" + match.group(1)).toString());
        return TextComponent.fromLegacy(ChatColor.translateAlternateColorCodes('&', hex));
    }

    private static void setClick(BaseComponent component, ClickEvent click) {
        component.setClickEvent(click);
        if (component.getExtra() != null) {
            component.getExtra().forEach(child -> setClick(child, click));
        }
    }
}
