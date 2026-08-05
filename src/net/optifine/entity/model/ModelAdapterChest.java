package net.optifine.entity.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityChestRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntityChest;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class ModelAdapterChest extends ModelAdapter
{
    public ModelAdapterChest()
    {
        super(TileEntityChest.class, "chest", 0.0F);
    }

    public ModelBase makeModel()
    {
        return new ModelChest();
    }

    public ModelRenderer getModelRenderer(ModelBase model, String modelPart)
    {
        if (!(model instanceof ModelChest))
        {
            return null;
        }
        else
        {
            ModelChest modelchest = (ModelChest)model;
            return modelPart.equals("lid") ? modelchest.chestLid : (modelPart.equals("base") ? modelchest.chestBelow : (modelPart.equals("knob") ? modelchest.chestKnob : null));
        }
    }

    public String[] getModelRendererNames()
    {
        return new String[] {"lid", "base", "knob"};
    }

    public IEntityRenderer makeEntityRender(ModelBase modelBase, float shadowSize)
    {
        TileEntityRendererDispatcher tileentityrendererdispatcher = TileEntityRendererDispatcher.instance;
        TileEntitySpecialRenderer tileentityspecialrenderer = tileentityrendererdispatcher.getSpecialRendererByClass(TileEntityChest.class);

        if (!(tileentityspecialrenderer instanceof TileEntityChestRenderer))
        {
            return null;
        }
        else
        {
            if (tileentityspecialrenderer.getEntityClass() == null)
            {
                tileentityspecialrenderer = new TileEntityChestRenderer();
                tileentityspecialrenderer.setRendererDispatcher(tileentityrendererdispatcher);
            }

            ((TileEntityChestRenderer)tileentityspecialrenderer).simpleChest = (ModelChest)modelBase;
            return tileentityspecialrenderer;
        }
    }
}
