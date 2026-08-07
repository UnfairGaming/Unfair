package cn.unfair.util.via;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import net.minecraft.client.Minecraft;

public class ViaProtocol {
    private static boolean notIsSinglePlayer() {
        return !Minecraft.getMinecraft().isSingleplayer();
    }

    public static boolean newerThan1_8() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().newerThan(ProtocolVersion.v1_8);
    }

    public static boolean olderThanTo1_13() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().olderThan(ProtocolVersion.v1_13);
    }

    public static boolean olderThanOrEqualsTo1_8() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8);
    }

    public static boolean olderThanOrEqualsTo1_13_2() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2);
    }

    public static boolean newerThanOrEqualTo1_9() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_9);
    }

    public static boolean newerThanOrEqualTo1_13() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_13);
    }

    public static boolean newerThanOrEqualTo1_14() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_14);
    }

    public static boolean newerThanOrEqualTo1_15() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_15);
    }

    public static boolean newerThanOrEqualTo1_16() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_16);
    }

    public static boolean newerThanOrEqualTo1_17() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_17);
    }

    public static boolean newerThanOrEqualTo1_18() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_18);
    }

    public static boolean newerThanOrEqualTo1_19() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_19);
    }

    public static boolean newerThanOrEqualTo1_20() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_20);
    }

    public static boolean newerThanOrEqualTo1_20_2() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_20_2);
    }

    public static boolean newerThanOrEqualTo1_21() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_21);
    }

    public static boolean newerThanOrEqualTo1_21_2() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_21_2);
    }

    public static boolean newerThanOrEqualTo1_21_5() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_21_5);
    }

    public static boolean olderThanOrEqualTo1_20_2() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_2);
    }

    public static boolean olderThanOrEqualTo1_20_5() {
        return notIsSinglePlayer() && ViaLoadingBase.getInstance().getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_5);
    }
}
