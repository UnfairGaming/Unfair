package net.minecraft.client.renderer;

import cn.unfair.Unfair;
import cn.unfair.module.modules.render.Xray;
import com.google.common.primitives.Floats;
import lombok.Getter;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.rendering.MemoryTracker;
import net.minecraft.src.Config;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.util.MathHelper;
import net.optifine.SmartAnimations;
import net.optifine.render.RenderEnv;
import net.optifine.shaders.SVertexBuilder;
import net.optifine.util.TextureUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.nio.*;
import java.util.Arrays;
import java.util.BitSet;

public class WorldRenderer {
    final VertexFormat vfmt = new VertexFormat();
    private final Logger logger = LogManager.getLogger("WorldRenderer");
    public IntBuffer rawIntBuffer;
    public FloatBuffer rawFloatBuffer;
    public int vertexCount;
    /**
     * None
     */
    public boolean noColor;
    public int drawMode;
    public SVertexBuilder sVertexBuilder;
    public RenderEnv renderEnv = null;
    public BitSet animatedSprites = null;
    public BitSet animatedSpritesCached = new BitSet();
    private ByteBuffer byteBuffer;
    private ShortBuffer rawShortBuffer;
    private VertexFormatElement vertexFormatElement;
    private int vertexFormatIndex;
    private double xOffset;
    private double yOffset;
    private double zOffset;
    @Getter
    private VertexFormat vertexFormat;
    private boolean isDrawing;
    @Getter
    private EnumWorldBlockLayer blockLayer = null;
    private boolean[] drawnIcons = new boolean[256];
    private TextureAtlasSprite[] quadSprites = null;
    private TextureAtlasSprite[] quadSpritesPrev = null;
    private TextureAtlasSprite quadSprite = null;
    private boolean modeTriangles = false;
    private ByteBuffer byteBufferTriangles;

    public WorldRenderer(int bufferSizeIn) {
        this.byteBuffer = MemoryTracker.memAlloc(bufferSizeIn * 4);
        this.rawIntBuffer = this.byteBuffer.asIntBuffer();
        this.rawShortBuffer = this.byteBuffer.asShortBuffer();
        this.rawFloatBuffer = this.byteBuffer.asFloatBuffer();
        SVertexBuilder.initVertexBuilder(this);
    }

    private static void mergeSort(int[] indicesArray, float[] distanceArray) {
        mergeSort(indicesArray, 0, indicesArray.length, distanceArray, Arrays.copyOf(indicesArray, indicesArray.length));
    }

    private static void sliceQuad(FloatBuffer floatBuffer, int quadIdx, int quadStride) {
        int base = quadIdx * quadStride;

        floatBuffer.limit(base + quadStride);
        floatBuffer.position(base);
    }

    private static float getDistanceSq(FloatBuffer buffer, float xCenter, float yCenter, float zCenter, int stride, int start) {
        int vertexBase = start;
        float x1 = buffer.get(vertexBase);
        float y1 = buffer.get(vertexBase + 1);
        float z1 = buffer.get(vertexBase + 2);

        vertexBase += stride;
        float x2 = buffer.get(vertexBase);
        float y2 = buffer.get(vertexBase + 1);
        float z2 = buffer.get(vertexBase + 2);

        vertexBase += stride;
        float x3 = buffer.get(vertexBase);
        float y3 = buffer.get(vertexBase + 1);
        float z3 = buffer.get(vertexBase + 2);

        vertexBase += stride;
        float x4 = buffer.get(vertexBase);
        float y4 = buffer.get(vertexBase + 1);
        float z4 = buffer.get(vertexBase + 2);

        float xDist = ((x1 + x2 + x3 + x4) * 0.25F) - xCenter;
        float yDist = ((y1 + y2 + y3 + y4) * 0.25F) - yCenter;
        float zDist = ((z1 + z2 + z3 + z4) * 0.25F) - zCenter;

        return (xDist * xDist) + (yDist * yDist) + (zDist * zDist);
    }

    private static void mergeSort(final int[] a, final int from, final int to, float[] dist, final int[] supp) {
        int len = to - from;

        // Insertion sort on smallest arrays
        if (len < 16) {
            insertionSort(a, from, to, dist);
            return;
        }

        // Recursively sort halves of a into supp
        final int mid = (from + to) >>> 1;
        mergeSort(supp, from, mid, dist, a);
        mergeSort(supp, mid, to, dist, a);

        // If list is already sorted, just copy from supp to a. This is an
        // optimization that results in faster sorts for nearly ordered lists.
        if (Floats.compare(dist[supp[mid]], dist[supp[mid - 1]]) <= 0) {
            System.arraycopy(supp, from, a, from, len);
            return;
        }

        // Merge sorted halves (now in supp) into a
        for (int i = from, p = from, q = mid; i < to; i++) {
            if (q >= to || p < mid && Floats.compare(dist[supp[q]], dist[supp[p]]) <= 0) {
                a[i] = supp[p++];
            } else {
                a[i] = supp[q++];
            }
        }
    }

