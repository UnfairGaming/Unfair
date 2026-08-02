package cn.unfair.util.animations;

public class AnimationUtil {
    public static double animate(double target, double current, double speed) {
        boolean larger = target > current;
        if (speed < 0.0) {
            speed = 0.0;
        } else if (speed > 1.0) {
            speed = 1.0;
        }
        double dif = Math.max(target, current) - Math.min(target, current);
        double factor = dif * speed;
        current = larger ? current + factor : current - factor;
        return current;
    }

    public static float calculateCompensation(float target, float current, long delta, int speed) {
        float diff = current - target;
        if (delta < 1L) {
            delta = 1L;
        }
        if (diff > (float)speed) {
            double xD = (double)((float)((long)speed * delta) / 16.0f) < 0.25 ? 0.5 : (double)((float)((long)speed * delta) / 16.0f);
            if ((current -= (float)xD) < target) {
                current = target;
            }
        } else if (diff < (float)(-speed)) {
            double xD = (double)((float)((long)speed * delta) / 16.0f) < 0.25 ? 0.5 : (double)((float)((long)speed * delta) / 16.0f);
            if ((current += (float)xD) > target) {
                current = target;
            }
        } else {
            current = target;
        }
        return current;
    }

    public static float smooth(float current, float target, float speed) {
        long deltaTime = 16;

        speed = Math.abs(target - current) * speed;

        if (deltaTime < 1L) deltaTime = 1L;

        final float difference = current - target;
        final float smoothing = Math.max(speed * (deltaTime / 16F), .15F);

        if (difference > speed) {
            current = Math.max(current - smoothing, target);
        } else if (difference < -speed) {
            current = Math.min(current + smoothing, target);
        } else {
            current = target;
        }

        return current;
    }

    public static float moveUD(float current, float end, float smoothSpeed, float minSpeed) {
        boolean larger = end > current;
        if (smoothSpeed < 0.0f) {
            smoothSpeed = 0.0f;
        } else if (smoothSpeed > 1.0f) {
            smoothSpeed = 1.0f;
        }
        if (minSpeed < 0.0f) {
            minSpeed = 0.0f;
        } else if (minSpeed > 1.0f) {
            minSpeed = 1.0f;
        }
        float movement = (end - current) * smoothSpeed;
        if (movement > 0) {
            movement = Math.max(minSpeed, movement);
            movement = Math.min(end - current, movement);
        } else if (movement < 0) {
            movement = Math.min(-minSpeed, movement);
            movement = Math.max(end - current, movement);
        }
        if (larger){
            if (end <= current + movement){
                return end;
            }
        }else {
            if (end >= current + movement){
                return end;
            }
        }
        return current + movement;
    }
}
