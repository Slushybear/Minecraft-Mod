package com.slushybear.daedalus.module.visuals;

import com.slushybear.daedalus.module.Category;
import com.slushybear.daedalus.module.Module;
import com.slushybear.daedalus.module.WorldGizmos;
import com.slushybear.daedalus.module.setting.BoolSetting;
import com.slushybear.daedalus.module.setting.ChoiceSetting;
import com.slushybear.daedalus.module.setting.SliderSetting;
import com.slushybear.daedalus.module.setting.TextSetting;
import com.slushybear.daedalus.ui.Theme;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Highlights the blocks you list, for example ores. Once a second it scans the loaded chunks around you. Chunk
 * sections that cannot contain any listed block are skipped without reading their blocks, which keeps it fast.
 */
public final class BlockEsp extends Module implements WorldGizmos.Drawer {
    private static final int SCAN_EVERY_TICKS = 20;
    private static final int MAX_BLOCKS = 1024;

    private final TextSetting blocks = addSetting(new TextSetting("Blocks",
            "diamond_ore, deepslate_diamond_ore, ancient_debris"));
    private final SliderSetting range = addSetting(new SliderSetting("Range", 32, 8, 64, 8));
    private final ChoiceSetting mode = addSetting(new ChoiceSetting("Mode", "Outline", "Outline", "Box"));
    private final BoolSetting throughWalls = addSetting(new BoolSetting("Through walls", true));

    private List<BlockPos> found = List.of();
    private int cooldown;

    public BlockEsp() {
        super("Block ESP", "Highlights chosen blocks, like ores", Category.VISUALS, false);
    }

    @Override
    protected void onDisable() {
        found = List.of();
        cooldown = 0;
    }

    /** Reads the Blocks setting: names separated by commas or spaces, with or without "minecraft:". Unknown names are skipped. */
    private Set<Block> targets() {
        Set<Block> result = new HashSet<>();
        for (String token : blocks.get().toLowerCase(Locale.ROOT).split("[,\\s]+")) {
            if (token.isEmpty()) {
                continue;
            }
            Identifier id = Identifier.tryParse(token.contains(":") ? token : "minecraft:" + token);
            if (id != null) {
                BuiltInRegistries.BLOCK.getOptional(id).filter(b -> b != Blocks.AIR).ifPresent(result::add);
            }
        }
        return result;
    }

    @Override
    public void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            found = List.of();
            return;
        }
        if (--cooldown > 0) {
            return;
        }
        cooldown = SCAN_EVERY_TICKS;
        found = scan(client.level, client.player.blockPosition(), (int) Math.round(range.get()), targets());
    }

    private static List<BlockPos> scan(ClientLevel level, BlockPos center, int r, Set<Block> targets) {
        List<BlockPos> result = new ArrayList<>();
        if (targets.isEmpty()) {
            return result;
        }
        long maxDistSq = (long) r * r;
        int minSectionY = level.getMinSectionY();
        for (int cx = (center.getX() - r) >> 4; cx <= (center.getX() + r) >> 4; cx++) {
            for (int cz = (center.getZ() - r) >> 4; cz <= (center.getZ() + r) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    int sectionY = minSectionY + i;
                    int baseY = sectionY << 4;
                    if (baseY + 15 < center.getY() - r || baseY > center.getY() + r) {
                        continue;
                    }
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(state -> targets.contains(state.getBlock()))) {
                        continue;
                    }
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (!targets.contains(state.getBlock())) {
                                    continue;
                                }
                                BlockPos pos = new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z);
                                if (pos.distSqr(center) <= maxDistSq) {
                                    result.add(pos);
                                    if (result.size() >= MAX_BLOCKS) {
                                        return result;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void drawWorld(float partialTick) {
        int color = Theme.accent();
        GizmoStyle style = mode.get().equals("Box")
                ? GizmoStyle.strokeAndFill(color, 2f, Theme.withAlpha(color, 0.25f))
                : GizmoStyle.stroke(color, 2f);
        for (BlockPos pos : found) {
            GizmoProperties gizmo = Gizmos.cuboid(pos, style);
            if (throughWalls.get()) {
                gizmo.setAlwaysOnTop();
            }
        }
    }
}
