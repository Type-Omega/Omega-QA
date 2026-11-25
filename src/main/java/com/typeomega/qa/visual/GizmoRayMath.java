package com.typeomega.qa.visual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Pure JOML ray-unprojection, ray-to-axis closest-approach, AABB slab intersection, and integer snapping math
 * for 3D gizmos in the Omega QA Wand editor (no {@code GL11} or {@code GLU.gluUnProject}).
 */
public final class GizmoRayMath {

    private static final float EPSILON = 1e-6f;

    private GizmoRayMath() {}

    public record Ray(Vector3f origin, Vector3f direction) {}

    public record AxisApproach(double axisParam, double distanceSq) {}

    /**
     * Converts screen coordinates {@code [0..screenW, 0..screenH]} to normalized device coordinates and unprojects
     * through {@code viewProj.invert()} into a world-space ray.
     */
    public static Ray unprojectScreenRay(double mouseX, double mouseY, int screenW, int screenH,
                                         Matrix4f viewProjMatrix, Vector3f cameraPos) {
        double ndcX = screenW <= 0 ? 0.0 : (2.0 * mouseX / screenW) - 1.0;
        double ndcY = screenH <= 0 ? 0.0 : 1.0 - (2.0 * mouseY / screenH);
        return unprojectCursorRay(ndcX, ndcY, new Matrix4f(viewProjMatrix).invert(), cameraPos);
    }

    /**
     * Unprojects NDC coordinates {@code (ndcX, ndcY) in [-1, 1]} using a pre-inverted view-projection matrix.
     */
    public static Ray unprojectCursorRay(double ndcX, double ndcY, Matrix4f inverseViewProj, Vector3f cameraPos) {
        Vector4f nearClip = new Vector4f((float) ndcX, (float) ndcY, -1.0f, 1.0f);
        Vector4f farClip = new Vector4f((float) ndcX, (float) ndcY, 1.0f, 1.0f);
        nearClip.mul(inverseViewProj);
        farClip.mul(inverseViewProj);

        if (Math.abs(nearClip.w) > EPSILON) nearClip.div(nearClip.w);
        if (Math.abs(farClip.w) > EPSILON) farClip.div(farClip.w);

        Vector3f dir = new Vector3f(farClip.x - nearClip.x, farClip.y - nearClip.y, farClip.z - nearClip.z);
        if (dir.lengthSquared() < EPSILON) {
            dir.set(0.0f, 0.0f, -1.0f);
        } else {
            dir.normalize();
        }
        return new Ray(new Vector3f(cameraPos), dir);
    }

    /**
     * Computes the closest point parameter along line {@code axisOrigin + t * axisDir} to ray
     * {@code rayOrigin + s * rayDir}, returning {@code (t, distanceSq)}.
     */
    public static AxisApproach closestApproachOnAxis(Vector3f rayOrigin, Vector3f rayDir,
                                                     Vector3f axisOrigin, Vector3f axisDir) {
        Vector3f u = new Vector3f(rayDir).normalize();
        Vector3f v = new Vector3f(axisDir).normalize();
        Vector3f w = new Vector3f(rayOrigin).sub(axisOrigin);

        double a = u.dot(u);
        double b = u.dot(v);
        double c = v.dot(v);
        double d = u.dot(w);
        double e = v.dot(w);
        double denom = a * c - b * b;

        double s;
        double t;
        if (Math.abs(denom) < EPSILON) {
            s = 0.0;
            t = c > EPSILON ? e / c : 0.0;
        } else {
            s = (b * e - c * d) / denom;
            t = (a * e - b * d) / denom;
            if (s < 0.0) {
                s = 0.0;
                t = c > EPSILON ? e / c : 0.0;
            }
        }
        Vector3f pRay = new Vector3f(rayOrigin).fma((float) s, u);
        Vector3f pAxis = new Vector3f(axisOrigin).fma((float) t, v);
        double distSq = pRay.distanceSquared(pAxis);
        return new AxisApproach(t, distSq);
    }

    public static int snapToInt(double delta) {
        return (int) Math.round(delta);
    }

    public static Direction.Axis pickClosestAxis(Ray ray, Vector3f gizmoOrigin, double maxDistance) {
        Direction.Axis bestAxis = null;
        double bestDistSq = maxDistance * maxDistance;
        for (Direction.Axis axis : Direction.Axis.values()) {
            Vector3f axisDir = axisVector(axis);
            AxisApproach approach = closestApproachOnAxis(ray.origin(), ray.direction(), gizmoOrigin, axisDir);
            // EN: Restrict handle picking to [0.20, 2.50] along each positive axis so the origin doesn't bias X.
            // PT: Restringe a seleção da alça a [0.20, 2.50] ao longo de cada eixo positivo para evitar viés em X.
            // ZH: 将轴柄拾取限制在各正轴 [0.20, 2.50] 区间，避免原点处总是偏向 X 轴。
            if (approach.axisParam() >= 0.20 && approach.axisParam() <= 2.50 && approach.distanceSq() < bestDistSq) {
                bestDistSq = approach.distanceSq();
                bestAxis = axis;
            }
        }
        return bestAxis;
    }

    public static Vector3f axisVector(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vector3f(1, 0, 0);
            case Y -> new Vector3f(0, 1, 0);
            case Z -> new Vector3f(0, 0, 1);
        };
    }

    /** Standard slab intersection returning hit distance along ray, or {@link Double#POSITIVE_INFINITY} if missed. */
    public static double rayIntersectsAabb(Vector3f origin, Vector3f dir, BlockPos min, BlockPos maxInclusive) {
        float minX = min.getX();
        float minY = min.getY();
        float minZ = min.getZ();
        float maxX = maxInclusive.getX() + 1.0f;
        float maxY = maxInclusive.getY() + 1.0f;
        float maxZ = maxInclusive.getZ() + 1.0f;
        return rayIntersectsAabb(origin, dir, new Vector3f(minX, minY, minZ), new Vector3f(maxX, maxY, maxZ));
    }

    public static double rayIntersectsAabb(Vector3f origin, Vector3f dir, Vector3f min, Vector3f max) {
        double tMin = 0.0;
        double tMax = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 3; i++) {
            double o = origin.get(i);
            double d = dir.get(i);
            double bMin = min.get(i);
            double bMax = max.get(i);
            if (Math.abs(d) < EPSILON) {
                if (o < bMin || o > bMax) return Double.POSITIVE_INFINITY;
            } else {
                double t1 = (bMin - o) / d;
                double t2 = (bMax - o) / d;
                if (t1 > t2) {
                    double tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) return Double.POSITIVE_INFINITY;
            }
        }
        return tMin;
    }
}
