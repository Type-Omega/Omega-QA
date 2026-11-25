package com.typeomega.qa.wand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import com.typeomega.qa.visual.GizmoRayMath;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GizmoRayMathTest {

    @Test
    void unprojectsCenterNdcRayAlongCameraLookDirection() {
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), 16.0f / 9.0f, 0.05f, 1000.0f);
        Matrix4f view = new Matrix4f().lookAt(new Vector3f(0, 0, 0), new Vector3f(0, 0, -1), new Vector3f(0, 1, 0));
        Matrix4f invViewProj = new Matrix4f(proj).mul(view).invert();

        GizmoRayMath.Ray ray = GizmoRayMath.unprojectCursorRay(0.0, 0.0, invViewProj, new Vector3f(10, 64, 20));
        assertEquals(10.0f, ray.origin().x, 1e-4f);
        assertEquals(64.0f, ray.origin().y, 1e-4f);
        assertEquals(20.0f, ray.origin().z, 1e-4f);
        assertEquals(0.0f, ray.direction().x, 1e-3f);
        assertEquals(0.0f, ray.direction().y, 1e-3f);
        assertEquals(-1.0f, ray.direction().z, 1e-3f);
    }

    @Test
    void computesClosestApproachAlongGizmoAxisAndSnapsToInteger() {
        Vector3f rayOrigin = new Vector3f(3.4f, 10.0f, 5.0f);
        Vector3f rayDir = new Vector3f(0.0f, 0.0f, -1.0f);
        Vector3f axisOrigin = new Vector3f(0.0f, 10.0f, 0.0f);
        Vector3f xAxis = new Vector3f(1.0f, 0.0f, 0.0f);

        GizmoRayMath.AxisApproach approach = GizmoRayMath.closestApproachOnAxis(rayOrigin, rayDir, axisOrigin, xAxis);
        assertEquals(3.4, approach.axisParam(), 1e-4);
        assertEquals(0.0, approach.distanceSq(), 1e-4);
        assertEquals(3, GizmoRayMath.snapToInt(approach.axisParam()));
        assertEquals(4, GizmoRayMath.snapToInt(3.6));
    }

    @Test
    void picksClosestAxisHandleAndIntersectsAabb() {
        GizmoRayMath.Ray ray = new GizmoRayMath.Ray(new Vector3f(4.5f, 10.5f, 1.8f), new Vector3f(-1.0f, 0.0f, 0.0f));
        Direction.Axis picked = GizmoRayMath.pickClosestAxis(ray, new Vector3f(0.5f, 10.5f, 0.5f), 0.5);
        assertEquals(Direction.Axis.Z, picked);

        double hitDist = GizmoRayMath.rayIntersectsAabb(
                new Vector3f(1.5f, 10.5f, 6.0f), new Vector3f(0, 0, -1),
                new BlockPos(0, 10, 0), new BlockPos(2, 12, 2));
        assertTrue(Double.isFinite(hitDist) && Math.abs(hitDist - 3.0) < 1e-4);
    }
}
