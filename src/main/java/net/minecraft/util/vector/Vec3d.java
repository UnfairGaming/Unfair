package net.minecraft.util.vector;

import lombok.Getter;

@Getter
public class Vec3d {

    public double x;
    public double y;
    public double z;

    public Vec3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void set(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void setAdd(Vec3d vec, double x, double y, double z) {
        this.x = vec.x + x;
        this.y = vec.y + y;
        this.z = vec.z + z;
    }

    public Vec3d div(Vec3d rayDir) {
        this.x /= rayDir.x;
        this.z /= rayDir.z;
        this.y /= rayDir.y;
        return this;
    }

    public Vec3d normalize() {
        double mag = Math.sqrt(x * x + y * y + z * z);
        this.x /= mag;
        this.y /= mag;
        this.z /= mag;
        return this;
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Vec3d)) {
            return false;
        }
        Vec3d vec3d = (Vec3d) other;
        if (Double.compare(vec3d.x, x) != 0) {
            return false;
        }
        if (Double.compare(vec3d.y, y) != 0) {
            return false;
        }
        return Double.compare(vec3d.z, z) == 0;
    }

    @Override
    public int hashCode() {
        int i = Double.hashCode(x);
        i = 31 * i + Double.hashCode(y);
        i = 31 * i + Double.hashCode(z);
        return i;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }

}
