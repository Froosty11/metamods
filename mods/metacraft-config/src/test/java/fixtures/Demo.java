package fixtures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

import java.util.Optional;

@Config(name = "Demo", description = "For tests.")
public record Demo(
		@Option(description = "On.") boolean on,
		@Option(description = "Stitches.", min = 1, max = 16) int stitches,
		@Option(description = "Speed.", min = 0) double speed,
		@Option(description = "Cap.", min = 0) Optional<Double> cap,
		@Option(description = "Needs a restart.", restart = true) boolean heavy,
		@Option(description = "Inner.") Inner inner
) {
	public static final Demo DEFAULT = new Demo(true, 6, 2.5, Optional.empty(), false, Inner.DEFAULT);

	public static final MapCodec<Demo> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.BOOL.fieldOf("on").forGetter(Demo::on),
			Codec.intRange(1, 16).fieldOf("stitches").forGetter(Demo::stitches),
			Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("speed").forGetter(Demo::speed),
			Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("cap").forGetter(Demo::cap),
			Codec.BOOL.fieldOf("heavy").forGetter(Demo::heavy),
			Inner.CODEC.codec().fieldOf("inner").forGetter(Demo::inner)
	).apply(i, Demo::new));

	@Config(name = "Inner")
	public record Inner(@Option(description = "Name.") String name) {
		public static final Inner DEFAULT = new Inner("x");

		public static final MapCodec<Inner> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.STRING.fieldOf("name").forGetter(Inner::name)
		).apply(i, Inner::new));
	}
}