    private static void insertionSort(final int[] a, final int from, final int to, final float[] dist) {
        for (int i = from; ++i < to; ) {
            int t = a[i];
            int j = i;

            for (int u = a[j - 1]; Floats.compare(dist[u], dist[t]) < 0; u = a[--j - 1]) {
                a[j] = u;
                if (from == j - 1) {
                    --j;
                    break;
                }
            }

            a[j] = t;
        }
    }

    private void growBuffer(int p_181670_1_) {
        if (p_181670_1_ > this.rawIntBuffer.remaining()) {
            int i = this.byteBuffer.capacity();
            int j = i % 2097152;
            int k = j + (((this.rawIntBuffer.position() + p_181670_1_) * 4 - j) / 2097152 + 1) * 2097152;
            logger.warn("Needed to grow BufferBuilder buffer: Old size {} bytes, new size {} bytes.", i, k);
            int l = this.rawIntBuffer.position();
            ByteBuffer bytebuffer = MemoryTracker.memAlloc(k);
            this.byteBuffer.position(0);
            bytebuffer.put(this.byteBuffer);
            bytebuffer.rewind();
            MemoryTracker.memFree(this.byteBuffer);
            this.byteBuffer = bytebuffer;
            this.rawFloatBuffer = this.byteBuffer.asFloatBuffer();
            this.rawIntBuffer = this.byteBuffer.asIntBuffer();
            this.rawIntBuffer.position(l);
            this.rawShortBuffer = this.byteBuffer.asShortBuffer();
            this.rawShortBuffer.position(l << 1);

            if (this.quadSprites != null) {
                TextureAtlasSprite[] atextureatlassprite = this.quadSprites;
                int i1 = this.getBufferQuadSize();
                this.quadSprites = new TextureAtlasSprite[i1];
                System.arraycopy(atextureatlassprite, 0, this.quadSprites, 0, Math.min(atextureatlassprite.length, this.quadSprites.length));
                this.quadSpritesPrev = null;
            }
        }
    }

    public void sortVertexData(float p_181674_1_, float p_181674_2_, float p_181674_3_) {
//        int i = this.vertexCount / 4;
//        final float[] afloat = new float[i];
//
//        for (int j = 0; j < i; ++j) {
//            afloat[j] = getDistanceSq(this.rawFloatBuffer, (float) ((double) p_181674_1_ + this.xOffset), (float) ((double) p_181674_2_ + this.yOffset), (float) ((double) p_181674_3_ + this.zOffset), this.vertexFormat.getIntegerSize(), j * this.vertexFormat.getNextOffset());
//        }
//
//        Integer[] ainteger = new Integer[i];
//
//        for (int k = 0; k < ainteger.length; ++k) {
//            ainteger[k] = k;
//        }
//
//        Arrays.sort(ainteger, new Comparator<Integer>() {
//            public int compare(Integer p_compare_1_, Integer p_compare_2_) {
//                return Floats.compare(afloat[p_compare_2_.intValue()], afloat[p_compare_1_.intValue()]);
//            }
//        });
//        BitSet bitset = new BitSet();
//        int l = this.vertexFormat.getNextOffset();
//        int[] aint = new int[l];
//
//        for (int l1 = 0; (l1 = bitset.nextClearBit(l1)) < ainteger.length; ++l1) {
//            int i1 = ainteger[l1].intValue();
//
//            if (i1 != l1) {
//                this.rawIntBuffer.limit(i1 * l + l);
//                this.rawIntBuffer.position(i1 * l);
//                this.rawIntBuffer.get(aint);
//                int j1 = i1;
//
//                for (int k1 = ainteger[i1].intValue(); j1 != l1; k1 = ainteger[k1].intValue()) {
//                    this.rawIntBuffer.limit(k1 * l + l);
//                    this.rawIntBuffer.position(k1 * l);
//                    IntBuffer intbuffer = this.rawIntBuffer.slice();
//                    this.rawIntBuffer.limit(j1 * l + l);
//                    this.rawIntBuffer.position(j1 * l);
//                    this.rawIntBuffer.put(intbuffer);
//                    bitset.set(j1);
//                    j1 = k1;
//                }
//
//                this.rawIntBuffer.limit(l1 * l + l);
//                this.rawIntBuffer.position(l1 * l);
//                this.rawIntBuffer.put(aint);
//            }
//
//            bitset.set(l1);
//        }
//
//        this.rawIntBuffer.limit(this.rawIntBuffer.capacity());
//        this.rawIntBuffer.position(this.getBufferSize());
//
//        if (this.quadSprites != null) {
//            TextureAtlasSprite[] atextureatlassprite = new TextureAtlasSprite[this.vertexCount / 4];
//            int i2 = this.vertexFormat.getNextOffset() / 4 * 4;
//
//            for (int j2 = 0; j2 < ainteger.length; ++j2) {
//                int k2 = ainteger[j2].intValue();
//                atextureatlassprite[j2] = this.quadSprites[k2];
//            }
//
//            System.arraycopy(atextureatlassprite, 0, this.quadSprites, 0, atextureatlassprite.length);
//        }

        this.byteBuffer.clear();
        FloatBuffer floatBuffer = this.byteBuffer.asFloatBuffer();

        int vertexStride = this.vertexFormat.getSize();
        int quadStride = this.vertexFormat.getIntegerSize() * 4;

        int quadCount = this.vertexCount / 4;
        int vertexSizeInteger = this.vertexFormat.getIntegerSize();

        float[] distanceArray = new float[quadCount];
        int[] indicesArray = new int[quadCount];

        for (int quadIdx = 0; quadIdx < quadCount; ++quadIdx) {
            distanceArray[quadIdx] = getDistanceSq(floatBuffer, p_181674_1_, p_181674_2_, p_181674_3_, vertexSizeInteger, quadIdx * vertexStride);
            indicesArray[quadIdx] = quadIdx;
        }

        mergeSort(indicesArray, distanceArray);

        BitSet bits = new BitSet();

        FloatBuffer tmp = FloatBuffer.allocate(vertexSizeInteger * 4);

        for (int l = bits.nextClearBit(0); l < indicesArray.length; l = bits.nextClearBit(l + 1)) {
            int m = indicesArray[l];

            if (m != l) {
                sliceQuad(floatBuffer, m, quadStride);
                tmp.clear();
                tmp.put(floatBuffer);

                int n = m;

                for (int o = indicesArray[m]; n != l; o = indicesArray[o]) {
                    sliceQuad(floatBuffer, o, quadStride);
                    FloatBuffer floatBuffer3 = floatBuffer.slice();

                    sliceQuad(floatBuffer, n, quadStride);
                    floatBuffer.put(floatBuffer3);

                    bits.set(n);
                    n = o;
                }

                sliceQuad(floatBuffer, l, quadStride);
                tmp.flip();

                floatBuffer.put(tmp);
            }

            bits.set(l);
        }
    }

