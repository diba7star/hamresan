# Protocol & security notes

## Identities and signatures

- Every phone generates a random 32-byte Ed25519 seed on first launch. Its public key (base64) is the author ID.
- Every **item** (post, comment, like) is signed over the canonical string
  `item\n{type}\n{id}\n{author}\n{seq}\n{parentId}\n{title}\n{body}\n{sha256(image)}\n{createdAt}\n{expiresAt}`.
- Each author has one monotonically increasing `seq` across all their items. A peer's *have-vector* is `{authorPub: maxSeq}`; sync sends every item with a higher `seq`.
- A **writer certificate** is `cert\n{pub}\n{name}\n{exp}` signed by the community admin key. Posts are accepted only if their author holds a valid certificate of a community the receiver follows; comments and likes are accepted from anyone (one like per author per post).
- Item types: `post`, `vpost` (voice post: AAC bytes in the image slot, duration seconds in `title`), `comment`, `vcomment` (voice reply), `like` (one per author per post, empty body). Posts and voice posts need a writer certificate; replies are open.
- Live push-to-talk: a STREAM payload whose first 4 bytes are `PTT1`, then one length byte + sender name, then raw PCM 16 kHz mono 16-bit until the stream closes. Receivers play it straight to the speaker; it is not stored or relayed.
- Voice calls (relayed, ≤3 hops): JSON `{m:"call", a:"invite", id, from, fromName, to, path, ttl}` is flooded to all neighbours (dedup by id, ttl decremented); each relay remembers the hop it came from. The callee answers `{a:"accept", id, path}` back along that chain, so every relay learns both neighbours of the route; `{a:"end"}` and `{a:"busy"}` travel the same way. Audio: one STREAM per hop and direction, header `CALL` + 36-char call id + `A` (caller→callee) or `B` (callee→caller), then PCM 16 kHz mono 16-bit. Relays copy bytes to the next hop unchanged. Hellos carry `nb:[{pub,name}]` (the sender's direct neighbours) so phones can show who is reachable through whom. Peers without a shared community stay connected for calls, walkie-talkie and relaying, but exchange no posts.
- Expired posts stay as rows (images stripped) so have-vectors never develop gaps.

## Mesh (Nearby Connections, P2P_CLUSTER, service id `co.yooz.hamresan.mesh.v1`)

Messages are JSON, one per payload (BYTES < 30 KB, otherwise STREAM):

| m | fields | purpose |
|---|---|---|
| `hello` | `pub, name, cert?, cs:[{id,name,apub}], have:{}` | identity, followed communities with pinned admin keys, have-vector |
| `items` | `items:[{item:{…}, author:{pub,name,cert?,community?}}]` | missing items (batches ≤50 / ~900 KB) |
| `cert` | `cert:{pub,name,exp,sig}` | admin grants writer role to the peer |

On `hello` each side computes the intersection of followed communities; only those items are exchanged and gossiped. A community the receiver follows but has no key for is adopted from the neighbour **unverified** (trust on first use) and later checked against the directory; a verified mismatch wipes that community's content.

Endpoint names are `name#shortId` so a device ignores its own loopback discovery.

## Community server (`server/api.php`, POST JSON)

| action | body | reply |
|---|---|---|
| `login` | `username, password, pub` | `cert, community{id,name,pub}` |
| `sync` | `have` | `community, have, items[]` (client repeats until empty) |
| `push` | `items[]` | `accepted` |

GET returns `community{id,name,pub}`. The server verifies every signature and certificate exactly like the app.

## Directory (`server-dir/index.php`)

- `GET ?id=` → `{id,pub,name}`; `GET ?check=` → `{available}`; `GET ?list=1&q=` → public catalogue.
- `POST {action:"register", id, pub, name, ts, sig}` where `sig = Ed25519(pub) over "register\n{id}\n{pub}\n{name}\n{ts}"`, `|ts − now| < 15 min`. First-come; an existing ID can only be re-registered with the same key.
- Stores nothing but `id, pub, name, created`. No server addresses.

## Offline (local) communities

`id = "l-" + sha256(adminPub)[0:10]`, admin seed kept on the creator's phone. Registration later uses the same signed `register` message, so the ID never changes.

## Threat notes

- Content integrity and authorship: cryptographic.
- ID uniqueness: guaranteed by the directory for registered IDs, and by key derivation for local ones.
- A neighbour can advertise a fake key for an ID you have not verified yet; the directory check (first time online) corrects it and drops the fake content.
- The server URL is hidden in the UI only; app-private storage is readable on rooted devices.
- Nearby Connections encrypts links but does not authenticate peers; that is why every payload is signed.
