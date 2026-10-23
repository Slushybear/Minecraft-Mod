# What Baritone does, and what it means for Daedalus

Studied 2026-10-22 from `cabaletta/baritone` at commit `3c8f944` (cloned shallow into a scratch folder, read only, nothing run).

**Licence and approach.** The source files carry LGPL-3.0 headers. This document records ideas, numbers and behaviour in my own words. No Baritone code is copied, and Daedalus should stay a from-scratch implementation. File names are given so you can read the original yourself.

**Scope.** I read the pathfinding core, all the movement classes, the executor, the controller, the look and input handling, and the mine, farm, follow and get-to-block processes. I skimmed the builder, explore, backfill and elytra code and did not study them. Anything marked *(inferred)* is my reading, not something the code states.

---

## 1. The shape of the whole thing

Baritone is three layers, and the separation is the most important lesson.

| Layer | Baritone classes | Job |
|---|---|---|
| Search | `AStarPathFinder`, `AbstractNodeCostSearch`, `Moves`, the `Movement*` classes, `Goal*` | Turn a start, a goal and the world into a list of movements with costs |
| Executor and controller | `PathExecutor`, `PathingBehavior`, `LookBehavior`, `InputOverrideHandler` | Walk the movements, notice when it goes wrong, plan the next piece while walking |
| Processes | `MineProcess`, `FarmProcess`, `FollowProcess`, `GetToBlockProcess`, `ExploreProcess`, `BuilderProcess`, `BackfillProcess`, `InventoryPauserProcess` | Decide what the goal is, and act when something is in reach |

A **process never walks anywhere itself.** Every tick it returns a small command: a goal plus a type (set goal and path, request pause, revalidate, cancel). A `PathingControlManager` picks which process is in control by priority, lets a *temporary* process (backfill, inventory pausing) interrupt without cancelling the one underneath, and hands the winning goal to the single pathing engine.

When a process is close enough to act (mine the ore in reach, plant the seed under it), it asks the engine to pause, does its action, and goes back to setting goals. So walking, mining and farming are all "set a goal, then act when in reach", and they all share one walker.

**Daedalus today** is a list of sequential tasks per macro. Each task owns the keys while it runs, mining and walking are separate code, and the only arbitration is "one controlling macro at a time". That is simple, but it is why mining does not walk, farming does not walk, and nothing collects drops.

---

## 2. The search

- **Costs are in ticks**, derived from real speeds: walk 4.317 blocks/s, sprint 5.612, wading 2.2, sprint-swimming 2.86, ladder up 2.35, ladder down 3.0, sneaking 1.3. Falling uses the real gravity and drag formula, and a jump is the time to rise 1.25 blocks minus the time to fall 0.25. Because everything is in ticks, mining time, placing cost and walking time all add up in one currency.
- **Moves are 8-directional.** Traverse (4 sides), diagonal (4), ascend (4), descend/fall (4), plus pillar, downward and parkour. Diagonals are why Baritone does not walk staircases.
- **The heuristic** blends straight and diagonal distance and multiplies by a tuned constant (default 3.563, about the cost of a sprinted block). Height uses jump and fall costs.
- **Time limits, not node limits.** Default 500 ms primary, 2000 ms if nothing promising has been found yet, and 4000/5000 ms when planning ahead of a path being walked. The clock is only checked every 64 nodes because reading it is slow.
- **Partial paths.** If the goal is not reachable in time, or the search hits the edge of loaded chunks, it still returns something useful. It tracks the best node under seven different cost/distance trade-offs and takes the first that goes at least 5 blocks from the start. Edge-of-chunk contact is counted, and after 50 it gives up early. The result is cut off at the loaded edge. A far goal in unloaded chunks is simplified to "get to this X and Z".
- **Minimum improvement of 0.01 ticks** before an alternative route replaces an existing one, because re-propagating a hundredth of a tick is not worth the time.
- **Backtrack favouring.** When planning the next segment, cells on the current path cost half, so the next segment can splice onto the current one as early as possible.
- **Post-processing.** After the search, each pair of nodes is turned back into a movement and its cost is recomputed. If one has become impossible since the search touched it, the path is cut at that point.
- **Node storage** is grouped into 16x8x16 cells so neighbouring lookups hit the same cache line. A path finder is single use.
- **One frozen context per search.** Every setting is copied once, so a path is never planned under two rule sets. Search caches belong to the thread that claimed them.

