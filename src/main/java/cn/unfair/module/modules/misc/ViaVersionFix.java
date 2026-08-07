package cn.unfair.module.modules.misc;

import cn.unfair.util.via.BlockStatePredictionHandler;
import net.minecraft.client.Minecraft;

public class ViaVersionFix {
    public static int sequence() {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null && mc.theWorld.predictionHandler != null) {
            try (BlockStatePredictionHandler handler = mc.theWorld.predictionHandler.startPredicting()) {
                return handler.getCurrentSequence();
            }
        }
        return 0;
    }
}
