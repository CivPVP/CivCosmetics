# CivCosmetics

Standalone Paper cosmetics plugin for CivPvP.

## Features

- Prefixes and premade tags
- Kill effects
- Projectile trails
- Armor trim cosmetics
- Custom kill messages
- Long item lore through command, book, draft, and web editor workflows
- Sword linking with crafted-by and unique slayer history
- MySQL-backed player state and grants for multi-server Paper networks
- Optional integrations with CivChat2, TAB, Vault, and PlaceholderAPI

## Requirements

- Paper 1.21.11
- Java 21
- MySQL or MariaDB

The plugin is standalone. It does not depend on CivModCore.

## Build

```bash
./gradlew build
```

The plugin jar is created under `build/libs/`.

## Setup

1. Put the jar in each Paper server's `plugins` folder.
2. Start once to generate `config.yml`.
3. Set the same `mysql` settings on every server.
4. Restart.

If MySQL is enabled but unavailable, the plugin disables itself to avoid per-server data divergence.

## Main Commands

- `/cosmetics` opens the main menu.
- `/cosmetics help` lists all player and admin commands.
- `/killeffects` opens kill effects.
- `/trails` opens projectile trails.
- `/tags` opens premade tags and prefixes.
- `/settitle <text>` previews and saves a custom prefix.
- `/cleartitle` clears your custom prefix or tag.
- `/setkillmsg <message>` sets a custom kill message.
- `/clearkillmsg` clears your custom kill message.
- `/settrim` applies unlocked trims to held armor.
- `/setlore <Name|Lore line 1|Lore line 2>` previews short item lore.
- `/setlore book` reads long item lore from an offhand book.
- `/setlore draft ...` builds long lore in chat before applying.
- `/setlore editor` links to the CivPvP lore editor.
- `/clearlore` clears custom name/lore from the held item.
- `/link` links the held sword in chat.

## Admin Commands

- `/cosadmin help`
- `/cosadmin grant <player> <category> [id|all]`
- `/cosadmin revoke <player> <category> [id|all]`
- `/cosadmin clear <player> <category>`
- `/cosadmin preset <player> <preset> [replace]`
- `/cosadmin grantall <player> [loreCredits]`
- `/cosadmin patreon <player>`
- `/cosadmingui`
- `/titleadmin set|clear <player> [prefix]`
- `/addlorecredit <player> [amount]`

## License

MIT. See [LICENSE](LICENSE).
