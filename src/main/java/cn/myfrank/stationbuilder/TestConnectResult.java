package cn.myfrank.stationbuilder;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public record TestConnectResult(boolean success, double radius, ArrayList<Vec3d> positions) {
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("success", success);
        nbt.putDouble("radius", radius);

        if (positions != null) {
            NbtList positionsList = new NbtList();
            for (Vec3d pos : positions) {
                if (pos != null) {
                    NbtCompound posNbt = new NbtCompound();
                    posNbt.putDouble("x", pos.getX());
                    posNbt.putDouble("y", pos.getY());
                    posNbt.putDouble("z", pos.getZ());
                    positionsList.add(posNbt);
                }
            }
            nbt.put("positions", positionsList);
        } else {
            nbt.put("positions", new NbtList());
        }

        return nbt;
    }

    public static TestConnectResult fromNbt(@NotNull NbtCompound nbt) {
        boolean success = nbt.getBoolean("success");
        double radius = nbt.getDouble("radius");

        ArrayList<Vec3d> positions = new ArrayList<>();

        if (nbt.contains("positions", NbtElement.LIST_TYPE)) {
            NbtList positionsList = nbt.getList("positions", NbtElement.COMPOUND_TYPE);

            for (int i = 0; i < positionsList.size(); i++) {
                NbtCompound posNbt = positionsList.getCompound(i);
                if (posNbt.contains("x") && posNbt.contains("y") && posNbt.contains("z")) {
                    double x = posNbt.getDouble("x");
                    double y = posNbt.getDouble("y");
                    double z = posNbt.getDouble("z");
                    positions.add(new Vec3d(x, y, z));
                }
            }
        }

        return new TestConnectResult(success, radius, positions);
    }
}