package nu.metacraft.lib.config.container;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import nu.metacraft.lib.config.ObjectStorage;
import nu.metacraft.lib.config.container.impl.BasicConfigContainer;
import nu.metacraft.lib.config.describe.*;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.*;

/**
 * A config container contains the config instance and takes care of saving/loading the config file.
 * @param <T> The type of the config instance.
 */
public interface ConfigContainer<T> extends ConfigContainerBase<T>, ConfigContainerWithSingleton<T> {

	/**
	 * Modifies the config
	 * @param modifier A function that modifies the config. If it returns true, the change will be saved, otherwise it will not.
	 * @throws IllegalStateException If config is not modifiable.
	 * @deprecated Needs a mutable config and applies later; use {@link #update}.
	 */
	@Deprecated
	void modify(Predicate<T> modifier);

	/** Replaces the config with {@code newConfig}, saves, and notifies listeners if it changed. */
	void replace(T newConfig);

	/** Replaces the config with {@code change} applied to it, saves, and notifies listeners if it changed. */
	default void update(UnaryOperator<T> change) {
		replace(change.apply(get()));
	}

	/** Called with the old and new value after an update, or a reload that changed the value. */
	void addChangeListener(BiConsumer<T, T> listener);

	class Builder<T> {

		protected final MapCodec<T> codec;
		protected final Supplier<T> defaultConfigInitializer;
		protected Function<HolderLookup.Provider, T> defaultConfigInitializerWithLookup;
		protected boolean reloadsBeforeServer = false;
		protected boolean reloadsAfterServer = false;
		protected ReloadFunction<T> reloader = ReloadFunction.getDefault();
		protected @Nullable Class<? extends Record> described;

		public static <T> Builder<T> create(MapCodec<T> codec, Supplier<T> defaultConfigInitializer) {
			return new Builder<>(codec, defaultConfigInitializer);
		}

		protected Builder(MapCodec<T> codec, Supplier<T> defaultConfigInitializer) {
			this.codec = codec;
			this.defaultConfigInitializer = defaultConfigInitializer;
		}

		/**
		 * Always attempt to reload the config whenever the server reload (whenever /reload is executed),
		 * will reload the config even if the datapack reload fails.
		 * @return The builder.
		 */
		public Builder<T> reloadBeforeServer() {
			this.reloadsBeforeServer = true;
			return this;
		}

		/**
		 * Reload the config after the server reload is completed.
		 * Will only reload if the server reload was successful.
		 * @return The builder.
		 */
		public Builder<T> reloadAfterServer() {
			this.reloadsAfterServer = true;
			return this;
		}

		/**
		 * Set a custom reloading function.
		 * Allows you to reload the config in multiple steps.
		 * For example, reloading commands before the server reload, then reloading items after.
		 * @param reloader The new reloader function. Takes the old config, a function that might create a new config as well as the reload cause as arguments.
		 * @return The builder.
		 */
		public Builder<T> setReloader(ReloadFunction<T> reloader) {
			this.reloader = reloader;
			return this;
		}

		public Builder<T> registryAvailableConfigInitializer(Function<HolderLookup.Provider, T> defaultConfigInitializerWithLookup) {
			this.defaultConfigInitializerWithLookup = defaultConfigInitializerWithLookup;
			return this;
		}

		/**
		 * Lists this config in the config screen, described by the annotations on {@code type}, which
		 * must be the config's own record. Its id is the file name without {@code .json}.
		 */
		public Builder<T> describedBy(Class<? extends Record> type) {
			this.described = type;
			return this;
		}

		/**
		 * Builds a normal config container.
		 * @return The config container.
		 */
		public ConfigContainer<T> build(Path configPath) {
			ConfigContainer<T> container = new BasicConfigContainer<>(codec.codec(), configPath, defaultConfigInitializer, reloadsBeforeServer, reloadsAfterServer, reloader);
			if (described != null) register(configPath, container, t -> t, (whole, part) -> part);
			return container;
		}

		/**
		 * Builds a normal config container with registry access.
		 * @return The config container.
		 */
		public ConfigContainer<T> build(Path configPath, Supplier<HolderLookup.Provider> lookupSupplier) {
			ConfigContainer<T> container = new BasicConfigContainer.WithLookup<>(
					codec.codec(), configPath,
					defaultConfigInitializerWithLookup != null ? defaultConfigInitializerWithLookup : l -> defaultConfigInitializer.get(),
					reloadsBeforeServer, reloadsAfterServer, reloader, lookupSupplier
			);
			if (described != null) register(configPath, container, t -> t, (whole, part) -> part);
			return container;
		}

		/**
		 * Builds a normal config container with registry access.
		 * @return The config container.
		 */
		public ConfigContainer<T> build(Path configPath, HolderLookup.Provider lookup) {
			return build(configPath, () -> lookup);
		}

		/**
		 * Registers a described config backed by part of the container's value.
		 * @param configPath The file the container writes to; its name without {@code .json} is the id.
		 * @param container The container holding the whole value {@code C}.
		 * @param part Reads this described record {@code T} out of the whole value.
		 * @param withPart Rebuilds the whole value with a new described record.
		 */
		@SuppressWarnings({"unchecked", "rawtypes"})
		protected <C> void register(Path configPath, ConfigContainer<C> container, Function<C, T> part, BiFunction<C, T, C> withPart) {
			ConfigSpec spec = ConfigSpec.of((Class) described);
			if (!(codec instanceof DescribedCodec)) spec.checkWrittenBy(codec.codec());
			String id = configPath.getFileName().toString().replaceFirst("\\.json$", "");
			ConfigRegistry.register(new DescribedConfig(id, spec, codec.codec(), container, part, withPart));
		}

