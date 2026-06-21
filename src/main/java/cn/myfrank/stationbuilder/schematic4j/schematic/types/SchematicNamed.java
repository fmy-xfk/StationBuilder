package cn.myfrank.stationbuilder.schematic4j.schematic.types;

import java.util.Comparator;

import org.jetbrains.annotations.NotNull;

public abstract class SchematicNamed implements Comparable<SchematicNamed> {

	/**
	 * The resource name, usually as a resource identifier like "minecraft:dirt".
	 */
	public @NotNull String name;

	public SchematicNamed(@NotNull String name) {
		this.name = name;
	}

	/**
	 * The resource name.
	 *
	 * @return The resource name
	 */
	public @NotNull String name() {
		return name;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		SchematicNamed that = (SchematicNamed) o;

		return name.equals(that.name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + '[' + name + ']';
	}

	@Override
	public int compareTo(@NotNull SchematicNamed o) {
		return Comparator.nullsLast(Comparator.<SchematicNamed, String>comparing(obj -> obj.name)).compare(this, o);
	}
}
