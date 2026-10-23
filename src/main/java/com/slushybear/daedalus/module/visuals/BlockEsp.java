package com.slushybear.daedalus.module.visuals;

import com.slushybear.daedalus.module.Category;
import com.slushybear.daedalus.module.Module;
import com.slushybear.daedalus.module.WorldGizmos;
import com.slushybear.daedalus.module.setting.BoolSetting;
import com.slushybear.daedalus.module.setting.ChoiceSetting;
import com.slushybear.daedalus.module.setting.MultiChoiceSetting;
import com.slushybear.daedalus.module.setting.SliderSetting;
import com.slushybear.daedalus.module.setting.TextSetting;
import com.slushybear.daedalus.ui.Theme;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.RectGizmo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Highlights chosen blocks such as ores, each type in its own colour.
 *
 * Touching blocks are drawn as one shape: only the outer faces of a vein are outlined (or filled), not every block's
 * own box, which keeps clusters readable. Optional tracer lines point from the crosshair to each vein. Finding the
 * blocks is done by {@link BlockScanner}, a little every tick.
 */
public final class BlockEsp extends Module implements WorldGizmos.Drawer {
    /** At most this many blocks are drawn, nearest first, so huge finds (like all coal) stay smooth. */
    private static final int MAX_DRAWN = 1500;
    private static final int MAX_TRACERS = 24;

    private record Preset(String name, int color, String... ids) {}

    private static final List<Preset> PRESETS = List.of(
            new Preset("Diamond", 0xFF4FE3E8, "diamond_ore", "deepslate_diamond_ore"),
            new Preset("Ancient debris", 0xFFC27A4A, "ancient_debris"),
            new Preset("Emerald", 0xFF2FD36B, "emerald_ore", "deepslate_emerald_ore"),
            new Preset("Gold", 0xFFFFD23A, "gold_ore", "deepslate_gold_ore", "nether_gold_ore"),
            new Preset("Iron", 0xFFE0B895, "iron_ore", "deepslate_iron_ore"),
            new Preset("Redstone", 0xFFFF3B3B, "redstone_ore", "deepslate_redstone_ore"),
            new Preset("Lapis", 0xFF3A6BFF, "lapis_ore", "deepslate_lapis_ore"),
            new Preset("Copper", 0xFFE77C4F, "copper_ore", "deepslate_copper_ore"),
            new Preset("Coal", 0xFF9A9A9A, "coal_ore", "deepslate_coal_ore"),
            new Preset("Quartz", 0xFFEDE6DA, "nether_quartz_ore"),
            new Preset("Spawners", 0xFFC04BFF, "spawner", "trial_spawner"),
            new Preset("Chests", 0xFFFFA43A, "chest", "trapped_chest", "barrel", "ender_chest"));

    private final MultiChoiceSetting blocks = addSetting(new MultiChoiceSetting("Blocks",
            PRESETS.stream().map(Preset::name).toList(), "Diamond", "Ancient debris"));
    private final TextSetting extra = addSetting(new TextSetting("Extra blocks (ids, comma separated)", ""));
    private final SliderSetting range = addSetting(new SliderSetting("Range", 48, 8, 128, 8));
    private final ChoiceSetting mode = addSetting(new ChoiceSetting("Mode", "Outline", "Outline", "Box"));
    private final ChoiceSetting colours = addSetting(new ChoiceSetting("Colours", "Per block", "Per block", "Accent"));
    private final BoolSetting tracers = addSetting(new BoolSetting("Tracers", false));
    private final BoolSetting throughWalls = addSetting(new BoolSetting("Through walls", true));

    private final BlockScanner scanner = new BlockScanner();
    private String scanKey = "";
    private Shapes shapes = Shapes.EMPTY;

    /** Pre-built lines and faces, rebuilt only when the scan results change, so drawing each frame is cheap. */
    private record Line(Vec3 a, Vec3 b, int color) {}

    private record Face(Vec3 min, Vec3 max, Direction side, int color) {}

    private record Vein(Vec3 center, int color) {}

    private record Shapes(List<Line> lines, List<Face> faces, List<Vein> veins) {
        static final Shapes EMPTY = new Shapes(List.of(), List.of(), List.of());
    }

    public BlockEsp() {
        super("Block ESP", "Highlights ores and other chosen blocks", Category.VISUALS, false);
    }

    @Override
    protected void onDisable() {
        scanner.clear();
        scanKey = "";
        shapes = Shapes.EMPTY;
    }

