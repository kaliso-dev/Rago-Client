# Rago Network

The small server behind Rago Client's online features: which players use Rago (the Rago logo next
to their name, in the tab list and on name tags), their role (creator, partner, staff) and the
cosmetics they show (cape, hat, wings, shield), their friends, and how many Rago players are online.

Python 3.10 or newer, standard library only, SQLite for storage.

## How a player signs in (and why the token never reaches us)

This is the check every Minecraft server makes when you join it:

1. The client asks for a random challenge: `POST /v1/auth/start {"name": "qRago"}` →
   `{"serverId": "<40 hex digits>"}`.
2. The client tells **Mojang** that it is joining a server whose id is the SHA-1 of
   `rago-network:` followed by that challenge, written the way Minecraft writes server ids (two's
   complement hex) (`joinServer` in the game's own authlib, with the player's Minecraft token). The
   token goes to Mojang only.
3. The client asks us to check: `POST /v1/auth/finish {"name": "qRago", "serverId": "<the challenge>"}`.
   We compute the same id and ask Mojang (`hasJoined`) whether that player joined it. If Mojang says
   yes, we know the player's real uuid and name, and give the client a session token (valid 24
   hours). Only a SHA-256 hash of that token is stored.

A challenge works once, for one name, for 60 seconds. Because the id given to Mojang is a hash the
client computes, a Rago Network server (even a hostile one) cannot hand the client the id of a real
Minecraft server's login and so join that server as the player.

## API

| Call | Sign-in | What it does |
| --- | --- | --- |
| `GET /` | no | `{"service": "Rago Network", "ok": true, ...}`: what a browser shows at the server's address |
| `GET /v1/health` | no | `{"ok": true}` |
| `GET /v1/stats` | no | `{"online": 12, "players": 340}`, cached 15 s |
| `POST /v1/auth/start` | no | `{"name"}` → `{"serverId", "expiresIn"}` |
| `POST /v1/auth/finish` | no | `{"name", "serverId"}` → `{"token", "uuid", "name", "role", "expiresIn"}` |
| `POST /v1/presence` | yes | `{"visible", "version", "cosmetics"}` → `{"online", "cosmetics"}` (the loadout as accepted) |
| `POST /v1/lookup` | yes | `{"uuids": [...]}` (100 at most) → `{"players": {uuid: {"role", "cosmetics"}}}` |
| `POST /v1/emote` | yes | `{"emote": "dab", "speed": 100}` (`"none"` stops it) → `{"ok": true}`; kept in memory only, 10 minutes at most |
| `POST /v1/emotes` | yes | `{"uuids": [...]}` (100 at most) → `{"emotes": {uuid: {"emote", "speed", "elapsed"}}}`, elapsed in ms |
| `POST /v1/friends` | yes | `{}` → `{"friends": [{"uuid", "name", "role", "online"}], "received": [{"uuid", "name"}], "sent": [...]}`: friends online first then by name, requests newest first |
| `POST /v1/friends/add` | yes | `{"name"}` → `{"state": "sent" \| "friends", "name"}`; `404 not_on_rago`, `400 yourself` / `bad_name`, `409 too_many_friends` / `too_many_requests`. Asking someone who already asked you makes you friends |
| `POST /v1/friends/answer` | yes | `{"uuid", "accept": true \| false}` → `{"state": "friends" \| "declined"}`; `404 no_request` |
| `POST /v1/friends/remove` | yes | `{"uuid"}` → `{"removed": true \| false}`: ends a friendship, or takes back or turns down a request |
| `DELETE /v1/me` | yes | forgets the player, their sessions, friends and requests |

Signed-in calls send `Authorization: Bearer <token>`.

