package nu.metacraft.bundles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

@Config(name = "Bundles", description = "Resizes bundles.")
public record BundleConfig(
		@Option(description = "Show a bundle's contents in its tooltip and on the item model.", key = "enable_bundle_rendering", restart = true) boolean bundleRendering
) {

	public static final MapCodec<BundleConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.BOOL.fieldOf("enable_bundle_rendering").forGetter(BundleConfig::bundleRendering)
			).apply(instance, BundleConfig::new)
	);

	public static final BundleConfig DEFAULT = new BundleConfig(true);

	private static final ConfigContainer<BundleConfig> CONTAINER = ConfigContainer.Builder.create(
			CODEC, () -> DEFAULT
	).describedBy(BundleConfig.class).build(FabricLoader.getInstance().getConfigDir().resolve("metacraft-bundles.json"));


	public static BundleConfig getInstance() {
		return CONTAINER.get();
	}

}
