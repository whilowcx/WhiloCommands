# WhiloCommands

WhiloCommands adds configurable chat commands to Velocity and BungeeCord. Each command can display colored text, open a URL, show hover text, require a permission and apply a per-player cooldown shared by its aliases.

Download the JAR for your proxy from [WhiloCommands 1.0](https://github.com/whilowcx/WhiloCommands/releases/tag/1.0):

- [WhiloCommands-Velocity-1.0.jar](https://github.com/whilowcx/WhiloCommands/releases/download/1.0/WhiloCommands-Velocity-1.0.jar)
- [WhiloCommands-Bungee-1.0.jar](https://github.com/whilowcx/WhiloCommands/releases/download/1.0/WhiloCommands-Bungee-1.0.jar)

Place only the matching JAR in the proxy's `plugins/` directory and start the proxy. It creates `config.yml` and `lang.yml` in `plugins/whilocommands/` on Velocity or `plugins/WhiloCommands/` on BungeeCord.

The configuration starts with `commands: {}`. Add your commands, then run `/whilocommands reload` or `/wc reload` with the `whilocommands.reload` permission. The console can also reload the configuration.

```yaml
commands:
  vote:
    aliases:
      - votes
    permission: ''
    message: '&#55ff55Click here to vote'
    hover: '&7Open the voting page'
    click: 'https://example.com/vote'
    cooldown: 5
```

Replace the example URL with your voting page. Both `/vote` and `/votes` display the message and share the same cooldown.

- `permission`: empty or omitted allows everyone.
- `message`: chat text. An empty message returns the configured `not-configured` response.
- `hover`: optional hover text.
- `click`: optional absolute HTTP or HTTPS URL.
- `cooldown`: whole seconds; zero or omitted disables it. Cooldowns are independent for each command and player.

Text supports legacy `&` colors and `&#RRGGBB` hex colors. Hex colors require a compatible client. On BungeeCord, an explicit `click` URL also overrides URLs detected inside the displayed text.

Command names are case-insensitive and cannot contain whitespace, slashes or colons. Names and aliases must be unique; `whilocommands` and `wc` are reserved. Invalid configuration, duplicate keys and conflicts with other registered commands reject the reload and leave the active configuration unchanged. If configuration fails on startup, the reload command remains available.

Successful reloads reset cooldowns. Disconnecting also clears a player's cooldowns; they do not persist across reconnections or proxy restarts.

`lang.yml` controls permission, cooldown, player-only, configuration and reload responses. Missing keys use the bundled messages. The cooldown response supports `%time%`, rounded up to whole seconds. Configuration files are never overwritten automatically.

BungeeCord uses its native permission dispatcher. Velocity checks permissions during execution so denials stay on the proxy and display the configured message; restricted commands may still appear in completion.

Build both JARs from the project root with JDK 21 or newer and Maven:

```sh
mvn clean verify
```

The installable files are `velocity/target/WhiloCommands-Velocity-1.0.jar` and `bungee/target/WhiloCommands-Bungee-1.0.jar`.
