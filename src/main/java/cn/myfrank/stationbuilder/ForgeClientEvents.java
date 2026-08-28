package cn.myfrank.stationbuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// 重新启用自动扫描，指定仅在客户端加载
@Mod.EventBusSubscriber(modid = StationBuilder.MOD_ID, value = Dist.CLIENT)
public final class ForgeClientEvents {

    public static void registerReceivers() {
        StationBuilder.LOGGER.info("[Client] Registering packet receivers...");
        PlatformServices.registerClientReceiver(StationBuilder.PACKET_SYNC_OPEN, buf -> {
            var pos = buf.readBlockPos();
            int facing = buf.readInt();
            var nbt = buf.readNbt();
            StationBuilder.LOGGER.info("[Client] Processing PACKET_SYNC_OPEN at pos: {}", pos);
            Minecraft.getInstance().execute(() -> {
                StationBuilder.LOGGER.info("[Client] Opening StationEditorScreen...");
                Minecraft.getInstance().setScreen(new StationEditorScreen(pos, Direction.from2DDataValue(facing), nbt));
            });
        });
        PlatformServices.registerClientReceiver(StationBuilder.PACKET_SYNC_OPEN_RAIL, buf -> {
            var nbt = buf.readNbt();
            StationBuilder.LOGGER.info("[Client] Processing PACKET_SYNC_OPEN_RAIL");
            Minecraft.getInstance().execute(() -> {
                StationBuilder.LOGGER.info("[Client] Opening RailBuilderScreen...");
                Minecraft.getInstance().setScreen(new RailBuilderScreen(nbt));
            });
        });
        PlatformServices.registerClientReceiver(StationBuilder.PACKET_SYNC_OPEN_SELECTOR, buf -> {
            boolean hasP1 = buf.readBoolean();
            var p1 = hasP1 ? buf.readBlockPos() : null;
            boolean hasP2 = buf.readBoolean();
            var p2 = hasP2 ? buf.readBlockPos() : null;
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().setScreen(new BuildingSelectorScreen(p1, p2));
            });
        });
        PlatformServices.registerClientReceiver(StationBuilder.PACKET_SYNC_OPEN_PLACER, buf -> {
            var nbt = buf.readNbt();
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().setScreen(new BuildingPlacerScreen(nbt));
            });
        });
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (ForgeClientModEvents.CLEAR_RAIL_STATE.consumeClick() && client.player != null && client.player.getMainHandItem().getItem() instanceof RailBuilderItem) {
            PlatformServices.sendToServer(StationBuilder.PACKET_CLEAR_RAIL, StationBuilder.buf(b -> {}));
        }
        if (ForgeClientModEvents.UNDO_PLACER.consumeClick() && client.player != null && client.player.getMainHandItem().getItem() instanceof BuildingPlacerItem) {
            PlatformServices.sendToServer(StationBuilder.PACKET_UNDO_PLACER, StationBuilder.buf(b -> {}));
        }
    }
}