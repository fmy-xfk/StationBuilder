package cn.myfrank.stationbuilder.schematic4j.schematic;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import cn.myfrank.stationbuilder.schematic4j.SchematicFormat;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.Pair;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlock;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockEntity;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockPos;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicEntity;

import static cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlock.AIR;

/**
 * A Litematica schematic. Read more about it at
 * <a href="https://github.com/maruohon/litematica/issues/53#issuecomment-520279558">https://github.com/maruohon/litematica/issues/53#issuecomment-520279558</a>.
 */
public class LitematicaSchematic implements Schematic {

	/**
	 * The schematic format version being used.
	 **/
	public int version = 1;

	/**
	 * Specifies the data version of Minecraft that was used to create the schematic.
	 * <p>
	 * This is to allow for block and entity data to be validated and auto-converted from older versions.
	 * This is dependent on the Minecraft version, e.g. Minecraft 1.12.2's data version is
	 * <a href="https://minecraft.gamepedia.com/1.12.2">1343</a>.
	 */
	public  Integer minecraftDataVersion;

	/**
	 * The optional metadata about the schematic.
	 */
	public  Metadata metadata = new Metadata();

	/**
	 * The regions that compose this schematic. They can be thought of as their own little schematics.
	 */
	public Region  [] regions = new Region[0];

	/**
	 * A Litematica schematic.
	 */
	public LitematicaSchematic() {
	}

	@Override
	public  SchematicFormat format() {
		return SchematicFormat.LITEMATICA;
	}

	@Override
	public int width() {
		return metadata.enclosingSize != null ? metadata.enclosingSize.x : 0;
	}

	@Override
	public int height() {
		return metadata.enclosingSize != null ? metadata.enclosingSize.y : 0;
	}

	@Override
	public int length() {
		return metadata.enclosingSize != null ? metadata.enclosingSize.z : 0;
	}

	@Override
	public  SchematicBlockPos offset() {
		return SchematicBlockPos.ZERO;
	}

	@Override
	public  SchematicBlock block(int x, int y, int z) {
		for (Region region : regions) {
			if ((region.position.x <= x && region.position.x + region.size.x > x)
					&& (region.position.y <= y && region.position.y + region.size.y > y)
					&& (region.position.z <= z && region.position.z + region.size.z > z)) {

				final int blockStateIndex = region.posToIndex(x, y, z);
				final int paletteIndex = region.blockStates[blockStateIndex];
				return region.blockStatePalette[paletteIndex];
			}
		}

		return AIR; // outside bounds
	}

	@Override
	public  Stream<Pair<SchematicBlockPos, SchematicBlock>> blocks() {
		return Arrays.stream(regions).flatMap(region -> IntStream.range(0, region.blockStates.length).mapToObj(idx -> {
			final SchematicBlockPos pos = region.indexToPos(idx);
			final int paletteIdx = region.blockStates[idx];
			final SchematicBlock block = region.blockStatePalette[paletteIdx];
			return new Pair<>(pos, block);
		}));
	}

	@Override
	public  Stream<SchematicBlockEntity> blockEntities() {
		return Arrays.stream(regions).flatMap(r -> Arrays.stream(r.blockEntities));
	}

	@Override
	public  Stream<SchematicEntity> entities() {
		return Arrays.stream(regions).flatMap(r -> Arrays.stream(r.entities));
	}

	/**
	 * The regions that compose this schematic. They can be thought of as their own little schematics.
	 *
	 * @return The regions
	 */
	public  Region[] regions() {
		return regions;
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
		return metadata.timeCreated;
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
		return minecraftDataVersion;
	}

	/**
	 * The optional metadata about the schematic.
	 *
	 * @return The schematic metadata
	 */
	public  Metadata metadata() {
		return metadata;
	}

