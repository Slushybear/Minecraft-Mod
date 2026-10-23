package com.slushybear.daedalus.module.visuals;

import com.slushybear.daedalus.module.Category;
import com.slushybear.daedalus.module.Module;

/**
 * Lights everything up as if you had night vision. The work is done in
 * {@link com.slushybear.daedalus.mixin.LightmapExtractorMixin}, which turns the night vision strength to full
 * whenever this module is enabled.
 */
public final class Fullbright extends Module {
    public Fullbright() {
        super("Fullbright", "Max brightness everywhere", Category.VISUALS, false);
    }
}
