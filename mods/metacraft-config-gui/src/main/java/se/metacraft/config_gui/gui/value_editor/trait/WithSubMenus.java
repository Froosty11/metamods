package se.metacraft.config_gui.gui.value_editor.trait;

import net.minecraft.server.level.ServerPlayer;
import se.metacraft.config_gui.CodecDialog;
import se.metacraft.config_gui.gui.value_editor.click.handlers.SubMenu;

public interface WithSubMenus {

	CodecDialog.Type getType(SubMenu.SubMenuKey key, ServerPlayer player);
	Object getObject(SubMenu.SubMenuKey key, ServerPlayer player);

}
