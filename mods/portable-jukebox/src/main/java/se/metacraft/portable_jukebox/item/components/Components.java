package se.metacraft.portable_jukebox.item.components;

import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.world.level.storage.loot.ContainerComponentManipulator;
import se.metacraft.portable_jukebox.PortableJukebox;

import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public class Components {

	//Careful, this is used by a datafixer!
	public static final DataComponentType<PortableJukeboxInvData> PORTABLE_JUKEBOX = register(
			"portable_jukebox", component -> component.persistent(PortableJukeboxInvData.CODEC)
	);

	public static final DataComponentType<PortableJukeboxEntityEntry> PORTABLE_JUKEBOX_ENTITY = register(
			"portable_jukebox_entity", component -> component.persistent(PortableJukeboxEntityEntry.CODEC)
	);

	public static final DataComponentType<PortableJukeboxConfiguration> PORTABLE_JUKEBOX_CONFIGURATION = register(
			"portable_jukebox_configuration", component -> component.persistent(PortableJukeboxConfiguration.CODEC)
	);



	public static void init() {
		Manipulators.init();
	}

	private static <T> DataComponentType<T> register(String id, UnaryOperator<DataComponentType.Builder<T>> componentBuilder) {
		var component = componentBuilder.apply(DataComponentType.builder()).build();
		PolymerComponent.registerDataComponent(component);
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, PortableJukebox.getID(id), component);
	}

	public static class Manipulators {

		public static final ContainerComponentManipulator<PortableJukeboxInvData> PORTABLE_JUKEBOX = new ContainerComponentManipulator<>(Components.PORTABLE_JUKEBOX, PortableJukeboxInvData.EMPTY);

		public static void init() {

		}
	}
}