    public WorldRenderer.State getVertexState() {
        this.rawIntBuffer.rewind();
        int i = this.getBufferSize();
        this.rawIntBuffer.limit(i);
        int[] aint = new int[i];
        this.rawIntBuffer.get(aint);
        this.rawIntBuffer.limit(this.rawIntBuffer.capacity());
        this.rawIntBuffer.position(i);
        TextureAtlasSprite[] atextureatlassprite = null;

        if (this.quadSprites != null) {
            int j = this.vertexCount / 4;
            atextureatlassprite = new TextureAtlasSprite[j];
            System.arraycopy(this.quadSprites, 0, atextureatlassprite, 0, j);
        }

        return new State(aint, new VertexFormat(this.vertexFormat), atextureatlassprite);
    }

    public void setVertexState(WorldRenderer.State state) {
        this.rawIntBuffer.clear();
        this.growBuffer(state.getRawBuffer().length);
        this.rawIntBuffer.put(state.getRawBuffer());
        this.vertexCount = state.getVertexCount();
        vfmt.clear();
        vfmt.deepCopy(state.getVertexFormat());
        this.vertexFormat = vfmt;

        if (state.stateQuadSprites != null) {
            if (this.quadSprites == null) {
                this.quadSprites = this.quadSpritesPrev;
            }

            if (this.quadSprites == null || this.quadSprites.length < this.getBufferQuadSize()) {
                this.quadSprites = new TextureAtlasSprite[this.getBufferQuadSize()];
            }

            TextureAtlasSprite[] atextureatlassprite = state.stateQuadSprites;
            System.arraycopy(atextureatlassprite, 0, this.quadSprites, 0, atextureatlassprite.length);
        } else {
            if (this.quadSprites != null) {
                this.quadSpritesPrev = this.quadSprites;
            }

            this.quadSprites = null;
        }
    }

    public int getBufferSize() {
        return this.vertexCount * this.vertexFormat.getIntegerSize();
    }

    public void reset() {
        this.vertexCount = 0;
        this.vertexFormatElement = null;
        this.vertexFormatIndex = 0;
        this.quadSprite = null;

        if (SmartAnimations.isActive()) {
            if (this.animatedSprites == null) {
                this.animatedSprites = this.animatedSpritesCached;
            }

            this.animatedSprites.clear();
        } else if (this.animatedSprites != null) {
            this.animatedSprites = null;
        }

        this.modeTriangles = false;

        MemoryTracker.memFree(byteBufferTriangles);
        byteBufferTriangles = null;
    }

