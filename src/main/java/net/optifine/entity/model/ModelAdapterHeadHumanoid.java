package net.optifine.entity.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelHumanoidHead;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySkullRenderer;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntitySkull;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class ModelAdapterHeadHumanoid extends ModelAdapter
{
    public ModelAdapterHeadHumanoid()
    {
        super(TileEntitySkull.class, "head_humanoid", 0.0F);
    }

    public ModelBase makeModel()
    {
        return new ModelHumanoidHead();
    }

    public ModelRenderer getModelRenderer(ModelBase model, String modelPart)
    {
        if (!(model instanceof ModelHumanoidHead))
        {
            return null;
        }
        else
        {
            ModelHumanoidHead modelhumanoidhead = (ModelHumanoidHead)model;
            return modelPart.equals("head") ? modelhumanoidhead.skeletonHead : (modelPart.equals("head2") ? modelhumanoidhead.head : null);
        }
    }

    public String[] getModelRendererNames()
    {
        return new String[] {"head"};
    }

    public IEntityRenderer makeEntityRender(ModelBase modelBase, float shadowSize)
    {
        TileEntityRendererDispatcher tileentityrendererdispatcher = TileEntityRendererDispatcher.instance;
        TileEntitySpecialRenderer tileentityspecialrenderer = tileentityrendererdispatcher.getSpecialRendererByClass(TileEntitySkull.class);

        if (!(tileentityspecialrenderer instanceof TileEntitySkullRenderer))
        {
            return null;
        }
        else
        {
            if (tileentityspecialrenderer.getEntityClass() == null)
            {
                tileentityspecialrenderer = new TileEntitySkullRenderer();
                tileentityspecialrenderer.setRendererDispatcher(tileentityrendererdispatcher);
            }

            ((TileEntitySkullRenderer)tileentityspecialrenderer).humanoidHead = (ModelHumanoidHead)modelBase;
            return tileentityspecialrenderer;
        }
    }
}
