package com.slushybear.daedalus.automation;

import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Crops the farming automation knows how to handle. The remote allowlist can only narrow this set. */
public enum CropType {
    WHEAT(Blocks.WHEAT, Items.WHEAT_SEEDS),
    CARROTS(Blocks.CARROTS, Items.CARROT),
    POTATOES(Blocks.POTATOES, Items.POTATO),
    BEETROOTS(Blocks.BEETROOTS, Items.BEETROOT_SEEDS);

    private final Block block;
    private final Item seed;

    CropType(Block block, Item seed) {
        this.block = block;
        this.seed = seed;
    }

    public Block block() {
        return block;
    }

    public Item seed() {
        return seed;
    }

    public static Optional<CropType> byId(String id) {
        for (CropType crop : values()) {
            if (crop.name().equalsIgnoreCase(id)) {
                return Optional.of(crop);
            }
        }
        return Optional.empty();
    }

    public boolean isMature(BlockState state) {
        return state.is(block) && state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }
}
