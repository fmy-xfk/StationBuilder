package cn.myfrank.stationbuilder;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;

public class StationBuilderClient implements ClientModInitializer {
	private static final RailPreviewCache previewCache = new RailPreviewCache();
	@Override
	public void onInitializeClient() {
		StationBuilderKeyBindings.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (StationBuilderKeyBindings.CLEAR_RAIL_STATE.wasPressed() && client.player != null && client.player.getMainHandStack().getItem() instanceof RailBuilderItem) {
                ClientPlayNetworking.send(StationBuilder.CLEAR_RAIL_PACKET, PacketByteBufs.empty());
            }
			if (StationBuilderKeyBindings.UNDO_PLACER.wasPressed() && client.player != null && client.player.getMainHandStack().getItem() instanceof BuildingPlacerItem) {
				ClientPlayNetworking.send(StationBuilder.UNDO_PLACER_PACKET, PacketByteBufs.empty());
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(StationBuilder.SYNC_AND_OPEN_PACKET, (client, handler, buf, responseSender) -> {
			BlockPos pos = buf.readBlockPos();
			int facingIdx = buf.readInt();
			NbtCompound nbt = buf.readNbt();
			client.execute(() -> {
				client.setScreen(new StationEditorScreen(pos, Direction.fromHorizontal(facingIdx), nbt));
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(StationBuilder.SYNC_AND_OPEN_PACKET_RAIL, (client, handler, buf, responseSender) -> {
			NbtCompound nbt = buf.readNbt();
			client.execute(() -> {
				client.setScreen(new RailBuilderScreen(nbt));
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(StationBuilder.SYNC_AND_OPEN_PACKET_SELECTOR, (client, handler, buf, responseSender) -> {
			boolean hasP1 = buf.readBoolean();
			BlockPos p1 = hasP1 ? buf.readBlockPos() : null;
			boolean hasP2 = buf.readBoolean();
			BlockPos p2 = hasP2 ? buf.readBlockPos() : null;
			client.execute(() -> {
				client.setScreen(new BuildingSelectorScreen(p1, p2));
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(StationBuilder.SYNC_AND_OPEN_PACKET_PLACER, (client, handler, buf, responseSender) -> {
			NbtCompound nbt = buf.readNbt();
			client.execute(() -> {
				client.setScreen(new BuildingPlacerScreen(nbt));
			});
		});

		ModelPredicateProviderRegistry.register(
            ModItems.RAIL_BUILDER_ITEM,
            new Identifier("building"),
            (stack, world, entity, seed) -> {
                // 必须有 world（GUI / 手持时一定有）
                if (world == null) return 0;

                // 是否有 lastPos（建造中）
                if (!RailBuilderState.isBuilding(stack)) {
                    return 0;
                }

                // 用世界时间做闪烁（每 10 tick 切一次）
                long t = world.getTime();
                return (t / 10 % 2 == 0) ? 1 : 0;
            }
        );

		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.world == null) return;

			ItemStack stack = client.player.getMainHandStack();

			// 1. 选取工具高亮
			if (stack.getItem() instanceof BuildingSelectorItem) {
				BlockPos p1 = BuildingSelectorItem.getPos1(stack);
				BlockPos p2 = BuildingSelectorItem.getPos2(stack);
				if (p1 != null || p2 != null) {
					renderSelectionPreview(context, p1, p2);
				}
				return;
			}

			// 2. 放置预览盒高亮
			if (stack.getItem() instanceof BuildingPlacerItem) {
				HitResult hit = client.crosshairTarget;
				if (hit instanceof BlockHitResult bhr) {
					BlockPos pos = bhr.getBlockPos().offset(bhr.getSide());
					renderPlacerPreview(context, pos, stack);
				}
				return;
			}

			// 3. 铁轨放置工具高亮
			if (stack.getItem() instanceof RailBuilderItem) {
				HitResult hit = client.crosshairTarget;
				if (!(hit instanceof BlockHitResult bhr)) return;
				var state = client.world.getBlockState(bhr.getBlockPos());
				var pos = bhr.getBlockPos();
				if (!StationBuilder.isSoftTransparent(state)) pos = pos.offset(bhr.getSide());
				renderRailPreview(context, client.player, pos, stack);
			}
		});
	}

	private static void renderSelectionPreview(WorldRenderContext context, BlockPos p1, BlockPos p2) {
		Vec3d cam = context.camera().getPos();
		VertexConsumer consumer = context.consumers().getBuffer(RenderLayer.getLines());
		MatrixStack matrices = context.matrixStack();

		if (p1 != null && p2 != null) {
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
			Box box = new Box(
					min.getX(), min.getY(), min.getZ(),
					max.getX() + 1, max.getY() + 1, max.getZ() + 1
			).offset(-cam.x, -cam.y, -cam.z);
			WorldRenderer.drawBox(matrices, consumer, box, 0f, 1f, 0f, 0.4f);
		} else {
			BlockPos setPos = p1 != null ? p1 : p2;
			Box box = new Box(setPos).offset(-cam.x, -cam.y, -cam.z);
			WorldRenderer.drawBox(matrices, consumer, box, 0f, 1f, 1f, 0.4f);
		}
	}

	private static void renderPlacerPreview(WorldRenderContext context, BlockPos targetPos, ItemStack stack) {
		BuildingPlacerConfig cfg = BuildingPlacerConfig.fromItem(stack);
		var templateOpt = BuildingTemplateManager.getTemplate(cfg.presetName);
		if (templateOpt.isPresent()) {
			StructureTemplate template = templateOpt.get();
			Vec3i rawSize = template.getSize();
			BlockPos sizePos = new BlockPos(rawSize.getX(), rawSize.getY(), rawSize.getZ());

			net.minecraft.structure.StructurePlacementData placementData = new net.minecraft.structure.StructurePlacementData()
					.setRotation(cfg.rotation)
					.setMirror(BlockMirror.NONE);

			BlockPos rotatedSize = StructureTemplate.transform(placementData, sizePos);

			double minX = targetPos.getX();
			double minY = targetPos.getY();
			double minZ = targetPos.getZ();
			
			double rotatedSizeX = rotatedSize.getX();
			double rotatedSizeY = rotatedSize.getY();
			double rotatedSizeZ = rotatedSize.getZ();

			// 修正：如果在负方向延伸，由于方块占用的是格子，需在極值下限上安全地引入 +1 偏移校正
			double realMinX = Math.min(minX, minX + rotatedSizeX) + (rotatedSizeX < 0 ? 1 : 0);
			double realMaxX = Math.max(minX, minX + rotatedSizeX) + (rotatedSizeX < 0 ? 1 : 0);

			double realMinY = Math.min(minY, minY + rotatedSizeY) + (rotatedSizeY < 0 ? 1 : 0);
			double realMaxY = Math.max(minY, minY + rotatedSizeY) + (rotatedSizeY < 0 ? 1 : 0);

			double realMinZ = Math.min(minZ, minZ + rotatedSizeZ) + (rotatedSizeZ < 0 ? 1 : 0);
			double realMaxZ = Math.max(minZ, minZ + rotatedSizeZ) + (rotatedSizeZ < 0 ? 1 : 0);

			Vec3d cam = context.camera().getPos();
			VertexConsumer consumer = context.consumers().getBuffer(RenderLayer.getLines());
			MatrixStack matrices = context.matrixStack();
			Box box = new Box(realMinX, realMinY, realMinZ, realMaxX, realMaxY, realMaxZ).offset(-cam.x, -cam.y, -cam.z);
			WorldRenderer.drawBox(matrices, consumer, box, 1f, 0.5f, 0f, 0.4f);
		}
	}

	private static void renderRailPreview(
			WorldRenderContext context,
			PlayerEntity player,
			BlockPos targetPos,
			ItemStack stack
	) {
		ArrayList<BlockPos> nodes = RailGenerator.calcRailNodes(
			targetPos, player.getYaw(), RailBuilderConfig.fromItem(stack)
		);
		if (nodes == null) return;
		VertexConsumer consumer = context.consumers().getBuffer(RenderLayer.getLines());
		MatrixStack matrices = context.matrixStack();
		Vec3d cam = context.camera().getPos();

		var lastPair = RailBuilderState.getLastNodesAndAngle(stack);
		if (lastPair == null || lastPair.left() == null) {
			for (BlockPos node : nodes) {
				drawBox(matrices, consumer, node, cam, 0f, 1f, 1f, 0.6f); // 青色半透明
			}
		} else {
			var lastNodes = lastPair.left();
			float lastAngle = lastPair.right();

			if (lastNodes.size() != nodes.size()) {
				// 清除本地状态（物品NBT）
				RailBuilderState.clear(stack);
				stack.getOrCreateNbt().remove("CustomModelData");
				// 通知服务端清除状态
				ClientPlayNetworking.send(StationBuilder.CLEAR_RAIL_PACKET, PacketByteBufs.create());
				// 直接返回，不再绘制任何预览（避免越界）
				return;
			}

			RailMath.adjustPointSequence(lastNodes, nodes);
			float angle = player.getYaw();
			Vec3d textPos = getPreviewCenterPos(lastNodes, targetPos);
			var d = getDelta(lastNodes, targetPos);
			double minRadius = 1e9, minLength = 1e9;
			int successCount = 0;

			for (int i = 0; i < lastNodes.size(); ++i) {
				var node = nodes.get(i);
				var lastNode = lastNodes.get(i);
				drawBox(matrices, consumer, lastNode, cam, 0f, 1f, 1f, 0.6f); // 青色半透明
				drawBox(matrices, consumer, node, cam, 0f, 1f, 1f, 0.6f); // 青色半透明
				if (StationBuilder.isMtrLoaded()) {
					var preview = previewCache.get(lastNode, MTRIntegration.parseAngle(lastAngle),
							node, MTRIntegration.parseAngle(angle));
					if(preview == null) {
						preview = MTRIntegration.testConnectRailNodes(lastAngle, angle, lastNode, node);
						previewCache.put(lastNode, MTRIntegration.parseAngle(lastAngle),
								node, MTRIntegration.parseAngle(angle), preview);
					}
					if (preview.success()) {
						successCount += 1;
						renderCurve(matrices, context.consumers(), context.camera(), preview.positions());
						if (preview.radius() > 0) {
							minRadius = Math.min(minRadius, preview.radius());
							minLength = Math.min(minLength, preview.length());
						}
					}
				}
			}
			String extras = "";
			int extra_color = 0xAAAAAA;
			if (successCount < lastNodes.size()) {
				extras = Text.translatable("message.stationbuilder.rail_builder.fail", lastNodes.size() - successCount).getString();
				if (successCount > 0) {
					extras += ", ";
				}
				extra_color = 0xFF5555;
			}
			if (successCount > 0) {
				if (minRadius < 1e9) {
					extras += Text.translatable("message.stationbuilder.rail_builder.min_radius", String.format("%.2f", minRadius), String.format("%.2f", minLength)).getString();
				} else {
					extras += Text.translatable("message.stationbuilder.rail_builder.straight_line").getString();
				}
			}
			renderDeltaText3D(context, textPos, d, extras, extra_color);
		}
	}

	private static void renderCurve(
			MatrixStack matrices,
			VertexConsumerProvider consumers,
			Camera camera,
			List<Vec3d> points
	) {
		if (points.size() < 2) return;

		Vec3d camPos = camera.getPos();

		VertexConsumer vc = consumers.getBuffer(RenderLayer.LINES);

		matrices.push();
		matrices.translate(-camPos.x, -camPos.y, -camPos.z);

		Matrix4f mat = matrices.peek().getPositionMatrix();

		for (int i = 0; i < points.size() - 1; i++) {
			Vec3d p0 = points.get(i);
			Vec3d p1 = points.get(i + 1);

			vc.vertex(mat, (float)p0.x, (float)p0.y, (float)p0.z)
					.color(255, 0, 0, 255)
					.normal(1, 0, 0)
					.next();

			vc.vertex(mat, (float)p1.x, (float)p1.y, (float)p1.z)
					.color(255, 0, 0, 255)
					.normal(1, 0, 0)
					.next();
		}

		matrices.pop();
	}

	private static void drawBox(
			MatrixStack matrices,
			VertexConsumer consumer,
			BlockPos pos,
			Vec3d cam,
			float r, float g, float b, float a
	) {
		Box box = new Box(pos).offset(-cam.x, -cam.y, -cam.z);
		WorldRenderer.drawBox(matrices, consumer, box, r, g, b, a);
	}

	private static Vec3d getPreviewCenterPos(
			List<BlockPos> lastNodes,
			BlockPos anchor
	) {
		Vec3d lastCenter = Vec3d.ZERO;
		for (BlockPos p : lastNodes) {
			lastCenter = lastCenter.add(p.toCenterPos());
		}
		lastCenter = lastCenter.multiply(1.0 / lastNodes.size());

		Vec3d delta = anchor.toCenterPos().subtract(lastCenter);

		return lastCenter.add(delta).add(0, 0.5, 0); // 文本抬高一点
	}

	private static Vec3i getDelta(List<BlockPos> lastNodes, BlockPos anchor) {
		double sumX = 0, sumY = 0, sumZ = 0;
		for (BlockPos p : lastNodes) {
			sumX += p.getX();
			sumY += p.getY();
			sumZ += p.getZ();
		}
		int lastX = (int) Math.round(sumX / lastNodes.size());
		int lastY = (int) Math.round(sumY / lastNodes.size());
		int lastZ = (int) Math.round(sumZ / lastNodes.size());
		return new Vec3i(anchor.getX() - lastX,anchor.getY() - lastY, anchor.getZ() - lastZ);
	}

	private static void renderDeltaText3D(
			WorldRenderContext context,
			Vec3d worldPos,
			Vec3i d,
			String extras,
			int extra_color
	) {
		MinecraftClient client = MinecraftClient.getInstance();
		Camera camera = context.camera();
		MatrixStack matrices = context.matrixStack();

		TextRenderer textRenderer = client.textRenderer;

		String text;
		int color;

		if (d.getY() > 0) {
			text = d.getX() + ", ↑ +" + d.getY() + ", " + d.getZ();
			color = 0x00FF00;
		} else if (d.getY() < 0) {
			text = d.getX() + ", ↓ " + d.getY() + ", " + d.getZ();
			color = 0xFF5555;
		} else {
			text = d.getX() + ", = 0, " + d.getZ();
			color = 0xAAAAAA;
		}

		matrices.push();

		// 世界坐标 → 相机坐标
		Vec3d camPos = camera.getPos();
		matrices.translate(
				worldPos.x - camPos.x,
				worldPos.y - camPos.y,
				worldPos.z - camPos.z
		);

		// billboard：始终面向玩家
		matrices.multiply(camera.getRotation());

		// 缩放（非常关键）
		float scale = 0.025F;
		matrices.scale(-scale, -scale, scale);

		// 文字居中
		float x = -textRenderer.getWidth(text) / 2f;
		float y = 0;

		RenderSystem.disableDepthTest();

		textRenderer.draw(
				text,
				x,
				y,
				color,
				false,
				matrices.peek().getPositionMatrix(),
				context.consumers(),
				TextRenderer.TextLayerType.NORMAL,
				0,
				0xF000F0
		);

		float x2 = -textRenderer.getWidth(extras) / 2f;
		textRenderer.draw(
				extras,
				x2,
				y + textRenderer.getWrappedLinesHeight(text, 100) + 3,
				extra_color,
				false,
				matrices.peek().getPositionMatrix(),
				context.consumers(),
				TextRenderer.TextLayerType.NORMAL,
				0,
				0xF000F0
		);

		RenderSystem.enableDepthTest();

		matrices.pop();
	}

}