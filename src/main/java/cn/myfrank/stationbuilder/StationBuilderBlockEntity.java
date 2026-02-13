package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.StationElement;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;

public class StationBuilderBlockEntity extends BlockEntity {
    public int length = StationBuilder.DEFAULT_STATION_LENGTH;
    public List<StationElement> elements = new ArrayList<>();

    public StationBuilderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.STATION_BUILDER_ENTITY, pos, state);
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        nbt.putInt("length", length);
        NbtList list = new NbtList();
        for (StationElement e : elements) list.add(e.toNbt());
        nbt.put("elements", list);
        super.writeNbt(nbt);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.length = nbt.getInt("length");
        this.elements.clear();
        NbtList list = nbt.getList("elements", 10);
        for (int i = 0; i < list.size(); i++) {
            this.elements.add(StationElement.fromNbt(list.getCompound(i)));
        }
    }
}