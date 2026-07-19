package me.biquaternions.fish.threadedregions;

import io.papermc.paper.redstone.RedstoneWireTurbo;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class GlobalServerData {

    public final RedstoneWireTurbo turbo;

    public boolean shouldSignal = true;

    public GlobalServerData() {
        this.turbo = new RedstoneWireTurbo((RedStoneWireBlock) Blocks.REDSTONE_WIRE);
    }

}
