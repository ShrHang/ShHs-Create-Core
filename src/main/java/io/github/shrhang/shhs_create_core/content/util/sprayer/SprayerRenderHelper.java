package io.github.shrhang.shhs_create_core.content.util.sprayer;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;

public class SprayerRenderHelper {

    public static Axis getGaugeAxis(Axis facingAxis, Axis shaftAxis) {
        for (Axis axis : Axis.values()) {
            if (axis != facingAxis && axis != shaftAxis) {
                return axis;
            }
        }
        return Axis.Y;
    }

    public static FaceRotation getRotationForFace(Direction face) {
        return switch (face) {
            case NORTH -> new FaceRotation(0, 0);
            case SOUTH -> new FaceRotation(180, 0);
            case EAST -> new FaceRotation(90, 0);
            case WEST -> new FaceRotation(270, 0);
            case UP -> new FaceRotation(0, 90);
            case DOWN -> new FaceRotation(0, 270);
        };
    }

    public record FaceRotation(float yDegrees, float xDegrees) {
    }
}
