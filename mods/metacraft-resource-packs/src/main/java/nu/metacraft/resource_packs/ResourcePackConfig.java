package nu.metacraft.resource_packs;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.MinecraftServer;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.METACodecs;
import nu.metacraft.resource_packs.mixin.HttpUtilAccessor;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import se.metacraft.config.container.ConfigContainer;
import se.metacraft.config.container.ReloadCause;
import se.metacraft.config.extensions.LoadAware;
import se.metacraft.config.extensions.ReloadAware;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.UnknownHostException;
import java.nio.file.*;
import java.util.*;

public class ResourcePackConfig implements LoadAware, ReloadAware {

	@SuppressWarnings("deprecation")
	private static final HashFunction SHA1 = Hashing.sha1();

	public static final ReloadCause SOFT = ReloadCause.of(METAcraftLib.getID("soft"));

	private static final Path configDir = FabricLoader.getInstance().getConfigDir().resolve(ResourcePacks.MODID);
	public static final Path RESOURCE_PACK_DIR = configDir.resolve("resource-packs");
	public static final Path RP_UPDATE_DIR = RESOURCE_PACK_DIR.resolve("update");

	public static final MapCodec<ResourcePackConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					METACodecs.createPMapCodec(
						UUIDUtil.STRING_CODEC, ResourcePack.CODEC, HashTreePMap.empty()
					).fieldOf("resource_packs").forGetter(
							c -> c.resourcePacks
					),
					Codec.BOOL.fieldOf("required").forGetter(c -> c.required),
					ComponentSerialization.CODEC.optionalFieldOf("prompt").forGetter(c -> c.prompt),
					Codec.STRING.fieldOf("server_address").forGetter(c -> c.serverAddress),
					Codec.STRING.optionalFieldOf("network_address").forGetter(c -> c.networkAddress),
					Codec.intRange(0, 65535).fieldOf("port").forGetter(c -> c.port),
					Codec.INT.fieldOf("max_connections").forGetter(c -> c.maxConnections),
					Codec.BOOL.fieldOf("allow_manual_downloads").forGetter(c -> c.allowManualDownloads),
					ResourcePackServer.SSLSettings.CODEC.optionalFieldOf("ssl").forGetter(c -> c.sslSettings)
			).apply(instance, ResourcePackConfig::new)
	);

	private static final ConfigContainer<ResourcePackConfig> CONFIG = ConfigContainer.Builder.create(
			CODEC,
			ResourcePackConfig::new
	).setReloader((old, reloaded, cause) -> reloaded.get().map(c -> {
		c.handleReload(old, cause == SOFT);
		return c;
	}).orElse(old)).withPath(configDir.resolve("config.json5")).build("resource-packs");

	private final PMap<UUID, ResourcePack> resourcePacks;
	private final Set<UUID> removedPacks = new HashSet<>();
	private final Set<UUID> modifiedPacks = new HashSet<>();
	private final Set<UUID> prevGlobals = new HashSet<>();
	private final Set<UUID> newGlobals = new HashSet<>();
	private final boolean required;
	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private final Optional<Component> prompt;
	private final String serverAddress;
	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private final Optional<String> networkAddress;
	private final int port;
	private final int maxConnections;
	private final boolean allowManualDownloads;
	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private final Optional<ResourcePackServer.SSLSettings> sslSettings;

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	public ResourcePackConfig(
			PMap<UUID, ResourcePack> resourcePacks,
			boolean required, Optional<Component> prompt, String serverAddress,
			Optional<String> networkAddress, int port, int maxConnections,
			boolean allowManualDownloads,
			Optional<ResourcePackServer.SSLSettings> sslSettings
	) {
		this.resourcePacks = resourcePacks;
		this.required = required;
		this.prompt = prompt;
		this.serverAddress = serverAddress;
		this.networkAddress = networkAddress;
		this.port = port;
		this.maxConnections = maxConnections;
		this.allowManualDownloads = allowManualDownloads;
		this.sslSettings = sslSettings;
	}

	public ResourcePackConfig() {
		this(
				HashTreePMap.empty(), true,
				Optional.empty(), "localhost",
				Optional.empty(), 25585,
				50, false, Optional.empty()
		);
	}

	public void copyTransientFrom(ResourcePackConfig source) {
		removedPacks.addAll(source.removedPacks);
		modifiedPacks.addAll(source.modifiedPacks);
		prevGlobals.addAll(source.prevGlobals);
		newGlobals.addAll(source.newGlobals);
	}

	public static ResourcePackConfig getConfig() {
		return CONFIG.get();
	}

	public static void reload(boolean soft) {
		CONFIG.reload(soft ? SOFT : ReloadCause.DEFAULT);
	}

	public boolean resourcePackExists(UUID uuid) {
		return resourcePacks.containsKey(uuid);
	}

	public Optional<ResourcePack> getResourcePack(UUID uuid) {
		return Optional.ofNullable(resourcePacks.get(uuid));
	}

	private void onRemove(UUID uuid) {
		removedPacks.add(uuid);
		modifiedPacks.remove(uuid);
		newGlobals.remove(uuid);
	}

	private void onNewOrModified(UUID uuid) {
		modifiedPacks.add(uuid);
		removedPacks.remove(uuid);
	}

	private void onNewGlobal(UUID uuid) {
		newGlobals.add(uuid);
		prevGlobals.remove(uuid);
	}

	private void onNoLongerGlobal(UUID uuid) {
		newGlobals.remove(uuid);
		prevGlobals.add(uuid);
	}

	private void handleReload(ResourcePackConfig old, boolean soft) {
		if (soft) {
			modifiedPacks.addAll(old.modifiedPacks);
			removedPacks.addAll(old.removedPacks);
			newGlobals.addAll(old.newGlobals);
			prevGlobals.addAll(old.prevGlobals);
		}
		resourcePacks.forEach((id, pack) -> {
			var p = old.getResourcePack(id);
			if (p.isEmpty() || !Objects.equals(p.get().getHash(), pack.getHash())) {
				onNewOrModified(id);
			}
			boolean oldGlobal = old.resourcePacks.containsKey(id) && old.resourcePacks.get(id).isGlobal();
			boolean newGlobal = pack.isGlobal();
			if (oldGlobal && !newGlobal) {
				onNoLongerGlobal(id);
			}
			if (newGlobal && !oldGlobal) {
				onNewGlobal(id);
			}
		});
		old.resourcePacks.forEach((id, pack) -> {
			if (!resourcePacks.containsKey(id)) {
				if (pack.isGlobal()) {
					onNoLongerGlobal(id);
				}
				onRemove(id);
			}
		});
	}

	public boolean hasChangedButStillExists(UUID pack) {
		return modifiedPacks.contains(pack);
	}

	public boolean isNowGlobal(UUID pack) {
		return newGlobals.contains(pack);
	}

	public Collection<Map.Entry<UUID, ResourcePack>> getResourcePacks() {
		return Collections.unmodifiableSet(resourcePacks.entrySet());
	}

	public Iterable<UUID> getRemovedPacks() {
		return removedPacks::iterator;
	}

	public Iterable<UUID> getPrevGlobals() {
		return prevGlobals::iterator;
	}

	public String getServerAddress() {
		return serverAddress;
	}

	public int getPort() {
		return port;
	}

	public ResourcePackServer createResourcePackServer(MinecraftServer server) throws UnknownHostException, BindException {
		return new ResourcePackServer(server, port, networkAddress, maxConnections, sslSettings);
	}

	public boolean allowManualDownloads() {
		return allowManualDownloads;
	}

	public Optional<ClientboundResourcePackPushPacket> createEnablePacket(UUID uuid) {
		return getResourcePack(uuid).map(entry -> {
			String protocol = sslSettings.isPresent() ? "https" : "http";
			return new ClientboundResourcePackPushPacket(
					uuid,  protocol + "://" + getServerAddress() + ":" + getPort()+ "/" + uuid.toString(),
					entry.getHash().toString(), required, prompt
			);
		});
	}

	public ResourcePackConfig withPack(UUID uuid, ResourcePack pack) {
		var packs = new ResourcePackConfig(
			resourcePacks.plus(uuid, pack), required, prompt, serverAddress, networkAddress, port, maxConnections, allowManualDownloads, sslSettings
		);
		packs.copyTransientFrom(this);
		return packs;
	}

	private void movePacks() {
		try {
			Files.createDirectories(RP_UPDATE_DIR);
			var zipsToUpdate = RP_UPDATE_DIR.toFile().listFiles(file -> file.getName().endsWith(".zip"));
			if (zipsToUpdate != null) {
				for (File file : zipsToUpdate) {
					Path dest = RESOURCE_PACK_DIR.resolve(file.getName());
					ResourcePacks.LOGGER.info("Moving {} to {}", file, dest);
					Files.move(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		} catch (IOException err) {
			ResourcePacks.LOGGER.error(err.getMessage(), err);
		}
	}

	@Override
	public void beforeReload(ReloadCause cause) {
		movePacks();
	}

	@Override
	public void afterLoad(Optional<ReloadCause> cause) {
		try {
			if (cause.isEmpty()) movePacks();
			var resourcePackZips = RESOURCE_PACK_DIR.toFile().listFiles(file -> file.getName().endsWith(".zip"));
			if (resourcePackZips != null) {
				for (var file : resourcePackZips) {
					var path = file.toPath();
					var hash = HttpUtilAccessor.callHashFile(path, SHA1);
					UUID uuid = UUID.randomUUID();
					CONFIG.modify(c -> {
						if (c.resourcePacks.containsKey(uuid)) {
							//Duplicate
							ResourcePacks.LOGGER.warn(
									"Resource pack {} tried to use UUID {} " +
											"but it was already present! This resource pack " +
											"must be added to config manually.", file, uuid
							);
						}

						for (var pack : c.resourcePacks.values()) {
							//Resource pack update.
							if (pack.getFile().equals(path)) {
								pack.setHash(hash);
								return c;
							}
							//Try to follow renamed file (might cause mismatches).
							if ((pack.getHash() == null || pack.getHash().equals(hash)) && !pack.getFile().toFile().exists()) {
								var newPack = pack.withFile(path);
								newPack.setHash(hash);
								return c.withPack(uuid, newPack);
							}
						}

						return c.withPack(
							uuid, new ResourcePack(
								path, false, Optional.empty(), hash
							)
						);
					});
				}
			}
		} catch (IOException e) {
			ResourcePacks.LOGGER.error(e.getMessage(), e);
		}

		cause.ifPresent(reloadCause -> ResourcePackServerManager.getServers().forEach(
				server -> {
					ResourcePackHelper.resendResourcePacks(server, reloadCause != SOFT);
				}
		));
	}

	public static class ResourcePack {

		private final Path file;
		private final boolean global;
		private final Optional<Boolean> allowManualDownloads;
		private HashCode hash;

		public static final Codec<ResourcePack> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.STRING.comapFlatMap(file -> {
							try {
								return DataResult.success(Path.of(file));
							} catch (InvalidPathException e) {
								return DataResult.error(e::getMessage);
							}
						}, Path::toString).fieldOf("file").forGetter(ResourcePack::getFile),
						Codec.BOOL.fieldOf("global").forGetter(ResourcePack::isGlobal),
						Codec.BOOL.optionalFieldOf("allow_manual_downloads").forGetter(pack -> pack.allowManualDownloads)
				).apply(instance, ResourcePack::new)
		);

		public ResourcePack(
				Path file, boolean global,
				@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Boolean> allowManualDownloads
		) {
			this.file = file;
			this.global = global;
			this.allowManualDownloads = allowManualDownloads;
		}

		public ResourcePack(
				Path file, boolean global,
				@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Boolean> allowManualDownloads,
				HashCode hash
		) {
			this(file, global, allowManualDownloads);
			this.hash = hash;
		}

		public HashCode getHash() {
			if (hash == null && file.toFile().exists()) {
				try {
					hash = HttpUtilAccessor.callHashFile(file, SHA1);
				} catch (IOException err) {
					ResourcePacks.LOGGER.error(err.getMessage(), err);
				}
			}
			return hash;
		}

		public ResourcePack withFile(Path path) {
			return new ResourcePack(path, global, allowManualDownloads, hash);
		}

		public void setHash(HashCode hash) {
			this.hash = hash;
		}

		public Path getFile() {
			return file;
		}

		public boolean isGlobal() {
			return global;
		}

		public boolean allowManualDownloads() {
			return allowManualDownloads.orElse(global);
		}
	}
}
