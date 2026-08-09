package cn.unfair.util.via;

import com.viaversion.viaversion.api.connection.UserConnection;
import de.florianmichael.viamcp.ViaMCP;
import net.minecraft.client.Minecraft;

public class ViaVersionFix {
    public static int sequence() {
        if (ModernOffhandInteraction.isModernTarget()) {
            UserConnection connection = ViaMCP.INSTANCE != null ? ViaMCP.INSTANCE.user : null;
            if (connection != null) {
                ModernSequenceStorage storage = connection.get(ModernSequenceStorage.class);
                if (storage == null) {
                    storage = new ModernSequenceStorage();
                    connection.put(storage);
                }
                return storage.next();
            }
        }

        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null && mc.theWorld.predictionHandler != null) {
            try (BlockStatePredictionHandler handler = mc.theWorld.predictionHandler.startPredicting()) {
                return handler.getCurrentSequence();
            }
        }
        return 0;
    }
}
