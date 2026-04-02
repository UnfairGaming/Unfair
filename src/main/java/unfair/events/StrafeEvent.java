package unfair.events;

import unfair.event.events.Event;

public class StrafeEvent implements Event {
    private float strafe;
    private float forward;
    private float friction;

    public StrafeEvent(float strafe, float forward, float friction) {
        this.strafe = strafe;
        this.forward = forward;
        this.friction = friction;
    }

    public float getStrafe() {
        return this.strafe;
    }

    public void setStrafe(float float1) {
        this.strafe = float1;
    }

    public float getForward() {
        return this.forward;
    }

    public void setForward(float float1) {
        this.forward = float1;
    }

    public float getFriction() {
        return this.friction;
    }

    public void setFriction(float float1) {
        this.friction = float1;
    }
}
