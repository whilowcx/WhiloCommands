package dev.whilo.whilocommands;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record PluginConfig(List<CommandDefinition> commands, Map<String, String> messages) {
    private static final Set<String> OPTIONS = Set.of("aliases", "permission", "message", "hover", "click", "cooldown");

    public PluginConfig {
        commands = List.copyOf(commands);
        messages = Map.copyOf(messages);
    }

    public static Map<String, String> defaultMessages() throws IOException {
        Map<String, String> messages = new HashMap<>();
        try (InputStream input = resource("lang.yml")) {
            readYaml(input, "bundled lang.yml").forEach((key, value) -> messages.put((String) key, (String) value));
        }
        return Map.copyOf(messages);
    }

    public static PluginConfig load(Path directory) throws IOException {
        Files.createDirectories(directory);
        for (String name : List.of("config.yml", "lang.yml")) {
            if (Files.notExists(directory.resolve(name))) {
                try (InputStream input = resource(name)) {
                    Files.copy(input, directory.resolve(name));
                }
            }
        }
        List<CommandDefinition> commands;
        try (InputStream input = Files.newInputStream(directory.resolve("config.yml"))) {
            commands = readCommands(readYaml(input, "config.yml"));
        }
        Map<String, String> messages = new HashMap<>(defaultMessages());
        try (InputStream input = Files.newInputStream(directory.resolve("lang.yml"))) {
            Map<?, ?> overrides = readYaml(input, "lang.yml");
            for (String key : messages.keySet()) {
                if (overrides.get(key) != null) {
                    messages.put(key, text(overrides, key, "lang.yml"));
                }
            }
        }
        return new PluginConfig(commands, messages);
    }

    private static List<CommandDefinition> readCommands(Map<?, ?> root) throws IOException {
        if (root.isEmpty()) {
            return List.of();
        }
        if (!(root.get("commands") instanceof Map<?, ?> sections)) {
            throw new IOException("config.yml: 'commands' must be a mapping");
        }
        Map<String, String> owners = new HashMap<>(Map.of("whilocommands", "reload command", "wc", "reload command"));
        List<CommandDefinition> commands = new ArrayList<>();
        for (var section : sections.entrySet()) {
            String name = commandName(section.getKey());
            String location = "commands." + name;
            if (!(section.getValue() instanceof Map<?, ?> options)) {
                throw new IOException("config.yml: " + location + " must be a mapping");
            }
            for (Object key : options.keySet()) {
                if (!OPTIONS.contains(key)) {
                    throw new IOException("config.yml: unknown option " + location + "." + key);
                }
            }
            claim(owners, name, location);
            List<String> aliases = new ArrayList<>();
            if (options.get("aliases") != null) {
                if (!(options.get("aliases") instanceof List<?> names)) {
                    throw new IOException("config.yml: " + location + ".aliases must be a list");
                }
                for (Object alias : names) {
                    String normalized = commandName(alias);
                    claim(owners, normalized, location);
                    aliases.add(normalized);
                }
            }
            String click = text(options, "click", location).trim();
            if (!click.isEmpty()) {
                try {
                    URI url = new URI(click);
                    if (!("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme()))
                            || url.getHost() == null) {
                        throw new URISyntaxException(click, "expected an absolute HTTP(S) URL");
                    }
                } catch (URISyntaxException e) {
                    throw new IOException("config.yml: " + location + ".click must be an absolute HTTP(S) URL", e);
                }
            }
            long cooldown = 0;
            Object seconds = options.get("cooldown");
            if (seconds != null) {
                if (!(seconds instanceof Integer || seconds instanceof Long)
                        || ((Number) seconds).longValue() < 0
                        || ((Number) seconds).longValue() > Long.MAX_VALUE / 1_000_000_000L) {
                    throw new IOException("config.yml: " + location + ".cooldown must be whole seconds between 0 and 9223372036");
                }
                cooldown = ((Number) seconds).longValue() * 1_000_000_000L;
            }
            commands.add(new CommandDefinition(name, aliases, text(options, "permission", location).trim(),
                text(options, "message", location), text(options, "hover", location), click, cooldown));
        }
        return commands;
    }

    private static String text(Map<?, ?> section, String key, String location) throws IOException {
        Object value = section.get(key);
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        throw new IOException(location + "." + key + " must be text");
    }

    private static String commandName(Object value) throws IOException {
        if (!(value instanceof String name) || name.isBlank()
                || name.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)
                    || Character.isISOControl(c) || c == '/' || c == ':')) {
            throw new IOException("config.yml: invalid command name: " + value);
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private static void claim(Map<String, String> owners, String name, String location) throws IOException {
        String previous = owners.putIfAbsent(name, location);
        if (previous != null) {
            throw new IOException("config.yml: /" + name + " is used by both " + previous + " and " + location);
        }
    }

    private static Map<?, ?> readYaml(InputStream input, String name) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try {
            Object document = new Yaml(new SafeConstructor(options)).load(input);
            if (document == null) {
                return Map.of();
            }
            if (document instanceof Map<?, ?> mapping) {
                return mapping;
            }
            throw new IOException(name + ": expected a YAML mapping");
        } catch (YAMLException e) {
            throw new IOException(name + ": " + e.getMessage(), e);
        }
    }

    private static InputStream resource(String name) throws IOException {
        InputStream input = PluginConfig.class.getResourceAsStream("/" + name);
        if (input == null) {
            throw new IOException("Missing bundled resource: " + name);
        }
        return input;
    }
}
