# Daedalus dev log

Client-side Fabric mod for Minecraft 26.2. Players build their own macros from a fixed list of actions, with a pathfinder and mining built in. No player-supplied code ever runs.

## Current state (2026-10-21)

| Area | Status |
|---|---|
| Build against MC 26.2 | Works. `./gradlew build` produces `build/libs/daedalus-client-0.1.0.jar` |
| Pathfinding and motion unit tests | 182 passing (PresetsTest excluded: it calls a method that does not exist yet) (`PathSearchTest`, `GoalAndCostTest`, `MovementTest`, `PathTrackerTest`, `JumpPlannerTest`, `StartCellAndNavigatorTest`, `GridSnapshotTest`) |
| Pathfinder, Baritone-style rewrite (2026-10-22) | Search, moves, tracker, jump planner, segments and walker written and unit tested where pure. Walker **not yet run in game** |
| Damage observer | Written, compiles, **not yet run in game** |
| Macros, commands, key slots | Written, compiles, **not yet run in game** |
| GOTO, MINE, crop harvest/replant | Written, compiles, **not yet run in game** |
| Remote definitions sync | Written, file is live on GitHub, **not yet run in game** |
| Menu UI | Reworked 2026-10-22: categories Combat, Mining, Farming, Macros (macro editor), Miscellaneous, Visuals + Settings. Own menu scale, pixel icons, real text fields. Client loads, **not yet clicked through in game** |
| Client modules | 2026-10-22: Hit indicator, Damage numbers, Target HUD, ArrayList, Block ESP, Mob ESP, Fullbright, Freecam, Freelook, Camera, Custom inventory written (module/combat, module/visuals, mixin/). Loads into a world with all mixins applied; **not yet checked one by one** |
| Server-side mod and claims check | Not started, planned for later |

## Versions

Checked 2026-10-21.

| Item | Version | Source |
|---|---|---|
| Minecraft | 26.2 (Mojang names, unobfuscated) | spec |
| Java | 25 | spec |
| Fabric Loader | 0.19.5 (0.20.1 exists, 26.2 support unconfirmed) | maven.fabricmc.net |
| Fabric API | 0.161.0+26.2 | Modrinth API |
| Fabric Loom | 1.19.2 | maven.fabricmc.net |
| Gradle | 9.8.1 | wrapper |
| JUnit | 5.11.4 | build.gradle.kts |

`JAVA_HOME` on the dev machine points at `C:\path\to\java21`, which does not exist. Set it to `C:\Program Files\Java\jdk-25.0.2` before running Gradle.

## How it fits together

- `automation/` holds the macro system.
  - `Macro` is a name, a list of `Step`s, an optional interval, and an optional key slot (1-10).
  - `ActionType` is the fixed action list: `WAIT`, `HOLD`, `SWAP_SLOT`, `CHAT`, `HARVEST_CROPS`, `REPLANT_CROPS`, `GOTO`, `MINE`.
  - `DefinitionValidator` is the only way to build a `Step`. It checks every field against the `Allowlist` and `Limits`.
  - `StepExecutor` turns a step into a `Task`. `MacroRunner` runs one task at a time. `MacroManager` owns the list, interval countdowns and running macros.
  - `MacroStore` saves to `config/daedalus/macros.json` through a temp file, and re-validates every entry on load.
  - `MacroCommands` registers `/macro` through Fabric's client command API.
- `pathfinding/` has no Minecraft imports, so it is unit tested without the game. See the Navigation engine section.
- `automation/NavigationTask` plans each segment on a worker over a `GridSnapshot`, and `PathWalker` walks it through `PathingInput`.
- `sync/DefinitionSync` fetches `definitions/definitions.json` from this repo every 10 minutes and swaps the allowlist in. Remote data can only tighten the limits compiled into the mod. It never downloads or runs code.

## Navigation engine (Baritone-style rewrite, 2026-10-22)

Rebuilt from scratch following `docs/BARITONE_STUDY.md`. Ideas are Baritone's; the code is ours. The guiding rule: pathing decides how to get there, and something above it decides whether to keep going.

