package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.StationElement;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
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

	public static final ItemGroup STATION_GROUP = Registry.register(
			Registries.ITEM_GROUP,
			Identifier.of(MOD_ID, "station_group"),
			FabricItemGroup.builder()
					.displayName(Text.translatable("itemGroup.stationbuilder.group"))
					.icon(() -> new ItemStack(ModBlocks.STATION_BUILDER_ITEM))
					.entries((displayContext, entries) -> {
						entries.add(ModBlocks.STATION_BUILDER_ITEM);
						entries.add(ModItems.RAIL_BUILDER_ITEM);
						entries.add(ModItems.BUILDING_SELECTOR_ITEM);
						entries.add(ModItems.BUILDING_PLACER_ITEM);
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

	public static final class SyncOpenSelectorPayload implements CustomPayload {
		public static final CustomPayload.Id<SyncOpenSelectorPayload> ID =
				new CustomPayload.Id<>(Identifier.of(MOD_ID, "sync_open_selector"));
		public static final PacketCodec<RegistryByteBuf, SyncOpenSelectorPayload> CODEC =
				PacketCodec.ofStatic(
						(buf, payload) -> {
							buf.writeBoolean(payload.pos1 != null);
							if (payload.pos1 != null) buf.writeBlockPos(payload.pos1);
							buf.writeBoolean(payload.pos2 != null);
							if (payload.pos2 != null) buf.writeBlockPos(payload.pos2);
						},
						buf -> {
							BlockPos p1 = buf.readBoolean() ? buf.readBlockPos() : null;
							BlockPos p2 = buf.readBoolean() ? buf.readBlockPos() : null;
							return new SyncOpenSelectorPayload(p1, p2);
						}
				);

		public final BlockPos pos1;
		public final BlockPos pos2;

		public SyncOpenSelectorPayload(BlockPos pos1, BlockPos pos2) {
			this.pos1 = pos1;
			this.pos2 = pos2;
		}

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

    // 选取工具 保存选取结构
	public static final class SaveSelectionPayload implements CustomPayload {
		public static final CustomPayload.Id<SaveSelectionPayload> ID =
				new CustomPayload.Id<>(Identifier.of(MOD_ID, "save_selection"));
		public static final PacketCodec<RegistryByteBuf, SaveSelectionPayload> CODEC =
				PacketCodec.ofStatic(
						(buf, payload) -> {
							buf.writeString(payload.name);
							buf.writeBlockPos(payload.pos1);
							buf.writeBlockPos(payload.pos2);
							buf.writeBoolean(payload.includeEntities);
						},
						buf -> new SaveSelectionPayload(
								buf.readString(),
								buf.readBlockPos(),
								buf.readBlockPos(),
								buf.readBoolean()
						)
				);

		public final String name;
		public final BlockPos pos1;
		public final BlockPos pos2;
		public final boolean includeEntities;

		public SaveSelectionPayload(String name, BlockPos pos1, BlockPos pos2, boolean includeEntities) {
			this.name = name;
			this.pos1 = pos1;
			this.pos2 = pos2;
			this.includeEntities = includeEntities;
		}

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

    // 放置工具 GUI 开启同步
	public static final class SyncOpenPlacerPayload implements CustomPayload {
		public static final CustomPayload.Id<SyncOpenPlacerPayload> ID =
				new CustomPayload.Id<>(Identifier.of(MOD_ID, "sync_open_placer"));
		public static final PacketCodec<RegistryByteBuf, SyncOpenPlacerPayload> CODEC =
				PacketCodec.ofStatic(
						(buf, payload) -> buf.writeNbt(payload.nbt),
						buf -> new SyncOpenPlacerPayload(buf.readNbt())
				);

		public final NbtCompound nbt;

		public SyncOpenPlacerPayload(NbtCompound nbt) {
			this.nbt = nbt;
		}

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

    // 放置工具 保存属性配置
	public static final class SavePlacerPayload implements CustomPayload {
		public static final CustomPayload.Id<SavePlacerPayload> ID =
				new CustomPayload.Id<>(Identifier.of(MOD_ID, "save_placer"));
		public static final PacketCodec<RegistryByteBuf, SavePlacerPayload> CODEC =
				PacketCodec.ofStatic(
						(buf, payload) -> buf.writeNbt(payload.nbt),
						buf -> new SavePlacerPayload(buf.readNbt())
				);

		public final NbtCompound nbt;

		public SavePlacerPayload(NbtCompound nbt) {
			this.nbt = nbt;
		}

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public static final class UndoPlacerPayload implements CustomPayload {
		public static final CustomPayload.Id<UndoPlacerPayload> ID =
				new CustomPayload.Id<>(Identifier.of(MOD_ID, "undo_placer"));
		public static final PacketCodec<RegistryByteBuf, UndoPlacerPayload> CODEC =
				PacketCodec.ofStatic(
						(buf, payload) -> { /* 空包 */ },
						buf -> new UndoPlacerPayload()
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

	public static boolean isNotLiquidTransparent(BlockState state) {
		return state.isAir() || state.isReplaceable() ||
				state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES);
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

		PayloadTypeRegistry.playS2C().register(SyncOpenSelectorPayload.ID, StationBuilder.SyncOpenSelectorPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncOpenPlacerPayload.ID, StationBuilder.SyncOpenPlacerPayload.CODEC);

        // 注册 C2S 网络通道
        PayloadTypeRegistry.playC2S().register(SaveSelectionPayload.ID, StationBuilder.SaveSelectionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SavePlacerPayload.ID, StationBuilder.SavePlacerPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(UndoPlacerPayload.ID, StationBuilder.UndoPlacerPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(UndoPlacerPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				net.minecraft.server.network.ServerPlayerEntity player = context.player();
				if (player.getMainHandStack().getItem() instanceof BuildingPlacerItem) {
					boolean success = PlacerHistoryManager.undo(player);
					if (success) {
						player.sendMessage(Text.translatable("message.stationbuilder.undo_success")
								.formatted(net.minecraft.util.Formatting.GREEN), true);
					} else {
						player.sendMessage(Text.translatable("message.stationbuilder.undo_no_history")
								.formatted(net.minecraft.util.Formatting.RED), true);
					}
				}
			});
		});

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
						Direction.fromHorizontalQuarterTurns(payload.facing),
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
		// 新增：拦截选取器的左键动作设定 Pos 1
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.getItem() instanceof BuildingSelectorItem) {
                if (!world.isClient) {
                    BuildingSelectorItem.setPos1(stack, pos);
                    player.sendMessage(Text.translatable("message.stationbuilder.pos1_set_to:", pos.toShortString())
                            .formatted(net.minecraft.util.Formatting.GREEN), true);
                }
                return ActionResult.SUCCESS; // 拦截默认打破方块行为
            }
            return ActionResult.PASS;
        });

        // 放置包处理：客户端改变参数并关闭 Placer 界面时发给服务器
        ServerPlayNetworking.registerGlobalReceiver(SavePlacerPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ItemStack stack = context.player().getMainHandStack();
                if (stack.getItem() instanceof BuildingPlacerItem) {
                    BuildingPlacerConfig cfg = BuildingPlacerConfig.fromItem(stack);
                    cfg.fromNbt(payload.nbt);
                    cfg.saveToItem(stack);
                }
            });
        });

        // 选取结构保存包：在服务端提取坐标信息并构建 StructureTemplate 存盘
        ServerPlayNetworking.registerGlobalReceiver(SaveSelectionPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerWorld world = context.player().getServerWorld();
                BlockPos p1 = payload.pos1;
                BlockPos p2 = payload.pos2;
                if (p1 == null || p2 == null) return;

                BlockPos min = new BlockPos(
                        Math.min(p1.getX(), p2.getX()),
                        Math.min(p1.getY(), p2.getY()),
                        Math.min(p1.getZ(), p2.getZ())
                );
                BlockPos max = new BlockPos(
                        Math.max(p1.getX(), p2.getX()),
                        Math.max(p1.getY(), p2.getY()),
                        Math.max(p1.getZ(), p2.getZ())
                );
                // 1.21.4 `saveFromWorld` 使用的体积需为 Vec3i 宽、高、长
                net.minecraft.util.math.Vec3i size = new net.minecraft.util.math.Vec3i(
                        max.getX() - min.getX() + 1,
                        max.getY() - min.getY() + 1,
                        max.getZ() - min.getZ() + 1
                );

                StructureTemplate template = new StructureTemplate();
                // 传入忽略列表，通常为空 list
                template.saveFromWorld(world, min, size, payload.includeEntities, null);
                BuildingTemplateManager.addTemplate(payload.name, template);

                context.player().sendMessage(Text.translatable("message.stationbuilder.save_selection_success", payload.name)
                        .formatted(net.minecraft.util.Formatting.GREEN), false);
            });
        });
	}
	public static String getRotName(BlockRotation rotation) {
		return Text.translatable("gui.stationbuilder.rotation_" + rotation.name()).getString();
	}
}