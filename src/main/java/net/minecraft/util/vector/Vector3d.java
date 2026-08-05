package net.minecraft.util.vector;

import lombok.Getter;

@Getter
public class Vector3d {
    private final double x;
    private final double y;
    private final double z;

    /** Constructs a new 3D vector */
    public Vector3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Adds scalar values to this vector */
    public Vector3d add(double x, double y, double z) {
        return new Vector3d(this.x + x, this.y + y, this.z + z);
    }

    /** Adds another vector to this vector */
    public Vector3d add(Vector3d vector) {
        return add(vector.x, vector.y, vector.z);
    }

    /** Subtracts scalar values from this vector */
    public Vector3d subtract(double x, double y, double z) {
        return add(-x, -y, -z);
    }

    /** Subtracts another vector from this vector */
    public Vector3d subtract(Vector3d vector) {
        return add(-vector.x, -vector.y, -vector.z);
    }

    /** Calculates the length of this vector */
    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /** Multiplies this vector by a scalar value */
    public Vector3d multiply(double v) {
        return new Vector3d(x * v, y * v, z * v);
    }

    /** Compares this vector with another object using floor comparison */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Vector3d vector)) return false;

        return ((Math.floor(x) == Math.floor(vector.x)) && Math.floor(y) == Math.floor(vector.y) && Math.floor(z) == Math.floor(vector.z));
    }
}