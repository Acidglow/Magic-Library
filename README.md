# Magic Library

Magic Library is a tiered enchantment storage and amplification mod for NeoForge 1.21.11. Instead of treating enchanted books as disposable one-off items, it turns enchantments into a long-term resource you can extract, bank, upgrade, and reapply through a dedicated magical library block.

The mod is built around three library tiers:

| Tier | Role |
| --- | --- |
| Apprentice Library | Early access enchant extraction and book crafting |
| Adept Library | Larger storage, better extraction cap, better long-term throughput |
| Archmage Library | Full power tier with direct gear enchanting and enchant amplification |

## What It Does

- Extracts non-curse enchantments from enchanted books and gear into a persistent library.
- Stores enchantments as reusable point-based progress instead of one physical book per use.
- Lets you rebuild enchanted books from your stored enchant pool.
- Lets Archmage libraries apply enchantments directly onto gear, not just books.
- Adds Tome of Amplification support to raise an enchantment's maximum library level beyond vanilla.
- Preserves stored data when a library block is broken or upgraded.
- Improves tooltip and Roman numeral rendering for high-level enchantments so amplified gear reads correctly in-game.
- Generates a per-enchantment cap config for both vanilla and modded enchantments.

## Core Gameplay Loop

1. Craft an `Apprentice Library`.
2. Feed it `Redstone`, `Glowstone Dust`, or `Amethyst Shards` to charge it with `ME`.
3. Insert enchanted books or gear into the extraction slot.
4. The library strips eligible enchantments, converts them into stored enchant points, and records the highest level you have discovered.
5. Use the enchant list to prepare new enchanted books from the library's stored pool.
6. Upgrade the block with `Adept Core` and later `Archmage Core` to keep the same stored data while unlocking stronger features.
7. At Archmage tier, enchant gear directly and use the `Tome of Amplification` to push supported enchantments beyond vanilla caps.

## Tier Breakdown

| Tier | Capacity | Base upkeep | Extraction discovery cap | Special features |
| --- | ---: | ---: | ---: | --- |
| Apprentice | 1,000,000 ME | 2.0 ME/t | Level II | Book-based enchanting |
| Adept | 10,000,000 ME | 4.0 ME/t | Level IV | Better storage and extraction |
| Archmage | 200,000,000 ME | 8.0 ME/t | Unlimited | Direct gear enchanting, Tome amplification, Nether Star fuel |

Additional upkeep is `0.1 ME/t` per stored enchantment type.

## Fuel Values

| Fuel | ME restored |
| --- | ---: |
| Redstone | 10,000 |
| Glowstone Dust | 40,000 |
| Amethyst Shard | 100,000 |
| Nether Star | 100,000,000 |

`Nether Star` fuel is only accepted by the Archmage Library.

## Extraction Rules

- Only non-curse enchantments are extracted.
- Apprentice libraries can discover up to level II from extracted items.
- Adept libraries can discover up to level IV.
- Archmage libraries can discover the full extracted level.
- Extracting from normal gear damages the item by default for `25%` of max durability.
- If extraction would destroy the item, the GUI shows a warning and requires confirmation.
- Extracting from enchanted books does not apply durability damage.

## Enchanting Rules

- Stored enchantments are tracked as points plus the highest discovered level.
- Point gain and point cost use a power-of-two curve: level `N` is worth `2^N`.
- Creating or upgrading an enchanted book spends stored enchant points from the library.
- Archmage libraries can apply enchantments directly to valid gear placed in the output slot.
- Direct Archmage gear enchanting also costs player XP, with a 10% discount applied to the raw curve.
- By default, incompatible enchantments still respect vanilla compatibility rules unless the config says otherwise.

## Tome Of Amplification

The `Tome of Amplification` is the Archmage Library's endgame feature.

- Each use raises one stored enchantment's maximum library level by `+1`.
- Each amplification consumes `1 Tome of Amplification`, `100,000 ME`, and player XP based on the upgrade step.
- Default Tome XP cost is `10 levels` for the first amplification step, then `+15 levels` for each further step.
- Global amplification soft cap defaults to `255`.
- Per-enchantment caps can override that limit or disable amplification entirely.

The shipped defaults explicitly support many vanilla enchants and disable binary/special effects such as `Silk Touch`, `Infinity`, `Mending`, `Multishot`, and similar cases.

## Crafting And Upgrades

### Apprentice Library

Recipe:

```text
B E B
M T M
B E B

B = Bookshelf
E = Enchanted Book
M = Emerald
T = Enchanting Table
```

### Adept Core

Use on an `Apprentice Library` to upgrade it into an `Adept Library` without losing stored data.

```text
A L A
R B R
A L A

A = Amethyst Shard
L = Lapis Lazuli
R = Redstone
B = Blaze Powder
```

### Archmage Core

Use on an `Adept Library` to upgrade it into an `Archmage Library` without losing stored data.

```text
A D A
G E G
A D A

A = Amethyst Shard
D = Diamond
G = Glowstone Dust
E = Ender Eye
```

### Tome Of Amplification

The item exists and is fully integrated into Archmage Library gameplay, but this repository does not currently ship a crafting recipe for it in `data/magiclibrary/recipe`.

## UI And Presentation

Magic Library is not just functional storage. It also includes:

- a custom-themed GUI with a tier-specific look
- searchable enchant lists with category grouping
- discovery popups when new enchantments are learned
- confirmation dialogs for destructive extraction and amplification
- corrected tooltip scaling for amplified enchantments
- special legendary coloring for enchantments above vanilla maximum level
- unrestricted Roman numeral rendering for very high enchant levels

## Configuration

The mod exposes two main configuration layers.

### `magiclibrary-common.toml`

Controls core behavior such as:

- enchant compatibility rules
- tier capacities
- ME upkeep
- extraction durability damage
- global Tome soft cap
- Tome XP and ME costs

### `config/magiclibrary/enchant_caps.toml`

This file is generated automatically and is one of the mod's strongest features.

- Vanilla enchantments ship with curated defaults.
- Missing enchantments from other mods are appended automatically.
- Auto-generated entries include heuristic comments to make review easier.
- Each enchant can be set to a numeric cap or `"disabled"`.

This makes Magic Library much more practical in larger modpacks where custom enchantments would otherwise need hand-written compatibility support.

## Commands

For admin/testing use, the mod registers:

```text
/magiclibrary make <targets> supreme
```

After using it, the next Archmage Library opened by that player becomes a fully charged "supreme" library preloaded with all non-curse enchantments and their discovered base max levels.

## Technical Target

- Minecraft: `1.21.11`
- Loader: `NeoForge 21.11.38-beta`
- Java: `21`

## License

Magic Library is licensed under the `MIT` License. See [LICENSE](D:\MC projekter\Magic-Library\LICENSE).

## Summary

Magic Library turns enchanting into infrastructure instead of loot roulette. You build a library, feed it power, archive enchantments permanently, and grow it from a modest book workshop into an endgame enchantment lab that can manufacture legendary gear.