	@Override
	public String toString() {
		return "SchematicLitematica[" +
				"name=" + name() +
				", version=" + version +
				", dataVersion=" + minecraftDataVersion +
				", metadata=" + metadata +
				", regions=" + Arrays.toString(regions) +
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
		 * The description of the schematic.
		 */
		public  String description;

		/**
		 * The name of the author of the schematic.
		 */
		public  String author;

		/**
		 * The date that this schematic was created on.
		 */
		public  LocalDateTime timeCreated;

		/**
		 * The date that this schematic was modified on.
		 */
		public  LocalDateTime timeModified;

		/**
		 * The size of the schematic including all regions.
		 */
		public  SchematicBlockPos enclosingSize;

		/**
		 * The number of regions inside this schematic.
		 */
		public  Integer regionCount;

		/**
		 * The total number of blocks from all the regions that compose this schematic. Does not include air blocks.
		 */
		public  Long totalBlocks;

		/**
		 * The total volume of blocks from all the regions that compose this schematic. This includes air blocks.
		 */
		public  Long totalVolume;

		/**
		 * Schematic thumbnail, if available.
		 */
		public int  [] previewImageData;

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
					", timeCreated=" + timeCreated +
					", timeModified=" + timeModified +
					']';
		}
	}

	/**
	 * A schematic region.
	 * <p>
	 * It can be thought of as its own little schematics, since it contains a unique size and block palette.
	 */
	public static class Region {
		/**
		 * The region name.
		 */
		public  String name;

		/**
		 * The region position in reference to the schematic origin at (0, 0, 0).
		 */
		public  SchematicBlockPos position = SchematicBlockPos.ZERO;

		/**
		 * The region size.
		 */
		public  SchematicBlockPos size = SchematicBlockPos.ZERO;

		/**
		 * The encoded (but unpacked) block states. Each index represents a block position and each value represents
		 * an index in the {@link Region#blockStatePalette}.
		 * <p>
		 * Each index is encoded as {@code x + (z * regionSize.x) (y * regionSize.x * regionSize.z)} and can be decoded as follows:
		 * <pre>
		 * int x = index % regionSize.x;
		 * int z = (index / regionSize.x) % regionSize.z;
		 * int y = index / (regionSize.x * regionSize.z);
		 * </pre>
		 *
		 * @see Region#indexToPos(int) to convert an index to a block position
		 * @see Region#posToIndex(int, int, int) to convert a block position to an index
		 */
		public int  [] blockStates = new int[0];

		/**
		 * The block state palette. Each entry in the array represents a unique block state in this schematic region.
		 * <p>
		 * The values in {@link Region#blockStates} are indices to this array.
		 */
		public SchematicBlock  [] blockStatePalette = new SchematicBlock[0];

		/**
		 * The block/tile entities in this schematic region.
		 */
		public SchematicBlockEntity  [] blockEntities = new SchematicBlockEntity[0];

		/**
		 * The entities in this schematic region.
		 */
		public SchematicEntity  [] entities = new SchematicEntity[0];

		/**
		 * The list of blocks with pending tick calculations.
		 */
		public PendingTicks  [] pendingBlockTicks = new PendingTicks[0];

		/**
		 * The list of fluids with pending tick calculations.
		 */
		public PendingTicks  [] pendingFluidTicks = new PendingTicks[0];

		public Region() {
		}

		public int posToIndex(int x, int y, int z) {
			return x + (z * size.x) + (y * size.x * size.z);
		}

		public  SchematicBlockPos indexToPos(int index) {
			final int x = index % size.x;
			final int z = (index / size.x) % size.z;
			final int y = index / (size.x * size.z);
			return new SchematicBlockPos(x, y, z);
		}

		/**
		 * The region name.
		 *
		 * @return The region name
		 */
		public  String name() {
			return name;
		}

		/**
		 * The region position in reference to the schematic origin at (0, 0, 0).
		 *
		 * @return The region position
		 */
		public  SchematicBlockPos position() {
			return position;
		}

		/**
		 * The region size.
		 *
		 * @return The region size
		 */
		public  SchematicBlockPos size() {
			return size;
		}

		/**
		 * The encoded (but unpacked) block states. Each index represents a block position and each value represents
		 * an index in the {@link Region#blockStatePalette}.
		 * <p>
		 * Each index is encoded as {@code x + (z * regionSize.x) (y * regionSize.x * regionSize.z)} and can be decoded as follows:
		 * <pre>
		 * int x = index % regionSize.x;
		 * int z = (index / regionSize.x) % regionSize.z;
		 * int y = index / (regionSize.x * regionSize.z);
		 * </pre>
		 *
		 * @return The encoded (but unpacked) block states
		 * @see Region#indexToPos(int) to convert an index to a block position
		 * @see Region#posToIndex(int, int, int) to convert a block position to an index
		 */
		public int  [] blockStates() {
			return blockStates;
		}

		/**
		 * The block state palette. Each entry in the array represents a unique block state in this schematic region.
		 * <p>
		 * The values in {@link Region#blockStates} are indices to this array.
		 *
		 * @return The block state palette
		 */
		public SchematicBlock  [] blockStatePalette() {
			return blockStatePalette;
		}

		/**
		 * The block/tile entities in this schematic region.
		 *
		 * @return The block entities in this region
		 */
		public SchematicBlockEntity  [] blockEntities() {
			return blockEntities;
		}

		/**
		 * The entities in this schematic region.
		 *
		 * @return The entities in this region
		 */
		public SchematicEntity  [] entities() {
			return entities;
		}

		/**
		 * The list of blocks with pending tick calculations.
		 *
		 * @return The blocks pending ticks
		 */
		public PendingTicks  [] pendingBlockTicks() {
			return pendingBlockTicks;
		}

		/**
		 * The list of fluids with pending tick calculations.
		 *
		 * @return The fluids pending ticks
		 */
		public PendingTicks  [] pendingFluidTicks() {
			return pendingFluidTicks;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Region region = (Region) o;

			if (!Objects.equals(name, region.name)) return false;
			if (!position.equals(region.position)) return false;
			return size.equals(region.size);
		}

		@Override
		public int hashCode() {
			int result = name != null ? name.hashCode() : 0;
			result = 31 * result + position.hashCode();
			result = 31 * result + size.hashCode();
			return result;
		}

		@Override
		public String toString() {
			return "Region[" +
					"name='" + name + '\'' +
					", position=" + position +
					", size=" + size +
					']';
		}
	}

	/**
	 * Represents a block or fluid pending tick calculations.
	 */
	public static class PendingTicks {
		/**
		 * The pending tick priority.
		 */
		public  Integer priority;

		/**
		 * The sub-tick.
		 */
		public  Long subTick;

		/**
		 * The time.
		 */
		public  Integer time;

		/**
		 * The X coordinate inside the region it is found.
		 */
		public  Integer x;

		/**
		 * The Y coordinate inside the region it is found.
		 */
		public  Integer y;

		/**
		 * The Z coordinate inside the region it is found.
		 */
		public  Integer z;
	}
}
