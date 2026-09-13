package dev.whilo.whilocommands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PluginConfigTest {
    @TempDir
    Path directory;

    @Test
    void createsAnEmptyConfigurationAndBundledMessages() throws IOException {
        PluginConfig config = PluginConfig.load(directory);
        assertTrue(config.commands().isEmpty());
        assertEquals(PluginConfig.defaultMessages(), config.messages());
        assertTrue(Files.exists(directory.resolve("lang.yml")));
    }

    @Test
    void readsExistingFormatWithoutOverwritingFiles() throws IOException {
        String yaml = """
            commands:
              Vote:
                aliases: [Votes]
                permission: 'whilocommands.vote'
                message: '&#12ab34Vote'
                hover: '&7Open link'
                click: 'https://example.com/vote'
                cooldown: 5
            """;
        Files.writeString(directory.resolve("config.yml"), yaml);
        Files.writeString(directory.resolve("lang.yml"), "no-permission: 'Denied'");
        PluginConfig config = PluginConfig.load(directory);
        assertEquals(List.of(new CommandDefinition("vote", List.of("votes"), "whilocommands.vote",
            "&#12ab34Vote", "&7Open link", "https://example.com/vote", 5_000_000_000L)), config.commands());
        assertEquals("Denied", config.messages().get("no-permission"));
        assertEquals(PluginConfig.defaultMessages().get("cooldown"), config.messages().get("cooldown"));
        assertEquals(yaml, Files.readString(directory.resolve("config.yml")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "- vote", "commands: [vote]", "commands: [",
        "commands:\n  vote: false",
        "commands:\n  vote: {}\n  vote: {}",
        "commands:\n  vote: {}\n  VOTE: {}",
        "commands:\n  wc: {}",
        "commands:\n  vote:\n    aliases: [whilocommands]",
        "commands:\n  vote:\n    aliases: [votes, VOTES]",
        "commands:\n  vote:\n    aliases: [shop]\n  shop: {}",
        "commands:\n  vote:\n    aliases: votes",
        "commands:\n  vote:\n    aliases: [null]",
        "commands:\n  '/vote': {}",
        "commands:\n  'two words': {}",
        "commands:\n  vote:\n    permission: false",
        "commands:\n  vote:\n    permisson: staff",
        "commands:\n  vote:\n    message: [hello]",
        "commands:\n  vote:\n    cooldown: -1",
        "commands:\n  vote:\n    cooldown: 0.5",
        "commands:\n  vote:\n    cooldown: '5'",
        "commands:\n  vote:\n    cooldown: 9223372037",
        "commands:\n  vote:\n    click: 'javascript:alert(1)'",
        "commands:\n  vote:\n    click: 'https://'",
        "commands:\n  vote:\n    click: 'https://example.com/two words'",
        "commands: !!java.util.HashMap {}"
    })
    void rejectsInvalidConfiguration(String yaml) throws IOException {
        Files.writeString(directory.resolve("config.yml"), yaml);
        assertThrows(IOException.class, () -> PluginConfig.load(directory));
        assertEquals(yaml, Files.readString(directory.resolve("config.yml")));
    }

    @Test
    void rejectsNonTextMessages() throws IOException {
        Files.writeString(directory.resolve("lang.yml"), "no-permission: false");
        assertThrows(IOException.class, () -> PluginConfig.load(directory));
    }
}
