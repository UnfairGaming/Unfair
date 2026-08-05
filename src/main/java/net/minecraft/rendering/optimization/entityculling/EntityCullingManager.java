package net.minecraft.rendering.optimization.entityculling;

import net.minecraft.rendering.culling.OcclusionCullingInstance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashSet;

public class EntityCullingManager {

    private static final Logger LOGGER = LogManager.getLogger();
    public static EntityCullingManager instance = new EntityCullingManager();
    public OcclusionCullingInstance culling;
    public static boolean enabled = true;
    public CullTask cullTask;

    public int renderedBlockEntities = 0;
    public int skippedBlockEntities = 0;
    public int renderedEntities = 0;
    public int skippedEntities = 0;

    public void onInitialize() {
        LOGGER.debug("[*] Initializing Entity Culling!");
        instance = this;
        culling = new OcclusionCullingInstance(128, new Provider());
        cullTask = new CullTask(culling, new HashSet<>(Collections.singletonList("tile.beacon")));

        Thread cullThread = new Thread(cullTask, "CullThread");
        cullThread.setUncaughtExceptionHandler((thread, ex) -> {
            LOGGER.error("Cull thread exception", ex);
        });
        cullThread.setPriority(10);
        cullThread.start();
    }

    public void worldTick() {
        cullTask.requestCull = true;
    }

    public void clientTick() {
        cullTask.requestCull = true;
    }

}