		/**
		 * Converts this builder into a registry aware builder.
		 * Make sure to run all non-registry-dependent functions first.
		 * @param registryAwareCodec The codec to use for the registry-aware part.
		 * @return The registry aware builder.
		 * @param <S> The type of the registry aware part.
		 */
		public <S> RegistryAwareBuilder<S> makeRegistryAware(MapCodec<S> registryAwareCodec) {
			return new RegistryAwareBuilder<>(registryAwareCodec);
		}

		public class RegistryAwareBuilder<S> {

			private final MapCodec<S> serverAwareCodec;
			private @Nullable ServerAware.Parser<ConfigContainer<ServerAware.ConfigPair<T, S>>, S> parser;
			private boolean refreshOnReload = false;
			private Supplier<ObjectStorage<S>> defaultRegistryAwareInitializer;
			private ReloadFunction<S> cacheReloader = ReloadFunction.getDefault();

			public RegistryAwareBuilder(MapCodec<S> codec) {
				this.serverAwareCodec = codec;
			}

			/** As {@link Builder#describedBy}; describes the static part of the config. */
			public RegistryAwareBuilder<S> describedBy(Class<? extends Record> type) {
				Builder.this.described = type;
				return this;
			}

			/**
			 * Sets the default initializer manually.
			 * Please run {@link RegistryAwareBuilder#refreshOnReload} first if desired.
			 * @param initializer The initializer.
			 * @return The builder.
			 */
			public RegistryAwareBuilder<S> setInitializerManually(Supplier<ObjectStorage<S>> initializer) {
				this.defaultRegistryAwareInitializer = initializer;
				return this;
			}

			/**
			 * Sets the default initializer without registries.
			 * Please run {@link RegistryAwareBuilder#refreshOnReload} first if desired.
			 * @param initializer The initializer.
			 * @return The builder.
			 */
			public RegistryAwareBuilder<S> setInitializer(Supplier<S> initializer) {
				this.defaultRegistryAwareInitializer = () -> ObjectStorage.fromValue(
						serverAwareCodec.codec(), initializer.get(), refreshOnReload
				);
				return this;
			}

			/**
			 * Sets the default initializer using builtin registries.
			 * Please run {@link RegistryAwareBuilder#refreshOnReload} first if desired.
			 * @param initializer The initializer.
			 * @return The builder.
			 */
			public RegistryAwareBuilder<S> setInitializer(Function<HolderLookup.Provider, S> initializer) {
				this.defaultRegistryAwareInitializer = () -> ObjectStorage.fromValueWithDefaultOps(
						serverAwareCodec.codec(), initializer, refreshOnReload
				);
				return this;
			}

			/**
			 * Overrides the default parser, in case you want access to more than just registries for example.
			 * @param parser The parser to use.
			 * @return The builder.
			 */
			public RegistryAwareBuilder<S> setParser(ServerAware.Parser<ConfigContainer<ServerAware.ConfigPair<T, S>>, S> parser) {
				this.parser = parser;
				return this;
			}

			/**
			 * Changes the reload function.
			 * @param cacheReloader The new reload function to use.
			 * @return The builder.
			 */
			public RegistryAwareBuilder<S> setReloader(ReloadFunction<S> cacheReloader) {
				this.cacheReloader = cacheReloader;
				return this;
			}

			/**
			 * Notify that this config should always refresh the ObjectStorage on reload.
			 * If using the default parser, it will also gain access to reloadable registries (such as predicates for example).
			 * @return The builder.
			 */
			public RegistryAwareBuilder<S> refreshOnReload() {
				if (defaultRegistryAwareInitializer != null) throw new IllegalStateException("Please run refreshOnReload before setInitializer!");
				refreshOnReload = true;
				return this;
			}

			/** Parses the registry-aware values with the server's registries (reloadable ones when refreshing on reload). */
			private ServerAware.Parser<ConfigContainer<ServerAware.ConfigPair<T, S>>, S> defaultParser(boolean reloadable) {
				return (config, server) -> config.get().serverAwareValues().parse(
						reloadable ? server.reloadableRegistries().lookup() : server.registryAccess());
			}

			/**
			 * Builds a normal config container.
			 * @return The config container.
			 */
			public ServerAware<ConfigContainer<ServerAware.ConfigPair<T, S>>, S> build(Path configPath) {
				if (defaultRegistryAwareInitializer == null) throw new IllegalStateException("Please set the initializer first");
				var inner = new BasicConfigContainer<>(
						ServerAware.ConfigPair.createCodec(codec, serverAwareCodec, refreshOnReload),
						configPath, () -> new ServerAware.ConfigPair<>(
								defaultConfigInitializer.get(), defaultRegistryAwareInitializer.get()
						),
						reloadsBeforeServer, reloadsAfterServer,
						ServerAware.wrapReload(reloader)
				);
				if (described != null) {
					register(configPath, inner, ServerAware.ConfigPair::staticValues,
							(pair, part) -> new ServerAware.ConfigPair<>(part, pair.serverAwareValues()));
				}
				return ServerAware.wrap(inner, parser != null ? parser : defaultParser(refreshOnReload), cacheReloader, defaultRegistryAwareInitializer);
			}
		}
	}
}
