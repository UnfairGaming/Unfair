package cn.unfair.module.modules.render;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.AttackEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class HitParticles extends Module {
    private static final int PARTICLE_CRIT = 4;
    private static final int PARTICLE_CRIT_MAGIC = 13;
    private static final double ARROW_SCAN_EXPAND = 96.0D;
    private static final int[] ARGS_NONE = new int[0];
    private static final Random RANDOM = new Random();
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final String[] PARTICLE_NAMES = {
            "Angry Villager",
            "Blood",
            "Cloud",
            "Confetti",
            "Critical",
            "Crit/Magic Crit",
            "Enchantment",
            "Explosion",
            "Flame",
            "Happy Villager",
            "Heart",
            "Instant Spell",
            "Lava",
            "Magic Critical",
            "Mob Spell",
            "Music Note",
            "Portal",
            "Slime",
            "Smoke",
            "Snow",
            "Spark",
            "Spell",
            "Splash",
            "Witch"
    };

    private static final int[] PARTICLE_IDS = {
            20, 37, 32, 30, 9, -1, 25, 1, 26, 21, 34, 14, 27, 10, 15, 23, 24, 33, 11, 31, 3, 13, 5, 17
    };

    private static final int[] PARTICLE_COUNTS = {
            8, 32, 16, 16, 32, -1, 16, 1, 16, 16, 8, 16, 16, 32, 16, 8, 32, 16, 16, 16, 16, 16, 32, 16
    };

    private static final float[] PARTICLE_OFFSETS = {
            2.5f, 1.2f, 0.2f, 3.0f, 1.0f, -1.0f, 1.0f, 1.0f, 0.05f, 2.5f, 2.0f, 0.7f, 1.0f, 1.0f, 1.0f, 3.0f, 1.0f, 0.5f, 0.08f, 1.0f, 0.1f, 1.0f, 1.0f, 0.5f
    };

    private static final boolean[] PARTICLE_IGNORE_DIST = {
            true, true, false, true, false, false, true, true, false, true, true, false, false, false, false, true, true, false, true, true, false, false, false, false
    };

    private static final int[][] PARTICLE_ARGS = {
            ARGS_NONE,
            new int[]{Block.getIdFromBlock(Blocks.netherrack)},
            ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE,
            ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE,
            ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE
    };

    private final BooleanProperty onMelee = new BooleanProperty("melee-hits", true);
    private final ModeProperty meleeParticle = new ModeProperty("melee-particle", 4, PARTICLE_NAMES);
    private final IntProperty meleeMultiplier = new IntProperty("melee-multiplier", 1, 1, 8);
    private final BooleanProperty onRanged = new BooleanProperty("arrow-hits", true);
    private final ModeProperty rangedParticle = new ModeProperty("ranged-particle", 4, PARTICLE_NAMES);
    private final IntProperty rangedMultiplier = new IntProperty("ranged-multiplier", 1, 1, 8);

    private final Map<Integer, Integer> rangedSpawnForArrow = new HashMap<>();

    public HitParticles() {
        super("HitParticles", false, true);
    }

    @Override
    public void onDisabled() {
        rangedSpawnForArrow.clear();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!isEnabled() || !onMelee.getValue() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (!(event.getTarget() instanceof EntityLivingBase target)) {
            return;
        }
        spawnParticleType(meleeParticle.getValue(), target, meleeMultiplier.getValue());
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || !onRanged.getValue() || event.type() != EventType.POST || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        pruneArrowDedupeMap();

        AxisAlignedBB scan = mc.thePlayer.getEntityBoundingBox().expand(ARROW_SCAN_EXPAND, ARROW_SCAN_EXPAND, ARROW_SCAN_EXPAND);
        @SuppressWarnings("unchecked")
        List<EntityArrow> arrows = mc.theWorld.getEntitiesWithinAABB(EntityArrow.class, scan);
        for (EntityArrow arrow : arrows) {
            if (arrow.shootingEntity != mc.thePlayer) {
                continue;
            }
            EntityLivingBase target = getCollisionEntity(arrow);
            if (target == null || target.hurtTime > 0) {
                continue;
            }
            int arrowId = arrow.getEntityId();
            int targetId = target.getEntityId();
            Integer prev = rangedSpawnForArrow.get(arrowId);
            if (prev != null && prev == targetId) {
                continue;
            }
            rangedSpawnForArrow.put(arrowId, targetId);
            spawnParticleType(rangedParticle.getValue(), target, rangedMultiplier.getValue());
        }
    }

    private void pruneArrowDedupeMap() {
        Iterator<Map.Entry<Integer, Integer>> iterator = rangedSpawnForArrow.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Integer> entry = iterator.next();
            if (mc.theWorld.getEntityByID(entry.getKey()) == null) {
                iterator.remove();
            }
        }
    }

    private void spawnParticleType(int index, Entity entity, int multiplier) {
        if (index < 0 || index >= PARTICLE_IDS.length) {
            return;
        }

        int id = PARTICLE_IDS[index];
        if (id == -1) {
            spawnParticleRaw(9, entity, multiplier, PARTICLE_COUNTS[PARTICLE_CRIT], PARTICLE_OFFSETS[PARTICLE_CRIT], PARTICLE_IGNORE_DIST[PARTICLE_CRIT], PARTICLE_ARGS[PARTICLE_CRIT]);
            spawnParticleRaw(10, entity, multiplier, PARTICLE_COUNTS[PARTICLE_CRIT_MAGIC], PARTICLE_OFFSETS[PARTICLE_CRIT_MAGIC], PARTICLE_IGNORE_DIST[PARTICLE_CRIT_MAGIC], PARTICLE_ARGS[PARTICLE_CRIT_MAGIC]);
        } else {
            spawnParticleRaw(id, entity, multiplier, PARTICLE_COUNTS[index], PARTICLE_OFFSETS[index], PARTICLE_IGNORE_DIST[index], PARTICLE_ARGS[index]);
        }
    }

    private void spawnParticleRaw(int id, Entity entity, int multiplier, int count, float offset, boolean ignoreDistance, int[] args) {
        for (int i = 0; i < count * multiplier; i++) {
            double xOffset = RANDOM.nextFloat() * (offset * 2.0f) - offset;
            double yOffset = RANDOM.nextFloat() * (offset * 2.0f) - offset;
            double zOffset = RANDOM.nextFloat() * (offset * 2.0f) - offset;
            if (ignoreDistance || xOffset * xOffset + yOffset * yOffset + zOffset * zOffset <= 1.0D) {
                double x = entity.posX + xOffset * entity.width / 4.0D;
                double y = entity.getEntityBoundingBox().minY + entity.height / 2.0D + yOffset * entity.height / 4.0D;
                double z = entity.posZ + zOffset * entity.width / 4.0D;
                mc.effectRenderer.spawnEffectParticle(id, x, y, z, xOffset, yOffset, zOffset, args);
            }
        }
    }

    private static EntityLivingBase getCollisionEntity(EntityArrow arrow) {
        World world = arrow.worldObj;
        Vec3 pos = new Vec3(arrow.posX, arrow.posY, arrow.posZ);
        Vec3 motionEnd = new Vec3(arrow.posX + arrow.motionX, arrow.posY + arrow.motionY, arrow.posZ + arrow.motionZ);
        MovingObjectPosition rayTrace = world.rayTraceBlocks(pos, motionEnd, false, true, false);
        Vec3 traceEnd = rayTrace != null ? rayTrace.hitVec : motionEnd;

        EntityLivingBase target = null;
        double closestSq = 0.0D;
        AxisAlignedBB search = arrow.getEntityBoundingBox().addCoord(arrow.motionX, arrow.motionY, arrow.motionZ).expand(1.0D, 1.0D, 1.0D);
        @SuppressWarnings("unchecked")
        List<Entity> entities = world.getEntitiesWithinAABBExcludingEntity(arrow, search);

        for (Entity entity : entities) {
            if (!(entity instanceof EntityLivingBase living)) {
                continue;
            }
            if (!living.canBeCollidedWith() || living == arrow.shootingEntity) {
                continue;
            }

            AxisAlignedBB collisionBox = entity.getEntityBoundingBox().expand(0.3D, 0.3D, 0.3D);
            MovingObjectPosition collision = collisionBox.calculateIntercept(pos, traceEnd);
            if (collision == null) {
                continue;
            }

            double distSq = pos.squareDistanceTo(collision.hitVec);
            if (distSq >= closestSq && closestSq != 0.0D) {
                continue;
            }
            target = living;
            closestSq = distSq;
        }

        return target;
    }
}
