package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.StationElement;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

public class StationBuilder implements ModInitializer {
	public static final String MOD_ID = "stationbuilder";
	public static final int DEFAULT_STATION_LENGTH = 51;
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Identifier BUILD_PACKET_ID = new Identifier(MOD_ID, "build_station");
	public static final Identifier SYNC_AND_OPEN_PACKET = new Identifier(MOD_ID, "sync_open");
	public static final Identifier SAVE_DATA_PACKET = new Identifier(MOD_ID, "save_data");
	public static final Identifier SYNC_AND_OPEN_PACKET_RAIL = new Identifier(MOD_ID, "sync_open_rail");
	public static final Identifier SAVE_DATA_PACKET_RAIL = new Identifier(MOD_ID, "save_data_rail");
	public static final Identifier CLEAR_RAIL_PACKET = new Identifier("stationbuilder", "clear_rail_state");
	
	public static final ItemGroup STATION_GROUP = Registry.register(Registries.ITEM_GROUP,
			new Identifier(MOD_ID, "station_group"),
			FabricItemGroup.builder()
					.displayName(Text.translatable("itemGroup.stationbuilder.group")) // 语言文件中的名字
					.icon(() -> new ItemStack(ModBlocks.STATION_BUILDER_ITEM)) // 图标
					.entries((displayContext, entries) -> {
						entries.add(ModBlocks.STATION_BUILDER_ITEM);
						entries.add(ModItems.RAIL_BUILDER_ITEM);
					})
					.build());

	private static final boolean hasMTR = FabricLoader.getInstance().isModLoaded("mtr");
	public static boolean isMtrLoaded() { return hasMTR; }

	private static final boolean hasMSD = FabricLoader.getInstance().isModLoaded("msd");
	public static boolean isMsdLoaded() { return hasMSD; }

	public static boolean isSoftTransparent(net.minecraft.block.BlockState state) {
		return state.isAir() || state.isReplaceable() ||
				!state.getFluidState().isEmpty() || state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES);
	}

	@Override
	public void onInitialize() {
		ModBlocks.register();
		ModItems.register();
		TickScheduler.init();
		
		ServerPlayNetworking.registerGlobalReceiver(SAVE_DATA_PACKET, (server, player, handler, buf, responseSender) -> {
			BlockPos pos = buf.readBlockPos();
			buf.readInt(); // facingInt, not used
			int length = buf.readInt();
			int elementCount = buf.readInt();

			List<StationElement> elements = new ArrayList<>();
			for (int i = 0; i < elementCount; i++) {
				elements.add(StationElement.read(buf));
			}

			server.execute(() -> {
				if (player.getWorld().getBlockEntity(pos) instanceof StationBuilderBlockEntity be) {
					be.length = length;
					be.elements = elements;
					be.markDirty();
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(
			SAVE_DATA_PACKET_RAIL,
			(server, player, handler, buf, responseSender) -> {
				NbtCompound nbt = buf.readNbt();
				server.execute(() -> {
					ItemStack stack = player.getMainHandStack();
					if (stack.getItem() instanceof RailBuilderItem && nbt != null) {
						RailBuilderConfig cfg = RailBuilderConfig.fromItem(stack);
						cfg.fromNbt(nbt);
						cfg.saveToItem(stack);
					}
				});
			}
		);

		ServerPlayNetworking.registerGlobalReceiver(BUILD_PACKET_ID, (server, player, handler, buf, responseSender) -> {
			BlockPos pos = buf.readBlockPos();
			int facingInt = buf.readInt();
			int length = buf.readInt();
			int elementCount = buf.readInt();

			List<StationElement> elements = new ArrayList<>();
			for (int i = 0; i < elementCount; i++) {
				elements.add(StationElement.read(buf));
			}

			server.execute(() -> {
				// 先存数据
				if (player.getWorld().getBlockEntity(pos) instanceof StationBuilderBlockEntity be) {
					be.length = length;
					be.elements = elements;
					be.markDirty();
				}
				// 后建造
				StationGenerator.build(player, player.getServerWorld(), pos,
						Direction.fromHorizontal(facingInt), length, elements);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(
                StationBuilder.CLEAR_RAIL_PACKET,
                (server, player, handler, buf, responseSender) -> {
                    server.execute(() -> {
                        ItemStack stack = player.getMainHandStack();

                        if (!stack.isEmpty()) {
                            RailBuilderState.clear(stack);
                            stack.getOrCreateNbt().remove("CustomModelData");
                            player.sendMessage(Text.translatable(
                                "message.stationbuilder.rail_builder.end"
                            ), true);
                        }
                    });
                }
        );
	}
}