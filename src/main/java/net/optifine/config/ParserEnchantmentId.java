package net.optifine.config;

import net.minecraft.enchantment.Enchantment;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class ParserEnchantmentId implements IParserInt
{
    public int parse(String str, int defVal)
    {
        Enchantment enchantment = Enchantment.getEnchantmentByLocation(str);
        return enchantment == null ? defVal : enchantment.effectId;
    }
}
