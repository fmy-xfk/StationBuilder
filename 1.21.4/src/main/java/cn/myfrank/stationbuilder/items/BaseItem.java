package cn.myfrank.stationbuilder.items;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public abstract class BaseItem extends Item {
    public BaseItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return handleUse(level, player, hand);
    }

    /**
     * 统一交由 common 模块的具体物品类实现业务逻辑
     */
    public abstract InteractionResult handleUse(Level level, Player player, InteractionHand hand);
}