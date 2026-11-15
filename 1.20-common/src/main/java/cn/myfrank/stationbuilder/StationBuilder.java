package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.blocks.ModBlocks;
import cn.myfrank.stationbuilder.blocks.StationBuilderBlockEntity;
import cn.myfrank.stationbuilder.generator.StationGenerator;
import cn.myfrank.stationbuilder.items.BuildingPlacerItem;
import cn.myfrank.stationbuilder.items.BuildingSelectorItem;
import cn.myfrank.stationbuilder.items.ModItems;
import cn.myfrank.stationbuilder.items.RailBuilderItem;
import cn.myfrank.stationbuilder.manager.BuildingTemplateManager;
import cn.myfrank.stationbuilder.manager.PlacerHistoryManager;
import cn.myfrank.stationbuilder.mtr.MtrDepotOffsetWriter;
import cn.myfrank.stationbuilder.mtr.MtrGameDataSource;
import cn.myfrank.stationbuilder.scheduler.Json;
import cn.myfrank.stationbuilder.scheduler.PayloadCompression;
import cn.myfrank.stationbuilder.items.BuildingPlacerConfig;
import cn.myfrank.stationbuilder.items.RailBuilderConfig;
import cn.myfrank.stationbuilder.items.RailBuilderState;
import cn.myfrank.stationbuilder.utils.TickScheduler;

