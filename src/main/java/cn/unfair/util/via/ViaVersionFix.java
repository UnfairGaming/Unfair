package cn.unfair.util.via;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import de.florianmichael.viamcp.ViaMCP;
import net.minecraft.client.Minecraft;

public class ViaVersionFix {
    public static int sequence() {
        if (ModernOffhandInteraction.isModernTarget()) {
            UserConnection connection = connection();
            if (connection != null) {
                return sequence(connection);
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

    public static int sequence(UserConnection connection) {
        ModernSequenceStorage storage = connection.get(ModernSequenceStorage.class);
        if (storage == null) {
            storage = new ModernSequenceStorage();
            connection.put(storage);
        }
        return storage.next();
    }

    public static UserConnection connection() {
        if (ViaMCP.INSTANCE != null && ViaMCP.INSTANCE.user != null) {
            return ViaMCP.INSTANCE.user;
        }
        if (Via.getManager() == null || Via.getManager().getConnectionManager() == null) {
            return null;
        }
        return Via.getManager().getConnectionManager().getConnections().stream()
                .filter(connection -> connection.getChannel() != null && connection.getChannel().isActive())
                .findFirst()
                .orElse(null);
    }
}
