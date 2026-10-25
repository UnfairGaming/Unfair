package net.optifine.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.util.EnumFacing;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class ModelUtils
{
    public static IBakedModel duplicateModel(IBakedModel model)
    {
        List list = duplicateQuadList(model.getGeneralQuads());
        EnumFacing[] aenumfacing = EnumFacing.VALUES;
        List list1 = new ArrayList();

        for (int i = 0; i < aenumfacing.length; ++i)
        {
            EnumFacing enumfacing = aenumfacing[i];
            List list2 = model.getFaceQuads(enumfacing);
            List list3 = duplicateQuadList(list2);
            list1.add(list3);
        }

        SimpleBakedModel simplebakedmodel = new SimpleBakedModel(list, list1, model.isAmbientOcclusion(), model.isGui3d(), model.getParticleTexture(), model.getItemCameraTransforms());
        return simplebakedmodel;
    }

    public static List duplicateQuadList(List lists)
    {
        List list = new ArrayList();

        for (Object o : lists)
        {
            BakedQuad bakedquad = (BakedQuad) o;
            BakedQuad bakedquad1 = duplicateQuad(bakedquad);
            list.add(bakedquad1);
        }

        return list;
    }

    public static BakedQuad duplicateQuad(BakedQuad quad)
    {
        BakedQuad bakedquad = new BakedQuad(quad.getVertexData().clone(), quad.getTintIndex(), quad.getFace(), quad.getSprite());
        return bakedquad;
    }
}
