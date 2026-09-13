package dev.whilo.whilocommands;

import java.util.List;
import java.util.stream.Stream;

public record CommandDefinition(String name, List<String> aliases, String permission,
                                String message, String hover, String click, long cooldownNanos) {
    public CommandDefinition {
        aliases = List.copyOf(aliases);
    }

    public Stream<String> names() {
        return Stream.concat(Stream.of(name), aliases.stream());
    }
}
