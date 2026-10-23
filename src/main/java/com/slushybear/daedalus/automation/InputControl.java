package com.slushybear.daedalus.automation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;

/**
 * Takes over the player's movement input for the length of a walk, and gives it back afterwards. Also switches off the
 * game's auto-jump while walking, since it jumps on its own at blocks in front and fights the walker's own jumps.
 */
final class InputControl {
    private LocalPlayer owner;
    private ClientInput previous;
    private PathingInput input;
    private Boolean previousAutoJump;

    /** The walker's input, installing it first if needed. Call every tick: a respawn replaces the player object. */
    PathingInput take(Minecraft client) {
        LocalPlayer player = client.player;
        if (input != null && owner == player && player.input == input) {
            return input;
        }
        giveBack(client);
        owner = player;
        previous = player.input;
        input = new PathingInput();
        player.input = input;
        previousAutoJump = client.options.autoJump().get();
        client.options.autoJump().set(false);
        return input;
    }

    void giveBack(Minecraft client) {
        if (input == null) {
            return;
        }
        input.release();
        if (owner != null && owner.input == input) {
            owner.input = previous; // only if nothing else replaced it in the meantime, such as a freecam
        }
        if (previousAutoJump != null) {
            client.options.autoJump().set(previousAutoJump);
        }
        owner = null;
        previous = null;
        input = null;
        previousAutoJump = null;
    }
}
