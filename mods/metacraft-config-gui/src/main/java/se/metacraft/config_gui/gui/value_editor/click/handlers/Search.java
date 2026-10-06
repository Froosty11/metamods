package se.metacraft.config_gui.gui.value_editor.click.handlers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.DialogGUI;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerRegistry;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerType;
import se.metacraft.config_gui.gui.value_editor.trait.WithSearchBar;

public record Search(String searchInputField, String searchQuery) implements ClickHandler {

	public static final MapCodec<Search> CODEC = Codec.STRING.dispatchMap(
		"search$input_field", Search::searchInputField, field -> Codec.STRING.fieldOf(field).xmap(
			query -> new Search(field, query),
			Search::searchQuery
		)
	);

	@Override
	public DialogGUI onClick(ServerPlayer player, ClickHandlerGUI gui) {
		if (gui instanceof WithSearchBar searchBar) {
			return searchBar.onSearch(player, searchInputField, searchQuery);
		}
		return gui;
	}

	@Override
	public ClickHandlerType<Search> type() {
		return ClickHandlerRegistry.SEARCH;
	}
}
