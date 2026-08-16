package cn.unfair.module.modules.movement;

import cn.unfair.Unfair;
import cn.unfair.enums.BlinkModules;
import cn.unfair.enums.DelayModules;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.*;
import cn.unfair.module.Module;
import cn.unfair.module.modules.player.Scaffold;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.player.HeypixelStuckController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class Stuck extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Vanilla", "Heypixel"});

    public final IntProperty stuckTicks = new IntProperty("Stuck Ticks", 10, 1, 100, ()-> this.mode.getValue() == 0);
    private double savedMotionX;
    private double savedMotionY;
    private double savedMotionZ;
    private int tick;
    private boolean using = false;

    private final HeypixelStuckController heypixel = new HeypixelStuckController();

    public Stuck() {
        super("Stuck", false, false);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (mc.thePlayer != null) {
            if (enabled) {
                super.setEnabled(true);
            } else if (this.mode.getModeString().equals("Heypixel")) {
                if (this.heypixel.canDisable()) {
                    super.setEnabled(false);
                } else {
                    this.heypixel.requestDisable();
                }
            } else {
                super.setEnabled(false);
            }
        }
    }

    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) {
            if (this.mode.getModeString().equals("Heypixel")) {
                Scaffold scaffold = (Scaffold) Unfair.moduleManager.getModule(Scaffold.class);
                if (scaffold != null && scaffold.isEnabled()) {
                    scaffold.releaseClutchForHeypixelStuck();
                    scaffold.toggle();
                }
                this.heypixel.start();
            } else {
                tick = 0;
                using = true;
                savedMotionX = mc.thePlayer.motionX;
                savedMotionY = mc.thePlayer.motionY;
                savedMotionZ = mc.thePlayer.motionZ;
            }
        }
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null && this.mode.getModeString().equals("Vanilla")) {
            using = false;
            Unfair.blinkManager.setBlinkState(false, BlinkModules.BLINK);
            mc.thePlayer.motionX = savedMotionX;
            mc.thePlayer.motionZ = savedMotionZ;
            mc.thePlayer.motionY = savedMotionY;
            Unfair.delayManager.setDelayState(false, DelayModules.VELOCITY);
            mc.timer.timerSpeed = 1.0F;
        }
    }


    @EventTarget(1)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;

        if (this.mode.getModeString().equals("Heypixel")) {
            if (this.heypixel.handlePacket(event)) {
                this.setEnabled(false);
            }
        } else {
            handleVanillaPacket(event);
        }
    }

    private void handleVanillaPacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE) {
            if (event.getPacket() instanceof S12PacketEntityVelocity s12PacketEntityVelocity) {
                if (s12PacketEntityVelocity.getEntityID() == mc.thePlayer.getEntityId()) {
                    Unfair.delayManager.setDelayState(true, DelayModules.VELOCITY);
                    tick = this.stuckTicks.getValue();
                    Unfair.delayManager.delayedPacket.offer(s12PacketEntityVelocity);
                    event.setCancelled(true);
                }
            }
        }
    }


    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) return;

        if (this.mode.getModeString().equals("Heypixel")) {
            this.heypixel.update(event);
        } else {
            Unfair.blinkManager.setBlinkState(true, BlinkModules.BLINK);
            KeyBinding.unPressAllKeys();
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionZ = 0.0;
            mc.thePlayer.motionY = 0.0;
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.mode.getModeString().equals("Heypixel")) return;

        if (using && event.type() == EventType.PRE) {
            int ticks = this.stuckTicks.getValue();
            if (tick == ticks) {
                this.setEnabled(false);
                using = true;
            }
            if (tick == ticks + 1) {
                this.setEnabled(true);
                tick = 0;
            }
            tick++;
        }
    }


    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled()) return;

        if (this.mode.getModeString().equals("Heypixel")) {
            this.heypixel.stopMovementInput();
        } else {
            mc.thePlayer.movementInput.moveForward = 0.0f;
            mc.thePlayer.movementInput.moveStrafe = 0.0f;
            mc.thePlayer.movementInput.jump = false;
            mc.thePlayer.movementInput.sneak = false;
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        if (this.isEnabled() && this.mode.getModeString().equals("Heypixel")) {
            this.heypixel.reset();
            super.setEnabled(false);
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!this.isEnabled()) return;
        if (this.mode.getModeString().equals("Heypixel")) return;

        mc.thePlayer.motionX = 0.0;
        mc.thePlayer.motionY = 0.0;
        mc.thePlayer.motionZ = 0.0;
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!this.isEnabled()) return;
        if (this.mode.getModeString().equals("Heypixel")) return;

        event.setForward(0.0f);
        event.setStrafe(0.0f);
    }
}