| Layer | Our code |
|---|---|
| Costs | `Costs`, in ticks from vanilla speeds: walk 4.317 b/s, sprint 5.612, wade 2.2, swim 2.86, ladder up 2.35, down 3.0. Falls use vanilla gravity (1 block 5.6 ticks, 3 blocks 9.5). Jumps add a 2 tick penalty, cells beside lava 10 |
| Rules | `PathSettings`, frozen per search: sprint, diagonals, parkour, long jumps, water, ladders, max fall 3 onto ground and 20 into water, node cap, time limits |
| Moves | `Moves`: walk (8 directions, diagonals need both corners open and no lava under them), step up, dynamic falls (scan down to ground, water or a ladder to grab), ladders, swimming with a breath limit, gap jumps |
| Search | `PathSearch`: A* stopping on goal, node cap, or time (500 ms once a usable partial route exists, 2 s regardless). Returns a partial route of 5+ blocks when the goal is out of reach. Ignores improvements under 0.01 ticks. Favours cells of the previous route at half cost |
| Goals | `GoalBlock`, `GoalNear`, `GoalYLevel`, `GoalComposite`, `GoalGetToBlock`. Heuristics are lower bounds in ticks, checked against every generated move in `GoalAndCostTest` |
| Start | `StartCell`: on the edge of a block, start from the block holding you up; mid-jump, start from the ground below |
| Tracking | `PathTracker`: valid cells per move (corners of diagonals, fall columns, jump arcs). Re-syncs backwards and forwards. Off the route is distance to the nearest cell of the whole route: over 2 blocks tolerated 200 ticks, over 3 lost. Each move times out at its expected ticks plus 100. Safe to cancel only when supported and not committed to a jump |
| Jumps | `JumpPlanner`: simulates vanilla movement along the jump from the real position and speed, and presses jump at the last tick that still lands (checked at 90/100/110% speed) |
| Walking | `PathWalker` with `PathingInput`: replaces the player's input object while walking (no key bindings touched), turns auto-jump off, re-checks the current move every tick and the next 5 once per move against the live world, sprints only on straight stretches |
| Segments | `NavigationTask`: plans each segment over a 65x33x65 copy around the player, walks it, plans the next. On a problem it waits until safe, excludes the failed move's cell, and replans favouring the old route. 4 failures in a row ends it; a finished segment resets the count |
| World | `WorldView`: AIR, SOLID, BLOCKED (fences, walls, bottom slabs), WATER (still only), CLIMBABLE, DANGER (lava, fire, magma, cactus, berry bush, cobweb, powder snow, campfires, wither rose, bubble columns, portals, flowing water), UNLOADED |
| Control ownership | Only one macro that uses the controls may run at a time. `WAIT` and `CHAT` never conflict |
| Behavior layer | `DamageObserver` records each health loss. While a controlling macro runs, damage stops it with a message |

Gap jumps follow Baritone: a plain jump for gaps of 1 and 2 blocks, a sprint-jump only for 3 (long jumps) and for landing a block higher. Never offered when the first cell has a floor. The two cells past the landing must be safe to stumble into, not solid.

Beyond Baritone (2026-10-22), all behind long jumps and backed by the `JumpPlanner` model in `JumpPlannerTest`:

| Jump | Rule |
|---|---|
| Level 4-block gap (`RUN_JUMP`) | Needs one block of run-up: from a standstill on the takeoff the body is too slow by the edge. The move starts on the run-up block and lands 6 blocks along. Costs an extra 10 ticks so it is only chosen when it saves real time |
| Jump down 1 or 2 blocks | Across gaps of 1 to 4, no run-up. The column just before the landing must be open down to the landing's height |
| Jump up 1 block | Still gaps of 1 and 2 only. The model says a 3-block gap up is possible, but that is where it is least trustworthy |
| Level 5-block gap | Not possible without potions or ice, not offered |
| Neo (`NEO`) | A level landing across a gap of 1 to 3 with something standing in the straight line: the body curves out and back in. Only where one side of the obstacle is open air over a gap (with a floor beside it, walking round wins), and never around lava or fire. Sprint only. +10 tick penalty |
| Side jump (`SIDE_JUMP`) | A landing 2 to 4 ahead and one to the side, level, one lower, or one higher for 2 or 3 ahead. Only when none of the three cells next to the takeoff in that direction can be walked to |

