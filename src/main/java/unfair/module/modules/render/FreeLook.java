package unfair.module.modules.render;

import net.minecraft.client.Minecraft;
import unfair.event.EventTarget;
import unfair.events.TickEvent;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.property.properties.IntProperty;
import unfair.util.KeyBindUtil;

public class FreeLook extends Module {
    public static FreeLook INSTANCE;
    public final BooleanProperty autoF5 = new BooleanProperty("AutoF5", true);
    public final IntProperty keyBind = new IntProperty("Key", 56, 0, 1000);
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
                boolean isKeyDown = KeyBindUtil.isKeyDown(this.keyBind.getValue()) && mc.currentScreen == null;
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