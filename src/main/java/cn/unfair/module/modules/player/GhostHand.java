package cn.unfair.module.modules.player;

import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.util.ItemUtil;
import cn.unfair.util.TeamUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public class GhostHand extends Module {
    public final BooleanProperty teamsOnly = new BooleanProperty("Team Only", true);
    public final BooleanProperty ignoreWeapons = new BooleanProperty("Ignore Weapons", false);

    public GhostHand() {
        super("GhostHand", false);
    }

    public boolean shouldSkip(Entity entity) {
        return entity instanceof EntityPlayer
                && !TeamUtil.shouldBlockBot((EntityPlayer) entity)
                && (!this.teamsOnly.getValue() || TeamUtil.shouldBlockTeam((EntityPlayer) entity))
                && (!this.ignoreWeapons.getValue() || !ItemUtil.hasRawUnbreakingEnchant());
    }
}
