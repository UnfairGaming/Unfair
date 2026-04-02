package unfair.management;

import net.minecraft.client.Minecraft;
import unfair.enums.FloatModules;
import unfair.event.EventTarget;
import unfair.events.PlayerUpdateEvent;

import java.util.LinkedHashMap;

public class FloatManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final LinkedHashMap<FloatModules, Boolean> activeMap;
    private boolean floating;

    public FloatManager() {
        this.activeMap = new LinkedHashMap<>();
        this.floating = false;
    }

    public boolean isPredicted() {
        return this.floating;
    }

    public boolean isFalling() {
        return mc.thePlayer.onGround && mc.thePlayer.posY - mc.thePlayer.lastTickPosY < 0.0 && mc.thePlayer.motionY < 0.0;
    }

    public boolean hasActiveModule() {
        return this.activeMap.containsValue(true);
    }

    public void setFloatState(boolean state, FloatModules floatModules) {
        this.activeMap.put(floatModules, state);
    }

    @EventTarget
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if ((this.hasActiveModule() || this.isPredicted()) && this.isFalling()) {
            mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + 0.001, mc.thePlayer.posZ);
            this.floating = true;
        } else {
            this.floating = false;
        }
    }
}
