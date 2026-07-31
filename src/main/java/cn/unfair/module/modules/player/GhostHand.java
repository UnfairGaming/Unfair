package cn.unfair.module.modules.player;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.util.ItemUtil;
import cn.unfair.util.TeamUtil;

public class GhostHand extends Module {
    public final BooleanProperty teamsOnly = new BooleanProperty("team-only", true);
    public final BooleanProperty ignoreWeapons = new BooleanProperty("ignore-weapons", false);

    public GhostHand() {
        super("GhostHand", false);
    }

    public boolean shouldSkip(Entity entity) {
        return entity instanceof EntityPlayer
                && !TeamUtil.isBot((EntityPlayer) entity)
                && (!this.teamsOnly.getValue() || TeamUtil.isSameTeam((EntityPlayer) entity))
                && (!this.ignoreWeapons.getValue() || !ItemUtil.hasRawUnbreakingEnchant());
    }
}
