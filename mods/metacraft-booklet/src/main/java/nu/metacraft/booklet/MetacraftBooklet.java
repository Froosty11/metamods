package nu.metacraft.booklet;

import eu.pb4.booklet.impl.BookletImplUtil;
import eu.pb4.booklet.impl.BookletOpenState;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The server's guidebook. <a href="https://github.com/Patbox/booklet">Booklet</a> draws the book
 * (a dialog on a vanilla client) and lists every page in its {@code booklet:main_page} category on
 * its index, "The Encyclopedia"; this mod supplies the pages, one chapter per mod the server runs.
 *
 * <p>Each chapter is a built-in datapack under {@code resourcepacks/<name>/} of this jar and is
 * registered only when its mod is loaded ({@link #HOOKS}), so a server without ovvar has no ovvar
 * chapter and none of its item icons to resolve. No chapter's mod is a dependency.
 *
 * <p>Besides pages: {@code polydecorations_s6} switches off every PolyDecorations recipe but those
 * of the few things METAcraft keeps, and the resource pack turns the warning button vanilla puts on every server
 * dialog into a question mark.
 *
 * <p>{@code /guide} (anyone) opens the index.
 */
public final class MetacraftBooklet implements ModInitializer {
	public static final String MOD_ID = "metacraft-booklet";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Booklet's own index page: every page in {@code booklet:main_page}. */
	public static final Identifier INDEX = Identifier.fromNamespaceAndPath("booklet", "index");

	/**
	 * A built-in datapack switched on when {@code modId} is loaded. {@code optional} packs are
	 * enabled by default but can be turned off with {@code /datapack disable}; chapters cannot.
	 */
	public record Hook(String modId, String pack, boolean optional) {
		public Identifier id() {
			return Identifier.fromNamespaceAndPath(MOD_ID, pack);
		}
	}

	/**
	 * Every hook, in the order the chapters are listed (each chapter's own {@code order=} decides
	 * that on the index). A new mod's chapter is a folder under {@code resourcepacks/} and a line here.
	 */
	public static final List<Hook> HOOKS = List.of(
			new Hook("ovvar", "ovvar", false),
			new Hook("polydecorations", "decorating", false),
			new Hook("polydecorations", "polydecorations_s6", true),
			new Hook("metacraft-qol", "qol", false));

	@Override
	public void onInitialize() {
		ModContainer self = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow();
		for (Hook hook : HOOKS) {
			if (!FabricLoader.getInstance().isModLoaded(hook.modId())) continue;
			boolean ok = ResourceLoader.registerBuiltinPack(hook.id(), self,
					Component.literal("METAcraft Booklet: " + hook.pack()),
					hook.optional() ? PackActivationType.DEFAULT_ENABLED : PackActivationType.ALWAYS_ENABLED);
			LOGGER.info("[{}] {} is here: {} {}", MOD_ID, hook.modId(), hook.pack(), ok ? "on" : "could not be registered");
		}

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("guide").executes(ctx -> {
					boolean opened = BookletImplUtil.openPage(ctx.getSource().getPlayerOrException(), INDEX, BookletOpenState.DEFAULT);
					return opened ? 1 : 0;
				})));

		Beside.init();

		// The question-mark sprites under assets/minecraft, and Booklet's own assets need the pack too.
		PolymerResourcePackUtils.addModAssets(MOD_ID);
		PolymerResourcePackUtils.markAsRequired();
	}
}