### Goals

`GoalBlock`, `GoalXZ`, `GoalYLevel`, `GoalTwoBlocks` (feet or head in the cell), `GoalGetToBlock` (adjacent, below or on top), `GoalNear` (within a radius), `GoalAxis`, `GoalRunAway`, `GoalInverted`, and `GoalComposite` (any of a list). Mining ore is a composite of "stand where you can reach each ore". The composite heuristic takes the nearest sub-goal. Baritone's own comments say that is not strictly admissible, and `GoalNear` ignores its radius in the heuristic. It prefers speed to strict optimality there.

**Ours:** `Goal`, `GoalBlock`, `GoalNear`, `GoalYLevel` exist and are cost-consistent. No composite, no "get to block" goal, no run-away. Our costs are unitless (10, 15, 20 and so on), not ticks. Our moves are 4-directional.

---

## 3. Movements

Each movement class does four jobs, and that is the structure worth copying:

1. **Cost.** A static function used by the search. It is also re-run during execution to see whether the world changed.
2. **What must be broken or placed** before it can run (blocks in the way, a bridge block).
3. **Valid positions.** The set of cells the player may legitimately be in while doing it. Traverse is source and destination. Descend adds the cell above the destination. Parkour is every cell along the jump, two high. Ascend includes a cell behind the start, because it sometimes backs up or sprints into it.
4. **Per-tick update and `safeToCancel`.** What to press this tick, when it succeeded, when it failed, and whether stopping now is safe. Parkour is cancellable only before its first tick. Fall only before you step off. Diagonal checks you are not standing in a corner without support.

Things the movements encode that we have not:

- **Ascend** checks for a falling block (gravel, sand) above that would suffocate you, bottom slabs, soul sand, and head clearance on the neighbouring cells so you do not bonk your head. It waits until you are within about 1.2 blocks and lined up before jumping.
- **Descend** has a safe mode: if sprinting straight through could overshoot into a hazard, it approaches at 17 percent / 83 percent along the edge and walks.
- **Fall** is dynamic: it scans down to find where you land, treats water as a landing, grabs ladders and vines on the way (resetting fall speed), and can use a water bucket for up to 20 blocks.
- **Traverse** can open doors and fence gates, break blocks in the way (and keeps walking while breaking), bridge by placing blocks, and sprint when nothing is in the way.
- **Pillar** and **downward** exist for vertical digging and building.

### Parkour specifically (your sore point)

- A jump is "distance" 2, 3 or 4 cells from takeoff to landing (1, 2 or 3 blocks of gap). Distance 4 requires sprinting ability, otherwise the maximum is 3. Soul sand caps it at 2. Ascending landings are allowed up to distance 3 with sprinting.
- It **refuses to plan a jump when you could simply walk** the first cell, and refuses to jump from stairs, bottom slabs, ladders, or out of water.
- The cell next to takeoff must be fully passable at feet, head and the cell above the head, and the takeoff cell needs clear headroom.
- **Overshoot safety:** the two cells beyond the landing must merely not be dangerous (lava, cactus, fire, fluid). It does not require them to be solid.
- **Costs** are walk-time per block for distance 2 and 3, sprint-time for distance 4, plus a jump penalty. Landing on farmland is disallowed on purpose.
- **Execution is timing, not geometry.** It does not sprint for distance 2 or 3, only for 4 and for ascends. While you stand on the takeoff cell it just walks forward. It presses jump **when your feet leave the takeoff block**, which is the last possible moment. For a 2-block gap it deliberately waits until you are at least 0.7 from the takeoff cell's centre, so it does not jump too early. If you are behind the takeoff it walks to the cell behind it first for a run-up. It fails if your feet drop below the takeoff height.
- **Parkour place** (placing the landing block mid-jump) and **sprint-jumping** across straight runs (`SprintJump`, below) are opt-in.
- **Everything experimental is off by default:** `allowParkour`, `allowParkourPlace`, `sprintJumping`, `allowSwimming`, `allowVines`, diagonal ascend and descend.

