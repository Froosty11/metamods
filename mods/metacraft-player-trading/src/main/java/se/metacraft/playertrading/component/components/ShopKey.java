package se.metacraft.playertrading.component.components;

import com.mojang.serialization.Codec;
import org.apache.commons.lang3.RandomStringUtils;

public record ShopKey(String secret) {

	public static final Codec<ShopKey> CODEC = Codec.STRING.xmap(ShopKey::new, ShopKey::secret);

	public static ShopKey generate() {
		return new ShopKey(RandomStringUtils.secureStrong().nextAscii(32));
	}

}
