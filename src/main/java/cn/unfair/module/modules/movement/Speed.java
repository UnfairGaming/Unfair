package cn.unfair.module.modules.movement;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.*;
import cn.unfair.management.RotationState;
import cn.unfair.module.Module;
import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.module.modules.world.Scaffold;
import cn.unfair.property.properties.*;
import cn.unfair.util.player.MoveUtil;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

public class Speed extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"TimerBalance", "Normal"});
    public final FloatProperty timerBoostMultiplier = new FloatProperty("TimerBoostMultiplier", 0.5f, 0.1f, 1f, () -> this.mode.getValue() == 0);
    public final IntProperty lowTimerTicks = new IntProperty("LowTimerTicks", 6, 1, 10, () -> this.mode.getValue() == 0);
    public final BooleanProperty rotation = new BooleanProperty("Rotation", false, () -> this.mode.getValue() == 0);
    public final FloatProperty multiplier = new FloatProperty("Multiplier", 1.0F, 0.0F, 10.0F, () -> this.mode.getValue() == 1);
    public final FloatProperty friction = new FloatProperty("Friction", 1.0F, 0.0F, 10.0F, () -> this.mode.getValue() == 1);
    public final PercentProperty strafe = new PercentProperty("Strafe", 0, () -> this.mode.getValue() == 1);
    private int ticks = 0;
    private float yaw = 0f;
    private final YawOffsetMode yawOffsetMode = YawOffsetMode.AIR;
    private boolean finished = false;
    public Speed() {
        super("Speed", false);
    }

    private boolean canBoost() {
        Scaffold scaffold = (Scaffold) Unfair.moduleManager.modules.get(Scaffold.class);
        return !scaffold.isEnabled() && MoveUtil.isForwardPressed()
                && mc.thePlayer.getFoodStats().getFoodLevel() > 6
                && !mc.thePlayer.isSneaking()
                && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isInLava()
                && !mc.thePlayer.getIsInWeb();
    }

    private void computeGroundYawOffset() {
        if (mc.thePlayer.onGround) {
            yaw = getYawOffsetFromKeys();
        } else {
            yaw = 0f;
        }
    }

    private void computeAirYawOffset() {
        if (!mc.thePlayer.onGround
                && mc.gameSettings.keyBindForward.isKeyDown()
                && !mc.gameSettings.keyBindLeft.isKeyDown()
                && !mc.gameSettings.keyBindRight.isKeyDown()) {
            yaw = -45f;
        } else {
            yaw = 0f;
        }
    }

    private void computeConstantYawOffset() {
        yaw = getYawOffsetFromKeys();
    }

    private float getYawOffsetFromKeys() {
        KeyBinding forward = mc.gameSettings.keyBindForward;
        KeyBinding back = mc.gameSettings.keyBindBack;
        KeyBinding left = mc.gameSettings.keyBindLeft;
        KeyBinding right = mc.gameSettings.keyBindRight;

        if (forward.isKeyDown() && left.isKeyDown()) return 45f;
        if (forward.isKeyDown() && right.isKeyDown()) return -45f;
        if (back.isKeyDown() && left.isKeyDown()) return 135f;
        if (back.isKeyDown() && right.isKeyDown()) return -135f;
        if (back.isKeyDown()) return 180f;
        if (left.isKeyDown()) return 90f;
        if (right.isKeyDown()) return -90f;
        return 0f;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (isEnabled() && this.mode.getValue() == 0 && event.type() == EventType.PRE) {
            if (canBoost()) {
                if (!mc.thePlayer.onGround) {
                    if (ticks < lowTimerTicks.getValue() && !finished && mc.thePlayer.motionY < 0) {
                        ticks++;
                        mc.timer.timerSpeed = timerBoostMultiplier.getValue();
                        if (ticks == lowTimerTicks.getValue()) {
                            finished = true;
                        }
                    }
                    if (finished) {
                        if (ticks > 0) {
                            ticks--;
                            mc.timer.timerSpeed = 2.0F;
                            if (ticks == 0) {
                                mc.timer.timerSpeed = 1.0F;
                                finished = false;
                            }
                        }
                    }
                } else {
                    finished = false;
                    mc.timer.timerSpeed = 1.0F;
                    ticks = 0;
                }
            } else {
                finished = false;
                mc.timer.timerSpeed = 1.0F;
                ticks = 0;
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (isEnabled() && this.mode.getValue() == 0 && event.getType() == EventType.PRE && rotation.getValue()) {
            if (canBoost() && !Unfair.moduleManager.modules.get(KillAura.class).isEnabled()) {
                switch (yawOffsetMode) {
                    case GROUND:
                        computeGroundYawOffset();
                        break;
                    case AIR:
                        computeAirYawOffset();
                        break;
                    case CONSTANT:
                        computeConstantYawOffset();
                        break;
                }
                event.setRotation(mc.thePlayer.rotationYaw - yaw, mc.thePlayer.rotationPitch, 2);
                event.setPervRotation(mc.thePlayer.rotationYaw - yaw, 2);
            }
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (this.isEnabled() && this.mode.getValue() == 0 && rotation.getValue() && canBoost() && !Unfair.moduleManager.modules.get(KillAura.class).isEnabled()) {
            if (RotationState.isActived() && RotationState.getPriority() == 2.0F && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
        }
    }

    @EventTarget(Priority.LOW)
    public void onStrafe(StrafeEvent event) {
        if (this.mode.getValue() == 1) {
            if (this.isEnabled() && this.canBoost()) {
                if (mc.thePlayer.onGround) {
                    mc.thePlayer.motionY = 0.42F;
                    MoveUtil.setSpeed(
                            MoveUtil.getJumpMotion() * (double) this.multiplier.getValue(),
                            MoveUtil.getMoveYaw()
                    );
                } else {
                    if (this.friction.getValue() != 1.0F) {
                        event.setFriction(event.getFriction() * this.friction.getValue());
                    }
                    if (this.strafe.getValue() > 0) {
                        double speed = MoveUtil.getSpeed();
                        MoveUtil.setSpeed(speed * (double) ((float) (100 - this.strafe.getValue()) / 100.0F), MoveUtil.getDirectionYaw());
                        MoveUtil.addSpeed(
                                speed * (double) ((float) this.strafe.getValue() / 100.0F), MoveUtil.getMoveYaw()
                        );
                        MoveUtil.setSpeed(speed);
                    }
                }
            }
        }
    }

    @Override
    public void onDisabled() {
        finished = false;
        mc.timer.timerSpeed = 1.0F;
        ticks = 0;
    }

    @EventTarget(Priority.LOW)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.mode.getValue() == 1) {
            if (this.isEnabled() && this.canBoost()) {
                mc.thePlayer.movementInput.jump = false;
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getModeString()};
    }

    @Getter
    public enum YawOffsetMode {
        GROUND("Ground"),
        AIR("Air"),
        CONSTANT("Constant");

        private final String tag;

        YawOffsetMode(String tag) {
            this.tag = tag;
        }

    }
}
