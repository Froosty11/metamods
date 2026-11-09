package se.metacraft.ovvar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The {@code server} block of {@code config/ovvar.json}: what this server calls itself. ovvar no
 * longer sets the MOTD from it (that is {@code server.properties}' job); the key stays so existing
 * files still read. Every key has a default.
 *
 * @param name this server's name, as players read it in the server list (default "METAcraft")
 * @param shaderPatcherUrl the web page {@code /ovvar shaders} links to, where players patch their shaderpack
 */
public record ServerConfig(String name, String shaderPatcherUrl) {
	public static final String DEFAULT_SHADER_PATCHER_URL = "https://metacraft.se/ovvar/shaders";
	public static final ServerConfig DEFAULT = new ServerConfig("METAcraft", DEFAULT_SHADER_PATCHER_URL);


	public static final MapCodec<ServerConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ConfigFields.field(Codec.STRING, "name", DEFAULT.name, "This server's name, as players read it in the server list.").forGetter(ServerConfig::name),
			ConfigFields.field(Codec.STRING, "shader_patcher_url", DEFAULT.shaderPatcherUrl, "The page /ovvar shaders links to, where players patch their shaderpack for ovvar.").forGetter(ServerConfig::shaderPatcherUrl)
	).apply(instance, (name, shaderPatcherUrl) -> new ServerConfig(name, shaderPatcherUrl)));
}
