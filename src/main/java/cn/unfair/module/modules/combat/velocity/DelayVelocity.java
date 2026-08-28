package cn.unfair.module.modules.combat.velocity;

import cn.unfair.Unfair;
import cn.unfair.enums.DelayModules;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.SubModule;
import cn.unfair.module.modules.combat.AutoBlock;
import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.module.modules.movement.LongJump;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.util.player.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

import static cn.unfair.module.modules.combat.Velocity.isInLiquidOrWeb;

public class DelayVelocity extends SubModule {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty delayTicks = new IntProperty("Delay Ticks", 2, 1, 5);
    private boolean delayActive = false;
    private boolean delayFlag = false;

    public DelayVelocity() {
        super("Delay");
    }

    private boolean canDelay() {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return false;
        }
        AutoBlock autoblock = (AutoBlock) Unfair.moduleManager.getModule(AutoBlock.class);
        KillAura killAura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class);
        return autoblock != null && autoblock.isActive()
                || mc.thePlayer.onGround && (!killAura.isEnabled() || !killAura.shouldAutoBlock());
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (this.isEnabled() && event.getType() == EventType.RECEIVE && !event.isCancelled()) {
            if (event.getPacket() instanceof S12PacketEntityVelocity packet) {
                if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                    LongJump longJump = (LongJump) Unfair.moduleManager.modules.get(LongJump.class);
                    if (!this.delayFlag
                            && !this.canDelay()
                            && !isInLiquidOrWeb()
                            && (!longJump.isEnabled() || !longJump.canStartJump())) {
                        {
                            Unfair.delayManager.setDelayState(true, DelayModules.VELOCITY);
                            Unfair.delayManager.delayedPacket.offer(packet);
                            event.setCancelled(true);
                            this.delayFlag = true;
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (event.getType() == EventType.POST) {
            if (this.delayFlag
                    && (
                    this.canDelay()
                            || isInLiquidOrWeb()
                            || Unfair.delayManager.getDelay() >= (long) this.delayTicks.getValue()
            )) {
                Unfair.delayManager.setDelayState(false, DelayModules.VELOCITY);
                this.delayFlag = false;
            }
            if (this.delayActive) {
                MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                this.delayActive = false;
            }
        }
    }
}
