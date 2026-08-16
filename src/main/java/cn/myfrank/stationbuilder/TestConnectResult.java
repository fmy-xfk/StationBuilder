package cn.myfrank.stationbuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public record TestConnectResult(boolean success, double radius, double length, ArrayList<Vec3> positions) {
    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean("success", success);
        nbt.putDouble("radius", radius);
        nbt.putDouble("length", length);

        if (positions != null) {
            ListTag positionsList = new ListTag();
            for (Vec3 pos : positions) {
                if (pos != null) {
                    CompoundTag posNbt = new CompoundTag();
                    posNbt.putDouble("x", pos.x);
                    posNbt.putDouble("y", pos.y);
                    posNbt.putDouble("z", pos.z);
                    positionsList.add(posNbt);
                }
            }
            nbt.put("positions", positionsList);
        } else {
            nbt.put("positions", new ListTag());
        }

        return nbt;
    }

    public static TestConnectResult fromNbt(@NotNull CompoundTag nbt) {
        boolean success = nbt.getBoolean("success");
        double radius = nbt.getDouble("radius");
        double length = nbt.getDouble("length");
        ArrayList<Vec3> positions = new ArrayList<>();

        if (nbt.contains("positions", Tag.TAG_LIST)) {
            ListTag positionsList = nbt.getList("positions", Tag.TAG_COMPOUND);

            for (int i = 0; i < positionsList.size(); i++) {
                CompoundTag posNbt = positionsList.getCompound(i);
                if (posNbt.contains("x") && posNbt.contains("y") && posNbt.contains("z")) {
                    double x = posNbt.getDouble("x");
                    double y = posNbt.getDouble("y");
                    double z = posNbt.getDouble("z");
                    positions.add(new Vec3(x, y, z));
                }
            }
        }

        return new TestConnectResult(success, radius, length,positions);
    }
}