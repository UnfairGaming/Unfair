package cn.unfair.util.player;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;

import java.util.List;

public class SimulatedPlayer {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private final EntityPlayerSP player;
    private final MovementInput movementInput;
    public float rotationYaw;
    public boolean onGround;
    public boolean isCollidedHorizontally;
    public float fallDistance;
    private AxisAlignedBB box;
    private double posX;
    private double posY;
    private double posZ;
    private double motionX;
    private double motionY;
    private double motionZ;
    private boolean isSprinting;
    private int jumpTicks;
    private final boolean checkGroundSupportAfterMove;
    private final boolean normalizeSmallMotion;

    private SimulatedPlayer(EntityPlayerSP player, MovementInput movementInput, boolean checkGroundSupportAfterMove,
                            boolean normalizeSmallMotion) {
        this.player = player;
        this.movementInput = movementInput;
        this.checkGroundSupportAfterMove = checkGroundSupportAfterMove;
        this.normalizeSmallMotion = normalizeSmallMotion;
        this.box = player.getEntityBoundingBox();
        this.posX = player.posX;
        this.posY = player.posY;
        this.posZ = player.posZ;
        this.motionX = player.motionX;
        this.motionY = player.motionY;
        this.motionZ = player.motionZ;
        this.rotationYaw = player.rotationYaw;
        this.onGround = player.onGround;
        this.isCollidedHorizontally = player.isCollidedHorizontally;
        this.fallDistance = player.fallDistance;
        this.isSprinting = player.isSprinting();
        this.jumpTicks = player.getJumpTicks();
    }

    public static SimulatedPlayer fromClientPlayer(MovementInput input) {
        return fromClientPlayer(input, true, false);
    }

    public static SimulatedPlayer fromClientPlayer(MovementInput input, boolean checkGroundSupportAfterMove) {
        return fromClientPlayer(input, checkGroundSupportAfterMove, false);
    }

    public static SimulatedPlayer fromClientPlayer(MovementInput input, boolean checkGroundSupportAfterMove,
                                                    boolean normalizeSmallMotion) {
        MovementInput copy = new MovementInput();
        copy.moveForward = input.moveForward;
        copy.moveStrafe = input.moveStrafe;
        copy.jump = input.jump;
        copy.sneak = input.sneak;
        return new SimulatedPlayer(mc.thePlayer, copy, checkGroundSupportAfterMove, normalizeSmallMotion);
    }

    public Vec3 getPos() {
        return new Vec3(this.posX, this.posY, this.posZ);
    }

