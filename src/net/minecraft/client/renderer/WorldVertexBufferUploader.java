package net.minecraft.client.renderer;

import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.src.Config;
import net.optifine.shaders.SVertexBuilder;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.List;

public class WorldVertexBufferUploader {
    @SuppressWarnings("incomplete-switch")
    public void draw(WorldRenderer buffer) {
        if (buffer.getVertexCount() > 0) {
            if (buffer.getDrawMode() == 7 && Config.isQuadsToTriangles()) {
                buffer.quadsToTriangles();
            }

            VertexFormat vertexformat = buffer.getVertexFormat();
            int i = vertexformat.getSize();
            ByteBuffer bytebuffer = buffer.getByteBuffer();
            List<VertexFormatElement> list = vertexformat.getElements();

            VertexFormatElement.EnumUsage curUsage = null;

            int index = 0;
            for (final VertexFormatElement currentElement : list) {
                bytebuffer.position(vertexformat.getOffset(index));

                currentElement.getUsage().preDraw(vertexformat, index, i, bytebuffer);

                ++index;
            }

            if (buffer.isMultiTexture()) {
                buffer.drawMultiTexture();
            } else if (Config.isShaders()) {
                SVertexBuilder.drawArrays(buffer.getDrawMode(), 0, buffer.getVertexCount(), buffer);
            } else {
                GL11.glDrawArrays(buffer.getDrawMode(), 0, buffer.getVertexCount());
            }

            index = 0;

            for (final VertexFormatElement currentElement : list) {
                currentElement.getUsage().postDraw(vertexformat, index, i, bytebuffer);

                index++;
            }

        }

        buffer.reset();
    }
}
