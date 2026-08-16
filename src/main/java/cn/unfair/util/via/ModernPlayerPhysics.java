package cn.unfair.util.via;

import net.minecraft.util.BlockPos;
import net.minecraft.util.MovementInput;

public interface ModernPlayerPhysics {

    boolean isModernSwimming();

    boolean wasModernSwimming();

    boolean isModernSubmergedInWater();

    void setModernSubmergedInWater(boolean submerged);

    boolean wasModernEyeInWater();

    float getModernEyeHeight();

    double getModernWaterHeight();

    void setModernWaterHeight(double height);

    double getModernLavaHeight();

    void setModernLavaHeight(double height);

    boolean isTouchingModernLava();

    void setTouchingModernLava(boolean touching);

    BlockPos getMainSupportingBlock();

    boolean wasSupportingBlockOnGround();

    void setMainSupportingBlock(BlockPos position, boolean onGround);

    void markLocalItemUseFinished();

    void confirmServerItemUseFinished();

    void updateModernMovementInput(MovementInput input);
}
