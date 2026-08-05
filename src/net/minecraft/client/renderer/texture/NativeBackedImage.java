package net.minecraft.client.renderer.texture;

import lombok.Getter;
import net.minecraft.rendering.MemoryTracker;
import org.apache.commons.io.IOUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;

public class NativeBackedImage extends BufferedImage implements AutoCloseable {

    @Getter
    private final int width;
    @Getter
    private final int height;
    private long pointer;

    private NativeBackedImage(int width, int height, long pointer) {
        super(1, 1, TYPE_INT_ARGB);
        this.width = width;
        this.height = height;
        this.pointer = pointer;
    }

    @Override
    public int getWidth(ImageObserver observer) {
        return width;
    }

    @Override
    public int getHeight(ImageObserver observer) {
        return height;
    }

    @Override
    public int[] getRGB(int startX, int startY, int w, int h, int[] rgbArray, int offset, int scansize) {

        if (rgbArray == null) {
            rgbArray = new int[offset+h*scansize];
        }

        // Inverse itr for cache coherency
        for (int z = startY; z < h; z++) {
            for (int x = startX; x < w; x++) {
                int color = MemoryUtil.memGetInt(this.pointer + ((x + (long) z * width) * 4));
                // ABGR -> ARGB
                int a = (color >> 24) & 0xFF;
                int b = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int r = (color) & 0xFF;

                int finalColor = (a << 24) | (r << 16) | (g << 8) | b;

                rgbArray[x + (z * width)] = finalColor;
            }
        }

        return rgbArray;
    }

    @Override
    public int getRGB(int x, int z) {
        checkBounds(x, z);

        return MemoryUtil.memGetInt(this.pointer + ((x + (long) z * width) * 4));
    }

    @Override
    public void setRGB(int x, int z, int rgb) {
        checkBounds(x, z);

        MemoryUtil.memPutInt(this.pointer + ((x + (long) z * width) * 4), rgb);
    }

    @Override
    public BufferedImage getSubimage(int x, int y, int w, int h) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void close() {
        if (this.pointer != 0) {
            STBImage.nstbi_image_free(this.pointer);

            this.pointer = 0;
        }
    }

    private void checkBounds(int x, int z) {
        if (x < 0 || x >= this.width || z < 0 || z >= this.height) {
            throw new IllegalStateException(
                    "Out of bounds: " + x + ", " + z + " (width: " + this.width + ", height: " + this.height + ")");
        }
    }

    // Parsing

    public static NativeBackedImage make(InputStream stream) {
        ByteBuffer imgBuf = null;

        try {
            imgBuf = readResource(stream);
            imgBuf.rewind();

            try (MemoryStack memoryStack = MemoryStack.stackPush()) {
                IntBuffer width = memoryStack.mallocInt(1);
                IntBuffer height = memoryStack.mallocInt(1);
                IntBuffer channels = memoryStack.mallocInt(1);

                // 4 channels: RGBA
                ByteBuffer buf = STBImage.stbi_load_from_memory(imgBuf, width, height, channels, 4);
                if (buf == null) {
                    throw new RuntimeException("Could not load image: " + STBImage.stbi_failure_reason());
                }

                return new NativeBackedImage(width.get(0), height.get(0), MemoryUtil.memAddress(buf));
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // free
            MemoryTracker.memFree(imgBuf);
            IOUtils.closeQuietly(stream);
        }

        return null;
    }

    public static NativeBackedImage makeNoClosing(InputStream stream) {
        ByteBuffer imgBuf = null;

        try {
            imgBuf = readResource(stream);
            imgBuf.rewind();

            try (MemoryStack memoryStack = MemoryStack.stackPush()) {
                IntBuffer width = memoryStack.mallocInt(1);
                IntBuffer height = memoryStack.mallocInt(1);
                IntBuffer channels = memoryStack.mallocInt(1);

                // 4 channels: RGBA
                ByteBuffer buf = STBImage.stbi_load_from_memory(imgBuf, width, height, channels, 4);
                if (buf == null) {
                    throw new RuntimeException("Could not load image: " + STBImage.stbi_failure_reason());
                }

                return new NativeBackedImage(width.get(0), height.get(0), MemoryUtil.memAddress(buf));
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // free
            MemoryTracker.memFree(imgBuf);
        }

        return null;
    }

    private static final int TRANSFER_SIZE = 16384;
    private static ByteBuffer readResource(InputStream inputStream) throws IOException {

        ByteBuffer byteBuffer;
        if (inputStream instanceof FileInputStream) {
            FileChannel fileChannel = ((FileInputStream) inputStream).getChannel();
            byteBuffer = MemoryTracker.memAlloc((int) fileChannel.size() + 1);

            while (fileChannel.read(byteBuffer) != -1) {
            }
        } else {
            int sizeGuess = 4096;
            try {
                sizeGuess = Math.max(4096, inputStream.available());
            } catch (IOException ignored) {
            }

            byteBuffer = MemoryTracker.memAlloc(sizeGuess * 2);

            while (read(byteBuffer, inputStream) != -1) {
                // If we've filled the buffer, make it twice as large and reparse
                if (byteBuffer.remaining() == 0) {
                    byteBuffer = MemoryTracker.memRealloc(byteBuffer, byteBuffer.capacity() * 2);
                }
            }
        }

        return byteBuffer;
    }

    private static int read(ByteBuffer dst, InputStream in) throws IOException {
        int len = dst.remaining();
        int totalRead = 0;
        int bytesRead = 0;
        byte[] buf = new byte[TRANSFER_SIZE];
        while (totalRead < len) {
            int bytesToRead = Math.min((len - totalRead), TRANSFER_SIZE);
            if (buf.length < bytesToRead) {
                buf = new byte[bytesToRead];
            }

            if ((totalRead > 0) && !(in.available() > 0)) {
                break; // block at most once
            }

            bytesRead = in.read(buf, 0, bytesToRead);

            if (bytesRead < 0) {
                break;
            } else {
                totalRead += bytesRead;
            }
            dst.put(buf, 0, bytesRead);
        }

        if ((bytesRead < 0) && (totalRead == 0)) {
            return -1;
        }

        return totalRead;
    }
}