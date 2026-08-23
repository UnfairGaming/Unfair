package cn.unfair.module.modules.render;

import cn.unfair.event.EventTarget;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.util.client.KeyBindUtil;
import net.minecraft.client.Minecraft;

public class FreeLook extends Module {
    public static FreeLook INSTANCE;
    public final BooleanProperty autoF5 = new BooleanProperty("Auto F5", true);
    public boolean active = false;
    public float cameraYaw;
    public float cameraPitch;
    public float prevCameraYaw;
    public float prevCameraPitch;
    private int prevPerspective = 0;

    public FreeLook() {
        super("FreeLook", false, true);
        INSTANCE = this;
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.active = false;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) {
            if (this.active) {
                this.active = false;
                Minecraft.getMinecraft().gameSettings.thirdPersonView = this.prevPerspective;
            }

        } else {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                this.prevCameraYaw = this.cameraYaw;
                this.prevCameraPitch = this.cameraPitch;
                boolean isKeyDown = KeyBindUtil.isKeyDown(this.getKey()) && mc.currentScreen == null;
                if (isKeyDown) {
                    if (!this.active) {
                        this.active = true;
                        this.prevPerspective = mc.gameSettings.thirdPersonView;
                        if (this.autoF5.getValue()) {
                            mc.gameSettings.thirdPersonView = 1;
                        }

                        this.cameraYaw = mc.thePlayer.rotationYaw;
                        this.cameraPitch = mc.thePlayer.rotationPitch;
                        this.prevCameraYaw = this.cameraYaw;
                        this.prevCameraPitch = this.cameraPitch;
                    }
                } else if (this.active) {
                    this.active = false;
                    mc.gameSettings.thirdPersonView = this.prevPerspective;
                }

            }
        }
    }

    public void onDisabled() {
        if (this.active) {
            this.active = false;
            Minecraft.getMinecraft().gameSettings.thirdPersonView = this.prevPerspective;
        }

    }

    public boolean isActive() {
        return this.isEnabled() && this.active;
    }
}
