package net.minecraft.client.resources;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.SneakyThrows;
import net.minecraft.rendering.AnimatedTexture;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Cleaner;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FileResourcePack extends AbstractResourcePack implements Closeable {
    private static final Cleaner CLEANER = Cleaner.create();
    public static final Splitter entryNameSplitter = Splitter.on('/').omitEmptyStrings().limit(3);
    private final ZipFileState zipFileState = new ZipFileState();
    private final Cleaner.Cleanable cleanable = CLEANER.register(this, this.zipFileState);

    public FileResourcePack(File resourcePackFileIn) {
        super(resourcePackFileIn);
    }

    private ZipFile getResourcePackZipFile() throws IOException {
        return this.zipFileState.get(this.resourcePackFile);
    }

    protected InputStream getInputStreamByName(String name) throws IOException {
        ZipFile zipfile = this.getResourcePackZipFile();
        ZipEntry zipentry = zipfile.getEntry(name);

        if (zipentry == null) {
            throw new ResourcePackFileNotFoundException(this.resourcePackFile, name);
        } else {
            return zipfile.getInputStream(zipentry);
        }
    }

    public boolean hasResourceName(String name) {
        try {
            return this.getResourcePackZipFile().getEntry(name) != null;
        } catch (IOException var3) {
            return false;
        }
    }

    public Set<String> getResourceDomains() {
        ZipFile zipfile;

        try {
            zipfile = this.getResourcePackZipFile();
        } catch (IOException var8) {
            return Collections.emptySet();
        }

        Enumeration<? extends ZipEntry> enumeration = zipfile.entries();
        Set<String> set = Sets.newHashSet();

        while (enumeration.hasMoreElements()) {
            ZipEntry zipentry = enumeration.nextElement();
            String s = zipentry.getName();

            if (s.startsWith("assets/")) {
                List<String> list = Lists.newArrayList(entryNameSplitter.split(s));

                if (list.size() > 1) {
                    String s1 = list.get(1);

                    if (!s1.equals(s1.toLowerCase())) {
                        this.logNameNotLowercase(s1);
                    } else {
                        set.add(s1);
                    }
                }
            }
        }

        return set;
    }

    public void close() throws IOException {
        this.zipFileState.close();
        this.cleanable.clean();
    }

    private static final class ZipFileState implements Runnable {
        private ZipFile zipFile;

        synchronized ZipFile get(File file) throws IOException {
            if (this.zipFile == null) {
                this.zipFile = new ZipFile(file);
            }

            return this.zipFile;
        }

        synchronized void close() throws IOException {
            ZipFile zipFileToClose = this.zipFile;
            this.zipFile = null;

            if (zipFileToClose != null) {
                zipFileToClose.close();
            }
        }

        @Override
        public void run() {
            try {
                this.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    @SneakyThrows
    public boolean hasAnimations() {
        ZipFile zipfile;

        try {
            zipfile = this.getResourcePackZipFile();
        } catch (IOException var8) {
            return false;
        }

        Enumeration<? extends ZipEntry> enumeration = zipfile.entries();

        while (enumeration.hasMoreElements()) {
            ZipEntry zipentry = enumeration.nextElement();
            String s = zipentry.getName();

            if (s.startsWith("assets/minecraft/textures/items") && s.endsWith(".mcmeta")) {

                if (AnimatedTexture.metadataHasAnimationFrames(zipfile.getInputStream(zipentry)))
                    return true;
            }
        }

        return false;
    }

    @Override
    public boolean hasSounds() {
        ZipFile zipfile;

        try {
            zipfile = this.getResourcePackZipFile();
        } catch (IOException var8) {
            return false;
        }

        Enumeration<? extends ZipEntry> enumeration = zipfile.entries();

        while (enumeration.hasMoreElements()) {
            ZipEntry zipentry = enumeration.nextElement();
            String s = zipentry.getName();

            if (s.startsWith("assets/minecraft/sounds/") && s.endsWith(".ogg")) {
                return true;
            }
        }

        return false;
    }
}
