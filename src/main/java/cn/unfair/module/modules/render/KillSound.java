package cn.unfair.module.modules.render;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.AttackEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.util.CodecMp3;
import cn.unfair.util.SoundUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.util.Random;

public class KillSound extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ResourceLocation KILL_SOUND_1 = new ResourceLocation("minecraft:unfair/sound/killsound1.mp3");
    private static final ResourceLocation KILL_SOUND_2 = new ResourceLocation("minecraft:unfair/sound/killsound2.mp3");
    private final Random random = new Random();
    private EntityPlayer target;

    public KillSound() {
        super("KillSound", false);
        CodecMp3.register();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        if (event.getTarget() instanceof EntityPlayer player && player != mc.thePlayer) {
            this.target = player;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (this.target == null || mc.theWorld == null) {
            return;
        }
        if (target.isDead) {
            SoundUtil.playSound(this.random.nextBoolean() ? KILL_SOUND_1 : KILL_SOUND_2);
            this.target = null;
        }
    }

    @Override
    public void onDisabled() {
        this.target = null;
    }
}