Smooth, human-paced movement (2026-10-23). Ground moves (walk, diagonal, step up, drop) give the eyes, feet and route separate jobs. No randomness anywhere: it is all easing and look-ahead.

| Part | Behaviour |
|---|---|
| `LookControl` | Eases the view toward a target. Wanted turn speed is 20% of the remaining angle, capped at 16 degrees per tick, and the real speed moves toward it by at most 3.2 per tick to speed up and 5 to slow down: a gentle start, fastest in the middle, soft landing, no overshoot. Always turns the short way. The state is shared across a task's segments so a new segment carries on turning |
| Eyes | Look at the farthest of the next 3 cells within 4.5 blocks of walking, so the view turns into a corner before the body reaches it. Held within 45 degrees of the feet's heading. Within 2 blocks of a jump's takeoff the view is already along the jump. Pitch aims at chest height of what is ahead, so a drop looks down at it |
| Feet | Head for the next cell, cut up to 0.35 blocks toward the one after it at an open corner (the inside corner cell must be air at feet and head, so no clipping). Move by analog stick values, so the body can go at an angle to the view |
| `Gait` | Within 30 degrees of the view: full speed (sprint within 20). 30 to 100 degrees: slows down to 35%. 100 or more: stands until the view comes round |
| Speed | Eases off over the last 3 blocks of a walk that ends at the goal (not at the end of a segment), to 80% for a drop, to 70% near a sharp turn (over 60 degrees). Changes by at most 6% a tick |
| Unchanged | Jumps, neos, swimming and ladders still set an exact heading. Mining still snaps to the block |