- The client sends its presence every minute. A player counts as online for 150 seconds after the
  last one, and only players online with `"visible": true` are returned by lookups ("Show that I use
  Rago" off in the client hides you).
- Cosmetics are checked against [`cosmetics.json`](cosmetics.json): unknown fields are dropped and
  invalid values put back to their default. A value listed under `exclusive` is only accepted from
  the roles and the players (uuid, 32 hex digits) given, e.g.
  `{"cape": {"creator": ["creator", "<uuid of another account>"]}}`: a player listed wears it
  without getting the role.
- A friend is online like everyone else: a presence in the last 150 seconds with `"visible": true`.
  A player who hides that they use Rago shows offline to their friends and cannot be found by name
  (`404 not_on_rago`), but can still ask others. 200 friends and 50 requests waiting at most.
- Rate limits (per address for sign-in and stats, per player otherwise): sign-in 10 a minute,
  presence 6, lookup 30, friends 30, adding a friend 20, stats 60. Over the limit: `429`.
- Nothing is logged per request, addresses are never stored, and players not seen for 180 days are
  deleted, with their friendships and requests.

## Configuration

Copy `config.example.json` to `config.json` and edit it:

| Key | Default | Meaning |
| --- | --- | --- |
| `host`, `port` | `0.0.0.0`, `8080` | where to listen (the `PORT` environment variable, set by hosts such as Render, wins over `port`) |
| `trust_proxy` | `false` | use `X-Forwarded-For` from the reverse proxy for rate limits |
| `database` | `rago-network.db` | SQLite file, or a `postgresql://` address (the `DATABASE_URL` environment variable wins) |
| `catalogue` | | path to `catalogue.json`: every cosmetic, its rarity and how it is won |
| `founder_until` | | time (Unix seconds) before which players who sign in get the founder badge |
| `cosmetics` | | path to `cosmetics.json` |
| `session_ttl` | `86400` | session length, seconds |
| `online_window` | `150` | how long a presence counts, seconds |
| `forget_after` | `15552000` | delete players not seen for this long, seconds |
| `roles` | `{}` | `{"<uuid>": "creator"}`: roles by uuid (preferred) |
| `role_names` | `{}` | `{"qRago": "creator"}`: roles by name, applied when that player signs in |
| `own_everything` | `[]` | `["<uuid>"]`: accounts that own every cosmetic without a role (qAbel, the creator's other account); the creator role owns everything too |

Roles: `user`, `partner`, `staff`, `creator`. A role can also be set from the command line once the
player has signed in once:

```
python -m rago_network --config config.json role <uuid> partner
```

## Running it

```
cd server
python -m unittest discover -s tests
python -m rago_network --config config.json
```

With Docker (a small VPS is enough), behind Caddy for HTTPS:

```
docker build -t rago-network server
docker run -d --name rago-network --restart unless-stopped -p 127.0.0.1:8080:8080 \
  -v rago-data:/data -v $PWD/server/config.json:/app/config.json:ro rago-network
```

Caddyfile:

```
network.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

Then set the server's address in the client (Settings, Rago Network).

## Free hosting (Render)

[`render.yaml`](../render.yaml) at the root of the repository describes the server for Render's
free plan, so it takes a few clicks and no command line:

1. Sign in on [render.com](https://render.com) with GitHub and allow it to read this repository.
2. **New > Blueprint**, pick this repository and the branch that has `server/`, then **Apply**.
   Render builds the Dockerfile and gives the server an address like
   `https://rago-network-xxxx.onrender.com` (HTTPS included).
3. Open `https://<that address>/v1/health`: it answers `{"ok": true}`.
4. Put the address in the client: the Rago Network mod, option "Server".

What the free plan means here:

- The server sleeps after 15 minutes without a request and takes about a minute to wake. The
  client gives up after 5 seconds and tries again 30 seconds later, so the first player after a
  quiet time gets the Rago logos and cosmetics a little later; nothing else changes. While someone
  plays, their presence every minute keeps it awake.
- 750 free instance hours a month per Render workspace, enough for one service all month; past
  them Render suspends the workspace's free services until the next month.
- The disk is not kept: when the server sleeps, restarts or is redeployed, a SQLite database starts
  empty, and Rago Points, chests and cosmetics won are lost with it. Give the server a free
  PostgreSQL database instead (next section). Without one, players sign in again on their own (the
  client does it on the 401), their loadout comes back with their next presence (every minute) and
  roles given in `role_names` come back when those players sign in. Roles given with the `role`
  command are lost: put them in `role_names` (or `roles`) in `config.example.json`, which the
  Docker image uses as its config.
- `PORT`, which Render sets, wins over `port` in the config.

### Keeping points and cosmetics (free Neon database)

1. Sign in on [neon.tech](https://neon.tech) and create a project (the free plan, a region near
   Frankfurt).
2. On the project's dashboard, **Connect**, and copy the connection string
   (`postgresql://...?sslmode=require`).
3. On Render, the `rago-network` service, **Environment**, add `DATABASE_URL` with that string,
   then **Save**: Render redeploys and the server creates its tables on its own.

The connection string is a password: keep it in Render's settings, never in the repository or a
chat. A free Neon database sleeps when unused; the server opens its connection again when it wakes.

When Rago has many players, the same Docker image runs on any small server with a disk (see
"Running it" above), and only the address in the client changes.
