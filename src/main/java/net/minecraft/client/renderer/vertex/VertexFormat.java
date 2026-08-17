package net.minecraft.client.renderer.vertex;

import com.google.common.collect.Lists;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class VertexFormat {
    private static final Logger LOGGER = LogManager.getLogger("VertexFormat");
    @Getter
    private final List<VertexFormatElement> elements;
    private final List<Integer> offsets;
    private final List<Integer> uvOffsetsById;
    /**
     * The total size of this vertex format.
     */
    private int vertexSize;
    private int colorElementOffset;
    private int normalElementOffset;

    public VertexFormat(VertexFormat vertexFormatIn) {
        this();

        for (int i = 0; i < vertexFormatIn.getElementCount(); ++i) {
            this.addElement(vertexFormatIn.getElement(i));
        }

        this.vertexSize = vertexFormatIn.getSize();
    }

    public VertexFormat() {
        this.elements = Lists.newArrayList();
        this.offsets = Lists.newArrayList();
        this.vertexSize = 0;
        this.colorElementOffset = -1;
        this.uvOffsetsById = Lists.newArrayList();
        this.normalElementOffset = -1;
    }

    public void clear() {
        this.elements.clear();
        this.offsets.clear();
        this.colorElementOffset = -1;
        this.uvOffsetsById.clear();
        this.normalElementOffset = -1;
        this.vertexSize = 0;
    }

    public void deepCopy(VertexFormat vertexFormatIn) {
        for (int i = 0; i < vertexFormatIn.getElementCount(); ++i) {
            this.addElement(vertexFormatIn.getElement(i));
        }

        this.vertexSize = vertexFormatIn.getSize();
    }

    @SuppressWarnings("incomplete-switch")
    public VertexFormat addElement(VertexFormatElement element) {
        if (element.isPositionElement() && this.hasPosition()) {
            LOGGER.warn("VertexFormat error: Trying to add a position VertexFormatElement when one already exists, ignoring.");
            return this;
        } else {
            this.elements.add(element);
            this.offsets.add(this.vertexSize);

            switch (element.getUsage()) {
                case NORMAL:
                    this.normalElementOffset = this.vertexSize;
                    break;

                case COLOR:
                    this.colorElementOffset = this.vertexSize;
                    break;

                case UV:
                    this.uvOffsetsById.add(element.getIndex(), this.vertexSize);
            }

            this.vertexSize += element.getSize();
            return this;
        }
    }

    public boolean hasNormal() {
        return this.normalElementOffset >= 0;
    }

    public int getNormalOffset() {
        return this.normalElementOffset;
    }

    public boolean hasColor() {
        return this.colorElementOffset >= 0;
    }

    public int getColorOffset() {
        return this.colorElementOffset;
    }

    public boolean hasUvOffset(int id) {
        return this.uvOffsetsById.size() - 1 >= id;
    }

    public int getUvOffsetById(int id) {
        return this.uvOffsetsById.get(id);
    }

    public String toString() {
        StringBuilder s = new StringBuilder("format: " + this.elements.size() + " elements: ");

        for (int i = 0; i < this.elements.size(); ++i) {
            s.append(this.elements.get(i).toString());

            if (i != this.elements.size() - 1) {
                s.append(" ");
            }
        }

        return s.toString();
    }

    private boolean hasPosition() {
        int i = 0;

        for (int j = this.elements.size(); i < j; ++i) {
            VertexFormatElement vertexformatelement = this.elements.get(i);

            if (vertexformatelement.isPositionElement()) {
                return true;
            }
        }

        return false;
    }

    public int getIntegerSize() {
        return this.getSize() / 4;
    }

    public int getSize() {
        return this.vertexSize;
    }

    public int getElementCount() {
        return this.elements.size();
    }

    public VertexFormatElement getElement(int index) {
        return this.elements.get(index);
    }

    public int getOffset(int p_181720_1_) {
        return this.offsets.get(p_181720_1_);
    }

    public boolean equals(Object p_equals_1_) {
        if (this == p_equals_1_) {
            return true;
        } else if (p_equals_1_ != null && this.getClass() == p_equals_1_.getClass()) {
            VertexFormat vertexformat = (VertexFormat) p_equals_1_;
            return this.vertexSize == vertexformat.vertexSize && (this.elements.equals(vertexformat.elements) && this.offsets.equals(vertexformat.offsets));
        } else {
            return false;
        }
    }

    public int hashCode() {
        int i = this.elements.hashCode();
        i = 31 * i + this.offsets.hashCode();
        i = 31 * i + this.vertexSize;
        return i;
    }
}
