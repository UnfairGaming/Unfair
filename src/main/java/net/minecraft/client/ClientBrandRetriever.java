package net.minecraft.client;

import cn.unfair.Unfair;
import cn.unfair.module.modules.misc.ClientSpoofer;

public class ClientBrandRetriever {
    public static String getClientModName() {
        if (Unfair.moduleManager != null) {
            ClientSpoofer clientSpoofer = (ClientSpoofer) Unfair.moduleManager.getModule(ClientSpoofer.class);
            if (clientSpoofer != null && clientSpoofer.isEnabled()) {
                return clientSpoofer.getClientBrand();
            }
        }
        return "vanilla";
    }
}
