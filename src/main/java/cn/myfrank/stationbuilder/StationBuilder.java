package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.StationElement;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
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
	public static final Identifier CLEAR_RAIL_PACKET = new Identifier(MOD_ID, "clear_rail_state");

	// 新增：选取器与放置器的 1.20.4 自定义网络 Identifier
	public static final Identifier SYNC_AND_OPEN_PACKET_SELECTOR = new Identifier(MOD_ID, "sync_open_selector");
	public static final Identifier SAVE_SELECTION_PACKET = new Identifier(MOD_ID, "save_selection");
	public static final Identifier SYNC_AND_OPEN_PACKET_PLACER = new Identifier(MOD_ID, "sync_open_placer");
	public static final Identifier SAVE_PLACER_PACKET = new Identifier(MOD_ID, "save_placer");
	public static final Identifier UNDO_PLACER_PACKET = new Identifier(MOD_ID, "undo_placer");

	public static final ItemGroup STATION_GROUP = Registry.register(Registries.ITEM_GROUP,
			new Identifier(MOD_ID, "station_group"),
			FabricItemGroup.builder()
					.displayName(Text.translatable("itemGroup.stationbuilder.group"))
					.icon(() -> new ItemStack(ModBlocks.STATION_BUILDER_ITEM))
					.entries((displayContext, entries) -> {
						entries.add(ModBlocks.STATION_BUILDER_ITEM);
						entries.add(ModItems.RAIL_BUILDER_ITEM);
						entries.add(ModItems.BUILDING_SELECTOR_ITEM); // 新增选取器
						entries.add(ModItems.BUILDING_PLACER_ITEM);   // 新增放置器
					})
					.build());

	private static final boolean hasMTR = FabricLoader.getInstance().isModLoaded("mtr");
	public static boolean isMtrLoaded() { return hasMTR; }

	private static final boolean hasMSD = FabricLoader.getInstance().isModLoaded("msd");
	public static boolean isMsdLoaded() { return hasMSD; }

	public static boolean isSoftTransparent(BlockState state) {
		return state.isAir() || state.isReplaceable() ||
				!state.getFluidState().isEmpty() || state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES);
	}

	public static boolean isNotLiquidTransparent(BlockState state) {
		return state.isAir() || state.isReplaceable() ||
				state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES);
	}

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
				if (player.getWorld().getBlockEntity(pos) instanceof StationBuilderBlockEntity be) {
					be.length = length;
					be.elements = elements;
					be.markDirty();
				}
				StationGenerator.build(player, player.getServerWorld(), pos,
						Direction.fromHorizontal(facingInt), length, elements);
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

		// 新增：结构选择器 C2S 存盘包
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

		// 新增：放置器配置 C2S 保存包
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

		// 新增：撤销 C2S 控制包
		ServerPlayNetworking.registerGlobalReceiver(UNDO_PLACER_PACKET, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> {
				if (player.getMainHandStack().getItem() instanceof BuildingPlacerItem) {
					boolean success = PlacerHistoryManager.undo(player);
					if (success) {
						player.sendMessage(Text.translatable("message.stationbuilder.undo_success").formatted(net.minecraft.util.Formatting.GREEN), true);
					} else {
						player.sendMessage(Text.translatable("message.stationbuilder.undo_no_history").formatted(net.minecraft.util.Formatting.RED), true);
					}
				}
			});
		});

		// 新增：选取器左键点击设定 Pos 1 回调
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
	public static String getRotName(BlockRotation rotation) {
		return Text.translatable("gui.stationbuilder.rotation_" + rotation.name()).getString();
	}
}