    public void begin(int glMode, VertexFormat format) {

        if (this.isDrawing) {
            throw new IllegalStateException("Already building!");
        } else {
            this.isDrawing = true;
            this.reset();
            this.drawMode = glMode;
            this.vertexFormat = format;
            this.vertexFormatElement = format.getElement(this.vertexFormatIndex);
            this.noColor = false;
            this.byteBuffer.limit(this.byteBuffer.capacity());

            if (Config.isShaders()) {
                SVertexBuilder.endSetVertexFormat(this);
            }

            if (Config.isMultiTexture()) {
                if (this.blockLayer != null) {
                    if (this.quadSprites == null) {
                        this.quadSprites = this.quadSpritesPrev;
                    }

                    if (this.quadSprites == null || this.quadSprites.length < this.getBufferQuadSize()) {
                        this.quadSprites = new TextureAtlasSprite[this.getBufferQuadSize()];
                    }
                }
            } else {
                if (this.quadSprites != null) {
                    this.quadSpritesPrev = this.quadSprites;
                }

                this.quadSprites = null;
            }
        }
    }

    public WorldRenderer tex(double u, double v) {
        if (this.quadSprite != null && this.quadSprites != null) {
            u = this.quadSprite.toSingleU((float) u);
            v = this.quadSprite.toSingleV((float) v);
            this.quadSprites[this.vertexCount / 4] = this.quadSprite;
        }

        int i = this.vertexCount * this.vertexFormat.getSize() + this.vertexFormat.getOffset(this.vertexFormatIndex);

        switch (this.vertexFormatElement.getType()) {
            case FLOAT:
                this.byteBuffer.putFloat(i, (float) u);
                this.byteBuffer.putFloat(i + 4, (float) v);
                break;

            case UINT:
            case INT:
                this.byteBuffer.putInt(i, (int) u);
                this.byteBuffer.putInt(i + 4, (int) v);
                break;

            case USHORT:
            case SHORT:
                this.byteBuffer.putShort(i, (short) ((int) v));
                this.byteBuffer.putShort(i + 2, (short) ((int) u));
                break;

            case UBYTE:
            case BYTE:
                this.byteBuffer.put(i, (byte) ((int) v));
                this.byteBuffer.put(i + 1, (byte) ((int) u));
        }

        this.nextVertexFormatIndex();
        return this;
    }

    public WorldRenderer lightmap(int p_181671_1_, int p_181671_2_) {
        int i = this.vertexCount * this.vertexFormat.getSize() + this.vertexFormat.getOffset(this.vertexFormatIndex);

        switch (this.vertexFormatElement.getType()) {
            case FLOAT:
                this.byteBuffer.putFloat(i, (float) p_181671_1_);
                this.byteBuffer.putFloat(i + 4, (float) p_181671_2_);
                break;

            case UINT:
            case INT:
                this.byteBuffer.putInt(i, p_181671_1_);
                this.byteBuffer.putInt(i + 4, p_181671_2_);
                break;

            case USHORT:
            case SHORT:
                this.byteBuffer.putShort(i, (short) p_181671_2_);
                this.byteBuffer.putShort(i + 2, (short) p_181671_1_);
                break;

            case UBYTE:
            case BYTE:
                this.byteBuffer.put(i, (byte) p_181671_2_);
                this.byteBuffer.put(i + 1, (byte) p_181671_1_);
        }

        this.nextVertexFormatIndex();
        return this;
    }

    public void putBrightness4(int p_178962_1_, int p_178962_2_, int p_178962_3_, int p_178962_4_) {
        int i = (this.vertexCount - 4) * this.vertexFormat.getIntegerSize() + this.vertexFormat.getUvOffsetById(1) / 4;
        int j = this.vertexFormat.getSize() >> 2;
        this.rawIntBuffer.put(i, p_178962_1_);
        this.rawIntBuffer.put(i + j, p_178962_2_);
        this.rawIntBuffer.put(i + j * 2, p_178962_3_);
        this.rawIntBuffer.put(i + j * 3, p_178962_4_);
    }

    public void putPosition(double x, double y, double z) {
        int i = this.vertexFormat.getIntegerSize();
        int j = (this.vertexCount - 4) * i;

        for (int k = 0; k < 4; ++k) {
            int l = j + k * i;
            int i1 = l + 1;
            int j1 = i1 + 1;
            this.rawIntBuffer.put(l, Float.floatToRawIntBits((float) (x + this.xOffset) + Float.intBitsToFloat(this.rawIntBuffer.get(l))));
            this.rawIntBuffer.put(i1, Float.floatToRawIntBits((float) (y + this.yOffset) + Float.intBitsToFloat(this.rawIntBuffer.get(i1))));
            this.rawIntBuffer.put(j1, Float.floatToRawIntBits((float) (z + this.zOffset) + Float.intBitsToFloat(this.rawIntBuffer.get(j1))));
        }
    }

    /**
     * Takes in the pass the call list is being requested for. Args: renderPass
     */
    public int getColorIndex(int p_78909_1_) {
        return ((this.vertexCount - p_78909_1_) * this.vertexFormat.getSize() + this.vertexFormat.getColorOffset()) / 4;
    }

