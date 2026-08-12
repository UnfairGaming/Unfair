package cn.unfair.module.modules.movement;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.util.ItemUtil;
import cn.unfair.util.MoveUtil;
import cn.unfair.util.PlayerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.input.Keyboard;

import java.util.Objects;

public class Eagle extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty minDelay = new IntProperty("min-delay", 2, 0, 10);
    public final IntProperty maxDelay = new IntProperty("max-delay", 3, 0, 10);
    public final BooleanProperty directionCheck = new BooleanProperty("direction-check", true);
    public final BooleanProperty pitchCheck = new BooleanProperty("pitch-check", true);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true);
    public final BooleanProperty sneakOnly = new BooleanProperty("sneaking-only", false);
    private int sneakDelay = 0;
    private boolean pressingSneak = false;

    public Eagle() {
        super("Eagle", false);
    }

    private boolean canMoveSafely() {
        double[] offset = MoveUtil.predictMovement();
        return PlayerUtil.canMove(mc.thePlayer.motionX + offset[0], mc.thePlayer.motionZ + offset[1]);
    }

    private boolean shouldSneak() {
        if (this.directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) {
            return false;
        } else if (this.pitchCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) {
            return false;
        } else if (sneakOnly.getValue() && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
            return false;
        } else {
            return (!this.blocksOnly.getValue() || ItemUtil.isHoldingBlock()) && mc.thePlayer.onGround;
        }
    }

    private void setSneakKey(boolean pressed) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), pressed);
        this.pressingSneak = pressed;
    }

    private void restoreSneakKey() {
        this.setSneakKey(Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()));
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.type() == EventType.PRE) {
            if (this.sneakDelay > 0) {
                this.sneakDelay--;
            }
            if (this.sneakDelay == 0 && this.canMoveSafely()) {
                this.sneakDelay = RandomUtils.nextInt(this.minDelay.getValue(), this.maxDelay.getValue() + 1);
            }

            boolean shouldPressSneak = mc.currentScreen == null
                    && this.shouldSneak()
                    && (this.sneakDelay > 0 || this.canMoveSafely());
            if (shouldPressSneak) {
                this.setSneakKey(true);
            } else if (this.pressingSneak) {
                this.restoreSneakKey();
            }
        } else if (this.pressingSneak) {
            this.restoreSneakKey();
        }
    }

    @Override
    public void onDisabled() {
        this.sneakDelay = 0;
        if (this.pressingSneak) {
            this.restoreSneakKey();
        }
    }

    @Override
    public void verifyValue(String name) {
        switch (name) {
            case "min-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.maxDelay.setValue(this.minDelay.getValue());
                }
                break;
            case "max-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.minDelay.setValue(this.maxDelay.getValue());
                }
        }
    }

    @Override
    public String[] getSuffix() {
        return Objects.equals(this.minDelay.getValue(), this.maxDelay.getValue())
                ? new String[]{this.minDelay.getValue().toString()}
                : new String[]{String.format("%d-%d", this.minDelay.getValue(), this.maxDelay.getValue())};
    }
}