This lines up with what the physics simulation found for us: sprint overshoots narrow landings, and the jump point matters. Baritone's answer is to jump later and not sprint for short gaps. Ours sprinted for 2-block gaps and jumped 0.15 early.

### `SprintJump`

For long straight runs Baritone simulates a sprint jump tick by tick with vanilla constants, starting from the player's *actual* position and velocity, and picks either full speed or a braking policy that lands exactly on a cell. It refuses if there are potion effects that change jump or gravity, if the floor is slippery or not full height, or if the player is drifting sideways. It steers in the air to cancel drift. It is the most sophisticated piece in the repo, and it is also off by default.

---

## 4. Execution and recovery (the part that would have prevented our bricking)

`PathExecutor` is where most of the robustness lives.

- **Where am I on the path?** Each tick it asks whether the player's feet are in the *current* movement's valid cells. If not, it looks backwards (lag, teleport back) and forwards (skipped ahead, jumped over some) through the other movements, and **re-synchronises the path position** to whichever contains the player. Only then does it consider the player lost.
- **Off path** is measured as the distance from the player to the nearest valid cell **anywhere on the path**, not to the next waypoint. 2 blocks away counts as drifting and is tolerated for up to 200 ticks, 3 blocks cancels at once. Mid-fall is exempt for falling movements, using flat distance to the landing.
- **Per-movement timeout:** the expected cost of the movement plus 100 ticks. The expected cost is captured when the movement starts, not recomputed as blocks get mined.
- **Cost re-verification:** every tick the current movement's cost is recomputed against the live world. It cancels if it became impossible, or got more than 10 ticks more expensive (unless the world was unloaded when planned). It also checks the next 5 movements for impossibility once per movement.
- **Cancelling only when safe.** Every cancel, replan, pause and splice is gated by `safeToCancel`, so it never stops mid-jump or over a ledge.
- **The executor decides sprinting**, not the movement. It looks ahead at the following movement (a traverse into an ascend can be sprinted, a descend into a flat walk can, a descend towards danger cannot). Releasing keys does not stop sprinting, so it cancels sprint explicitly.
- **Pause when the next destination is at the edge of loaded chunks**, and **pause when the best path being calculated passes through where you stand**, so it does not walk forward just to turn around.
- **Splicing.** While walking, it plans the next segment starting at the end of the current one (when under 150 ticks remain), then joins them. Path history longer than 300 movements is trimmed by 50.
- **No repair step.** When something goes wrong it cancels and the controller plans again from the player's position. (Our `PathRepair` is something Baritone does not have.)

### Controller (`PathingBehavior`)

- **A "real" start cell.** If the player is standing half off a block, or mid-jump, the controller picks a plausible nearby cell with support instead of the raw block position. Baritone takes this seriously (the comment cites an issue). We start from the raw `blockPosition()`.
- **A finished plan is discarded if it does not contain where the player is now.** (Orphan segment.)
- **A search that is no longer relevant is cancelled** when the player has moved so the new search's start is not on the current path.
- **Auto-jump is turned off** in the options while pathing and restored afterwards, because the game's auto-jump fights movement.
- **Events** are queued from the worker and dispatched on the game thread.

### Input and aim

