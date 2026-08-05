package net.minecraft.client.resources;

import com.google.gson.JsonParseException;
import lombok.SneakyThrows;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.data.PackMetadataSection;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.image.BufferedImage;
import java.io.IOException;

public class ResourcePackListEntryDefault extends ResourcePackListEntry {
    private static final Logger logger = LogManager.getLogger("ResourcePackListEntryDefault");
    private final IResourcePack field_148320_d;
    private final ResourceLocation resourcePackIcon;

    @SneakyThrows
    public ResourcePackListEntryDefault(GuiScreenResourcePacks resourcePacksGUIIn) {
        super(resourcePacksGUIIn);
        this.field_148320_d = this.mc.getResourcePackRepository().rprDefaultResourcePack;
        DynamicTexture dynamictexture;

        try {
            BufferedImage packImage = this.field_148320_d.getPackImage();
            dynamictexture = new DynamicTexture(packImage);

            if (packImage instanceof AutoCloseable)
                ((AutoCloseable) packImage).close();
        } catch (IOException var4) {
            dynamictexture = TextureUtil.missingTexture;
        }

        this.resourcePackIcon = this.mc.getTextureManager().getDynamicTextureLocation("texturepackicon", dynamictexture);
    }

    protected int getPackFormat() {
        return 1;
    }

    protected String getDescription() {
        try {
            PackMetadataSection packmetadatasection = this.field_148320_d.getPackMetadata(this.mc.getResourcePackRepository().rprMetadataSerializer, "pack");

            if (packmetadatasection != null) {
                return packmetadatasection.getPackDescription().getFormattedText();
            }
        } catch (JsonParseException | IOException jsonparseexception) {
            logger.error("Couldn't load metadata info", jsonparseexception);
        }

        return EnumChatFormatting.RED + "Missing " + "pack.mcmeta" + " :(";
    }

    protected boolean canBeSelected() {
        return false;
    }

    protected boolean canBeUnselected() {
        return false;
    }

    protected boolean canMoveUp() {
        return false;
    }

    protected boolean canMoveDown() {
        return false;
    }

    protected String getName() {
        return "Default";
    }

    protected void bindResourcePackIcon() {
        this.mc.getTextureManager().bindTexture(this.resourcePackIcon);
    }

    protected boolean canBeMoved() {
        return false;
    }
}
