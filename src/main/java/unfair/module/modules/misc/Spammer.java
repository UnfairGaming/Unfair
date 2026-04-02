package unfair.module.modules.misc;

import net.minecraft.client.Minecraft;
import unfair.event.EventTarget;
import unfair.events.Render2DEvent;
import unfair.module.Module;
import unfair.property.properties.FloatProperty;
import unfair.property.properties.IntProperty;
import unfair.property.properties.TextProperty;
import unfair.util.TimerUtil;

public class Spammer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final TextProperty text = new TextProperty("text", "meow");
    public final FloatProperty delay = new FloatProperty("delay", 3.5F, 0.0F, 3600.0F);
    public final IntProperty random = new IntProperty("random", 0, 0, 10);
    private final TimerUtil timer = new TimerUtil();
    private int charOffset = 19968;

    public Spammer() {
        super("Spammer", false);
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.isEnabled()) {
            if (this.timer.hasTimeElapsed((long) (this.delay.getValue() * 1000.0F))) {
                this.timer.reset();
                String text = this.text.getValue();
                if (this.random.getValue() > 0) {
                    text = String.format("%s ", text);
                    for (int i = 0; i < this.random.getValue(); i++) {
                        text = String.format("%s%s", text, (char) this.charOffset);
                        this.charOffset++;
                        if (this.charOffset > 40959) {
                            this.charOffset = 19968;
                        }
                    }
                }
                mc.thePlayer.sendChatMessage(text);
            }
        }
    }
}
