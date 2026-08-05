package net.minecraft.client.resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.util.concurrent.*;
import lombok.Getter;
import lombok.SneakyThrows;
import net.minecraft.rendering.ResPackPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreenWorking;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.NativeBackedImage;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.resources.data.PackMetadataSection;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.HttpUtil;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.comparator.LastModifiedFileComparator;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class ResourcePackRepository {
    private static final Logger logger = LogManager.getLogger("ResourcePackRepository");
    private static final FileFilter resourcePackFilter = p_accept_1_ -> {
        boolean flag = p_accept_1_.isFile() && p_accept_1_.getName().endsWith(".zip");
        boolean flag1 = p_accept_1_.isDirectory() && (new File(p_accept_1_, "pack.mcmeta")).isFile();
        return flag || flag1;
    };
    @Getter
    private final File dirResourcepacks;
    public final IResourcePack rprDefaultResourcePack;
    private final File dirServerResourcepacks;
    public final IMetadataSerializer rprMetadataSerializer;
    @Getter
    private IResourcePack resourcePackInstance;
    private final ReentrantLock lock = new ReentrantLock();
    private ListenableFuture<Object> downloadingPacks;
    private List<ResourcePackRepository.Entry> repositoryEntriesAll = Lists.newArrayList();
    public List<ResourcePackRepository.Entry> repositoryEntries = Lists.newArrayList();

    public ResourcePackRepository(File dirResourcepacksIn, File dirServerResourcepacksIn, IResourcePack rprDefaultResourcePackIn, IMetadataSerializer rprMetadataSerializerIn, GameSettings settings) {
        this.dirResourcepacks = dirResourcepacksIn;
        this.dirServerResourcepacks = dirServerResourcepacksIn;
        this.rprDefaultResourcePack = rprDefaultResourcePackIn;
        this.rprMetadataSerializer = rprMetadataSerializerIn;
        this.fixDirResourcepacks();
        this.updateRepositoryEntriesAll();
        Iterator<String> iterator = settings.resourcePacks.iterator();

        while (iterator.hasNext()) {
            String s = iterator.next();

            for (ResourcePackRepository.Entry resourcepackrepository$entry : this.repositoryEntriesAll) {
                if (resourcepackrepository$entry.getResourcePackName().equals(s)) {
                    if (resourcepackrepository$entry.getPackFormat() == 1 || settings.incompatibleResourcePacks.contains(resourcepackrepository$entry.getResourcePackName())) {
                        this.repositoryEntries.add(resourcepackrepository$entry);
                        break;
                    }

                    iterator.remove();
                    logger.warn("Removed selected resource pack {} because it's no longer compatible", resourcepackrepository$entry.getResourcePackName());
                }
            }
        }
    }

    private void fixDirResourcepacks() {
        if (this.dirResourcepacks.exists()) {
            if (!this.dirResourcepacks.isDirectory() && (!this.dirResourcepacks.delete() || !this.dirResourcepacks.mkdirs())) {
                logger.warn("Unable to recreate resourcepack folder, it exists but is not a directory: {}", this.dirResourcepacks);
            }
        } else if (!this.dirResourcepacks.mkdirs()) {
            logger.warn("Unable to create resourcepack folder: {}", this.dirResourcepacks);
        }
    }

    private List<File> getResourcePackFiles() {
        return this.dirResourcepacks.isDirectory() ? Arrays.asList(Objects.requireNonNull(this.dirResourcepacks.listFiles(resourcePackFilter))) : Collections.emptyList();
    }

    public void updateRepositoryEntriesAll() {

        final Map<Integer, ResourcePackRepository.Entry> all = new HashMap<>();
        for (ResourcePackRepository.Entry entry : this.getRepositoryEntriesAll()) {
            all.put(entry.hashCode(), entry);
        }

        final Set<ResourcePackRepository.Entry> newSet = new LinkedHashSet<>();
        for (File file : this.getResourcePackFiles()) {
            final ResourcePackRepository.Entry entry = this.new Entry(file);
            final int entryHash = entry.hashCode();
            if (!all.containsKey(entryHash)) {
                try {
                    entry.updateResourcePack();
                    newSet.add(entry);
                } catch (Exception ignored) {
                    newSet.remove(entry);
                }
            } else {
                newSet.add(all.get(entryHash));
            }
        }

        for (ResourcePackRepository.Entry entry : all.values()) {
            if (!newSet.contains(entry)) {
                entry.closeResourcePack();
            }
        }

        this.repositoryEntriesAll = new ArrayList<>(newSet);

//        List<ResourcePackRepository.Entry> list = Lists.newArrayList();
//
//        for (File file1 : this.getResourcePackFiles()) {
//            ResourcePackRepository.Entry resourcepackrepository$entry = new ResourcePackRepository.Entry(file1);
//
//            if (!this.repositoryEntriesAll.contains(resourcepackrepository$entry)) {
//                try {
//                    resourcepackrepository$entry.updateResourcePack();
//                    list.add(resourcepackrepository$entry);
//                } catch (Exception var61) {
//                    list.remove(resourcepackrepository$entry);
//                }
//            } else {
//                int i = this.repositoryEntriesAll.indexOf(resourcepackrepository$entry);
//
//                if (i > -1 && i < this.repositoryEntriesAll.size()) {
//                    list.add(this.repositoryEntriesAll.get(i));
//                }
//            }
//        }
//
//        this.repositoryEntriesAll.removeAll(list);
//
//        for (ResourcePackRepository.Entry resourcepackrepository$entry1 : this.repositoryEntriesAll) {
//            resourcepackrepository$entry1.closeResourcePack();
//        }
//
//        this.repositoryEntriesAll = list;
    }

    public List<ResourcePackRepository.Entry> getRepositoryEntriesAll() {
        return ImmutableList.copyOf(this.repositoryEntriesAll);
    }

    public List<ResourcePackRepository.Entry> getRepositoryEntries() {
        return ImmutableList.copyOf(this.repositoryEntries);
    }

    public void setRepositories(List<ResourcePackRepository.Entry> repositories) {
        this.repositoryEntries.clear();
        this.repositoryEntries.addAll(repositories);
    }

    public ListenableFuture<Object> downloadResourcePack(String url, String hash) {
        String s;

        if (hash.matches("^[a-f0-9]{40}$")) {
            s = hash;
        } else {
            s = "legacy";
        }

        final File file1 = new File(this.dirServerResourcepacks, s);
        this.lock.lock();

        try {
            this.clearResourcePack();

            if (file1.exists() && hash.length() == 40) {
                try {
                    String s1 = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(Files.toByteArray(file1)));

                    if (s1.equals(hash)) {
                        return this.setResourcePackInstance(file1);
                    }

                    logger.warn("File {} had wrong hash (expected {}, found {}). Deleting it.", file1, hash, s1);
                    FileUtils.deleteQuietly(file1);
                } catch (IOException | java.security.GeneralSecurityException ioexception) {
                    logger.warn("File {} couldn't be hashed. Deleting it.", file1, ioexception);
                    FileUtils.deleteQuietly(file1);
                }
            }

            this.deleteOldServerResourcesPacks();
            final GuiScreenWorking guiscreenworking = new GuiScreenWorking();
            Map<String, String> map = Minecraft.getSessionInfo();
            final Minecraft minecraft = Minecraft.getMinecraft();
            Futures.getUnchecked(minecraft.addScheduledTask(() -> minecraft.displayGuiScreen(guiscreenworking)));
            final SettableFuture<Object> settablefuture = SettableFuture.create();
            this.downloadingPacks = HttpUtil.downloadResourcePack(file1, url, map, 52428800, guiscreenworking, minecraft.getProxy());
            Futures.addCallback(this.downloadingPacks, new FutureCallback<>() {
                public void onSuccess(Object p_onSuccess_1_) {
                    ResourcePackRepository.this.setResourcePackInstance(file1);
                    settablefuture.set(null);
                }

                public void onFailure(Throwable p_onFailure_1_) {
                    settablefuture.setException(p_onFailure_1_);
                }
            }, MoreExecutors.directExecutor());
            return this.downloadingPacks;
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Keep only the 10 most recent resources packs, delete the others
     */
    private void deleteOldServerResourcesPacks() {

        if (!this.dirServerResourcepacks.exists()) {
            this.dirServerResourcepacks.mkdirs();
        }

        List<File> list = Lists.newArrayList(FileUtils.listFiles(this.dirServerResourcepacks, TrueFileFilter.TRUE, null));
        list.sort(LastModifiedFileComparator.LASTMODIFIED_REVERSE);
        int i = 0;

        for (File file1 : list) {
            if (i++ >= 10) {
                logger.info("Deleting old server resource pack {}", file1.getName());
                FileUtils.deleteQuietly(file1);
            }
        }
    }

    public ListenableFuture<Object> setResourcePackInstance(File resourceFile) {
        this.resourcePackInstance = new FileResourcePack(resourceFile);
        return Minecraft.getMinecraft().scheduleResourcesRefresh();
    }

    public void clearResourcePack() {
        this.lock.lock();

        try {
            if (this.downloadingPacks != null) {
                this.downloadingPacks.cancel(true);
            }

            this.downloadingPacks = null;

            if (this.resourcePackInstance != null) {
                this.resourcePackInstance = null;
                Minecraft.getMinecraft().scheduleResourcesRefresh();
            }
        } finally {
            this.lock.unlock();
        }
    }

    public class Entry {
        private final File resourcePackFile;
        private IResourcePack reResourcePack;
        private PackMetadataSection rePackMetadataSection;
        private BufferedImage texturePackIcon;
        private ResourceLocation locationTexturePackIcon;

        @Getter
        private final List<ResPackPreview> previewImages = new ArrayList<>();

        @Getter
        private final List<String> resPackInfo = new CopyOnWriteArrayList<>();

        @Getter
        private final AtomicBoolean previewsLoaded = new AtomicBoolean(false);

        public boolean triggeredLoading = false;

        private Entry(File resourcePackFileIn) {
            this.resourcePackFile = resourcePackFileIn;
        }

        public void updateResourcePack() throws IOException {
            this.reResourcePack = this.resourcePackFile.isDirectory() ? new FolderResourcePack(this.resourcePackFile) : new FileResourcePack(this.resourcePackFile);
            this.rePackMetadataSection = this.reResourcePack.getPackMetadata(ResourcePackRepository.this.rprMetadataSerializer, "pack");

            try {
                this.texturePackIcon = this.reResourcePack.getPackImage();
            } catch (IOException var2) {
            }

            if (this.texturePackIcon == null) {
                this.texturePackIcon = ResourcePackRepository.this.rprDefaultResourcePack.getPackImage();
            }

            this.closeResourcePack();
        }

        public void loadPreviewsIfNotLoaded() {

            if (!this.triggeredLoading) {
                this.triggeredLoading = true;

                CompletableFuture.runAsync(() -> {
                    try {
                        this.genPreviewImages();
                        this.detectResPackInfo();
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                    this.closeResourcePack();
                });
            }

        }

        private void detectResPackInfo() {
            this.resPackInfo.add("Animations: " + (this.reResourcePack.hasAnimations() ? EnumChatFormatting.GREEN + "Yes" : EnumChatFormatting.RED + "No"));
            this.resPackInfo.add("Lightmap: " + (this.hasLightMap() ? EnumChatFormatting.GREEN + "Yes" : EnumChatFormatting.RED + "No"));
            this.resPackInfo.add("Sounds: " + (this.reResourcePack.hasSounds() ? EnumChatFormatting.GREEN + "Yes" : EnumChatFormatting.RED + "No"));
        }

        private boolean hasLightMap() {
            boolean b = this.reResourcePack.resourceExists(ResourceLocation.of("mcpatcher/lightmap/world0.png"));
            boolean b1 = this.reResourcePack.resourceExists(ResourceLocation.of("mcpatcher/lightmap/world1.png"));
            boolean b2 = this.reResourcePack.resourceExists(ResourceLocation.of("mcpatcher/lightmap/world-1.png"));

            return b || b1 || b2;
        }

        /**
         * 获取资源包中的图片
         * @param loc 位置
         * @return 图片, 是否从vanilla资源包中加载
         */
        @SneakyThrows
        private Tuple<NativeBackedImage, Boolean> getTextureImg(ResourceLocation loc) {
            InputStream is = null;

            try {
                is = this.reResourcePack.getInputStream(loc);
            } catch (IOException e) {
            }

            boolean fromDefault = false;

            if (is == null) {
                is = ResourcePackRepository.this.rprDefaultResourcePack.getInputStream(loc);
                fromDefault = true;
            }

            assert is != null;

            return Tuple.of(NativeBackedImage.make(is), fromDefault);
        }

        private void genPreviewImages() {
            List<String> listTextures = Arrays.asList(
                    "textures/blocks/stone.png",
                    "textures/blocks/cobblestone.png",
                    "textures/blocks/log_oak.png",
                    "textures/blocks/planks_oak.png",
                    "textures/blocks/diamond_ore.png",
                    "textures/items/bucket_water.png",
                    "textures/items/bucket_lava.png",
                    "textures/items/apple_golden.png",
                    "textures/items/ender_pearl.png",
                    "textures/items/bow_standby.png",
                    "textures/items/diamond_sword.png",
                    "textures/items/iron_sword.png",
                    "textures/items/diamond_pickaxe.png",
                    "textures/items/iron_pickaxe.png",
                    "textures/items/diamond_chestplate.png",
                    "textures/items/iron_chestplate.png"
            );

            int maxSize = -1;

            for (String listTexture : listTextures) {
                Tuple<NativeBackedImage, Boolean> tuple = this.getTextureImg(ResourceLocation.of(listTexture));
                NativeBackedImage img = tuple.getA();

                boolean loadedFromDefaultRespack = tuple.getB();
                if (!loadedFromDefaultRespack) {
                    if (img.getWidth() == img.getHeight())
                        maxSize = Math.max(maxSize, img.getWidth());

                }
                this.previewImages.add(new ResPackPreview(reResourcePack, img, listTexture));
            }

            if (maxSize != -1)
                this.resPackInfo.add("Res: " + EnumChatFormatting.GREEN + maxSize + "x");
            else
                this.resPackInfo.add("Res: " + EnumChatFormatting.YELLOW + "Unknown");

            previewsLoaded.set(true);
        }

        @SneakyThrows
        public void bindTexturePackIcon(TextureManager textureManagerIn) {
            if (this.locationTexturePackIcon == null) {
                this.locationTexturePackIcon = textureManagerIn.getDynamicTextureLocation("texturepackicon", new DynamicTexture(this.texturePackIcon));
                if (this.texturePackIcon instanceof AutoCloseable)
                    ((AutoCloseable) this.texturePackIcon).close();
            }

            textureManagerIn.bindTexture(this.locationTexturePackIcon);
        }

        public void closeResourcePack() {
            if (this.reResourcePack instanceof Closeable) {
                IOUtils.closeQuietly((Closeable) this.reResourcePack);
            }
        }

        public void cleanUp() {
            this.previewImages.forEach(ResPackPreview::cleanUp);
        }

        public IResourcePack getResourcePack() {
            return this.reResourcePack;
        }

        public String getResourcePackName() {
            return this.reResourcePack.getPackName();
        }

        public String getTexturePackDescription() {
            return this.rePackMetadataSection == null ? EnumChatFormatting.RED + "Invalid pack.mcmeta (or missing 'pack' section)" : this.rePackMetadataSection.getPackDescription().getFormattedText();
        }

        public int getPackFormat() {
            return this.rePackMetadataSection.getPackFormat();
        }

        public boolean equals(Object p_equals_1_) {
            return this == p_equals_1_ || (p_equals_1_ instanceof Entry && this.toString().equals(p_equals_1_.toString()));
        }

        public int hashCode() {
            return this.toString().hashCode();
        }

        public String toString() {
            return String.format("%s:%s:%d", this.resourcePackFile.getName(), this.resourcePackFile.isDirectory() ? "folder" : "zip", this.resourcePackFile.lastModified());
        }
    }
}
