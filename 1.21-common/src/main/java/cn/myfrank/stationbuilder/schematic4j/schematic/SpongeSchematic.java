package cn.myfrank.stationbuilder.schematic4j.schematic;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import cn.myfrank.stationbuilder.schematic4j.SchematicFormat;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.Pair;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBiome;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlock;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockEntity;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockPos;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicEntity;

import static cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlock.AIR;

/**
 * A Sponge schematic. Read more about it at <a href="https://github.com/SpongePowered/Schematic-Specification">https://github.com/SpongePowered/Schematic-Specification</a>.
 */
public class SpongeSchematic implements Schematic {

	/**
	 * The Sponge Schematic format version being used.
	 **/
	public int version = 1;

	/**
	 * Specifies the data version of Minecraft that was used to create the schematic.
	 * <p>
	 * This is to allow for block and entity data to be validated and auto-converted from older versions.
	 * This is dependent on the Minecraft version, e.g. Minecraft 1.12.2's data version is
	 * <a href="https://minecraft.gamepedia.com/1.12.2">1343</a>.
	 */
	public  Integer dataVersion;

	/**
	 * The optional metadata about the schematic.
	 */
	public  Metadata metadata = new Metadata();

	/**
	 * The width (the size of the area in the X-axis) of the schematic.
	 */
	public int width;

	/**
	 * The height (the size of the area in the Y-axis) of the schematic.
	 */
	public int height;

	/**
	 * The length (the size of the area in the Z-axis) of the schematic.
	 */
	public int length;

	/**
	 * The relative offset of the schematic from the paster. When pasting, if there is a reasonable location to use as
	 * a base position, implementations SHOULD offset the location of the paste by this vector. The default value if
	 * not provided is [0, 0, 0]. Example: If a player is pasting from 1, 2, 3, and the offset is 4, 5, 6, then the
	 * first block should be placed at 5, 7, 9
	 */
	public  SchematicBlockPos offset = SchematicBlockPos.ZERO;

	/**
	 * The unpacked block data indices.
	 */
	public int  [] blocks = new int[0];

	/**
	 * The unpacked block data indices.
	 */
	public SchematicBlock  [] blockPalette = new SchematicBlock[0];

	/**
	 * The block/tile entity data.
	 */
	public SchematicBlockEntity  [] blockEntities = new SchematicBlockEntity[0];

	/**
	 * The entity data.
	 */
	public SchematicEntity  [] entities = new SchematicEntity[0];

	/**
	 * The unpacked biome data.
	 */
	public int  [] biomes = new int[0];

	/**
	 * The biome palette data.
	 */
	public  SchematicBiome[] biomePalette = new SchematicBiome[0];

	public SpongeSchematic() {
	}

	@Override
	public  SchematicFormat format() {
		switch (version) {
			case 1:
				return SchematicFormat.SPONGE_V1;
			case 2:
				return SchematicFormat.SPONGE_V2;
			default:
			case 3:
				return SchematicFormat.SPONGE_V3;
		}
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
		return offset;
	}

	@Override
	public  SchematicBlock block(int x, int y, int z) {
		final int blockIndex = posToIndex(x, y, z);
		if (blockIndex < 0 || blockIndex >= blocks.length) {
			return AIR; // outside bounds
		}

		final int paletteIndex = blocks[blockIndex];
		return blockPalette[paletteIndex];
	}

	/**
	 * The raw block data.
	 *
	 * @return The raw block data
	 */
	public int  [] blockData() {
		return blocks;
	}

	/**
	 * The raw block palette.
	 *
	 * @return The raw block palette
	 */
	public SchematicBlock  [] blockPalette() {
		return blockPalette;
	}

	@Override
	public  Stream<SchematicBlockEntity> blockEntities() {
		return Arrays.stream(blockEntities);
	}

	/**
	 * The raw block entity data.
	 *
	 * @return The raw block data
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
	 * @return The raw block data
	 */
	public SchematicEntity  [] entityData() {
		return entities;
	}

	@Override
	public  SchematicBiome biome(int x, int y, int z) {
		// 3D biome data is only available starting in v3. Flatten the y coordinate for older versions
		if (version <= 2) {
			y = 0;
		}

		final int biomeIndex = posToIndex(x, y, z);
		if (biomeIndex < 0 || biomeIndex >= biomes.length) {
			return SchematicBiome.AIR; // outside bounds
		}

		final int paletteIndex = biomes[biomeIndex];
		return biomePalette[paletteIndex];
	}

	/**
	 * Iterate over the list of biomes. Follows a zigzag pattern: first visits X, then Z, then Y.
	 *
	 * @return an iterator
	 */
	@Override
	public  Stream<Pair<SchematicBlockPos, SchematicBiome>> biomes() {
		return IntStream.range(0, biomes.length).mapToObj(index -> {
			SchematicBlockPos pos = indexToPos(index);
			SchematicBiome biome = biomePalette[biomes[index]];
			return new Pair<>(pos, biome);
		});
	}

	/**
	 * The raw biome data.
	 *
	 * @return The raw biome data
	 */
	public int  [] biomeData() {
		return biomes;
	}

	/**
	 * The raw biome palette.
	 *
	 * @return The raw biome palette
	 */
	public SchematicBiome  [] biomePalette() {
		return biomePalette;
	}

	@Override
	public  String name() {
		return metadata.name;
	}

	@Override
	public  String author() {
		return metadata.author;
	}

	@Override
	public  LocalDateTime date() {
		return metadata.date;
	}

	/**
	 * Specifies the data version of Minecraft that was used to create the schematic.
	 * <p>
	 * This is to allow for block and entity data to be validated and auto-converted from older versions.
	 * This is dependent on the Minecraft version, e.g. Minecraft 1.12.2's data version is
	 * <a href="https://minecraft.gamepedia.com/1.12.2">1343</a>.
	 *
	 * @return The Minecraft data version
	 */
	public  Integer dataVersion() {
		return dataVersion;
	}

	/**
	 * @deprecated Use {@link SpongeSchematic#dataVersion()} instead
	 */
	@Deprecated
	public  Integer getDataVersion() {
		return dataVersion();
	}

	/**
	 * The optional metadata about the schematic.
	 *
	 * @return The schematic metadata
	 */
	public  Metadata metadata() {
		return metadata;
	}

	/**
	 * @deprecated Use {@link SpongeSchematic#metadata()} instead
	 */
	@Deprecated
	public  Metadata getMetadata() {
		return metadata();
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
		return "SchematicSponge[" +
				"name=" + name() +
				", version=" + version +
				", width=" + width +
				", height=" + height +
				", length=" + length +
				']';
	}

	/**
	 * The schematic metadata.
	 */
	public static class Metadata {
		/**
		 * The name of the schematic.
		 */
		public  String name;

		/**
		 * The name of the author of the schematic.
		 */
		public  String author;

		/**
		 * The date that this schematic was created on.
		 */
		public  LocalDateTime date;

		/**
		 * An array of mod IDs.
		 */
		public String  [] requiredMods = new String[0];

		/**
		 * Extra metadata not represented in the specification.
		 */
		public  Map<String, Object> extra = new TreeMap<>();

		public Metadata() {
		}

		@Override
		public String toString() {
			return "Metadata[" +
					"name='" + name + '\'' +
					", author='" + author + '\'' +
					", date=" + date +
					", requiredMods=" + Arrays.toString(requiredMods) +
					", extra=" + extra +
					']';
		}
	}
}
