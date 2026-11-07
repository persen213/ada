# Toaru Esper Add-on (for Re:AcademyCraft 1.5.0, Minecraft 1.20.1, Forge 47.4.10)

Version 0.2.0. Adds a new category **Senior** with the ability **Black Hole**.

> **Honest status:** the code is written but has **not been compiled against Minecraft and has not been tested in the game.**
> The only checks done: every file parses as valid Java, all Re:AcademyCraft API names resolve against the real 1.5.0 API jar,
> and a script confirms the add-on uses no particle code.

## What changed from 0.1.0
Gameplay
- The pull area **grows** from about half size to full size over 2 seconds, so the hole feels like it is waking up.
- The damage near the centre **grows as the hole gets older** (1x up to 2x by default).
- **Critical phase:** in the last 0.6 seconds the hole contracts and the pull is multiplied (2.2x by default), then it collapses.
- **Projectiles:** arrows and other projectiles (not yours) are bent toward the hole and swallowed near the centre. Fireballs only partly bend (they keep their own push).
- **Loose items** (for example drops) are pulled in and thrown back out by the collapse.
- **Walls protect:** only things with a clear line to the hole are pulled or hurt.
- The effect is re-sent every 2 seconds, so players who arrive late, or miss a message, still see the hole at the right moment.
- Ambient hum that rises in pitch as it ages.
- Every one of these has a setting (see below), so you can turn any of them off.

Visuals (still no particles, only 3D shapes)
- A bright glowing ring around the core that always faces you, and a dim halo (bent light) behind it.
- Two tilted discs that glow brighter on one side and swirl.
- Two energy jets shooting out along the disc's axis.
- Streaks of matter spiralling inward, and rings flowing inward.
- The core contracts and everything brightens before the collapse, then a double shockwave and a flash.
- **Screen effect:** dark edges when you stand close, and a flash at the collapse. Turn it off with `screen_effect = false` in `config/toaruesper-client.toml`.

## What Black Hole does (default values)
- Aim somewhere (up to 28 blocks) and use the skill. A black hole opens there.
- It stays 7 seconds. Pull radius 12 blocks (full size after 2 seconds). Creatures within 4 blocks of the centre take damage once a second.
- Then it collapses: heavy damage (24, less at the edge), survivors and items are thrown outward.
- Press the skill key again to collapse it early.
- It never breaks or changes blocks. Every hit goes through Re:AcademyCraft's safety check (protected areas, PvP, team rules).
- Bosses (Ender Dragon, Wither) are ignored by default. Creative-mode and spectator players are ignored. You are never pulled or hurt by your own hole.

## Zero particles
- Visuals are plain 3D shapes drawn directly in the world. No particles, no extra Minecraft entities.
- Only small messages go to players, and only to players within 160 blocks.
- Speed protections: at most 6 holes drawn at once, far ones use fewer segments, ones out of view are skipped, finished ones remove themselves.
- `python3 tools/check_no_particles.py` scans the code and fails if any particle code appears. GitHub runs it on every build.
- This covers our own code. Other mods, and Re:AcademyCraft's own skills, still use particles.

## Build the jar (pick ONE)

### Option A: let GitHub build it (nothing to install)
1. On github.com click **+** (top right), then **New repository**. Name it, click **Create repository**.
2. Click **uploading an existing file**. Open this unzipped folder and **drag everything inside it** (including the `.github` folder, `libs`, `gradle`, `src`, `tools`) onto the page. Click **Commit changes**.
3. Click the **Actions** tab. Open **Build the add-on**. Click **Run workflow**. Wait about 5-10 minutes for a green tick.
4. Open the finished run. At the bottom, under **Artifacts**, download **toaru-esper-addon**. Unzip it: the `.jar` inside is the add-on.
5. If the run shows a red X, open it, copy the red error text, and send it to me. I'll fix it.

### Option B: build on your own PC
1. Install **Java 17** (Temurin 17 JDK from adoptium.net).
2. Open the unzipped folder, click the address bar, type `cmd`, press Enter.
3. Type `gradlew.bat build` and press Enter. The first build downloads a lot and can take 10+ minutes.
4. The jar appears in the `build\libs` folder. If it fails, send me the red error text.

## Install
Put **two** jars in the `mods` folder of Minecraft **Forge 1.20.1 (47.4.10 or newer 47.x)**, on the server **and** on every player:
1. Re:AcademyCraft 1.5.0 (the normal mod jar from CurseForge, not the API jar)
2. `toaru-esper-addon-forge-1.20.1-0.2.0.jar`
Remove the old 0.1.0 jar first.

## Test it
1. Make a creative singleplayer world with cheats on.
2. In chat, one at a time:
   - `/aim cat toaruesper:senior`
   - `/aim learn toaruesper:black_hole`
   - `/aim fullcp`
   - If a command says you are not allowed, run `/aim cheats_on` first. If `learn` says the skill is unknown, tell me; that would be something for me to fix.
3. Press Esc, Options, Controls, find the **AcademyCraft** group. Find **Skills and presets** and **Skill slot 1** (I haven't checked the default keys). Put Black Hole in slot 1 of preset 1.
4. Press **Toggle ability** to switch your ability on, look at a group of mobs about 15 blocks away, press the **Skill slot 1** key.
5. What you should see, in order: a black ball growing with a bright ring around it, swirling purple-blue discs, jets, streaks and rings drawing inward, mobs pulled in and hurt, the hole contracting and brightening, then a bright double shockwave and survivors thrown outward. Stand near it to see the dark screen edges.
6. Also test: shoot arrows past it (they should bend in and vanish); stand behind a wall (you should not be pulled); drop some items near it; press the key again mid-way; two players watching (multiplayer); a player joining while it is open; death or a portal during the effect; and your frame rate with a few active at once. Tell me anything that looks or feels wrong.

## Settings
- `config/toaruesper-common.toml`: range, radius, start size, duration, pull strength, critical multiplier, damage radius, damage per second, damage growth, final damage, knockback, cooldown, CP cost, overload, limits on how many things are pulled, bosses on/off, projectiles on/off, items on/off, line of sight on/off. Restart to apply.
- `config/toaruesper-client.toml`: `screen_effect` (your own screen only).
- Re:AcademyCraft also creates `config/academy-addon-toaruesper.toml` where the skill can be switched off or its damage and CP use scaled.
- The CP cost (800) is a **guess**; tune it to your server's CP scale.

## Known open questions (untested)
- Whether other Re:AcademyCraft skills can be used while a Black Hole is open (it runs as a toggle session).
- Whether pulled players feel smooth or get jerked back by the game's movement correction.
- Whether `/aim learn` accepts this add-on's skill; whether the skill tree on the developer machine shows it correctly.
- Fireballs only partly bend toward the hole; they are swallowed if they get close enough.
- Shader packs (Oculus/Iris) and "Fabulous" graphics are untested.
- The category and skill have no custom icon pictures yet; only the Senior Factor item has a texture.
- All balance numbers are starting guesses.

## License note
Re:AcademyCraft and its API are GPLv3. This README is not legal advice: before you share this add-on outside your server, check the license obligations (see the SDK_README of Re:AcademyCraft).
