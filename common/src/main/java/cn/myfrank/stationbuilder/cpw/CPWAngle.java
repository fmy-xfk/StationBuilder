package cn.myfrank.stationbuilder.cpw;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * CPW (Pantographs and Wires) 16 方向角度枚举。
 * 步进为 22.5°，由 Direction facing (4向) 与 int rotation (0: -22.5°, 1: 0°, 2: +22.5°, 3: +45°) 组合而成。
 */
public enum CPWAngle {
    DEG_0(0.0F, Direction.SOUTH, 1),
    DEG_22_5(22.5F, Direction.SOUTH, 0),
    DEG_45(45.0F, Direction.WEST, 3),
    DEG_67_5(67.5F, Direction.WEST, 2),
    DEG_90(90.0F, Direction.WEST, 1),
    DEG_112_5(112.5F, Direction.WEST, 0),
    DEG_135(135.0F, Direction.NORTH, 3),
    DEG_157_5(157.5F, Direction.NORTH, 2),
    DEG_180(180.0F, Direction.NORTH, 1),
    DEG_202_5(202.5F, Direction.NORTH, 0),
    DEG_225(225.0F, Direction.EAST, 3),
    DEG_247_5(247.5F, Direction.EAST, 2),
    DEG_270(270.0F, Direction.EAST, 1),
    DEG_292_5(292.5F, Direction.EAST, 0),
    DEG_315(315.0F, Direction.SOUTH, 3),
    DEG_337_5(337.5F, Direction.SOUTH, 2);

    private final float degrees;
    private final Direction facing;
    private final int rotation;

    CPWAngle(float degrees, Direction facing, int rotation) {
        this.degrees = degrees;
        this.facing = facing;
        this.rotation = rotation;
    }

    public float getDegrees() {
        return degrees;
    }

    public Direction getFacing() {
        return facing;
    }

    public int getRotation() {
        return rotation;
    }

    public Direction facing() {
        return facing;
    }

    public int rotation() {
        return rotation;
    }

    /**
     * 按 22.5° 步长旋转（正数为顺时针，负数为逆时针）
     */
    public CPWAngle rotate(int steps) {
        int newIndex = (this.ordinal() + (steps % 16) + 16) % 16;
        return values()[newIndex];
    }

    public CPWAngle getClockWise() {
        return rotate(4);
    }

    public CPWAngle getCounterClockWise() {
        return rotate(-4);
    }

    public CPWAngle getOpposite() {
        return rotate(8);
    }

    /**
     * 获取沿当前角度由支柱伸向外侧的方块偏移 X 分量。
     *
     * <p>CPW 的 {@code AbstractRotatableBlock.getSupportBlockPos()} 在
     * {@code rotation == 3}（45° 斜向腕臂）时，会把支撑位置再向
     * {@code facing.rotateYClockwise()} 偏移一格：
     * {@code support = pos - facing + facing.clockwise}。
     * 要让支撑位置正好落在 mast 上，腕臂方块必须放在
     * {@code mast + facing - facing.clockwise}，而不是两个方向相加。</p>
     */
    public int getStepX() {
        if (rotation == 3) { // 45° 斜对角：抵消 CPW 对支撑位置的顺时针偏移
            return facing.getOffsetX() - facing.rotateYClockwise().getOffsetX();
        }
        return facing.getOffsetX();
    }

    /**
     * 获取沿当前角度由支柱伸向外侧的方块偏移 Z 分量（说明见 {@link #getStepX()}）。
     */
    public int getStepZ() {
        if (rotation == 3) { // 45° 斜对角：抵消 CPW 对支撑位置的顺时针偏移
            return facing.getOffsetZ() - facing.rotateYClockwise().getOffsetZ();
        }
        return facing.getOffsetZ();
    }

    public BlockPos offset(BlockPos basePos) {
        return basePos.add(getStepX(), 0, getStepZ());
    }

    public static CPWAngle fromAngle(double angleDegrees) {
        double normalized = (angleDegrees % 360.0 + 360.0) % 360.0;
        int sector = (int) Math.round(normalized / 22.5) % 16;
        return values()[sector];
    }

    public static CPWAngle fromVector(Vec3d dir) {
        if (dir == null || (Math.abs(dir.x) < 1e-8 && Math.abs(dir.z) < 1e-8)) {
            return DEG_0;
        }
        double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z));
        return fromAngle(yaw);
    }

    public static CPWAngle fromDirection(Direction direction) {
        if (direction == null) return DEG_0;
        return switch (direction) {
            case SOUTH -> DEG_0;
            case WEST -> DEG_90;
            case NORTH -> DEG_180;
            case EAST -> DEG_270;
            default -> DEG_0;
        };
    }
}
