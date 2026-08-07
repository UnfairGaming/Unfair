package cn.unfair.util.via;

import net.minecraft.util.BlockPos;
import net.minecraft.util.MovementInput;

public interface ModernPlayerPhysics {

    boolean viaforge$isModernSwimming();

    boolean viaforge$wasModernSwimming();

    boolean viaforge$isModernSubmergedInWater();

    void viaforge$setModernSubmergedInWater(boolean submerged);

    boolean viaforge$wasModernEyeInWater();

    float viaforge$getModernEyeHeight();

    double viaforge$getModernWaterHeight();

    void viaforge$setModernWaterHeight(double height);

    double viaforge$getModernLavaHeight();

    void viaforge$setModernLavaHeight(double height);

    boolean viaforge$isTouchingModernLava();

    void viaforge$setTouchingModernLava(boolean touching);

    BlockPos viaforge$getMainSupportingBlock();

    boolean viaforge$wasSupportingBlockOnGround();

    void viaforge$setMainSupportingBlock(BlockPos position, boolean onGround);

    void viaforge$markLocalItemUseFinished();

    void viaforge$confirmServerItemUseFinished();

    void viaforge$updateModernMovementInput(MovementInput input);
}
