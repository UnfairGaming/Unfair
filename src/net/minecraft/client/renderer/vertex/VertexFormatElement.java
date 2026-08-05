package net.minecraft.client.renderer.vertex;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL11;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;

public class VertexFormatElement {
    private static final Logger LOGGER = LogManager.getLogger("VertexFormatElement");
    private final VertexFormatElement.EnumType type;
    private final VertexFormatElement.EnumUsage usage;
    private final int index;
    private final int elementCount;

    public VertexFormatElement(int indexIn, VertexFormatElement.EnumType typeIn, VertexFormatElement.EnumUsage usageIn, int count) {
        if (!this.checkIndexUsage(indexIn, usageIn)) {
            LOGGER.warn("Multiple vertex elements of the same type other than UVs are not supported. Forcing type to UV.");
            this.usage = VertexFormatElement.EnumUsage.UV;
        } else {
            this.usage = usageIn;
        }

        this.type = typeIn;
        this.index = indexIn;
        this.elementCount = count;
    }

    private boolean checkIndexUsage(int index, VertexFormatElement.EnumUsage usage) {
        return index == 0 || usage == VertexFormatElement.EnumUsage.UV;
    }

    public final VertexFormatElement.EnumType getType() {
        return this.type;
    }

    public final VertexFormatElement.EnumUsage getUsage() {
        return this.usage;
    }

    public final int getElementCount() {
        return this.elementCount;
    }

    public final int getIndex() {
        return this.index;
    }

    public String toString() {
        return this.elementCount + "," + this.usage.getDisplayName() + "," + this.type.getDisplayName();
    }

    public final int getSize() {
        return this.type.getSize() * this.elementCount;
    }

    public final boolean isPositionElement() {
        return this.usage == VertexFormatElement.EnumUsage.POSITION;
    }

    public boolean equals(Object p_equals_1_) {
        if (this == p_equals_1_) {
            return true;
        } else if (p_equals_1_ != null && this.getClass() == p_equals_1_.getClass()) {
            VertexFormatElement vertexformatelement = (VertexFormatElement) p_equals_1_;
            return this.elementCount == vertexformatelement.elementCount && (this.index == vertexformatelement.index && (this.type == vertexformatelement.type && this.usage == vertexformatelement.usage));
        } else {
            return false;
        }
    }

    public int hashCode() {
        int i = this.type.hashCode();
        i = 31 * i + this.usage.hashCode();
        i = 31 * i + this.index;
        i = 31 * i + this.elementCount;
        return i;
    }

    public enum EnumType {
        FLOAT(4, "Float", 5126),
        UBYTE(1, "Unsigned Byte", 5121),
        BYTE(1, "Byte", 5120),
        USHORT(2, "Unsigned Short", 5123),
        SHORT(2, "Short", 5122),
        UINT(4, "Unsigned Int", 5125),
        INT(4, "Int", 5124);

        private final int size;
        private final String displayName;
        private final int glConstant;

        EnumType(int sizeIn, String displayNameIn, int glConstantIn) {
            this.size = sizeIn;
            this.displayName = displayNameIn;
            this.glConstant = glConstantIn;
        }

        public int getSize() {
            return this.size;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public int getGlConstant() {
            return this.glConstant;
        }
    }

    public enum EnumUsage {
        POSITION("Position"),
        NORMAL("Normal"),
        COLOR("Vertex Color"),
        UV("UV"),
        MATRIX("Bone Matrix"),
        BLEND_WEIGHT("Blend Weight"),
        PADDING("Padding");

        private final String displayName;

        EnumUsage(String displayNameIn) {
            this.displayName = displayNameIn;
        }

        public void preDraw(VertexFormat format, int element, int stride, ByteBuffer buffer) {
            VertexFormatElement attr = format.getElement(element);
            int count = attr.getElementCount();
            int constant = attr.getType().getGlConstant();
            buffer.position(format.getOffset(element));
            switch (this) {
                case POSITION:
                    GL11.glVertexPointer(count, constant, stride, buffer);
                    GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
                    break;
                case NORMAL:
                    if (count != 3) {
                        throw new IllegalArgumentException("Normal attribute should have the size 3: " + attr);
                    }

                    GL11.glNormalPointer(constant, stride, buffer);
                    GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
                    break;
                case COLOR:
                    GL11.glColorPointer(count, constant, stride, buffer);
                    GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
                    break;
                case UV:
                    OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + attr.getIndex());
                    GL11.glTexCoordPointer(count, constant, stride, buffer);
                    GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                    OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                case PADDING:
                    break;
            }

        }

        public void postDraw(VertexFormat format, int element, int stride, ByteBuffer buffer) {
            VertexFormatElement attr = format.getElement(element);
            switch (this) {
                case POSITION:
                    GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
                    break;
                case NORMAL:
                    GlStateManager.glDisableClientState(GL11.GL_NORMAL_ARRAY);
                    break;
                case COLOR:
                    GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
                    GlStateManager.resetColor();
                    break;
                case UV:
                    OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + attr.getIndex());
                    GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                    OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                case PADDING:
                    break;
            }

        }

        public String getDisplayName() {
            return this.displayName;
        }
    }
}
