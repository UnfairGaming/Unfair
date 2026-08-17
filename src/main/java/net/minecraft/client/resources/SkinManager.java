package net.minecraft.client.resources;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.InsecureTextureException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IImageBuffer;
import net.minecraft.client.renderer.ImageBufferDownload;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SkinManager {
    private static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(0, 2, 1L, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>());
    private static final PublicKey YGGDRASIL_PUBLIC_KEY = loadYggdrasilPublicKey();
    private final TextureManager textureManager;
    private final File skinCacheDir;
    private final LoadingCache<GameProfile, Map<Type, MinecraftProfileTexture>> skinCacheLoader;

    private final Map<ResourceLocation, BufferedImage> imgData = new HashMap<>();

    public SkinManager(TextureManager textureManagerInstance, File skinCacheDirectory) {
        this.textureManager = textureManagerInstance;
        this.skinCacheDir = skinCacheDirectory;
        this.skinCacheLoader = CacheBuilder.newBuilder().expireAfterAccess(15L, TimeUnit.SECONDS).build(new CacheLoader<GameProfile, Map<Type, MinecraftProfileTexture>>() {
            public Map<Type, MinecraftProfileTexture> load(GameProfile p_load_1_) throws Exception {
                return Minecraft.getMinecraft().getSessionService().getTextures(p_load_1_, false);
            }
        });
    }

    private static boolean hasValidSecureTextures(GameProfile profile) {
        if (YGGDRASIL_PUBLIC_KEY == null) {
            return true;
        }

        for (Property property : profile.getProperties().get("textures")) {
            if (!property.hasSignature()) {
                return false;
            }

            try {
                byte[] signature = Base64.getDecoder().decode(property.getSignature());
                int expectedLength = (((RSAPublicKey) YGGDRASIL_PUBLIC_KEY).getModulus().bitLength() + 7) >> 3;
                return signature.length == expectedLength && property.isSignatureValid(YGGDRASIL_PUBLIC_KEY);
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        return false;
    }

    private static PublicKey loadYggdrasilPublicKey() {
        try (InputStream stream = SkinManager.class.getResourceAsStream("/yggdrasil_session_pubkey.der")) {
            if (stream == null) {
                return null;
            }
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(stream.readAllBytes()));
        } catch (IOException | GeneralSecurityException exception) {
            return null;
        }
    }

    /**
     * Used in the Skull renderer to fetch a skin. May download the skin if it's not in the cache
     */
    public ResourceLocation loadSkin(MinecraftProfileTexture profileTexture, Type p_152792_2_) {
        return this.loadSkin(profileTexture, p_152792_2_, null);
    }

    /**
     * May download the skin if its not in the cache, can be passed a SkinManager#SkinAvailableCallback for handling
     */
    public ResourceLocation loadSkin(final MinecraftProfileTexture profileTexture, final Type p_152789_2_, final SkinManager.SkinAvailableCallback skinAvailableCallback) {

        String url = profileTexture.getUrl();

        final ResourceLocation resourcelocation = ResourceLocation.of("skins/" + url);
        ITextureObject itextureobject = this.textureManager.getTexture(resourcelocation);

        if (itextureobject != null) {
            if (skinAvailableCallback != null) {
                skinAvailableCallback.skinAvailable(p_152789_2_, resourcelocation, profileTexture, imgData.get(resourcelocation));
            }
        } else {

            String hash = url.substring(38);

            File file1 = new File(this.skinCacheDir, hash.length() > 2 ? hash.substring(0, 2) : "xx");
            File file2 = new File(file1, hash);
            final IImageBuffer iimagebuffer = p_152789_2_ == Type.SKIN ? new ImageBufferDownload() : null;
            ThreadDownloadImageData threaddownloadimagedata = new ThreadDownloadImageData(file2, url, DefaultPlayerSkin.getDefaultSkinLegacy(), new IImageBuffer() {

                private BufferedImage img;

                public BufferedImage parseUserSkin(BufferedImage image) {
                    if (iimagebuffer != null) {
                        image = iimagebuffer.parseUserSkin(image);
                        img = image;
                    }

                    return image;
                }

                public void skinAvailable() {
                    if (iimagebuffer != null) {
                        iimagebuffer.skinAvailable();
                    }

                    if (skinAvailableCallback != null) {
                        imgData.put(resourcelocation, img);
                        skinAvailableCallback.skinAvailable(p_152789_2_, resourcelocation, profileTexture, img);
                    }
                }
            });
            this.textureManager.loadTexture(resourcelocation, threaddownloadimagedata);
        }

        return resourcelocation;
    }

    public void loadProfileTextures(final GameProfile profile, final SkinManager.SkinAvailableCallback skinAvailableCallback, final boolean requireSecure) {
        THREAD_POOL.submit(() -> {
            final Map<Type, MinecraftProfileTexture> map = Maps.newHashMap();

            try {
                if (!requireSecure || hasValidSecureTextures(profile)) {
                    map.putAll(Minecraft.getMinecraft().getSessionService().getTextures(profile, requireSecure));
                }
            } catch (InsecureTextureException ignored) {
            }

            if (map.isEmpty() && profile.getId().equals(Minecraft.getMinecraft().getSession().getProfile().getId())) {
                profile.getProperties().clear();
                profile.getProperties().putAll(Minecraft.getMinecraft().getProfileProperties());
                map.putAll(Minecraft.getMinecraft().getSessionService().getTextures(profile, false));
            }

            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (map.containsKey(Type.SKIN)) {
                    SkinManager.this.loadSkin(map.get(Type.SKIN), Type.SKIN, skinAvailableCallback);
                }

                if (map.containsKey(Type.CAPE)) {
                    SkinManager.this.loadSkin(map.get(Type.CAPE), Type.CAPE, skinAvailableCallback);
                }
            });
        });
    }

    public Map<Type, MinecraftProfileTexture> loadSkinFromCache(GameProfile profile) {
        return this.skinCacheLoader.getUnchecked(profile);
    }

    public interface SkinAvailableCallback {
        void skinAvailable(Type p_180521_1_, ResourceLocation location, MinecraftProfileTexture profileTexture, BufferedImage img);
    }
}