Jitter fix (2026-10-23). The first version of the above still jittered. Causes and fixes: eye and feet targets jumped from cell to cell (now `Carrot`, a point a set distance along the route from the player's own position, so it slides: feet 1.1 blocks ahead, eyes 3.5); pitch followed the body's own height and bobbed on every hop (now it comes from the route's height ahead); sprint flipped on and off at one angle and pulsed the field of view (now it needs the view within 20 degrees to start, carries on to 35, and every change is held 12 ticks); swimming, ladders and jumps snapped the view (now they ease at a quick pace, `LookControl.Pace.QUICK`).

Speed-aware planning (2026-10-23). The planner reads the real movement speed attribute (`PlayerSpeed`, sprint bonus removed) when each segment is planned, and every cost that depends on horizontal speed follows it (`SpeedProfile`): walk, sprint, hop, walking off an edge. Costs that do not follow speed stay fixed: falling, ladders, swimming, time in the air for a jump. So a fast player stops paying for detours and starts paying for jumps and climbs. The log line now says the speed it planned for.

| Speed | What it does |
|---|---|
| Around vanilla (0.6x to 1.25x) | Sprint-jump chains ("hopping") beat sprinting, about 6.9 against 5.6 blocks a second at 1x, found by simulating vanilla movement. Hopping is costed on any level cell with two clear blocks above, and the walker hops only with 4 straight, level, open cells ahead, already sprinting and lined up, and not within 6 blocks of the goal |
| Above about 1.25x | Hopping loses to sprinting (the ground gets faster, the air does not), so it is dropped and the body just sprints |
| 0.85x to 1.15x | All jumps, including the sprint-jumps, run-jumps, neos and side jumps |
| Over 1.15x | Only plain jumps (gaps of 1 and 2, drops). The walker runs up at the input that gives vanilla pace (`jumpThrottle`, 1 / speed), so the jump model still holds. No jumping up a block, no long jumps, neos or side jumps, since those need the sprint boost |
| Under 0.85x | No jumps |

Jump costs are at least the time in the air (`Costs.jumpAirTicks`), which is what makes jumping poor value at high speed. The heuristic floor follows the speed too (`Costs.minFlat`, set per search), or it would overestimate for a fast player. For a high speed the walker also brakes ahead of any spot it has to stop at (a jump takeoff, a drop edge, an unsafe corner, the end of a segment) using how far the body coasts, and may slow down faster than it speeds up.

Not done: jump physics at high speed (a 10-block sprint-jump at 500% is possible and not planned), and water, ladders and falling are not scaled with the speed stat.

Jumps are not slowed down on approach: the run-up speed is what the jump model assumes, and the view is lined up early instead.

Neos and side jumps are planned and steered by `JumpSim`, a 2D simulation of vanilla movement with the body's 0.6 x 1.8 box against the world. It steers the way the walker does (face the next plan point not yet passed) and presses jump at the last tick that lands at 90/100/110% speed. The search runs it from a standstill to decide if a jump is possible; the walker runs it every tick from the real position and speed. The three neo steering lines in `JumpSim.NEO_LINES` came from sweeping the simulation over walls 1 and 2 long at every place in gaps of 1 to 3; neos with the obstacle right against the takeoff over a 3-gap, or over a 4-gap at all, never land. These are the least certain jumps in the mod: the model has not been checked against the game.

Swimming (wade, swim, dive, surface, fall into water, breath limit) and ladders (climb, descend, step onto a ladder top, catch a ladder while falling) were already in the move set from the rewrite, with tests.

2026-10-22 bug: `PathTracker.ahead(0)` returned the current target instead of the one after it, so the walker's look-ahead asked whether a cell could move to itself and rejected every route before its first step. Fixed, with a test.

Removed in the rewrite: path straightening (diagonals make it unnecessary, and it caused the bricking), route repair (replaced by re-sync plus favouring the old route, as Baritone does), `Course`, `FollowPathTask`.

Not built yet, in the order we expect to add them after the in-game smoke test:

1. Task checkpoints and pause/resume, so a task can be suspended and restarted from its objective instead of replaying old inputs.
2. A behavior supervisor with modes (travel, combat, retreat, recover) and a single control arbiter.
3. Limited melee combat for one ordinary target: safe pause, target validation, approach, attack, retreat threshold, restore the task. Retaliation stays off until damage observations look trustworthy in the log.
4. Eating and inventory actions, ranged threats, unstuck behavior beyond replanning.
5. Planning the next segment while the current one is still being walked, so there is no pause between segments.
6. Doors and gates, breaking or placing blocks while pathing, mining and farming as goal-setting processes on the shared walker, picking up drops.

Design notes worth keeping:

- Do not treat health loss as proof of a mob attack. Fall, fire and drowning damage have no attacker.
- A big penalty is not a substitute for "never do this". Hard rules are `DANGER` cells and the 3-block drop limit, which is also the largest drop with no fall damage.
- Replan on events (stuck, pushed off course, no path), not every tick.
- Searches run over an immutable copy of the terrain and never touch the live world from the worker.

## Pathfinder audit, gold route ESP and developer log (2026-10-23)

Bugs found and fixed:

| Bug | Effect | Fix |
|---|---|---|
| The walker marked the next 5 moves as checked even when it skipped the check because the body was in the air | After a hop or a jump, moves ahead were never re-checked against the live world | Only marked once actually checked on the ground |
| Walking off an edge kept full walking speed into the fall | Air drag only takes 9% a tick, so the body landed about a block past the target cell, possibly off another edge. `FallPredictorTest` shows walking pace overshoots 1 to 3 block drops | Take the edge at 0.1 blocks/tick when the route does not carry straight on, and steer in the air against the predicted landing error (`FallPredictor`) |
| Climbing out of water only jumped when on the ground | Stuck at the bank | Jump while in water too: it swims up, and vanilla's climb-out boost does the rest |
| Cells of failed moves stayed excluded for the rest of the walk | Could cut off the only way, or the goal: "no walkable path" | Never exclude a goal cell, forget exclusions after a finished segment, and on an empty plan with exclusions clear them and retry |
| The walk stopped at every segment end (every ~30 blocks) while the next one was planned | Stop-start walking | Plan the next segment from the segment's end while the last 6 moves are walked |
| Heading followed a carrot only centimetres away when arriving | View shook at the end of a walk | Heading held once the carrot is within 0.35 blocks |
| Swimming aimed at each cell's centre with on/off forward | Robotic, jerky swims | Steered like walking: carrot along the route, corners rounded only where the inside cell is open water or air, analog drive |
| Nothing kept the body on a ladder's column | Drifting off a ladder lets go | Gentle push back to the centre past 0.2 blocks |
| An exception in the walker would surface as a crash of the macro tick | | Caught in the navigation task, logged with the stack trace, reported as a failed walk |

Route ESP: the new Pathfinder module (Miscellaneous, on by default) draws, in the menu's accent gold (`Theme.accent()`), the top of each floor block still ahead on the route, in order: the next block brightest, fading along the route, a gold line joining them that arcs over jumps, an outline for water and ladder cells (decided from the world, not the move type), and a stronger frame on the last block. It reads the walker's own route and progress (`PathDisplay`), so it only ever shows the active route, moves on with the player, and is cleared on a replan, arrival, failure or cancel. Settings: show route, blocks ahead (40), through walls, route line.

Developer log: `PathLog` writes `logs/daedalus-pathfinder.log` (the previous session's is kept as `daedalus-pathfinder.old.log`), one line per event with a timestamp and level. INFO and up also go to the game log with a `[Pathfinder]` prefix.

| Level | Events |
|---|---|
| INFO | Walk started, route planned (speed, waypoints, ticks, cells searched, ms), speed changes with the resulting costs, arrival, retries with exclusions cleared |
| WARN | Replans with the reason (blocked, off route, timed out) and the walker's report, no route found (cells searched, exclusions), failed walks |
| ERROR | Exceptions in the search or the walker, with stack traces |
| DEBUG (Debug log setting) | Full route, each move as it starts, body state changes (ground, air, water, ladder) with position and speed, hop and braking start and stop with the numbers, rejected moves with what is in the cells (`Moves.explain`), each plan request, segment hand-overs |

Nothing logs per tick: every line is a change.

## Commands

```
/macro new <name> [seconds]     seconds 0 or omitted = manual only
/macro step <name> <line>       append a step, for example: HOLD FORWARD 40
/macro show | list
/macro run | stop | pause | resume | clear | delete <name>
/macro bind <name> <slot>       slot 1-10, 0 unbinds. Set the key in Controls
/macro sync                     refresh definitions now
```

Step lines: `WAIT <ticks>`, `HOLD <key> <ticks>`, `SWAP_SLOT <1-9>`, `CHAT <text>`, `HARVEST_CROPS <crop> <radius>`, `REPLANT_CROPS <crop> <radius>`, `GOTO <x> <y> <z>`, `MINE <block> <radius>`.

## Limits compiled into the mod

10 macros, 32 steps each, radius 6 for crops, mine radius 4 and 16 blocks per step, wait up to 1200 ticks, hold up to 200 ticks, interval 1 to 60 seconds, GOTO up to 2000 blocks away in segments, 200,000 search nodes and 0.5 to 2 s per segment, 200 ticks to plan, 12,000 ticks for the whole GOTO, each move timed out at its expected ticks plus 100, 4 failed replans in a row, 600 ticks per mine step, 8 task starts per client tick across all macros.

## Decisions and why

- **Client mod, not server mod.** The feature list (pathfinding, mining, ESP, camera, HUD) all needs the client. The server mod comes later, and the claims check belongs there.
- **Fixed action list instead of scripting.** Players combine allowlisted steps. This is the whole security model.
- **Remote file can only tighten rules.** Every number is clamped to the code limits, unknown names are dropped, and an empty allowlist is rejected so a bad file keeps the old rules.
- **Pathfinder kept free of Minecraft types.** Lets the hard-to-see bugs (drops, gaps, ceilings, lava, caps) be tested without launching the game.
- **Search runs on a worker over a copied grid.** The worker never reads the live world.
- **Reference repos were not copied.** Most are GPL-3.0, and most of their pages only had landing text. The design came from A* basics plus the features their READMEs describe.

## What went wrong and what we learned

- First Loom compile had 21 errors from four wrong 26.2 names. Fixed by reading the real jars with `javap`:
  - `ClientCommandManager` is now `ClientCommands`
  - `KeyBindingHelper.registerKeyBinding` is now `KeyMappingHelper.registerKeyMapping` in package `...client.keymapping.v1`
  - `client.screen` is now `client.gui.screen()`
  - `GuiGraphics` is now `GuiGraphicsExtractor`; screens draw in `extractRenderState` and `extractBackground`, text is `g.text(font, str, x, y, color, shadow)`
  - `player.displayClientMessage(Component, boolean)` is now `player.sendSystemMessage(Component)`
- **Pathfinder bricked (first in-game test, 2026-10-21).** `run/logs/latest.log` showed every GOTO failing in under a second: "pushed off course, replanning (1/3)", a new 2-waypoint route, then off course again, three times in the same second, then "ran out of retries". Root cause: `FollowPathTask` called the player off course when they were more than 4.5 blocks from the *next waypoint*. That was safe while waypoints were at most 3 apart, but path straightening collapses open ground to `[start, goal]`, so any walk longer than 4.5 blocks failed on its first tick. Fixed by measuring against the segment (`Course`), with a regression test built from the log. Lesson: when I added straightening I broke an assumption another class relied on, and nothing tested the two together. `CourseTest` now runs the real search and straightening and checks the walker's rule against the result.
- **Parkour rules were guesses.** The walker sprinted on every jump, and the planner assumed run-up and landing room it never checked. A physics simulation (`JumpPhysicsTest`, vanilla constants) showed a sprint-jump flies about 3.6 blocks, so it overshoots a 1-wide landing block, while a plain jump over a 1-block gap lands short enough. A plain jump only just clears a 2-block gap. So: 1-block gap is a plain jump, a 2-block gap is a sprint-jump and needs a second landing cell, and landing counts as done once the player stands on the landing block instead of passing its exact centre.
- **Baritone-style rewrite (2026-10-22).** The straightening plus repair design kept producing bugs where one class broke another's assumption. Rebuilt the engine around Baritone's split: rules and costs in one place, a tracker that only reads positions, a walker that only presses keys, a controller that only decides when to replan. The tests caught four of my own wrong numbers on the first run (fall times were a tick short, an off-by-one in a timeout test, and two tests whose setup could not trigger what they meant to test), all fixed in the tests, not the engine.
- The jar was once built before the sync URL change, so it carried a stale URL. Rebuild after any change.
- Compiling proves a name exists, not that it behaves as assumed.

## Not verified in game yet

`CropBlock.isMaxAge` for crop maturity, right-click harvest and replant, hotbar slot switching through `ServerboundSetCarriedItemPacket`, `keyUp.setDown` and `keyJump.setDown` surviving real keyboard input, the yaw and pitch formulas, `startDestroyBlock` and `continueDestroyBlock` actually breaking blocks, the line-of-sight ray, the key slot controls category, and the definitions sync against the live URL.

## Known gaps

- No chat rate limit. An interval macro with a `CHAT` step can spam.
- A failed move excludes its whole target cell from later plans, which is coarse. The cell might only have been awkward, not impassable.
- Copying the terrain for each segment happens on the client thread (about 140,000 cells), so expect a small hitch at each segment start. Baritone avoids this with a thread-safe copy of the chunk provider.
- There is a short pause between segments while the next one is planned.
- Damage observation relies on `getLastDamageSource()`, which may be stale or empty on the client. Treat `UNKNOWN` as unknown.
- Damage stops a macro even if the damage was unrelated to it (hunger, for example).
- The jump planner's physics is a model of vanilla. It is tested against itself and against the planner's rules, not against the real game. Potion effects, slippery or slow blocks and server corrections are not modelled.
- Swimming has no real breath tracking in game. The 20-step limit is a stand-in. Currents and ice are not modelled. Baritone keeps the head at the waterline with look pitch; ours holds jump.
- Doors, gates and trapdoors are treated as obstacles; the walker does not open them.
- Each segment only sees 32 blocks to each side and 16 up or down, so a detour bigger than that is found one segment at a time, or not at all.
- Mining reach uses a 4.5-block distance check, not the server's exact rules.
- Client-side checks do not stop a modified client. A server with anti-cheat may flag fast walking or mining.
- Menu has no "smooth font" option: vanilla only ships the pixel font.

## Next

1. In-game smoke test (see the Smoke test section below).
2. Fix whatever the smoke test breaks. Baritone study (`docs/BARITONE_STUDY.md`, 2026-10-22) lists the executor and controller changes that matter most, in section 7. Its suggested order is section 8.
3. Behavior layer and combat, following the staged plan in the Navigation engine section.
4. Chat rate limit.
5. Click through the menu (see DAEDALUS_UI_SPEC.md "Done when"), then the other client modules.
6. Server-side mod with the claims check.

## Smoke test

Run it in a singleplayer creative flat world first (`gradlew runClient`, or the jar in your mods folder). Do the steps in order and stop at the first one that fails. For each failure, note the step, what you saw, and the red or `[Daedalus]` chat message, then check `logs/latest.log`.

1. **Loads.** Game starts, Controls has a "Daedalus Macros" category with Macro 1 to 10 and Full Stop under Daedalus. Log shows "Definitions updated".
2. **Commands.** `/macro new t1`, `/macro list`, `/macro show t1`. Try `/macro step t1 NOPE` and expect an error, not a crash.
3. **Timed steps.** `/macro step t1 HOLD FORWARD 40`, `/macro run t1`. You should walk forward about 2 seconds and the key should release. Then check the `/macro stop` command while a long hold is running.
4. **Walk (flat).** Build a flat strip. Note coordinates (F3). `/macro new w`, `/macro step w GOTO <x> <y> <z>` 5 to 10 blocks away, `/macro run w`. Watch for turning, walking, stopping at the target.
5. **Step up, then drop.** Add a 1-block step, test GOTO over it. Then a 1 to 3 block drop. Then a wall with no way over: expect "no walkable path" in chat, no freeze.
6. **Stuck, replan and timeout.** Put a glass pane in the path. After about 2 seconds the walk should get stuck and replan (check the log for "replanning (1/3)"), and after 3 replans fail with "ran out of retries". The whole GOTO is capped at 60 seconds. Also shove yourself off course (knockback or walking sideways mid-walk) and expect it to replan, not fail.
7. **Menu cancel.** Start a long walk and open the inventory. The macro should stop and the forward key should release.
8. **Key slot.** `/macro bind w 1`, set Macro 1 to a key in Controls, press it.
9. **Mine.** Give yourself a pickaxe, hold it, place stone and an ore within 3 blocks. `/macro new m`, `/macro step m MINE STONE 3`, `/macro run m`. Then put a wall between you and an ore and check it is not mined through. Try `MINE DIAMOND 4` on a deepslate diamond ore.
10. **Chain.** `GOTO`, then `MINE`, then `CHAT done`, in one macro.
11. **Farm.** A small wheat patch: `HARVEST_CROPS WHEAT 4`, `REPLANT_CROPS WHEAT 4` with seeds in the hotbar.
12. **Persistence.** Quit and rejoin, `/macro list` should show the macros.
13. **Full Stop.** Sprint, press X, you should stop dead.
14. **Damage.** Run a long walk and let a zombie hit you (creative off, peaceful off), or fall 5 blocks. The macro should stop with "stopped: took damage (...)" and the log should show a `Damage ...` line with the attacker and confidence. Note whether the attacker is filled in for the zombie and empty for the fall.
15. **Swim.** Dig a small lake. `GOTO` to the far shore. Expect to wade in, swim at the surface, and climb out. Then `GOTO` a spot at the lake bed and a spot back on land.
16. **Ladder.** Build a 5-high ladder to a platform. `GOTO` the platform and back down. Expect to jump up the ladder and slide down it, and no stuck warnings.
17. **Gap jump.** Make a 1-block gap, then a 2-block gap, in a straight line with a few blocks of run-up. `GOTO` across. Expect a sprint-jump from the edge. Try a 3-block gap and expect "no walkable path".
18. **Straightening.** On open flat ground `GOTO` a spot about 10 blocks away diagonally. Expect a straight line, not a staircase. Put a wall in the way and expect it to go round, not through.
19. **Repair.** Start a long walk and get shoved sideways a few blocks. The log should say the route was repaired, and the walk should rejoin and carry on.
20. **Control ownership.** Start one walking macro, then `/macro run` a second macro that also walks. It should refuse with "is using the controls".

## Build and test

```
set JAVA_HOME=C:\Program Files\Java\jdk-25.0.2
gradlew build          # compiles, runs the 92 tests, writes build/libs/*.jar
gradlew runClient      # dev client, for a singleplayer test world
```
