package cn.myfrank.stationbuilder.schematic4j.parser;

import cn.myfrank.stationbuilder.schematic4j.exception.ParsingException;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.CompoundTag;
import cn.myfrank.stationbuilder.schematic4j.schematic.Schematic;

/**
 * A schematic parser.
 */
public interface Parser {

	/**
	 * Parses the input NBT into a schematic.
	 *
	 * @param nbt The input NBT.
	 * @return The parsed schematic.
	 * @throws ParsingException In case there is a parsing error
	 */
	
	Schematic parse( CompoundTag nbt) throws ParsingException;
}
