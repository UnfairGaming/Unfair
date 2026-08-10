package net.minecraft.client.resources;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.rendering.ResPackPreview;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

import java.util.List;

public abstract class ResourcePackListEntry implements GuiListExtended.IGuiListEntry {
    private static final ResourceLocation RESOURCE_PACKS_TEXTURE = ResourceLocation.of("textures/gui/resource_packs.png");
    private static final IChatComponent RP_INCOMPATIBLE = new ChatComponentTranslation("resourcePack.incompatible");
    private static final IChatComponent RP_INCOMPATIBLE_OLD = new ChatComponentTranslation("resourcePack.incompatible.old");
    private static final IChatComponent RP_INCOMPATIBLE_NEW = new ChatComponentTranslation("resourcePack.incompatible.new");
    protected final Minecraft mc;
    protected final GuiScreenResourcePacks resourcePacksGUI;

    public ResourcePackListEntry(GuiScreenResourcePacks resourcePacksGUIIn) {
        this.resourcePacksGUI = resourcePacksGUIIn;
        this.mc = Minecraft.getMinecraft();
    }

    public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected) {
        int format = this.getPackFormat();

        if (format != 1) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            Gui.drawRect(x - 1, y - 1, x + listWidth - 9, y + slotHeight + 1, -8978432);
        }

        this.bindResourcePackIcon();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0.0F, 0.0F, 32, 32, 32.0F, 32.0F);
        String name = this.getName();
        String desc = this.getDescription();

        if ((this.mc.gameSettings.touchscreen || isSelected) && this.canBeMoved()) {
            this.mc.getTextureManager().bindTexture(RESOURCE_PACKS_TEXTURE);
            Gui.drawRect(x, y, x + 32, y + 32, -1601138544);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            int j = mouseX - x;
            int k = mouseY - y;

            if (format < 1) {
                name = RP_INCOMPATIBLE.getFormattedText();
                desc = RP_INCOMPATIBLE_OLD.getFormattedText();
            } else if (format > 1) {
                name = RP_INCOMPATIBLE.getFormattedText();
                desc = RP_INCOMPATIBLE_NEW.getFormattedText();
            }

            if (this.canBeSelected()) {
                if (j < 32) {
                    Gui.drawModalRectWithCustomSizedTexture(x, y, 0.0F, 32.0F, 32, 32, 256.0F, 256.0F);
                } else {
                    Gui.drawModalRectWithCustomSizedTexture(x, y, 0.0F, 0.0F, 32, 32, 256.0F, 256.0F);
                }
            } else {
                if (this.canBeUnselected()) {
                    if (j < 16) {
                        Gui.drawModalRectWithCustomSizedTexture(x, y, 32.0F, 32.0F, 32, 32, 256.0F, 256.0F);
                    } else {
                        Gui.drawModalRectWithCustomSizedTexture(x, y, 32.0F, 0.0F, 32, 32, 256.0F, 256.0F);
                    }
                }

                if (this.canMoveUp()) {
                    if (j < 32 && j > 16 && k < 16) {
                        Gui.drawModalRectWithCustomSizedTexture(x, y, 96.0F, 32.0F, 32, 32, 256.0F, 256.0F);
                    } else {
                        Gui.drawModalRectWithCustomSizedTexture(x, y, 96.0F, 0.0F, 32, 32, 256.0F, 256.0F);
                    }
                }

                if (this.canMoveDown()) {
                    if (j < 32 && j > 16 && k > 16) {
                        Gui.drawModalRectWithCustomSizedTexture(x, y, 64.0F, 32.0F, 32, 32, 256.0F, 256.0F);
                    } else {
                        Gui.drawModalRectWithCustomSizedTexture(x, y, 64.0F, 0.0F, 32, 32, 256.0F, 256.0F);
                    }
                }
            }
        }

        int i1 = this.mc.fontRendererObj.getStringWidth(name);

        if (i1 > 157) {
            name = this.mc.fontRendererObj.trimStringToWidth(name, 157 - this.mc.fontRendererObj.getStringWidth("...")) + "...";
        }

        this.mc.fontRendererObj.drawStringWithShadow(name, (float) (x + 32 + 2), (float) (y + 1), 16777215);
        List<String> list = this.mc.fontRendererObj.listFormattedStringToWidth(desc, 157);

        for (int l = 0; l < 2 && l < list.size(); ++l) {
            this.mc.fontRendererObj.drawStringWithShadow(list.get(l), (float) (x + 32 + 2), (float) (y + 12 + 10 * l), 8421504);
        }

        boolean hovering = mouseX >= x - 1 && mouseX <= x + listWidth - 10 && mouseY >= y - 1 && mouseY <= y + slotHeight;

        if (hovering && !(this instanceof ResourcePackListEntryDefault)) {
            ResourcePackRepository.Entry entry = ((ResourcePackListEntryFound) this).getEntry();

            entry.loadPreviewsIfNotLoaded();

            if (entry.getPreviewsLoaded().get()) {
                resourcePacksGUI.renderCalls.add(() -> {
                    double posX = mouseX + 4;
                    double posY = mouseY + 4;

                    Gui.drawRect((int) posX, (int) posY, (int) posX + 215, (int) posY + 40, 0x326F6F6F);

                    double startX = posX + 4;
                    double startY = posY + 4;

                    List<ResPackPreview> previewImages = entry.getPreviewImages();
                    for (int i = 0; i < previewImages.size(); i++) {
                        ResPackPreview previewImage = previewImages.get(i);
                        previewImage.render(startX, startY, 16, 16);
                        startX += 17;

                        if (i == 7) {
                            startX = posX + 4;
                            startY += 17;
                        }
                    }

                    Gui.drawRect((int) (posX + 143), (int) (posY + 4), (int) (posX + 144), (int) (posY + 36), 0xFFFFFFFF);

                    List<String> resPackInfo = entry.getResPackInfo();
                    double yOffset = posY + 3;
                    for (String s : resPackInfo) {
                        mc.fontRendererObj.drawStringWithShadow(s, (float) (posX + 4 + 135 + 4 + 4), (float) yOffset, 16777215);
                        yOffset += mc.fontRendererObj.FONT_HEIGHT;
                    }
                });
            }
        }
    }

    protected abstract int getPackFormat();

    protected abstract String getDescription();

    protected abstract String getName();

    protected abstract void bindResourcePackIcon();

    protected boolean canBeMoved() {
        return true;
    }

    protected boolean canBeSelected() {
        return !this.resourcePacksGUI.hasResourcePackEntry(this);
    }

    protected boolean canBeUnselected() {
        return this.resourcePacksGUI.hasResourcePackEntry(this);
    }

    protected boolean canMoveUp() {
        List<ResourcePackListEntry> list = this.resourcePacksGUI.getListContaining(this);
        int i = list.indexOf(this);
        return i > 0 && list.get(i - 1).canBeMoved();
    }

    protected boolean canMoveDown() {
        List<ResourcePackListEntry> list = this.resourcePacksGUI.getListContaining(this);
        int i = list.indexOf(this);
        return i >= 0 && i < list.size() - 1 && list.get(i + 1).canBeMoved();
    }

    /**
     * Returns true if the mouse has been pressed on this control.
     */
    public boolean mousePressed(int slotIndex, int p_148278_2_, int p_148278_3_, int p_148278_4_, int p_148278_5_, int p_148278_6_) {
        if (this.canBeMoved() && p_148278_5_ <= 32) {
            if (this.canBeSelected()) {
                this.resourcePacksGUI.markChanged();
                int j = this.getPackFormat();

                if (j != 1) {
                    String s1 = I18n.format("resourcePack.incompatible.confirm.title");
                    String s = I18n.format("resourcePack.incompatible.confirm." + (j > 1 ? "new" : "old"));
                    this.mc.displayGuiScreen(new GuiYesNo(new GuiYesNoCallback() {
                        public void confirmClicked(boolean result, int id) {
                            List<ResourcePackListEntry> list2 = ResourcePackListEntry.this.resourcePacksGUI.getListContaining(ResourcePackListEntry.this);
                            ResourcePackListEntry.this.mc.displayGuiScreen(ResourcePackListEntry.this.resourcePacksGUI);

                            if (result) {
                                list2.remove(ResourcePackListEntry.this);
                                ResourcePackListEntry.this.resourcePacksGUI.getSelectedResourcePacks().add(0, ResourcePackListEntry.this);
                            }
                        }
                    }, s1, s, 0));
                } else {
                    this.resourcePacksGUI.getListContaining(this).remove(this);
                    this.resourcePacksGUI.getSelectedResourcePacks().add(0, this);
                }

                return true;
            }

            if (p_148278_5_ < 16 && this.canBeUnselected()) {
                this.resourcePacksGUI.getListContaining(this).remove(this);
                this.resourcePacksGUI.getAvailableResourcePacks().add(0, this);
                this.resourcePacksGUI.markChanged();
                return true;
            }

            if (p_148278_5_ > 16 && p_148278_6_ < 16 && this.canMoveUp()) {
                List<ResourcePackListEntry> list1 = this.resourcePacksGUI.getListContaining(this);
                int k = list1.indexOf(this);
                list1.remove(this);
                list1.add(k - 1, this);
                this.resourcePacksGUI.markChanged();
                return true;
            }

            if (p_148278_5_ > 16 && p_148278_6_ > 16 && this.canMoveDown()) {
                List<ResourcePackListEntry> list = this.resourcePacksGUI.getListContaining(this);
                int i = list.indexOf(this);
                list.remove(this);
                list.add(i + 1, this);
                this.resourcePacksGUI.markChanged();
                return true;
            }
        }

        return false;
    }

    public void setSelected(int p_178011_1_, int p_178011_2_, int p_178011_3_) {
    }

    /**
     * Fired when the mouse button is released. Arguments: index, x, y, mouseEvent, relativeX, relativeY
     */
    public void mouseReleased(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) {
    }
}