    public void putColorMultiplier(float red, float green, float blue, int p_178978_4_) {
        int i = this.getColorIndex(p_178978_4_);
        int j = -1;

        if (!this.noColor) {
            j = this.rawIntBuffer.get(i);

            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                int k = (int) ((float) (j & 255) * red);
                int l = (int) ((float) (j >> 8 & 255) * green);
                int i1 = (int) ((float) (j >> 16 & 255) * blue);
                j = j & -16777216;
                j = j | i1 << 16 | l << 8 | k;
            } else {
                int j1 = (int) ((float) (j >> 24 & 255) * red);
                int k1 = (int) ((float) (j >> 16 & 255) * green);
                int l1 = (int) ((float) (j >> 8 & 255) * blue);
                j = j & 255;
                j = j | j1 << 24 | k1 << 16 | l1 << 8;
            }
        }

        this.rawIntBuffer.put(i, this.applyXrayAlpha(j));
    }

    private int applyXrayAlpha(int color) {
        if (Unfair.moduleManager == null) {
            return color;
        }

        Xray xray = (Xray) Unfair.moduleManager.modules.get(Xray.class);
        return xray.isEnabled() ? color & 16777215 | (int) ((float) xray.opacity.getValue() * 255.0F / 100.0F) << 24 : color;
    }

    public void putColor(int argb, int p_178988_2_) {
        int i = this.getColorIndex(p_178988_2_);
        int j = argb >> 16 & 255;
        int k = argb >> 8 & 255;
        int l = argb & 255;
        int i1 = argb >> 24 & 255;
        this.putColorRGBA(i, j, k, l, i1);
    }

    public void putColorRGB_F(float red, float green, float blue, int p_178994_4_) {
        int i = this.getColorIndex(p_178994_4_);
        int j = MathHelper.clamp_int((int) (red * 255.0F), 0, 255);
        int k = MathHelper.clamp_int((int) (green * 255.0F), 0, 255);
        int l = MathHelper.clamp_int((int) (blue * 255.0F), 0, 255);
        this.putColorRGBA(i, j, k, l, 255);
    }

    public void putColorRGBA(int index, int red, int p_178972_3_, int p_178972_4_, int p_178972_5_) {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            this.rawIntBuffer.put(index, p_178972_5_ << 24 | p_178972_4_ << 16 | p_178972_3_ << 8 | red);
        } else {
            this.rawIntBuffer.put(index, red << 24 | p_178972_3_ << 16 | p_178972_4_ << 8 | p_178972_5_);
        }
    }

    /**
     * Disabels color processing.
     */
    public void noColor() {
        this.noColor = true;
    }

    public WorldRenderer color(float red, float green, float blue, float alpha) {
        return this.color((int) (red * 255.0F), (int) (green * 255.0F), (int) (blue * 255.0F), (int) (alpha * 255.0F));
    }

    public WorldRenderer color(int red, int green, int blue, int alpha) {
        if (!this.noColor) {
            int i = this.vertexCount * this.vertexFormat.getSize() + this.vertexFormat.getOffset(this.vertexFormatIndex);

            switch (this.vertexFormatElement.getType()) {
                case FLOAT:
                    this.byteBuffer.putFloat(i, (float) red / 255.0F);
                    this.byteBuffer.putFloat(i + 4, (float) green / 255.0F);
                    this.byteBuffer.putFloat(i + 8, (float) blue / 255.0F);
                    this.byteBuffer.putFloat(i + 12, (float) alpha / 255.0F);
                    break;

                case UINT:
                case INT:
                    this.byteBuffer.putFloat(i, (float) red);
                    this.byteBuffer.putFloat(i + 4, (float) green);
                    this.byteBuffer.putFloat(i + 8, (float) blue);
                    this.byteBuffer.putFloat(i + 12, (float) alpha);
                    break;

                case USHORT:
                case SHORT:
                    this.byteBuffer.putShort(i, (short) red);
                    this.byteBuffer.putShort(i + 2, (short) green);
                    this.byteBuffer.putShort(i + 4, (short) blue);
                    this.byteBuffer.putShort(i + 6, (short) alpha);
                    break;

                case UBYTE:
                case BYTE:
                    if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                        this.byteBuffer.put(i, (byte) red);
                        this.byteBuffer.put(i + 1, (byte) green);
                        this.byteBuffer.put(i + 2, (byte) blue);
                        this.byteBuffer.put(i + 3, (byte) alpha);
                    } else {
                        this.byteBuffer.put(i, (byte) alpha);
                        this.byteBuffer.put(i + 1, (byte) blue);
                        this.byteBuffer.put(i + 2, (byte) green);
                        this.byteBuffer.put(i + 3, (byte) red);
                    }
            }

            this.nextVertexFormatIndex();
        }
        return this;
    }

    public void addVertexData(int[] vertexData) {
        if (Config.isShaders()) {
            SVertexBuilder.beginAddVertexData(this, vertexData);
        }

        this.growBuffer(vertexData.length);
        this.rawIntBuffer.position(this.getBufferSize());
        this.rawIntBuffer.put(vertexData);
        this.vertexCount += vertexData.length / this.vertexFormat.getIntegerSize();

        if (Config.isShaders()) {
            SVertexBuilder.endAddVertexData(this);
        }
    }

    public void endVertex() {
        this.rawIntBuffer.position(this.rawIntBuffer.position() + this.vertexFormat.getIntegerSize());
        ++this.vertexCount;
        this.growBuffer(this.vertexFormat.getIntegerSize());
        this.vertexFormatIndex = 0;
        this.vertexFormatElement = this.vertexFormat.getElement(this.vertexFormatIndex);

        if (Config.isShaders()) {
            SVertexBuilder.endAddVertex(this);
        }
    }

    public WorldRenderer pos(double x, double y, double z) {
        if (Config.isShaders()) {
            SVertexBuilder.beginAddVertex(this);
        }

        int i = this.vertexCount * this.vertexFormat.getSize() + this.vertexFormat.getOffset(this.vertexFormatIndex);

        switch (this.vertexFormatElement.getType()) {
            case FLOAT:
                this.byteBuffer.putFloat(i, (float) (x + this.xOffset));
                this.byteBuffer.putFloat(i + 4, (float) (y + this.yOffset));
                this.byteBuffer.putFloat(i + 8, (float) (z + this.zOffset));
                break;

            case UINT:
            case INT:
                this.byteBuffer.putInt(i, Float.floatToRawIntBits((float) (x + this.xOffset)));
                this.byteBuffer.putInt(i + 4, Float.floatToRawIntBits((float) (y + this.yOffset)));
                this.byteBuffer.putInt(i + 8, Float.floatToRawIntBits((float) (z + this.zOffset)));
                break;

            case USHORT:
            case SHORT:
                this.byteBuffer.putShort(i, (short) ((int) (x + this.xOffset)));
                this.byteBuffer.putShort(i + 2, (short) ((int) (y + this.yOffset)));
                this.byteBuffer.putShort(i + 4, (short) ((int) (z + this.zOffset)));
                break;

            case UBYTE:
            case BYTE:
                this.byteBuffer.put(i, (byte) ((int) (x + this.xOffset)));
                this.byteBuffer.put(i + 1, (byte) ((int) (y + this.yOffset)));
                this.byteBuffer.put(i + 2, (byte) ((int) (z + this.zOffset)));
        }

        this.nextVertexFormatIndex();
        return this;
    }

    public void putNormal(float x, float y, float z) {
        int i = (byte) ((int) (x * 127.0F)) & 255;
        int j = (byte) ((int) (y * 127.0F)) & 255;
        int k = (byte) ((int) (z * 127.0F)) & 255;
        int l = i | j << 8 | k << 16;
        int i1 = this.vertexFormat.getSize() >> 2;
        int j1 = (this.vertexCount - 4) * i1 + this.vertexFormat.getNormalOffset() / 4;
        this.rawIntBuffer.put(j1, l);
        this.rawIntBuffer.put(j1 + i1, l);
        this.rawIntBuffer.put(j1 + i1 * 2, l);
        this.rawIntBuffer.put(j1 + i1 * 3, l);
    }

    public void nextVertexFormatIndex() {
        ++this.vertexFormatIndex;
        this.vertexFormatIndex %= this.vertexFormat.getElementCount();
        this.vertexFormatElement = this.vertexFormat.getElement(this.vertexFormatIndex);

        if (this.vertexFormatElement.getUsage() == VertexFormatElement.EnumUsage.PADDING) {
            this.nextVertexFormatIndex();
        }
    }

    public WorldRenderer normal(float p_181663_1_, float p_181663_2_, float p_181663_3_) {
        int i = this.vertexCount * this.vertexFormat.getSize() + this.vertexFormat.getOffset(this.vertexFormatIndex);

        switch (this.vertexFormatElement.getType()) {
            case FLOAT:
                this.byteBuffer.putFloat(i, p_181663_1_);
                this.byteBuffer.putFloat(i + 4, p_181663_2_);
                this.byteBuffer.putFloat(i + 8, p_181663_3_);
                break;

            case UINT:
            case INT:
                this.byteBuffer.putInt(i, (int) p_181663_1_);
                this.byteBuffer.putInt(i + 4, (int) p_181663_2_);
                this.byteBuffer.putInt(i + 8, (int) p_181663_3_);
                break;

            case USHORT:
            case SHORT:
                this.byteBuffer.putShort(i, (short) ((int) (p_181663_1_ * 32767.0F) & 65535));
                this.byteBuffer.putShort(i + 2, (short) ((int) (p_181663_2_ * 32767.0F) & 65535));
                this.byteBuffer.putShort(i + 4, (short) ((int) (p_181663_3_ * 32767.0F) & 65535));
                break;

            case UBYTE:
            case BYTE:
                this.byteBuffer.put(i, (byte) ((int) (p_181663_1_ * 127.0F) & 255));
                this.byteBuffer.put(i + 1, (byte) ((int) (p_181663_2_ * 127.0F) & 255));
                this.byteBuffer.put(i + 2, (byte) ((int) (p_181663_3_ * 127.0F) & 255));
        }

        this.nextVertexFormatIndex();
        return this;
    }

    public void setTranslation(double x, double y, double z) {
        this.xOffset = x;
        this.yOffset = y;
        this.zOffset = z;
    }

    public void finishDrawing() {
        if (!this.isDrawing) {
            throw new IllegalStateException("Not building!");
        } else {
            this.isDrawing = false;
            this.byteBuffer.position(0);
            this.rawIntBuffer.position(0);
            this.byteBuffer.limit(this.getBufferSize() * 4);
        }
    }

    public ByteBuffer getByteBuffer() {
        return this.modeTriangles ? this.byteBufferTriangles : this.byteBuffer;
    }

    public int getVertexCount() {
        return this.modeTriangles ? this.vertexCount / 4 * 6 : this.vertexCount;
    }

    public int getDrawMode() {
        return this.modeTriangles ? 4 : this.drawMode;
    }

    public void putColor4(int argb) {
        for (int i = 0; i < 4; ++i) {
            this.putColor(argb, i + 1);
        }
    }

    public void putColorRGB_F4(float red, float green, float blue) {
        for (int i = 0; i < 4; ++i) {
            this.putColorRGB_F(red, green, blue, i + 1);
        }
    }

    public void putSprite(TextureAtlasSprite p_putSprite_1_) {
        if (this.animatedSprites != null && p_putSprite_1_ != null && p_putSprite_1_.getAnimationIndex() >= 0) {
            this.animatedSprites.set(p_putSprite_1_.getAnimationIndex());
        }

        if (this.quadSprites != null) {
            int i = this.vertexCount / 4;
            this.quadSprites[i - 1] = p_putSprite_1_;
        }
    }

    public void setSprite(TextureAtlasSprite p_setSprite_1_) {
        if (this.animatedSprites != null && p_setSprite_1_ != null && p_setSprite_1_.getAnimationIndex() >= 0) {
            this.animatedSprites.set(p_setSprite_1_.getAnimationIndex());
        }

        if (this.quadSprites != null) {
            this.quadSprite = p_setSprite_1_;
        }
    }

    public boolean isMultiTexture() {
        return this.quadSprites != null;
    }

    public void drawMultiTexture() {
        if (this.quadSprites != null) {
            int i = Config.getMinecraft().getTextureMapBlocks().getCountRegisteredSprites();

            if (this.drawnIcons.length <= i) {
                this.drawnIcons = new boolean[i + 1];
            }

            Arrays.fill(this.drawnIcons, false);
            int j = 0;
            int k = -1;
            int l = this.vertexCount / 4;

            for (int i1 = 0; i1 < l; ++i1) {
                TextureAtlasSprite textureatlassprite = this.quadSprites[i1];

                if (textureatlassprite != null) {
                    int j1 = textureatlassprite.getIndexInMap();

                    if (!this.drawnIcons[j1]) {
                        if (textureatlassprite == TextureUtils.iconGrassSideOverlay) {
                            if (k < 0) {
                                k = i1;
                            }
                        } else {
                            i1 = this.drawForIcon(textureatlassprite, i1) - 1;
                            ++j;

                            if (this.blockLayer != EnumWorldBlockLayer.TRANSLUCENT) {
                                this.drawnIcons[j1] = true;
                            }
                        }
                    }
                }
            }

            if (k >= 0) {
                this.drawForIcon(TextureUtils.iconGrassSideOverlay, k);
                ++j;
            }
        }
    }

    private int drawForIcon(TextureAtlasSprite p_drawForIcon_1_, int p_drawForIcon_2_) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, p_drawForIcon_1_.glSpriteTextureId);
        int i = -1;
        int j = -1;
        int k = this.vertexCount / 4;

        for (int l = p_drawForIcon_2_; l < k; ++l) {
            TextureAtlasSprite textureatlassprite = this.quadSprites[l];

            if (textureatlassprite == p_drawForIcon_1_) {
                if (j < 0) {
                    j = l;
                }
            } else if (j >= 0) {
                this.draw(j, l);

                if (this.blockLayer == EnumWorldBlockLayer.TRANSLUCENT) {
                    return l;
                }

                j = -1;

                if (i < 0) {
                    i = l;
                }
            }
        }

        if (j >= 0) {
            this.draw(j, k);
        }

        if (i < 0) {
            i = k;
        }

        return i;
    }

    public void draw(int p_draw_1_, int p_draw_2_) {
        int i = p_draw_2_ - p_draw_1_;

        if (i > 0) {
            int j = p_draw_1_ * 4;
            int k = i * 4;
            GL11.glDrawArrays(this.drawMode, j, k);
        }
    }

    public void setBlockLayer(EnumWorldBlockLayer p_setBlockLayer_1_) {
        this.blockLayer = p_setBlockLayer_1_;

        if (p_setBlockLayer_1_ == null) {
            if (this.quadSprites != null) {
                this.quadSpritesPrev = this.quadSprites;
            }

            this.quadSprites = null;
            this.quadSprite = null;
        }
    }

    public int getBufferQuadSize() {
        return this.rawIntBuffer.capacity() * 4 / (this.vertexFormat.getIntegerSize() * 4);
    }

    public RenderEnv getRenderEnv(IBlockState p_getRenderEnv_1_, BlockPos p_getRenderEnv_2_) {
        if (this.renderEnv == null) {
            this.renderEnv = new RenderEnv(p_getRenderEnv_1_, p_getRenderEnv_2_);
        } else {
            this.renderEnv.reset(p_getRenderEnv_1_, p_getRenderEnv_2_);
        }
        return this.renderEnv;
    }

    public boolean isDrawing() {
        return this.isDrawing;
    }

    public double getXOffset() {
        return this.xOffset;
    }

    public double getYOffset() {
        return this.yOffset;
    }

    public double getZOffset() {
        return this.zOffset;
    }

    public void putColorMultiplierRgba(float p_putColorMultiplierRgba_1_, float p_putColorMultiplierRgba_2_, float p_putColorMultiplierRgba_3_, float p_putColorMultiplierRgba_4_, int p_putColorMultiplierRgba_5_) {
        int i = this.getColorIndex(p_putColorMultiplierRgba_5_);
        int j = -1;

        if (!this.noColor) {
            j = this.rawIntBuffer.get(i);

            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                int k = (int) ((float) (j & 255) * p_putColorMultiplierRgba_1_);
                int l = (int) ((float) (j >> 8 & 255) * p_putColorMultiplierRgba_2_);
                int i1 = (int) ((float) (j >> 16 & 255) * p_putColorMultiplierRgba_3_);
                int j1 = (int) ((float) (j >> 24 & 255) * p_putColorMultiplierRgba_4_);
                j = j1 << 24 | i1 << 16 | l << 8 | k;
            } else {
                int k1 = (int) ((float) (j >> 24 & 255) * p_putColorMultiplierRgba_1_);
                int l1 = (int) ((float) (j >> 16 & 255) * p_putColorMultiplierRgba_2_);
                int i2 = (int) ((float) (j >> 8 & 255) * p_putColorMultiplierRgba_3_);
                int j2 = (int) ((float) (j & 255) * p_putColorMultiplierRgba_4_);
                j = k1 << 24 | l1 << 16 | i2 << 8 | j2;
            }
        }

        this.rawIntBuffer.put(i, j);
    }

    public void quadsToTriangles() {
        if (this.drawMode == 7) {
            if (this.byteBufferTriangles == null) {
                this.byteBufferTriangles = MemoryTracker.memAlloc(this.byteBuffer.capacity() * 2);
            }

            if (this.byteBufferTriangles.capacity() < this.byteBuffer.capacity() * 2) {
                this.byteBufferTriangles = MemoryTracker.memAlloc(this.byteBuffer.capacity() * 2);
            }

            int i = this.vertexFormat.getSize();
            int j = this.byteBuffer.limit();
            this.byteBuffer.rewind();
            this.byteBufferTriangles.clear();

            for (int k = 0; k < this.vertexCount; k += 4) {
                this.byteBuffer.limit((k + 3) * i);
                this.byteBuffer.position(k * i);
                this.byteBufferTriangles.put(this.byteBuffer);
                this.byteBuffer.limit((k + 1) * i);
                this.byteBuffer.position(k * i);
                this.byteBufferTriangles.put(this.byteBuffer);
                this.byteBuffer.limit((k + 2 + 2) * i);
                this.byteBuffer.position((k + 2) * i);
                this.byteBufferTriangles.put(this.byteBuffer);
            }

            this.byteBuffer.limit(j);
            this.byteBuffer.rewind();
            this.byteBufferTriangles.flip();
            this.modeTriangles = true;
        }
    }

    public boolean isColorDisabled() {
        return this.noColor;
    }

    public static class State {
        private final int[] stateRawBuffer;
        private final VertexFormat stateVertexFormat;
        private TextureAtlasSprite[] stateQuadSprites;

        public State(int[] p_i1_2_, VertexFormat p_i1_3_, TextureAtlasSprite[] p_i1_4_) {
            this.stateRawBuffer = p_i1_2_;
            this.stateVertexFormat = p_i1_3_;
            this.stateQuadSprites = p_i1_4_;
        }

        public State(int[] buffer, VertexFormat format) {
            this.stateRawBuffer = buffer;
            this.stateVertexFormat = format;
        }

        public int[] getRawBuffer() {
            return this.stateRawBuffer;
        }

        public int getVertexCount() {
            return this.stateRawBuffer.length / this.stateVertexFormat.getIntegerSize();
        }

        public VertexFormat getVertexFormat() {
            return this.stateVertexFormat;
        }
    }
}
