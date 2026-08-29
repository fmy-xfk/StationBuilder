package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.StationElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class StationBuilderBlockEntity extends BlockEntity {
    public int length = StationBuilder.DEFAULT_STATION_LENGTH;
    public List<StationElement> elements = new ArrayList<>();

    public StationBuilderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.STATION_BUILDER_ENTITY.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        nbt.putInt("length", length);
        ListTag list = new ListTag();
        for (StationElement e : elements) list.add(e.toNbt());
        nbt.put("elements", list);
        super.saveAdditional(nbt, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        this.length = nbt.getInt("length");
        this.elements.clear();
        ListTag list = nbt.getList("elements", 10);
        for (int i = 0; i < list.size(); i++) {
            this.elements.add(StationElement.fromNbt(list.getCompound(i)));
        }
    }

    public void loadData(CompoundTag nbt, HolderLookup.Provider registries) {
        this.loadAdditional(nbt, registries);
        this.setChanged();
    }
}
