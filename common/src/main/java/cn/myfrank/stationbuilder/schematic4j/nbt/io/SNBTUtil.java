/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.io;

import java.io.IOException;

import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

public class SNBTUtil {

	public static String toSNBT(Tag<?> tag) throws IOException {
		return new SNBTSerializer().toString(tag);
	}

	public static Tag<?> fromSNBT(String string) throws IOException {
		return new SNBTDeserializer().fromString(string);
	}
}
