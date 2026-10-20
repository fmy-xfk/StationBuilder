package cn.myfrank.stationbuilder.schematic4j.schematic;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import cn.myfrank.stationbuilder.schematic4j.SchematicFormat;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlock;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockEntity;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockPos;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicEntity;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicItem;

import static cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlock.AIR;

/**
 * A Schematica schematic. Read more about it at <a href="https://minecraft.fandom.com/wiki/Schematic_file_format">https://minecraft.fandom.com/wiki/Schematic_file_format</a>
 * <br>
 * <h2>Implementations</h2>
 * <ul>
 *     <li><a href="https://github.com/EngineHub/WorldEdit/blob/master/worldedit-core/src/main/java/com/sk89q/worldedit/extent/clipboard/io/MCEditSchematicReader.java">WorldEdit</a></li>
 *     <li><a href="https://github.com/mcedit/pymclevel/blob/master/schematic.py">MCEdit</a></li>
 *     <li><a href="https://github.com/mcedit/mcedit2/blob/master/src/mceditlib/schematic.py">MCEdit2</a></li>
 *     <li><a href="https://github.com/Khroki/MCEdit-Unified/blob/master/pymclevel/schematic.py">MCEdit-Unified</a></li>
 *     <li><a href="https://github.com/Lunatrius/Schematica/blob/master/src/main/java/com/github/lunatrius/schematica/world/schematic/SchematicAlpha.java">Schematica</a></li>
 *     <li><a href="https://github.com/CzechPMDevs/BuilderTools">BuilderTools - PocketMine</a></li>
 * </ul>
 */
public class SchematicaSchematic implements Schematic {

	public static final String MATERIAL_CLASSIC = "Classic";
	public static final String MATERIAL_ALPHA = "Alpha";
	public static final String MATERIAL_STRUCTURE = "Structure";

	/**
	 * The schematic width, the X axis.
	 */
	public int width;

	/**
	 * The schematic height, the Y axis.
	 */
	public int height;

	/**
	 * The schematic length, the Z axis.
	 */
	public int length;

	/**
	 * The unpacked list of block IDs.
	 */
	public int  [] blockIds = new int[0];

	/**
	 * The unpacked list of block metadata (used as discriminator before Minecraft's 1.7 block ID overhaul).
	 */
	public int  [] blockMetadata = new int[0];

	/**
	 * The unpacked list of blocks.
	 */
	public String  [] blockPalette = new String[0];

	/**
	 * The list of block/tile entities.
	 */
	public  SchematicBlockEntity  [] blockEntities = new SchematicBlockEntity[0];

	/**
	 * The list of entities.
	 */
	public  SchematicEntity  [] entities = new SchematicEntity[0];

	/**
	 * The schematic icon, if available.
	 */
	public  SchematicItem icon;

	/**
	 * The schematic materials, if available.
	 * <p>
	 * One of:
	 * <ul>
	 *     <li>{@link SchematicaSchematic#MATERIAL_CLASSIC MATERIAL_CLASSIC}</li>
	 *     <li>{@link SchematicaSchematic#MATERIAL_ALPHA MATERIAL_ALPHA}</li>
	 *     <li>{@link SchematicaSchematic#MATERIAL_STRUCTURE MATERIAL_STRUCTURE}</li>
	 * </ul>
	 */
	public  String materials;

	public SchematicaSchematic() {
	}

	@Override
	public  SchematicFormat format() {
		return SchematicFormat.SCHEMATICA;
	}

	@Override
	public int width() {
		return width;
	}

	@Override
	public int height() {
		return height;
	}

	@Override
	public int length() {
		return length;
	}

	@Override
	public  SchematicBlockPos offset() {
		return SchematicBlockPos.ZERO;
	}

	@Override
	public  SchematicBlock block(int x, int y, int z) {
		final int blockIndex = posToIndex(x, y, z);
		if (blockIndex < 0 || blockIndex >= blockIds.length) {
			return AIR; // outside bounds
		}

		final int blockId = blockIds[blockIndex];
		String blockName = blockPalette[blockId];
		if (blockName == null) {
			blockName = "minecraft:legacy_id_" + blockId;
		}

		final int metadata = blockMetadata[blockIndex];
		final Map<String, String> states = new TreeMap<>();
		if (metadata != 0) {
			states.put("metadata", String.valueOf(metadata));
		}

		return new SchematicBlock(blockName, states);
	}

	/**
	 * The raw block ID data.
	 *
	 * @return The raw block data
	 */
	public int  [] blockIdData() {
		return blockIds;
	}

	/**
	 * The raw block metadata.
	 *
	 * @return The raw block data
	 */
	public int  [] blockMetadata() {
		return blockMetadata;
	}

	/**
	 * The raw block palette.
	 *
	 * @return The raw block palette
	 */
	public String  [] blockPalette() {
		return blockPalette;
	}

	@Override
	public  Stream<SchematicBlockEntity> blockEntities() {
		return Arrays.stream(blockEntities);
	}

	/**
	 * The raw block entity data.
	 *
	 * @return The raw block entity data
	 */
	public  SchematicBlockEntity[] blockEntityData() {
		return blockEntities;
	}

	@Override
	public  Stream<SchematicEntity> entities() {
		return Arrays.stream(entities);
	}

	/**
	 * The raw entity data.
	 *
	 * @return The raw entity data
	 */
	public  SchematicEntity[] entityData() {
		return entities;
	}

	@Override
	public  SchematicItem icon() {
		return icon;
	}

	/**
	 * The schematic materials, if available.
	 * <p>
	 * One of:
	 * <ul>
	 *     <li>{@link SchematicaSchematic#MATERIAL_CLASSIC MATERIAL_CLASSIC}</li>
	 *     <li>{@link SchematicaSchematic#MATERIAL_ALPHA MATERIAL_ALPHA}</li>
	 *     <li>{@link SchematicaSchematic#MATERIAL_STRUCTURE MATERIAL_STRUCTURE}</li>
	 * </ul>
	 *
	 * @return The schematic materials, if available
	 */
	public  String materials() {
		return materials;
	}

	public int posToIndex(int x, int y, int z) {
		return x + (z * width) + (y * width * length);
	}

	public  SchematicBlockPos indexToPos(int index) {
		final int x = index % width;
		final int z = (index / width) % length;
		final int y = index / (width * length);
		return new SchematicBlockPos(x, y, z);
	}

	@Override
	public String toString() {
		return "SchematicSchematica[" +
				"name=" + name() +
				", width=" + width +
				", height=" + height +
				", length=" + length +
				']';
	}
}
