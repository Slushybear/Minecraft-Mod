package com.slushybear.daedalus.automation;

import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** What a MINE step may break. Each ore covers its stone and deepslate form, so mining below y=0 finds it too. */
public enum MineTarget {
    STONE(Blocks.STONE),
    DEEPSLATE(Blocks.DEEPSLATE),
    COAL(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
    IRON(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
    COPPER(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
    GOLD(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE),
    REDSTONE(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
    LAPIS(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
    DIAMOND(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
    EMERALD(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);

    private final Set<Block> blocks;

    MineTarget(Block... blocks) {
        this.blocks = Set.of(blocks);
    }

    public Set<Block> blocks() {
        return blocks;
    }

    public static Optional<MineTarget> byId(String id) {
        for (MineTarget target : values()) {
            if (target.name().equalsIgnoreCase(id)) {
                return Optional.of(target);
            }
        }
        return Optional.empty();
    }
}
