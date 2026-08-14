package cn.unfair.module.modules.misc;

import cn.unfair.event.EventTarget;
import cn.unfair.events.Render2DEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.TextProperty;
import cn.unfair.util.TimerUtil;
import net.minecraft.client.Minecraft;

public class Spammer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final TextProperty text = new TextProperty("Text", "meow");
    public final FloatProperty delay = new FloatProperty("Delay", 3.5F, 0.0F, 3600.0F);
    public final IntProperty random = new IntProperty("Random", 0, 0, 10);
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