    @Override
    public void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            onDisable();
            return;
        }
        // Start over whenever the block choices change.
        String key = blocks.get() + "|" + extra.get() + "|" + colours.get() + "|" + Theme.accentRgb();
        if (!key.equals(scanKey)) {
            scanKey = key;
            scanner.restart(targets());
            shapes = Shapes.EMPTY;
        }
        if (scanner.tick(client.level, client.player.blockPosition(), (int) Math.round(range.get()))) {
            shapes = build(scanner.results(), client.player.blockPosition());
        }
    }

    /** Block to colour, from the ticked presets plus any extra block ids typed in. */
    private Map<Block, Integer> targets() {
        boolean accent = colours.get().equals("Accent");
        Map<Block, Integer> result = new HashMap<>();
        for (Preset preset : PRESETS) {
            if (blocks.has(preset.name())) {
                for (String id : preset.ids()) {
                    block(id).ifPresent(b -> result.put(b, accent ? Theme.accent() : preset.color()));
                }
            }
        }
        for (String token : extra.get().toLowerCase(Locale.ROOT).split("[,\\s]+")) {
            if (!token.isEmpty()) {
                block(token).ifPresent(b -> result.putIfAbsent(b, Theme.accent()));
            }
        }
        return result;
    }

    private static java.util.Optional<Block> block(String id) {
        Identifier parsed = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        return parsed == null ? java.util.Optional.empty()
                : BuiltInRegistries.BLOCK.getOptional(parsed).filter(b -> b != Blocks.AIR);
    }

    // ---- Turning found blocks into shapes ----

    /** For each axis, the face on the low side and the high side. */
    private static final Direction[][] SIDES = {
            {Direction.WEST, Direction.EAST},
            {Direction.DOWN, Direction.UP},
            {Direction.NORTH, Direction.SOUTH}};

    private static Shapes build(Long2IntOpenHashMap found, BlockPos player) {
        // Keep the nearest blocks only.
        List<Long> nearest = new ArrayList<>(found.keySet());
        if (nearest.size() > MAX_DRAWN) {
            nearest.sort(Comparator.comparingDouble(p -> BlockPos.of(p).distSqr(player)));
            nearest = nearest.subList(0, MAX_DRAWN);
        }
        LongOpenHashSet set = new LongOpenHashSet(nearest);

        List<Line> lines = new ArrayList<>();
        List<Face> faces = new ArrayList<>();
        for (long packed : set) {
            BlockPos pos = BlockPos.of(packed);
            int color = found.get(packed);
            // A face is "open" when the block next to it is not part of the highlight.
            boolean[] open = new boolean[6];
            for (Direction d : Direction.values()) {
                open[d.ordinal()] = !set.contains(BlockPos.offset(packed, d));
            }
            Vec3 min = Vec3.atLowerCornerOf(pos);
            Vec3 max = min.add(1, 1, 1);
            for (Direction d : Direction.values()) {
                if (open[d.ordinal()]) {
                    faces.add(new Face(min, max, d, color));
                }
            }
            // An edge sits between two faces on different axes. Drawing it only when both faces are open means the
            // seams between touching blocks disappear and a vein is outlined as one shape.
            for (int a1 = 0; a1 < 3; a1++) {
                for (int a2 = a1 + 1; a2 < 3; a2++) {
                    int along = 3 - a1 - a2;
                    for (int s1 = 0; s1 < 2; s1++) {
                        for (int s2 = 0; s2 < 2; s2++) {
                            if (!open[SIDES[a1][s1].ordinal()] || !open[SIDES[a2][s2].ordinal()]) {
                                continue;
                            }
                            double[] start = new double[3];
                            start[a1] = s1;
                            start[a2] = s2;
                            double[] end = start.clone();
                            end[along] = 1;
                            lines.add(new Line(min.add(start[0], start[1], start[2]), min.add(end[0], end[1], end[2]), color));
                        }
                    }
                }
            }
        }
        return new Shapes(lines, faces, veins(set, found, player));
    }

    /** Groups touching blocks into veins, for tracers. Returns the nearest veins' centres. */
    private static List<Vein> veins(LongOpenHashSet set, Long2IntOpenHashMap found, BlockPos player) {
        List<Vein> veins = new ArrayList<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        for (long start : set) {
            if (!seen.add(start)) {
                continue;
            }
            double sx = 0, sy = 0, sz = 0;
            int count = 0;
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            queue.enqueue(start);
            while (!queue.isEmpty()) {
                long p = queue.dequeueLong();
                sx += BlockPos.getX(p) + 0.5;
                sy += BlockPos.getY(p) + 0.5;
                sz += BlockPos.getZ(p) + 0.5;
                count++;
                for (Direction d : Direction.values()) {
                    long next = BlockPos.offset(p, d);
                    if (set.contains(next) && seen.add(next)) {
                        queue.enqueue(next);
                    }
                }
            }
            veins.add(new Vein(new Vec3(sx / count, sy / count, sz / count), found.get(start)));
        }
        Vec3 from = Vec3.atCenterOf(player);
        veins.sort(Comparator.comparingDouble(v -> v.center().distanceToSqr(from)));
        return veins.size() > MAX_TRACERS ? List.copyOf(veins.subList(0, MAX_TRACERS)) : veins;
    }

    // ---- Drawing ----

    @Override
    public void drawWorld(float partialTick) {
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        Vec3 eye = camera.position();
        double fadeRange = Math.max(16, range.get());
        boolean onTop = throughWalls.get();

        if (mode.get().equals("Box")) {
            for (Face face : shapes.faces()) {
                float fade = fade(face.min().add(0.5, 0.5, 0.5), eye, fadeRange);
                GizmoStyle style = GizmoStyle.fill(Theme.withAlpha(face.color(), 0.22f * fade));
                show(Gizmos.addGizmo(RectGizmo.fromCuboidFace(face.min(), face.max(), face.side(), style)), onTop);
            }
        }
        for (Line line : shapes.lines()) {
            float fade = fade(line.a(), eye, fadeRange);
            show(Gizmos.line(line.a(), line.b(), Theme.withAlpha(line.color(), fade), 2.5f), onTop);
        }
        if (tracers.get()) {
            // Start a little in front of the camera, so the lines come out of the crosshair.
            Vector3fc forward = camera.forwardVector();
            Vec3 start = eye.add(forward.x() * 0.5, forward.y() * 0.5, forward.z() * 0.5);
            for (Vein vein : shapes.veins()) {
                Gizmos.line(start, vein.center(), Theme.withAlpha(vein.color(), 0.55f), 1.2f).setAlwaysOnTop();
            }
        }
    }

    /** Things further away are drawn fainter, down to 40%, so nearby blocks stand out. */
    private static float fade(Vec3 point, Vec3 eye, double range) {
        double t = Math.min(1, point.distanceTo(eye) / range);
        return (float) (1 - 0.6 * t);
    }

    private static void show(GizmoProperties gizmo, boolean onTop) {
        if (onTop) {
            gizmo.setAlwaysOnTop();
        }
    }
}
