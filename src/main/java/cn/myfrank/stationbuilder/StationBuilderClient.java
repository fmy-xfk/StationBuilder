package cn.myfrank.stationbuilder;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
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
			if (StationBuilderKeyBindings.CLEAR_RAIL_STATE.wasPressed()
					&& client.player != null
					&& client.player.getMainHandStack().getItem() instanceof RailBuilderItem) {
				ClientPlayNetworking.send(new StationBuilder.ClearRailStatePayload());
			}
            
			if (StationBuilderKeyBindings.UNDO_PLACER.wasPressed()
					&& client.player != null
					&& client.player.getMainHandStack().getItem() instanceof BuildingPlacerItem) {
				ClientPlayNetworking.send(new StationBuilder.UndoPlacerPayload());
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(StationBuilder.SyncOpenStationPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				context.client().setScreen(
						new StationEditorScreen(
								payload.pos,
								Direction.fromHorizontal(payload.facing),
								payload.nbt
						)
				);
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(StationBuilder.SyncOpenRailPayload.ID, (payload, context) -> {
			context.client().execute(() -> context.client().setScreen(new RailBuilderScreen(payload.nbt)));
		});

		ClientPlayNetworking.registerGlobalReceiver(StationBuilder.SyncOpenSelectorPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				context.client().setScreen(new BuildingSelectorScreen(payload.pos1, payload.pos2));
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(StationBuilder.SyncOpenPlacerPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				context.client().setScreen(new BuildingPlacerScreen(payload.nbt));
			});
		});

		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.world == null) return;

			ItemStack stack = client.player.getMainHandStack();

			// 1. 选取工具 渲染
			if (stack.getItem() instanceof BuildingSelectorItem) {
				BlockPos p1 = BuildingSelectorItem.getPos1(stack);
				BlockPos p2 = BuildingSelectorItem.getPos2(stack);
				if (p1 != null || p2 != null) {
					renderSelectionPreview(context, p1, p2);
				}
				return;
			}

			// 2. 放置工具 渲染
			if (stack.getItem() instanceof BuildingPlacerItem) {
				HitResult hit = client.crosshairTarget;
				if (hit instanceof BlockHitResult bhr) {
					BlockPos pos = bhr.getBlockPos().offset(bhr.getSide());
					renderPlacerPreview(context, pos, stack);
				}
				return;
			}

			// 3. 原本的 RailBuilder 渲染
			if (stack.getItem() instanceof RailBuilderItem) {
				HitResult hit = client.crosshairTarget;
				if (!(hit instanceof BlockHitResult bhr)) return;
				var state = client.world.getBlockState(bhr.getBlockPos());
				var pos = bhr.getBlockPos();
				if (!StationBuilder.isSoftTransparent(state)) pos = pos.offset(bhr.getSide());
				renderRailPreviewGeometry(context, client.player, pos, stack);
			}
		});

		WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.world == null) return;

			ItemStack stack = client.player.getMainHandStack();
			if (!(stack.getItem() instanceof RailBuilderItem)) return;

			HitResult hit = client.crosshairTarget;
			if (!(hit instanceof BlockHitResult bhr)) return;

			var state = client.world.getBlockState(bhr.getBlockPos());
			var pos = bhr.getBlockPos();
			if (!StationBuilder.isSoftTransparent(state)) pos = pos.offset(bhr.getSide());

			renderRailPreviewText(context, client.player, pos, stack);
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
			WorldRenderer.drawBox(matrices, consumer, box, 0f, 1f, 0f, 0.4f); // 绿色高亮
		} else {
			BlockPos setPos = p1 != null ? p1 : p2;
			Box box = new Box(setPos).offset(-cam.x, -cam.y, -cam.z);
			WorldRenderer.drawBox(matrices, consumer, box, 0f, 1f, 1f, 0.4f); // 青色高亮单个方块
		}
	}

	private static void renderPlacerPreview(WorldRenderContext context, BlockPos targetPos, ItemStack stack) {
		BuildingPlacerConfig cfg = BuildingPlacerConfig.fromItem(stack);
		var templateOpt = BuildingTemplateManager.getTemplate(cfg.presetName);
		if (templateOpt.isPresent()) {
			StructureTemplate template = templateOpt.get();
			Vec3i rawSize = template.getSize();
			BlockPos sizePos = new BlockPos(rawSize.getX(), rawSize.getY(), rawSize.getZ());

			// 1. 创建对齐当前放置设置的 StructurePlacementData
			StructurePlacementData placementData = new StructurePlacementData()
					.setRotation(cfg.rotation)
					.setMirror(net.minecraft.util.BlockMirror.NONE);

			// 2. 使用正确的静态 transform 方法进行坐标偏转
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

	private static void renderRailPreviewGeometry(
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
				drawBox(matrices, consumer, node, cam, 0f, 1f, 1f, 0.6f);
			}
			return;
		}

		var lastNodes = lastPair.left();
		float lastAngle = lastPair.right();

		if (lastNodes.size() != nodes.size()) {
			return;
		}

		RailMath.adjustPointSequence(lastNodes, nodes);
		float angle = player.getYaw();

		for (int i = 0; i < lastNodes.size(); ++i) {
			var node = nodes.get(i);
			var lastNode = lastNodes.get(i);

			drawBox(matrices, consumer, lastNode, cam, 0f, 1f, 1f, 0.6f);
			drawBox(matrices, consumer, node, cam, 0f, 1f, 1f, 0.6f);

			if (StationBuilder.isMtrLoaded()) {
				var preview = previewCache.get(
						lastNode, MTRIntegration.parseAngle(lastAngle),
						node, MTRIntegration.parseAngle(angle)
				);
				if (preview == null) {
					preview = MTRIntegration.testConnectRailNodes(lastAngle, angle, lastNode, node);
					previewCache.put(
							lastNode, MTRIntegration.parseAngle(lastAngle),
							node, MTRIntegration.parseAngle(angle),
							preview
					);
				}
				if (preview != null && preview.success()) {
					renderCurve(matrices, context.consumers(), context.camera(), preview.positions());
				}
			}
		}
	}

	private static void renderRailPreviewText(
			WorldRenderContext context,
			PlayerEntity player,
			BlockPos targetPos,
			ItemStack stack
	) {
		ArrayList<BlockPos> nodes = RailGenerator.calcRailNodes(
				targetPos, player.getYaw(), RailBuilderConfig.fromItem(stack)
		);
		if (nodes == null) return;

		var lastPair = RailBuilderState.getLastNodesAndAngle(stack);
		if (lastPair == null || lastPair.left() == null) return;

		var lastNodes = lastPair.left();
		float lastAngle = lastPair.right();

		if (lastNodes.size() != nodes.size()) {
			return;
		}

		RailMath.adjustPointSequence(lastNodes, nodes);
		float angle = player.getYaw();

		Vec3d textPos = getPreviewCenterPos(lastNodes, targetPos);
		Vec3i d = getDelta(lastNodes, targetPos);

		double minRadius = 1e9, minLength = 1e9;
		int successCount = 0;

		for (int i = 0; i < lastNodes.size(); ++i) {
			var lastNode = lastNodes.get(i);
			var node = nodes.get(i);

			if (!StationBuilder.isMtrLoaded()) continue;

			var preview = previewCache.get(
					lastNode, MTRIntegration.parseAngle(lastAngle),
					node, MTRIntegration.parseAngle(angle)
			);
			if (preview == null) {
				preview = MTRIntegration.testConnectRailNodes(lastAngle, angle, lastNode, node);
				previewCache.put(
						lastNode, MTRIntegration.parseAngle(lastAngle),
						node, MTRIntegration.parseAngle(angle),
						preview
				);
			}

			if (preview != null && preview.success()) {
				successCount += 1;
				if (preview.radius() > 0) {
					minRadius = Math.min(minRadius, preview.radius());
					minLength = Math.min(minLength, preview.length());
				}
			}
		}

		String extras = "";
		int extraColor = 0xAAAAAA;

		if (successCount < lastNodes.size()) {
			extras = Text.translatable("message.stationbuilder.rail_builder.fail", lastNodes.size() - successCount).getString();
			if (successCount > 0) extras += ", ";
			extraColor = 0xFF5555;
		}

		if (successCount > 0) {
			if (minRadius < 1e9) {
				extras += Text.translatable(
						"message.stationbuilder.rail_builder.min_radius",
						String.format("%.2f", minRadius),
						String.format("%.2f", minLength)
				).getString();
			} else {
				extras += Text.translatable("message.stationbuilder.rail_builder.straight_line").getString();
			}
		}

		renderDeltaText3D(context, textPos, d, extras, extraColor);
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

			vc.vertex(mat, (float) p0.x, (float) p0.y, (float) p0.z)
					.color(255, 0, 0, 255)
					.normal(1, 0, 0);

			vc.vertex(mat, (float) p1.x, (float) p1.y, (float) p1.z)
					.color(255, 0, 0, 255)
					.normal(1, 0, 0);
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
		return lastCenter.add(delta).add(0, 0.5, 0);
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
		return new Vec3i(anchor.getX() - lastX, anchor.getY() - lastY, anchor.getZ() - lastZ);
	}

	private static void renderDeltaText3D(
			WorldRenderContext context,
			Vec3d worldPos,
			Vec3i d,
			String extras,
			int extraColor
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
		Vec3d camPos = camera.getPos();
		matrices.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
		matrices.multiply(camera.getRotation());
		float scale = 0.025F;
		matrices.scale(scale, -scale, scale);

		float x = -textRenderer.getWidth(text) / 2f;
		float y = 0;

		textRenderer.draw(
				text, x, y, color, false,
				matrices.peek().getPositionMatrix(),
				context.consumers(),
				TextRenderer.TextLayerType.SEE_THROUGH,
				0, 0xF000F0
		);

		if (extras != null && !extras.isEmpty()) {
			float x2 = -textRenderer.getWidth(extras) / 2f;
			float y2 = y + textRenderer.fontHeight + 3; // 或者按换行后的实际高度算
			textRenderer.draw(
					extras, x2, y2, extraColor, false,
					matrices.peek().getPositionMatrix(),
					context.consumers(),
					TextRenderer.TextLayerType.SEE_THROUGH,
					0, 0xF000F0
			);
		}

		matrices.pop();
	}
}