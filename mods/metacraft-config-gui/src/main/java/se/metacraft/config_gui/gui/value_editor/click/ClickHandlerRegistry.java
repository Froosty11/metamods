package se.metacraft.config_gui.gui.value_editor.click;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import se.metacraft.config_gui.ConfigGUI;
import se.metacraft.config_gui.gui.value_editor.click.handlers.*;

public class ClickHandlerRegistry {

	public static final Registry<ClickHandlerType<?>> REGISTRY = FabricRegistryBuilder.<ClickHandlerType<?>>create(
		ResourceKey.createRegistryKey(ConfigGUI.getID("click_handler"))
	).buildAndRegister();

	public static final ClickHandlerType<SubMenu> SUBMENU = register("submenu", SubMenu.CODEC);
	public static final ClickHandlerType<Back> BACK = register("back", Back.INSTANCE);
	public static final ClickHandlerType<Select> SELECT = register("select", Select.CODEC);
	public static final ClickHandlerType<Search> SEARCH = register("search", Search.CODEC);
	public static final ClickHandlerType<RemoveIndex> REMOVE_INDEX = register("remove_index", RemoveIndex.CODEC);
	public static final ClickHandlerType<MoveEntry> MOVE_INDEX = register("move_index", MoveEntry.CODEC);
	public static final ClickHandlerType<MultiAction> MULTI_ACTION = register("multi_action", MultiAction.CODEC);
	public static final ClickHandlerType<Resubmit> RESUBMIT = register("resubmit", Resubmit.INSTANCE);
	public static final ClickHandlerType<RemoveObject> REMOVE_OBJECT = register("remove_object", RemoveObject.CODEC);
	public static final ClickHandlerType<Confirmation> CONFIRMATION = register("confirmation", Confirmation.CODEC);

	public static void init() {

	}

	private static <T extends ClickHandler> ClickHandlerType<T> register(String id, MapCodec<T> codec) {
		return Registry.register(REGISTRY, id, new ClickHandlerType<>(codec));
	}

	private static <T extends ClickHandler> ClickHandlerType<T> register(String id, T instance) {
		return Registry.register(REGISTRY, id, new ClickHandlerType<>(instance));
	}

}