import cn.myfrank.stationbuilder.elements.PassageConfig;
import cn.myfrank.stationbuilder.elements.StationElement;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StationBuilder implements ModInitializer {
	public static final String MOD_ID = "stationbuilder";
	public static final int DEFAULT_STATION_LENGTH = 51;
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Identifier BUILD_PACKET_ID = new Identifier(MOD_ID, "build_station");
	public static final Identifier SYNC_AND_OPEN_PACKET = new Identifier(MOD_ID, "sync_open");
	public static final Identifier SAVE_DATA_PACKET = new Identifier(MOD_ID, "save_data");
	public static final Identifier SYNC_AND_OPEN_PACKET_RAIL = new Identifier(MOD_ID, "sync_open_rail");
	public static final Identifier SAVE_DATA_PACKET_RAIL = new Identifier(MOD_ID, "save_data_rail");
	public static final Identifier CLEAR_RAIL_PACKET = new Identifier(MOD_ID, "clear_rail_state");

	// 鏂板锛氶€夊彇鍣ㄤ笌鏀剧疆鍣ㄧ殑 1.20.4 鑷畾涔夌綉缁?Identifier
	public static final Identifier SYNC_AND_OPEN_PACKET_SELECTOR = new Identifier(MOD_ID, "sync_open_selector");
	public static final Identifier SAVE_SELECTION_PACKET = new Identifier(MOD_ID, "save_selection");
	public static final Identifier SYNC_AND_OPEN_PACKET_PLACER = new Identifier(MOD_ID, "sync_open_placer");
	public static final Identifier SAVE_PLACER_PACKET = new Identifier(MOD_ID, "save_placer");
	public static final Identifier UNDO_PLACER_PACKET = new Identifier(MOD_ID, "undo_placer");
	public static final Identifier MTR_SCHEDULER_REQUEST = new Identifier(MOD_ID, "mtr_scheduler_request");
	public static final Identifier MTR_SCHEDULER_WRITE = new Identifier(MOD_ID, "mtr_scheduler_write");
	public static final Identifier MTR_SCHEDULER_DATA = new Identifier(MOD_ID, "mtr_scheduler_data");

	public static final ItemGroup STATION_GROUP = Registry.register(Registries.ITEM_GROUP,
			new Identifier(MOD_ID, "station_group"),
			FabricItemGroup.builder()
					.displayName(Text.translatable("itemGroup.stationbuilder.group"))
					.icon(() -> new ItemStack(ModBlocks.STATION_BUILDER_ITEM))
					.entries((displayContext, entries) -> {
						entries.add(ModBlocks.STATION_BUILDER_ITEM);
						entries.add(ModItems.RAIL_BUILDER_ITEM);
						entries.add(ModItems.BUILDING_SELECTOR_ITEM); // 鏂板閫夊彇鍣?
						entries.add(ModItems.BUILDING_PLACER_ITEM);   // 鏂板鏀剧疆鍣?
						entries.add(ModItems.MTR_SCHEDULER_ITEM);
					})
					.build());

	@Override
	public void onInitialize() {
		ModBlocks.register();
		ModItems.register();
		BuildingTemplateManager.loadTemplates();
		TickScheduler.init();

		ServerPlayNetworking.registerGlobalReceiver(SAVE_DATA_PACKET, (server, player, handler, buf, responseSender) -> {
			BlockPos pos = buf.readBlockPos();
			buf.readInt();
			int length = buf.readInt();
			PassageConfig passage = PassageConfig.read(buf);
			int elementCount = buf.readInt();

			List<StationElement> elements = new ArrayList<>();
			for (int i = 0; i < elementCount; i++) {
				elements.add(StationElement.read(buf));
			}

			server.execute(() -> {
				if (player.getWorld().getBlockEntity(pos) instanceof StationBuilderBlockEntity be) {
					be.length = length;
					be.passage = passage;
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
			PassageConfig passage = PassageConfig.read(buf);
			int elementCount = buf.readInt();

			List<StationElement> elements = new ArrayList<>();
			for (int i = 0; i < elementCount; i++) {
				elements.add(StationElement.read(buf));
			}

			server.execute(() -> {
				if (player.getWorld().getBlockEntity(pos) instanceof StationBuilderBlockEntity be) {
					be.length = length;
					be.passage = passage;
					be.elements = elements;
					be.markDirty();
				}
				StationGenerator.build(player, player.getServerWorld(), pos,
						Direction.fromHorizontal(facingInt), length, passage, elements);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(
				CLEAR_RAIL_PACKET,
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

		// 鏂板锛氱粨鏋勯€夋嫨鍣?C2S 瀛樼洏鍖?
		ServerPlayNetworking.registerGlobalReceiver(SAVE_SELECTION_PACKET, (server, player, handler, buf, responseSender) -> {
			String name = buf.readString();
			BlockPos p1 = buf.readBlockPos();
			BlockPos p2 = buf.readBlockPos();
			boolean includeEntities = buf.readBoolean();
			server.execute(() -> {
				ServerWorld world = player.getServerWorld();
				BlockPos min = new BlockPos(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()));
				BlockPos max = new BlockPos(Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()));
				Vec3i size = new Vec3i(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1);

				StructureTemplate template = new StructureTemplate();
				template.saveFromWorld(world, min, size, includeEntities, null);
				BuildingTemplateManager.addTemplate(name, template);
				player.sendMessage(Text.translatable("message.stationbuilder.save_selection_success", name).formatted(net.minecraft.util.Formatting.GREEN), false);
			});
		});

		// 鏂板锛氭斁缃櫒閰嶇疆 C2S 淇濆瓨鍖?
		ServerPlayNetworking.registerGlobalReceiver(SAVE_PLACER_PACKET, (server, player, handler, buf, responseSender) -> {
			NbtCompound nbt = buf.readNbt();
			server.execute(() -> {
				ItemStack stack = player.getMainHandStack();
				if (stack.getItem() instanceof BuildingPlacerItem && nbt != null) {
					BuildingPlacerConfig cfg = BuildingPlacerConfig.fromItem(stack);
					cfg.fromNbt(nbt);
					cfg.saveToItem(stack);
				}
			});
		});

		// 鏂板锛氭挙閿€ C2S 鎺у埗鍖?
		ServerPlayNetworking.registerGlobalReceiver(UNDO_PLACER_PACKET, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> {
				// Global undo history: the latest action may come from any tool.
				String tool = PlacerHistoryManager.undo(player);
				if (tool != null) {
					String key = switch (tool) {
						case "station" -> "message.stationbuilder.undo_station";
						case "rail" -> "message.stationbuilder.undo_rail";
						case "placer" -> "message.stationbuilder.undo_placer";
						default -> "message.stationbuilder.undo_success";
					};
					player.sendMessage(Text.translatable(key).formatted(net.minecraft.util.Formatting.GREEN), true);
				} else {
					player.sendMessage(Text.translatable("message.stationbuilder.undo_no_history").formatted(net.minecraft.util.Formatting.RED), true);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(MTR_SCHEDULER_REQUEST, (server, player, handler, buf, responseSender) -> {
			int token = buf.readInt();
			server.execute(() -> {
				if (!cn.myfrank.stationbuilder.utils.CommonUtil.isMtrLoaded()) {
					sendSchedulerError(player, token, "MTR is not installed on this server");
					return;
				}
				try {
					MtrGameDataSource.collect(server, player.getServerWorld(),
							snapshot -> sendSchedulerSnapshot(player, token, snapshot));
				} catch (Throwable throwable) {
					sendSchedulerSnapshot(player, token,
							MtrGameDataSource.Snapshot.error("Could not request MTR data: " + throwable.getMessage()));
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(MTR_SCHEDULER_WRITE, (server, player, handler, buf, responseSender) -> {
			int token = buf.readInt();
			String offsetsJson = buf.readString();
			server.execute(() -> {
				if (!cn.myfrank.stationbuilder.utils.CommonUtil.isMtrLoaded()) {
					sendSchedulerError(player, token, "MTR is not installed on this server");
					return;
				}
				Map<String, Long> offsets;
				try {
					offsets = new LinkedHashMap<>();
					for (Map.Entry<String, Object> entry : Json.object(Json.parse(offsetsJson)).entrySet()) {
						offsets.put(entry.getKey(), Json.longValue(entry.getValue(), 0L));
					}
				} catch (RuntimeException exception) {
					sendSchedulerSnapshot(player, token,
							MtrGameDataSource.Snapshot.error("Invalid depot offsets: " + exception.getMessage()));
					return;
				}
				try {
					MtrGameDataSource.collect(server, player.getServerWorld(), snapshot -> {
						if (!snapshot.isSuccess()) {
							sendSchedulerSnapshot(player, token, snapshot);
							return;
						}
						MtrDepotOffsetWriter.Result result =
								MtrDepotOffsetWriter.write(snapshot.response(), offsets);
						if (!result.success()) {
							sendSchedulerSnapshot(player, token, MtrGameDataSource.Snapshot.error(result.message()));
							return;
						}
						MtrGameDataSource.collect(server, player.getServerWorld(),
								fresh -> sendSchedulerSnapshot(player, token, fresh));
					});
				} catch (Throwable throwable) {
					sendSchedulerSnapshot(player, token,
							MtrGameDataSource.Snapshot.error("Could not write MTR data: " + throwable.getMessage()));
				}
			});
		});

		// 鏂板锛氶€夊彇鍣ㄥ乏閿偣鍑昏瀹?Pos 1 鍥炶皟
		net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (stack.getItem() instanceof BuildingSelectorItem) {
				if (!world.isClient) {
					BuildingSelectorItem.setPos1(stack, pos);
					player.sendMessage(Text.translatable("message.stationbuilder.pos1_set_to:", pos.toShortString()).formatted(net.minecraft.util.Formatting.GREEN), true);
				}
				return ActionResult.SUCCESS;
			}
			return ActionResult.PASS;
		});
	}
	private static void sendSchedulerError(net.minecraft.server.network.ServerPlayerEntity player, int token, String message) {
		PacketByteBuf buffer = PacketByteBufs.create();
		buffer.writeInt(token);
		buffer.writeInt(0);
		buffer.writeInt(1);
		buffer.writeBoolean(false);
		buffer.writeString(message);
		buffer.writeByteArray(new byte[0]);
		ServerPlayNetworking.send(player, MTR_SCHEDULER_DATA, buffer);
	}

	private static void sendSchedulerSnapshot(
			net.minecraft.server.network.ServerPlayerEntity player,
			int token,
			MtrGameDataSource.Snapshot snapshot
	) {
		if (!snapshot.isSuccess()) {
			PacketByteBuf buffer = PacketByteBufs.create();
			buffer.writeInt(token);
			buffer.writeInt(0);
			buffer.writeInt(1);
			buffer.writeBoolean(false);
			buffer.writeString(snapshot.error());
			buffer.writeByteArray(new byte[0]);
			ServerPlayNetworking.send(player, MTR_SCHEDULER_DATA, buffer);
			return;
		}
		Map<String, Object> envelope = new LinkedHashMap<>();
		envelope.put("stations", snapshot.stationsAndRoutesJson());
		envelope.put("departures", snapshot.departuresJson());
		byte[] compressed = PayloadCompression.compress(Json.writeCompact(envelope));
		int chunkSize = 30_000;
		int parts = Math.max(1, (compressed.length + chunkSize - 1) / chunkSize);
		for (int part = 0; part < parts; part++) {
			int from = part * chunkSize;
			int to = Math.min(compressed.length, from + chunkSize);
			PacketByteBuf buffer = PacketByteBufs.create();
			buffer.writeInt(token);
			buffer.writeInt(part);
			buffer.writeInt(parts);
			buffer.writeBoolean(true);
			buffer.writeString("");
			buffer.writeByteArray(java.util.Arrays.copyOfRange(compressed, from, to));
			ServerPlayNetworking.send(player, MTR_SCHEDULER_DATA, buffer);
		}
	}
}