- Baritone does not toggle key mappings. It **replaces the player's input object** with its own while pathing, so movement keys are exactly what the movements asked for and are not mixed with the physical keyboard. Each movement recomputes its inputs every tick, and the forced-input map is cleared before each update. Sprint is overridden separately through an event on the player's sprint state.
- The look code can **quantise and randomise rotations to look like mouse movement and satisfy server anti-cheat**, and has a setting for silent server-side rotation. **That is detection evasion and I am not recommending we build it.** It is also not needed for the pathing itself.
- Breaking a block only starts when the **crosshair is really on that block**: it aims first, checks the actual ray trace hits the block it wants, then clicks. It goes through the game's own click and damage-block methods and swings the arm.

---

## 5. Block classification

Baritone's `MovementHelper` is a long list of hard-won special cases. The categories matter more than the list:

- **Can walk through** (feet and head cells): air; plants; wooden doors (opened by right-click); fence gates; carpets and thin snow (only if supported). **Not:** cobweb, fire, berry bushes, honey, trapdoors, slabs, big dripleaf, powder snow, cauldrons, shulker boxes, skulls, cocoa, bubble columns, flowing water.
- **Fully passable** (also safe to jump through, no slowing): the above minus doors, gates, fluids, ladders, vines, cobwebs, tripwire.
- **Can walk on:** full-shape solid blocks (tested by collision shape), farmland, paths, soul sand, chests, glass, stairs, ladders when vines are allowed, slabs, and water only in specific cases. Magma only if allowed. Honey and bubble columns never.
- **Avoid walking into:** any fluid, magma, cactus, berry bush, fire, end portal, cobweb, bubble column.
- **Never break:** anything touching liquid (it will flow in), a block with unsupported gravel or sand beside it, ice (it turns to water), infested blocks, bedrock above and below. Falling blocks stacked above add their own break cost.
- **Climbable:** ladder, vine, weeping and twisting vines (and their plants).
- Per-block-state answers are **precomputed into tables** (YES, NO, MAYBE) so the hot loop does an array lookup, with the position-dependent cases (snow depth, water flow, carpet support) resolved only for MAYBE.

**Ours:** `WorldView` calls a cell AIR if the collision shape is empty, SOLID otherwise, plus a short DANGER list, water, and ladders. That misses slabs, stairs, doors, gates, carpets, snow layers, cobwebs (empty shape but slows you), and flowing water. A bottom slab, for example, is "solid" to us but you cannot stand at the height we assume.

### Chunk cache

Chunks are packed to **2 bits per block**: AIR, SOLID, WATER, AVOID, so far-away terrain costs 16 KiB per chunk. A short list of landmark blocks is tracked separately (chests, portals, beds, ladders, vines, nether wart). Entirely-air sections are skipped. The cache can be saved to disk. Search reads loaded chunks from a thread-safe copy of the chunk provider plus this cache.

**Ours:** `GridSnapshot` is the same idea in a smaller form (one byte per cell, bounded box, copied on the game thread). That is sound and matches Baritone's rule that the worker never touches the live world.

---

## 6. Mining, farming, following

**Mine** (`MineProcess`):

- The target is a block filter. Known matching blocks come from a background rescan every 5 ticks, so the game thread never scans.
- The goal is a **composite of one goal per ore**, usually "get anywhere in this column so you can break it from below", or "feet or head in this cell" when a falling block sits above.
- Every candidate is **pruned** for plausibility: breakable with current tools, not touching liquid, not sealed between bedrock, within Y limits, not blacklisted, optionally only ores that are exposed to air.
- If the path search fails, the **closest ore is blacklisted** (and its neighbours) and mining continues with the rest, instead of aborting.
- When something to mine is in reach it asks the engine to pause, switches to the best tool, aims, and clicks.
- It **picks up dropped items** by treating item entities as goals. This is a real gap for us: our `MineTask` breaks blocks and never collects what drops.
- `legitMine` restricts it to ores the player could actually see and explores at a fixed Y until it finds some. The code comments say plainly that scanning unseen blocks is an x-ray. Our `MineTask` already only targets blocks in line of sight, which is the right side of that line.
- Quantity-limited: it stops once the inventory has enough.

