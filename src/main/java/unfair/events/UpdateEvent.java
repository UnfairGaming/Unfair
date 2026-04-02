package unfair.events;

import unfair.event.events.Event;
import unfair.event.types.EventType;

public class UpdateEvent implements Event {
    private final EventType type;
    private final float yaw;
    private final float pitch;
    private float newYaw;
    private float newPitch;
    private float prevYaw;
    private int lastPriority = -1;
    private int priority = -1;
    private boolean rotated = false;

    public UpdateEvent(EventType type, float yaw, float pitch, float newYaw, float newPitch) {
        this.type = type;
        this.yaw = yaw;
        this.pitch = pitch;
        this.newYaw = newYaw;
        this.newPitch = newPitch;
        this.prevYaw = newYaw;
    }

    public EventType getType() {
        return this.type;
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public float getNewYaw() {
        return this.newYaw;
    }

    public float getNewPitch() {
        return this.newPitch;
    }

    public float getPreYaw() {
        return this.prevYaw;
    }

    public int isRotating() {
        return this.priority;
    }

    public boolean isRotated() {
        return this.rotated;
    }

    public void setRotation(float yaw, float pitch, int priority) {
        if (this.type == EventType.PRE && this.lastPriority <= priority) {
            this.newYaw = yaw;
            this.newPitch = pitch;
            this.lastPriority = priority;
            this.rotated = true;
        }
    }

    public void setPervRotation(float yaw, int priority) {
        if (this.type == EventType.PRE && this.priority < priority) {
            this.prevYaw = yaw;
            this.priority = priority;
            this.rotated = true;
        }
    }
}
