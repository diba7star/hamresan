# Hamresan — a neighbourhood social network that works without internet

[فارسی](README.md) · [Self-hosting](docs/SELF-HOSTING.md) · [Build](docs/BUILD.md) · [Your own center](docs/CENTER.md) · [Protocol & security](docs/PROTOCOL.md)

**Hamresan** («همرسان») is an open-source Android app that spreads local blog posts from phone to phone over Bluetooth / Wi-Fi (Google Nearby Connections). No internet is required. Any phone that happens to be online acts as a courier and syncs with the community's server. Any admin can host their own community server on a plain PHP host, and every user can follow several communities. In places where nobody is online, a community can still be created offline and spread purely by proximity.

## How it works

- Every phone has an Ed25519 key; every post, comment and like is signed. Tampered or forged content is rejected by phones and servers alike.
- Only writers holding an admin-signed certificate for a community can publish; reading, commenting and liking are open to everyone.
- When two phones meet, only the *difference* is exchanged, and only for communities both follow. New items are forwarded to every other connected neighbour (gossip).
- Online phones sync two-way with the community server. The server address is entered once and then hidden.
- A tiny central **directory** maps `community ID → admin public key + name` so IDs are globally unique and cannot be hijacked (registration must be signed by the admin key). It stores no server addresses.
- Photos are downscaled to ≤1024 px / ≤200 KB; posts expire after 30 days (configurable).

## UI

Three built-in, offline-switchable looks: **Stories** (default, Instagram-like), **Magazine** (editorial) and **Browser** (one tab per community). Mockups live in [`designs/`](designs/).

## Quick start (user)

1. Install the latest APK from Releases (Android 7+, Google Play Services).
2. Enter your name and tap *follow* on a community from the public list — or from *nearby communities* your neighbours follow — or create your own.
3. Keep Bluetooth and Wi-Fi on and exclude the app from battery optimisation.

## Quick start (community admin, own server)

1. Upload `server/` to your host, e.g. `public_html/hamresan/`.
2. Open `https://your-domain/hamresan/install.php`, choose an ID and a name. The installer generates the admin key, registers the ID in the directory (duplicates are refused) and writes `config.php`.
3. Save the result page (admin key, panel link, server URL) and delete `install.php`.
4. Create writer accounts in `manage.php?token=…`; writers log in from the app's profile tab.

Host requirements: PHP 7.2+ with `pdo_sqlite`, `mbstring`, `curl`. The sodium extension is optional (a pure-PHP fallback is bundled in `vendor/`). Details: [docs/SELF-HOSTING.md](docs/SELF-HOSTING.md).

## Voice

- **Voice notes:** writers can publish a voice post (up to 60 s) instead of a photo; anyone can leave a voice reply under a post (hold the mic next to the comment box). Notes are small AAC files (~15 KB per 10 s), signed and gossiped like any item.
- **Live walkie-talkie:** on the *Neighbours* tab, hold the big button to talk live to every connected neighbour (30–80 m). One hop, no internet.

## Fully offline communities

*Explore → Create community*: the admin key is generated on the phone and the ID is derived from it (e.g. `l-93df06d242`), so it is unique without any directory. Neighbours see it under *nearby communities*. Whenever the admin gets online, *Register in public list* publishes the same ID.

## Repository layout

| Path | What |
|---|---|
| `app/` | Android source (Kotlin, Jetpack Compose, Room, Nearby Connections) |
| `server/` | Per-community server (PHP): `install.php`, `api.php`, `manage.php` |
| `server-dir/` | Central directory (PHP) — one instance for everybody |
| `designs/` | UI mockups |
| `docs/` | Guides |

## Running a completely independent network (your own "center")

Upload `hamresan-center.zip` to any PHP host and open `install.php`; you get a **center ID** such as `example.ir/hamresan-dir`. Users add it in the app under *Explore → Add center*; community admins enter it in their server installer. A phone can hold several centers at once. Details: [docs/CENTER.md](docs/CENTER.md).

## Limitations

Android only (iOS forbids background mesh); needs Google Play Services; each phone syncs with one server (other communities arrive via neighbours); the hidden server URL is readable on a rooted phone.

License: MIT.