    public void tick() {
        if (this.jumpTicks > 0) {
            --this.jumpTicks;
        }
        if (this.normalizeSmallMotion) {
            if (Math.abs(this.motionX) < 0.005D) {
                this.motionX = 0.0D;
            }
            if (Math.abs(this.motionY) < 0.005D) {
                this.motionY = 0.0D;
            }
            if (Math.abs(this.motionZ) < 0.005D) {
                this.motionZ = 0.0D;
            }
        }

        float forward = this.movementInput.moveForward * 0.98F;
        float strafe = this.movementInput.moveStrafe * 0.98F;
        boolean jumping = this.movementInput.jump;

        boolean canSprint = this.player.getFoodStats().getFoodLevel() > 6 || this.player.capabilities.allowFlying;
        if (this.onGround && forward >= 0.8F && !this.isSprinting && canSprint && !this.player.isUsingItem() && !this.player.isPotionActive(Potion.blindness)) {
            this.isSprinting = this.player.isSprinting();
        }
        if (this.movementInput.sneak) {
            this.isSprinting = false;
        }
        if (this.isSprinting && (forward < 0.8F || this.isCollidedHorizontally || !canSprint)) {
            this.isSprinting = false;
        }

        if (jumping) {
            if (this.player.isInWater() || this.player.isInLava()) {
                this.motionY += 0.03999999910593033D;
            } else if (this.onGround && this.jumpTicks == 0) {
                this.motionY = 0.42D;
                if (this.player.isPotionActive(Potion.jump)) {
                    this.motionY += (this.player.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1F;
                }
                if (this.isSprinting) {
                    float yawRad = this.rotationYaw * 0.017453292F;
                    this.motionX -= MathHelper.sin(yawRad) * 0.2F;
                    this.motionZ += MathHelper.cos(yawRad) * 0.2F;
                }
            }
        } else {
            this.jumpTicks = 0;
        }

        this.moveWithHeading(strafe, forward);
    }

    private void moveWithHeading(float strafe, float forward) {
        if (this.player.isInWater()) {
            this.moveFlying(strafe, forward, 0.02F);
            this.moveEntity(this.motionX, this.motionY, this.motionZ);
            this.motionX *= 0.800000011920929D;
            this.motionY *= 0.800000011920929D;
            this.motionZ *= 0.800000011920929D;
            this.motionY -= 0.02D;
            return;
        }

        if (this.player.isInLava()) {
            this.moveFlying(strafe, forward, 0.02F);
            this.moveEntity(this.motionX, this.motionY, this.motionZ);
            this.motionX *= 0.5D;
            this.motionY *= 0.5D;
            this.motionZ *= 0.5D;
            this.motionY -= 0.02D;
            return;
        }

        float slipperiness = 0.91F;
        if (this.onGround) {
            Block block = mc.theWorld.getBlockState(new BlockPos(MathHelper.floor_double(this.posX), MathHelper.floor_double(this.box.minY) - 1, MathHelper.floor_double(this.posZ))).getBlock();
            slipperiness = block.slipperiness * 0.91F;
        }

        float groundFactor = 0.16277136F / (slipperiness * slipperiness * slipperiness);
        float speed = this.onGround ? this.getAIMoveSpeed() * groundFactor : (this.isSprinting ? 0.026F : 0.02F);
        this.moveFlying(strafe, forward, speed);

        if (this.isOnLadder()) {
            this.motionX = MathHelper.clamp_double(this.motionX, -0.15D, 0.15D);
            this.motionZ = MathHelper.clamp_double(this.motionZ, -0.15D, 0.15D);
            this.fallDistance = 0.0F;
            if (this.motionY < -0.15D) {
                this.motionY = -0.15D;
            }
        }

        this.moveEntity(this.motionX, this.motionY, this.motionZ);
        if (this.isCollidedHorizontally && this.isOnLadder()) {
            this.motionY = 0.2D;
        }

        this.motionY -= 0.08D;
        this.motionY *= 0.9800000190734863D;
        this.motionX *= slipperiness;
        this.motionZ *= slipperiness;
    }

    private void moveFlying(float strafe, float forward, float friction) {
        float f = strafe * strafe + forward * forward;
        if (f >= 1.0E-4F) {
            f = MathHelper.sqrt_float(f);
            if (f < 1.0F) {
                f = 1.0F;
            }
            f = friction / f;
            strafe *= f;
            forward *= f;
            float sin = MathHelper.sin(this.rotationYaw * (float) Math.PI / 180.0F);
            float cos = MathHelper.cos(this.rotationYaw * (float) Math.PI / 180.0F);
            this.motionX += strafe * cos - forward * sin;
            this.motionZ += forward * cos + strafe * sin;
        }
    }

    private void moveEntity(double x, double y, double z) {
        double originalX = x;
        double originalY = y;
        double originalZ = z;
        boolean wasOnGround = this.onGround;
        List<AxisAlignedBB> collisions = mc.theWorld.getCollidingBoundingBoxes(this.player, this.box.addCoord(x, y, z));

        for (AxisAlignedBB collision : collisions) {
            y = collision.calculateYOffset(this.box, y);
        }
        this.box = this.box.offset(0.0D, y, 0.0D);

        for (AxisAlignedBB collision : collisions) {
            x = collision.calculateXOffset(this.box, x);
        }
        this.box = this.box.offset(x, 0.0D, 0.0D);

        for (AxisAlignedBB collision : collisions) {
            z = collision.calculateZOffset(this.box, z);
        }
        this.box = this.box.offset(0.0D, 0.0D, z);

        this.isCollidedHorizontally = originalX != x || originalZ != z;
        this.onGround = originalY != y && originalY < 0.0D;
        if (this.checkGroundSupportAfterMove && wasOnGround && this.onGround) {
            this.onGround = !mc.theWorld.getCollidingBoundingBoxes(
                    this.player, this.box.offset(0.0D, -0.001D, 0.0D)).isEmpty();
        }
        if (originalX != x) {
            this.motionX = 0.0D;
        }
        if (originalY != y) {
            this.motionY = 0.0D;
        }
        if (originalZ != z) {
            this.motionZ = 0.0D;
        }

        this.posX = (this.box.minX + this.box.maxX) / 2.0D;
        this.posY = this.box.minY;
        this.posZ = (this.box.minZ + this.box.maxZ) / 2.0D;
        if (!this.onGround && y < 0.0D) {
            this.fallDistance = (float) (this.fallDistance - y);
        } else if (this.onGround) {
            this.fallDistance = 0.0F;
        }
    }

    private boolean isOnLadder() {
        Block block = mc.theWorld.getBlockState(new BlockPos(MathHelper.floor_double(this.posX), MathHelper.floor_double(this.box.minY), MathHelper.floor_double(this.posZ))).getBlock();
        return block == net.minecraft.init.Blocks.ladder || block == net.minecraft.init.Blocks.vine;
    }

    private float getAIMoveSpeed() {
        return (float) this.player.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.movementSpeed).getAttributeValue();
    }
}