**Farm** (`FarmProcess`) is a small state machine run each tick. It classifies the nearby scanned blocks into ready-to-harvest, empty farmland, empty soul sand, cocoa-able logs and bone-meal targets. It handles one thing in reach (harvest, plant, bone meal), otherwise builds a composite goal from everything that still needs doing, including dropped crop items, and waits for immature crops if told to. It supports wheat, carrots, potatoes, beetroot, pumpkin, melon, nether wart, cocoa, sugar cane, bamboo and cactus. Maturity uses the crop block's own max-age test.

**Follow** and **pickup** are the same process with a different entity filter. A follow goal is "near the entity" with a radius (default 3), or into it for pickups.

**Get to block** finds known positions, blacklists unreachable ones the same way, and right-clicks containers on arrival.

---

## 7. Where Daedalus differs, ranked by how much it matters

### Fix for reliability (these would have prevented or softened the bricking)

1. **Off-course measured against the whole path, with a tolerance and a time allowance**, not one segment. We now measure against one segment (`Course`), which fixed the immediate bug, but Baritone's rule also covers lag, teleports and skipping ahead.
2. **Re-synchronise instead of failing.** If the player is on an earlier or later part of the route, move the position to match.
3. **Per-movement valid cells and timeouts** based on the expected cost. We have one global 1200-tick timeout and a 40-tick stuck counter.
4. **`safeToCancel`** so replanning never happens mid-jump.
5. **A real start cell** for planning (player half off an edge, or airborne).
6. **Discard a plan that does not contain the player.**
7. **Turn off the game's auto-jump** while pathing.
8. **Drive the player's input object**, not the key mappings, so a held physical key cannot fight the macro and the sprint/double-tap state does not get confused.
9. **Cost re-verification** against the live world with a short lookahead.

### Fix for correctness of movement

10. **Tick-based costs** from real speeds, so mining time and walking time compare.
11. **Diagonal moves**, which removes the need for straightening on open ground (and the bug class that came with it).
12. **Better block classification** with the categories above, especially slabs, stairs, doors, gates, cobweb, snow, flowing water.
13. **Parkour re-spec:** plain jump for gaps of 1 and 2, sprint only for 3, jump at the last supported moment, never when you could walk, overshoot cells merely safe, optional run-up. Keep it behind an off-by-default setting like Baritone does.

### Architecture

14. **A process layer on one shared walker**: mining, farming and following set goals and act when in reach, instead of each step owning the keys. This is also what makes macros composable.
15. **Composite goals**, a "get to block" goal, and run-away.
16. **Blacklist unreachable targets** and keep going.
17. **Pick up drops.**
18. **Segmented planning with plan-ahead** and cutting off at the edge of loaded chunks, so long walks work without a 96-block cap.
19. **Time-limited searches** (500 ms / 2 s), partial results, minimum improvement.
20. **Frozen settings per search** and a settings object where experimental movement types are off by default.

### Deliberately not taking

- Rotation quantising and randomisation for anti-cheat, silent server-side rotation.
- Elytra flight, the schematic builder, pig riding. Out of scope.
- Parkour place (placing blocks mid-jump) for now.

---

## 8. Suggested order

1. Reliability set (items 1 to 9). Mostly small and testable, and the walker is the part that failed in game.
2. Block classification (12), since everything else depends on the world reading being right.
3. Tick costs and diagonals (10, 11). This changes the search, so do it with the test suite as a safety net, and extend the tests with the staircase and diagonal cases.
4. Process layer and goals (14 to 17), starting with mine plus pickup, then farm.
5. Parkour re-spec and swimming, behind default-off flags, only after the walk loop is trusted in game.

Each step can be checked without the game where it is pure logic. The executor changes need the in-game smoke test from `DEVLOG.md` after each one.
