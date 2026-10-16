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
- Store fulfilment: cosmetic rarities, tokens, Founder cosmetics, and a duplicate-safe purchase ledger with refunds
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

## Store (Tebex) fulfilment

Every cosmetic has a rarity (Common, Rare, Epic, Legendary). `civ.cosmetic.rarity.<rarity>` unlocks all
cosmetics of that rarity; the store ranks grant these through LuckPerms groups. Founder cosmetics are separate
and need `civ.cosmetic.founder`.

Store deliveries run from the console only (no player permission can run them), take a player UUID (dashed or
undashed) and a Tebex transaction id, and work for offline players:

- `/cosadmin token <uuid> <rarity> <amount> <transaction> [package]`
- `/addlorecredit <uuid> <amount> <transaction> [package]`
- `/cosadmin founder <uuid> <transaction> [package]` (records the purchase; the badge itself is a LuckPerms group)
- `/cosadmin refund <transaction>`

Each delivery is stored in `civcosmetics_purchases`, keyed by (transaction, package), so a repeated command is
ignored. A refund removes unspent tokens, cosmetics redeemed with that purchase's tokens, and its lore credits
(credits already spent are noted in the ledger), and marks the row refunded. If the database is unreachable,
deliveries are queued in `plugins/CivCosmetics/pending-store-ops.txt` and retried.

Players redeem tokens in `/cosmetics` by clicking a locked cosmetic of the token's rarity.

Grants, lore credits and tokens are written as small atomic changes, never as a whole-row snapshot, so a server
holding an old copy of a player can't erase a purchase made on another server. Each server checks every
`entitlement-refresh-seconds` (default 15) whether an online player's entitlements changed elsewhere.

Run the storage tests against a disposable database with
`CIVCOSMETICS_TEST_DB_HOST=127.0.0.1:3306 ./gradlew test` (optional `CIVCOSMETICS_TEST_DB_NAME`, `_USER`, `_PASSWORD`).

## Admin Commands

- `/cosadmin help`
- `/cosadmin ledger <player|uuid>` shows recent store purchases.
- `/cosadmin grant <player> <category> [id|all]`
- `/cosadmin revoke <player> <category> [id|all]`
- `/cosadmin clear <player> <category>`
- `/cosadmin preset <player> <preset> [replace]`
- `/cosadmin grantall <player> [loreCredits]`
- `/cosadmin patreon <player>`
- `/cosadmingui`
- `/titleadmin set|clear <player> [prefix]`
- `/addlorecredit <player> [amount]`

Admin commands accept a player name or UUID and work for offline players. They need `civ.cosmetic.admin`
(default: op), which also unlocks every cosmetic.

## License

MIT. See [LICENSE](LICENSE).
