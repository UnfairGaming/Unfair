package net.minecraft.client.renderer;

import cn.unfair.util.via.ViaBackwardsItemModels;
import com.google.common.collect.Maps;
import lombok.Getter;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemTransformVec3f;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.block.Block;
import net.minecraft.block.ModernBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.src.Config;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.optifine.CustomItems;
import org.lwjgl.util.vector.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class ItemModelMesher {
    private final Map<Integer, ModelResourceLocation> simpleShapes = Maps.newHashMap();
    private final Map<Integer, IBakedModel> simpleShapesCache = Maps.newHashMap();
    private final Map<Item, ItemMeshDefinition> shapers = Maps.newHashMap();
    @Getter
    private final ModelManager modelManager;

    public ItemModelMesher(ModelManager modelManager) {
        this.modelManager = modelManager;
    }

    public TextureAtlasSprite getParticleIcon(Item item) {
        return this.getParticleIcon(item, 0);
    }

    public TextureAtlasSprite getParticleIcon(Item item, int meta) {
        return this.getItemModel(new ItemStack(item, 1, meta)).getParticleTexture();
    }

    public IBakedModel getItemModel(ItemStack stack) {
        Item item = stack == null ? null : stack.getItem();
        String viaModelName = ViaBackwardsItemModels.getModelName(stack);
        String itemModelName = isElytraModel(viaModelName) ? null : viaModelName;
        ModelResourceLocation viaModelLocation = itemModelName == null ? null : new ModelResourceLocation(itemModelName, "inventory");
        IBakedModel ibakedmodel = viaModelLocation != null
                ? this.modelManager.getModel(viaModelLocation)
                : item == null ? null : this.getItemModel(item, this.getMetadata(stack));

        if (ibakedmodel == null) {
            ItemMeshDefinition itemmeshdefinition = this.shapers.get(item);

            if (itemmeshdefinition != null) {
                ibakedmodel = this.modelManager.getModel(itemmeshdefinition.getModelLocation(stack));
            }
        }

        if (ibakedmodel == null) {
            ibakedmodel = this.modelManager.getMissingModel();
        }

        if (item != null && Config.isCustomItems()) {
            ibakedmodel = CustomItems.getCustomItemModel(stack, ibakedmodel, null, true);
        }

        return LegacyHandBakedModel.wrap(ibakedmodel, itemModelName, isModernBlockItem(stack, itemModelName));
    }

    private static boolean isModernBlockItem(ItemStack stack, String modelName) {
        if (ViaBackwardsItemModels.isBlockModel(modelName)) {
            return true;
        }
        Block block = modelName == null ? null : Block.blockRegistry.getObject(ResourceLocation.of(modelName));
        if (!(block instanceof ModernBlock) && stack != null && stack.getItem() instanceof ItemBlock) {
            block = ((ItemBlock) stack.getItem()).getBlock();
        }
        return block instanceof ModernBlock;
    }

    private static boolean isElytraModel(String modelName) {
        return modelName != null && (modelName.equals("elytra") || modelName.equals("elytra_broken"));
    }

    protected int getMetadata(ItemStack stack) {
        return stack.isItemStackDamageable() ? 0 : stack.getMetadata();
    }

    protected IBakedModel getItemModel(Item item, int meta) {
        return this.simpleShapesCache.get(this.getIndex(item, meta));
    }

    private int getIndex(Item item, int meta) {
        return Item.getIdFromItem(item) << 16 | meta;
    }

    public void register(Item item, int meta, ModelResourceLocation location) {
        this.simpleShapes.put(this.getIndex(item, meta), location);
        this.simpleShapesCache.put(this.getIndex(item, meta), this.modelManager.getModel(location));
    }

    public void register(Item item, ItemMeshDefinition definition) {
        this.shapers.put(item, definition);
    }

    public void rebuildCache() {
        this.simpleShapesCache.clear();

        for (Entry<Integer, ModelResourceLocation> entry : this.simpleShapes.entrySet()) {
            this.simpleShapesCache.put(entry.getKey(), this.modelManager.getModel(entry.getValue()));
        }
    }

    private static class LegacyHandBakedModel implements IBakedModel {
        private static final ItemTransformVec3f LEGACY_TOOL_THIRD_PERSON = transform(0.0F, 90.0F, -35.0F, 0.0F, 1.25F, -3.5F, 0.85F, 0.85F, 0.85F);
        private static final ItemTransformVec3f LEGACY_BOW_THIRD_PERSON = transform(5.0F, 80.0F, -45.0F, 0.75F, 0.0F, 0.25F, 1.0F, 1.0F, 1.0F);
        private static final ItemTransformVec3f LEGACY_FLAT_THIRD_PERSON = transform(-90.0F, 0.0F, 0.0F, 0.0F, 1.0F, -3.0F, 0.55F, 0.55F, 0.55F);
        private static final ItemTransformVec3f LEGACY_FIRST_PERSON = transform(0.0F, -135.0F, 25.0F, 0.0F, 4.0F, 2.0F, 1.7F, 1.7F, 1.7F);

        private final IBakedModel parent;
        private final ItemCameraTransforms transforms;

        private LegacyHandBakedModel(IBakedModel parent, ItemTransformVec3f thirdPerson, boolean resetGuiTransform) {
            this.parent = parent;
            ItemCameraTransforms original = parent.getItemCameraTransforms();
            this.transforms = new ItemCameraTransforms(
                    thirdPerson == null ? original.thirdPerson : thirdPerson,
                    thirdPerson == null ? original.firstPerson : LEGACY_FIRST_PERSON,
                    original.head,
                    resetGuiTransform ? ItemTransformVec3f.DEFAULT : original.gui,
                    original.ground,
                    original.fixed
            );
        }

        private static IBakedModel wrap(IBakedModel model, String viaModelName, boolean modernBlockItem) {
            ItemTransformVec3f thirdPerson = getLegacyThirdPersonTransform(model, viaModelName);
            boolean resetGuiTransform = modernBlockItem && model.isGui3d();
            return thirdPerson == null && !resetGuiTransform
                    ? model
                    : new LegacyHandBakedModel(model, thirdPerson, resetGuiTransform);
        }

        private static ItemTransformVec3f getLegacyThirdPersonTransform(IBakedModel model, String modelName) {
            if (modelName == null || modelName.equals("shield") || modelName.equals("shield_blocking") || modelName.equals("elytra") || modelName.equals("elytra_broken")) {
                return null;
            }

            if (modelName.equals("crossbow") || modelName.startsWith("crossbow_")) {
                return LEGACY_BOW_THIRD_PERSON;
            }

            if (modelName.endsWith("_sword") || modelName.endsWith("_pickaxe") || modelName.endsWith("_axe") || modelName.endsWith("_shovel") || modelName.endsWith("_hoe") || modelName.endsWith("_spear") || modelName.equals("trident")) {
                return LEGACY_TOOL_THIRD_PERSON;
            }

            if (model.isGui3d()) {
                return null;
            }

            return LEGACY_FLAT_THIRD_PERSON;
        }

        private static ItemTransformVec3f transform(float rotX, float rotY, float rotZ, float transX, float transY, float transZ, float scaleX, float scaleY, float scaleZ) {
            return new ItemTransformVec3f(new Vector3f(rotX, rotY, rotZ), new Vector3f(transX / 16.0F, transY / 16.0F, transZ / 16.0F), new Vector3f(scaleX, scaleY, scaleZ));
        }

        public List<BakedQuad> getFaceQuads(EnumFacing facing) {
            return this.parent.getFaceQuads(facing);
        }

        public List<BakedQuad> getGeneralQuads() {
            return this.parent.getGeneralQuads();
        }

        public boolean isAmbientOcclusion() {
            return this.parent.isAmbientOcclusion();
        }

        public boolean isGui3d() {
            return this.parent.isGui3d();
        }

        public boolean isBuiltInRenderer() {
            return this.parent.isBuiltInRenderer();
        }

        public TextureAtlasSprite getParticleTexture() {
            return this.parent.getParticleTexture();
        }

        public ItemCameraTransforms getItemCameraTransforms() {
            return this.transforms;
        }
    }
}
