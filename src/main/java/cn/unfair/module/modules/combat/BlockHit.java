package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.AttackEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.*;
import cn.unfair.util.ItemUtil;
import cn.unfair.util.KeyBindUtil;
import cn.unfair.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

public class BlockHit extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Helper", "Auto", "Lag"});
    private final IntProperty stopTime = new IntProperty("Stop Ticks", 2, 1, 5, () -> this.mode.getValue() == 0);
    private final ModeProperty autoBlockTime = new ModeProperty("Auto Block Time", 0, new String[]{"Delay", "HurtTime", "Sag"}, () -> this.mode.getValue() == 1);
    private final ModeProperty autoMode = new ModeProperty("Auto Mode", 0, new String[]{"Spam", "Hold"}, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 0);
    private final IntProperty holdTick = new IntProperty("Hold Tick", 2, 2, 5, () -> this.mode.getValue() == 1 && this.autoMode.getValue() == 1 && this.autoBlockTime.getValue() == 0);
    private final IntProperty blockDelay = new IntProperty("Block Delay", 100, 0, 1000, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 0);
    private final IntProperty minHurtTime = new IntProperty("Min Hurt Time", 10, 1, 10, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 1);
    private final IntProperty maxHurtTime = new IntProperty("Max Hurt Time", 10, 1, 10, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 1);
    private final IntProperty delayPacketTick = new IntProperty("Delay Packet Tick", 2, 1, 10, () -> this.mode.getValue() == 2);
    private final IntProperty blockTick = new IntProperty("Block Tick", 3, 1, 5, () -> this.mode.getValue() == 2);
    private final PercentProperty chance = new PercentProperty("Block Hit Chance", 50, () -> this.mode.getValue() == 1);
    private final BooleanProperty smart = new BooleanProperty("Smart", true, () -> this.mode.getValue() == 1);
    private final BooleanProperty autoBlockRange = new BooleanProperty("Auto Block Range", true, () -> this.mode.getValue() == 1);
    private final FloatProperty range = new FloatProperty("Range", 3.0f, 1f, 4f, () -> autoBlockRange.getValue() && mode.getValue() == 1);
    private final TimerUtil timer = new TimerUtil();
    private int holdTicks, stopTick;

    private boolean startBlocking;
    private boolean attacking;
    private int attackTicks;
    private int sagTicks = 0;
    private int blockTicks = 0;
    private EntityLivingBase target;

    public BlockHit() {
        super("BlockHit", false, false);
    }

    @Override
    public void onDisabled() {
        Unfair.lagManager.setDelay(0);
    }


    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (event.type() == EventType.PRE) {
            if (this.mode.getValue() == 0) {
                if (mc.gameSettings.keyBindAttack.isKeyDown()) {
                    if (mc.thePlayer.isBlocking()) {
                        startBlocking = true;
                        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                    }
                }
                if (startBlocking) stopTick++;
                if (stopTick == 2) {
                    KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());
                }
                if (stopTick > stopTime.getValue()) {
                    KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                    startBlocking = false;
                    stopTick = 0;
                }
            }
            if (this.mode.getValue() == 1) {
                if (target == null) return;
                if (attacking) {
                    attackTicks++;
                }
                if (attackTicks > 5) {
                    reset();
                    target = null;
                    return;
                }
                if (Math.random() > chance.getValue()) {
                    reset();
                    return;
                }
                if (autoBlockRange.getValue() && mc.thePlayer.getDistanceToEntity(target) >= range.getValue()) {
                    reset();
                    return;
                }
                if (smart.getValue() && target.hurtTime >= 8 && target.hurtTime <= 10) {
                    reset();
                    return;
                }
                if (attacking) {
                    if (autoBlockTime.getValue() == 0) {
                        if (timer.hasTimeElapsed(blockDelay.getValue().longValue())) {
                            if (this.autoMode.getValue() == 0) {
                                KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindUseItem.getKeyCode());
                                timer.reset();
                                reset();
                            }
                            if (this.autoMode.getValue() == 1) {
                                startBlocking = true;
                            }
                            if (startBlocking) {
                                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                                holdTicks++;
                            }
                            if (holdTicks > holdTick.getValue()) {
                                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                                startBlocking = false;
                                holdTicks = 0;
                                timer.reset();
                            }
                        }
                    }
                    if (autoBlockTime.getValue() == 1) {
                        if (mc.thePlayer.hurtTime >= minHurtTime.getValue() && mc.thePlayer.hurtTime <= maxHurtTime.getValue()) {
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                            startBlocking = true;
                        } else if (startBlocking) {
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                            startBlocking = false;
                        }
                    }
                    if (autoBlockTime.getValue() == 2) {
                        if (sagTicks < 10) {
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                            sagTicks++;
                        }
                        if (sagTicks >= 10) {
                            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                            sagTicks = 0;
                        }
                    }
                }
            }
            if (this.mode.getValue() == 2) {
                if (mc.thePlayer.hurtTime == 10) {
                    blockTicks = 1;
                }
                Unfair.lagManager.setDelay(delayPacketTick.getValue());
                if (blockTicks >= 1) {
                    KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                    blockTicks++;
                }
                if (blockTicks > blockTick.getValue()) {
                    KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                    Unfair.lagManager.setDelay(0);
                    blockTicks = 0;
                }
            } else Unfair.lagManager.setDelay(0);
        }
    }

    private void reset() {
        attacking = false;
        KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        holdTicks = sagTicks = 0;
        timer.reset();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (this.isEnabled() && ItemUtil.isHoldingSword()) {
            attacking = true;
            attackTicks = 0;
            target = (EntityLivingBase) event.getTarget();
        }
    }

    @Override
    public void verifyValue(String name) {
        if (this.minHurtTime.getName().equals(name)
                && this.minHurtTime.getValue() > this.maxHurtTime.getValue()) {
            this.maxHurtTime.setValue(this.minHurtTime.getValue());
        } else if (this.maxHurtTime.getName().equals(name)
                && this.minHurtTime.getValue() > this.maxHurtTime.getValue()) {
            this.minHurtTime.setValue(this.maxHurtTime.getValue());
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
