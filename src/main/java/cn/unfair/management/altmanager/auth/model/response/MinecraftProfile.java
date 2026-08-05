package cn.unfair.management.altmanager.auth.model.response;

public record MinecraftProfile(String id, String name, MinecraftSkin[] skins) {

    public record MinecraftSkin(String id, String state, String url, String variant, String alias) {
    }
}
