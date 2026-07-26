package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.StationElement;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
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
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class StationBuilder implements ModInitializer {
	public static final String MOD_ID = "stationbuilder";
	public static final int DEFAULT_STATION_LENGTH = 51;
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Identifier SYNC_AND_OPEN_PACKET = Identifier.of(MOD_ID, "sync_open");
	public static final Identifier SAVE_DATA_PACKET = Identifier.of(MOD_ID, "save_data");
	public static final Identifier SYNC_AND_OPEN_PACKET_RAIL = Identifier.of(MOD_ID, "sync_open_rail");

	public static final ItemGroup STATION_GROUP = Registry.register(
			Registries.ITEM_GROUP,
			Identifier.of(MOD_ID, "station_group"),
			FabricItemGroup.builder()
					.displayName(Text.translatable("itemGroup.stationbuilder.group"))
					.icon(() -> new ItemStack(ModBlocks.STATION_BUILDER_ITEM))
					.entries((displayContext, entries) -> {
						entries.add(ModBlocks.STATION_BUILDER_ITEM);
						entries.add(ModItems.RAIL_BUILDER_ITEM);
					})
					.build()
	);

	public static final class SyncOpenStationPayload implements CustomPayload {
		public static final CustomPayload.Id<SyncOpenStationPayload> ID =
				new CustomPayload.Id<>(Identifier.of(MOD_ID, "sync_open"));
		public static final PacketCodec<RegistryByteBuf, SyncOpenStationPayload> CODEC =
				PacketCodec.ofStatic(
						(buf, payload) -> {
							buf.writeBlockPos(payload.pos);
							buf.writeInt(payload.facing);
							buf.writeNbt(payload.nbt);
						},
						buf -> new SyncOpenStationPayload(
								buf.readBlockPos(),
								buf.readInt(),
								buf.readNbt()
						)
				);

		public final BlockPos pos;
		public final int facing;
		public final NbtCompound nbt;

		public SyncOpenStationPayload(BlockPos pos, int facing, NbtCompound nbt) {
			this.pos = pos;
			this.facing = facing;
			this.nbt = nbt;
		}

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public static final class SaveStationPayload implements CustomPayload {
		public static final CustomPayload.Id<SaveStationPayload> ID =
				new CustomPayload.Id<>(Identifier.of(MOD_ID, "save_data"));
		public static final PacketCodec<RegistryByteBuf, SaveStationPayload> CODEC =
				PacketCodec.ofStatic(
						(buf, payload) -> {
							buf.writeBlockPos(payload.pos);
							buf.writeInt(payload.facing);
							buf.writeInt(payload.length);
							buf.writeInt(payload.elements.size());
							for (StationElement element : payload.elements) {
								element.write(buf);
							}
						},
						buf -> {
							BlockPos pos = buf.readBlockPos();
							int facing = buf.readInt();
							int length = buf.readInt();
							int count = buf.readInt();
							List<StationElement> elements = new ArrayList<>();
							for (int i = 0; i < count; i++) {
								elements.add(StationElement.read(buf));
							}
							return new SaveStationPayload(pos, facing, length, elements);
						}
				);

		public final BlockPos pos;
		public final int facing;
		public final int length;
		public final List<StationElement> elements;

		public SaveStationPayload(BlockPos pos, int facing, int length, List<StationElement> elements) {
			this.pos = pos;
			this.facing = facing;
			this.length = length;
			this.elements = List.copyOf(elements);
		}

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public static final class BuildStationPayload implements CustomPayload {
		public static final CustomPayload.Id<BuildStationPayload> ID =
				new CustomPayload.Id<>(Identifier.of(MOD_ID,"build_station"));
		public static final PacketCodec<RegistryByteBuf, BuildStationPayload> CODEC =
				PacketCodec.ofStatic(
						(buf, payload) -> {
							buf.writeBlockPos(payload.pos);
							buf.writeInt(payload.facing);
							buf.writeInt(payload.length);
							buf.writeInt(payload.elements.size());
							for (StationElement element : payload.elements) {
								element.write(buf);
							}
						},
						buf -> {
							BlockPos pos = buf.readBlockPos();
							int facing = buf.readInt();
							int length = buf.readInt();
							int count = buf.readInt();
							List<StationElement> elements = new ArrayList<>();
							for (int i = 0; i < count; i++) {
								elements.add(StationElement.read(buf));
							}
							return new BuildStationPayload(pos, facing, length, elements);
						}
				);

		public final BlockPos pos;
		public final int facing;
		public final int length;
		public final List<StationElement> elements;

		public BuildStationPayload(BlockPos pos, int facing, int length, List<StationElement> elements) {
			this.pos = pos;
			this.facing = facing;
			this.length = length;
			this.elements = List.copyOf(elements);
		}

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public static final class SyncOpenRailPayload implements CustomPayload {
		public static final CustomPayload.Id<SyncOpenRailPayload> ID =
				new CustomPayload.Id<>(Identifier.of(MOD_ID, "sync_open_rail"));
		public static final PacketCodec<RegistryByteBuf, SyncOpenRailPayload> CODEC =
				PacketCodec.ofStatic(
						(buf, payload) -> buf.writeNbt(payload.nbt),
						buf -> new SyncOpenRailPayload(buf.readNbt())
				);

		public final NbtCompound nbt;

		public SyncOpenRailPayload(NbtCompound nbt) {
			this.nbt = nbt;
		}

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public static final class SaveRailPayload implements CustomPayload {
		public static final CustomPayload.Id<SaveRailPayload> ID =
				new CustomPayload.Id<>(Identifier.of(MOD_ID, "save_data_rail"));
		public static final PacketCodec<RegistryByteBuf, SaveRailPayload> CODEC =
				PacketCodec.ofStatic(
						(buf, payload) -> buf.writeNbt(payload.nbt),
						buf -> new SaveRailPayload(buf.readNbt())
				);

		public final NbtCompound nbt;

		public SaveRailPayload(NbtCompound nbt) {
			this.nbt = nbt;
		}

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public static final class ClearRailStatePayload implements CustomPayload {
		public static final CustomPayload.Id<ClearRailStatePayload> ID =
				new CustomPayload.Id<>(Identifier.of(MOD_ID, "clear_rail_state"));
		public static final PacketCodec<RegistryByteBuf, ClearRailStatePayload> CODEC =
				PacketCodec.ofStatic(
						(buf, payload) -> { /* 空数据包，不需要写入任何东西 */ },
						buf -> new ClearRailStatePayload()
				);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	private static final boolean hasMTR = FabricLoader.getInstance().isModLoaded("mtr");
	public static boolean isMtrLoaded() { return hasMTR; }

	private static final boolean hasMSD = FabricLoader.getInstance().isModLoaded("msd");
	public static boolean isMsdLoaded() { return hasMSD; }

	public static boolean isSoftTransparent(BlockState state) {
		return state.isAir() || state.isReplaceable()
				|| !state.getFluidState().isEmpty()
				|| state.isIn(BlockTags.LOGS)
				|| state.isIn(BlockTags.LEAVES);
	}

	@Override
	public void onInitialize() {
		ModComponents.initialize();
		ModBlocks.register();
		ModItems.register();
		BuildingTemplateManager.loadTemplates();
		TickScheduler.init();

		PayloadTypeRegistry.playS2C().register(SyncOpenStationPayload.ID, StationBuilder.SyncOpenStationPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncOpenRailPayload.ID, StationBuilder.SyncOpenRailPayload.CODEC);

		PayloadTypeRegistry.playC2S().register(SaveStationPayload.ID, StationBuilder.SaveStationPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(BuildStationPayload.ID, StationBuilder.BuildStationPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SaveRailPayload.ID, StationBuilder.SaveRailPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ClearRailStatePayload.ID, StationBuilder.ClearRailStatePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SaveStationPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				if (context.player().getWorld().getBlockEntity(payload.pos) instanceof StationBuilderBlockEntity be) {
					be.length = payload.length;
					be.elements = new ArrayList<>(payload.elements);
					be.markDirty();
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(BuildStationPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				if (context.player().getWorld().getBlockEntity(payload.pos) instanceof StationBuilderBlockEntity be) {
					be.length = payload.length;
					be.elements = new ArrayList<>(payload.elements);
					be.markDirty();
				}
				StationGenerator.build(
						context.player(),
						context.player().getServerWorld(),
						payload.pos,
						Direction.fromHorizontal(payload.facing),
						payload.length,
						new ArrayList<>(payload.elements)
				);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(SaveRailPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ItemStack stack = context.player().getMainHandStack();
				if (stack.getItem() instanceof RailBuilderItem) {
					RailBuilderConfig cfg = RailBuilderConfig.fromItem(stack);
					cfg.fromNbt(payload.nbt);
					cfg.saveToItem(stack);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(ClearRailStatePayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ItemStack stack = context.player().getMainHandStack();
				if (!stack.isEmpty()) {
					RailBuilderState.clear(stack);
					stack.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
					context.player().sendMessage(
							Text.translatable("message.stationbuilder.rail_builder.end"),
							true
					);
				}
			});
		});
	}
}