package nu.metacraft.lib.config.container.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.Unit;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.JsonHelper;
import nu.metacraft.lib.config.extensions.Modifiable;
import nu.metacraft.lib.config.container.ReloadCause;
import nu.metacraft.lib.config.container.ReloadFunction;
import nu.metacraft.lib.config.extensions.LoadAware;
import nu.metacraft.lib.config.extensions.ReloadAware;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class BasicConfigContainer<T> implements ConfigContainer<T> {

	private static final WeakHashMap<BasicConfigContainer<?>, Unit> containers = new WeakHashMap<>();

	static {
		ServerLifecycleEvents.START_DATA_PACK_RELOAD.register((server, manager) -> {
			containers.keySet().forEach(container -> {
				if (container.reloadsBeforeServer) {
					container.reload(ReloadCause.BEFORE_SERVER_RELOAD);
				}
			});
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
			if (success) {
				containers.keySet().forEach(container -> {
					if (container.reloadsAfterServer) {
						container.reload(ReloadCause.AFTER_SERVER_RELOAD);
					}
				});
			}
		});
	}

	protected final Codec<T> codec;
	protected final Path configPath;
	protected final Supplier<T> defaultConfigInitializer;
	protected final boolean reloadsBeforeServer;
	protected final boolean reloadsAfterServer;
	protected final ReloadFunction<T> reloader;
	protected Consumer<ReloadCause> onReload = cause -> {};

	protected T config;

	protected final List<Predicate<T>> modifiers = new ArrayList<>();

	protected @Nullable String loadError;
	protected final List<BiConsumer<T, T>> changeListeners = new ArrayList<>();

	public BasicConfigContainer(
			Codec<T> codec, Path configPath, Supplier<T> defaultConfigInitializer,
			boolean reloadsBeforeServer, boolean reloadsAfterServer,
			ReloadFunction<T> reloader
	) {
		this.codec = codec;
		this.configPath = configPath;
		this.defaultConfigInitializer = defaultConfigInitializer;
		this.reloadsBeforeServer = reloadsBeforeServer;
		this.reloadsAfterServer = reloadsAfterServer;
		this.reloader = reloader;
		containers.put(this, Unit.INSTANCE);
	}

	protected T initDefaultConfig() {
		return defaultConfigInitializer.get();
	}

	protected Optional<T> loadFromFile() {
		Optional<DataResult<T>> read = readFile();
		if (read.isEmpty()) {
			loadError = null;
			return Optional.empty();
		}
		DataResult<T> result = read.get();
		if (result.error().isPresent()) {
			loadError = result.error().get().message();
			METAcraftLib.LOGGER.error("Unable to load {}, keeping the settings in use: {}", configPath, loadError);
			return Optional.empty();
		}
		loadError = null;
		return result.result();
	}

	protected Optional<DataResult<T>> readFile() {
		return JsonHelper.read(configPath, codec, ops -> ops);
	}

	@Override
	public T get() {
		if (config == null) {
			try {
				config = loadFromFile().orElse(null);
				if (config != null) {
					triggerLoad(Optional.empty());
				} else {
					// No file: write the defaults. A file that does not load is left for a person to fix.
					config = initDefaultConfig();
					if (loadError == null) save();
				}
			} catch (Throwable t) {
				METAcraftLib.LOGGER.error("Unable to parse config: ", t);
			}
		} else if (config instanceof Modifiable modifiable) {
			if (!modifiers.isEmpty()) {
				boolean modified = modifiers.stream().map(
						action -> action.test(config)
				).reduce((lhs, rhs) -> lhs || rhs).orElse(false);
				if (modified) {
					modifiable.setModified(true);
				}
				modifiers.clear();
			}
			if (modifiable.isModified()) {
				save();
				modifiable.setModified(false);
			}
		}
		return config;
	}

	private void triggerLoad(Optional<ReloadCause> cause) {
		if (config instanceof LoadAware aware) {
			aware.afterLoad(cause);
		}
	}

	@Override
	public void reload(ReloadCause cause) {
		if (config instanceof ReloadAware r) {
			r.beforeReload(cause);
		}
		T old = config;
		config = reloader.reload(config, this::loadFromFile, cause);
		triggerLoad(Optional.of(cause));
		onReload.accept(cause);
		notifyChanged(old, config);
	}

	@Deprecated
	@Override
	public void modify(Predicate<T> modifier) {
		if (this.config != null) {
			if (this.config instanceof Modifiable modifiable) {
				if (modifier.test(config)) {
					modifiable.setModified(true);
				}
			} else {
				throw new IllegalStateException("Config is not modifiable!");
			}
		} else {
			modifiers.add(modifier);
		}
	}

	@Override
	public void save() {
		if (config == null) return;
		JsonHelper.save(configPath, codec, config);
	}

	@Override
	public void replace(T newConfig) {
		T old = config;
		this.config = newConfig;
		if (old != newConfig) {
			save();
			loadError = null;
		}
		notifyChanged(old, newConfig);
	}

	@Override
	public void addChangeListener(BiConsumer<T, T> listener) {
		changeListeners.add(listener);
	}

	@Override
	public Optional<String> loadError() {
		return Optional.ofNullable(loadError);
	}

	private void notifyChanged(@Nullable T old, @Nullable T current) {
		if (old == null || current == null || old.equals(current)) return;
		for (BiConsumer<T, T> listener : changeListeners) listener.accept(old, current);
	}

	@Override
	public void addReloadHandler(Consumer<ReloadCause> handler) {
		onReload = onReload.andThen(handler);
	}

	public static class WithLookup<T> extends BasicConfigContainer<T> {

		protected final Supplier<HolderLookup.Provider> lookupSupplier;
		protected final Function<HolderLookup.Provider, T> defaultConfigInitializer;

		public WithLookup(
				Codec<T> codec, Path configPath, Function<HolderLookup.Provider, T> defaultConfigInitializer,
				boolean reloadsBeforeServer, boolean reloadsAfterServer, ReloadFunction<T> reloader,
				Supplier<HolderLookup.Provider> lookupSupplier
		) {
			super(codec, configPath, null, reloadsBeforeServer, reloadsAfterServer, reloader);
			this.lookupSupplier = lookupSupplier;
			this.defaultConfigInitializer = defaultConfigInitializer;
		}

		@Override
		protected Optional<DataResult<T>> readFile() {
			return JsonHelper.read(configPath, codec, lookupSupplier.get()::createSerializationContext);
		}

		@Override
		public void save() {
			if (config == null) return;
			JsonHelper.save(configPath, codec, config, lookupSupplier.get());
		}

		@Override
		protected T initDefaultConfig() {
			return defaultConfigInitializer.apply(lookupSupplier.get());
		}

	}

}
