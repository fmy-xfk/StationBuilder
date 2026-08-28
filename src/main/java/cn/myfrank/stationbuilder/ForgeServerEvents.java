package cn.myfrank.stationbuilder;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

@Mod.EventBusSubscriber(modid = StationBuilder.MOD_ID)
public final class ForgeServerEvents {
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        TickScheduler.tick();
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof BuildingSelectorItem) {
            if (!event.getLevel().isClientSide) {
                BuildingSelectorItem.setPos1(stack, event.getPos());
                event.getEntity().displayClientMessage(Component.translatable("message.stationbuilder.pos1_set_to:", event.getPos().toShortString()).withStyle(net.minecraft.ChatFormatting.GREEN), true);
            }
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
