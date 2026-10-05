package se.metacraft.config.event;

import com.mojang.serialization.DataResult;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.HolderLookup;
import se.metacraft.config.BuiltinDefaults;
import se.metacraft.config.parser.result.AbstractCodecResult;
import se.metacraft.config.util.event.EventWithPhases;

import java.util.Optional;

public interface CodecDefaultValueEvent {

	Event<CodecDefaultValueEvent> EVENT = EventWithPhases.createPrePostEventWithInternal(
		CodecDefaultValueEvent.class, events -> (codec, lookup) -> EventWithPhases.getOptionalResult(
			events, event -> event.tryGetDefaultValue(codec, lookup)
		),
		BuiltinDefaults.BUILTIN
	);

	Optional<DataResult<?>> tryGetDefaultValue(AbstractCodecResult codec, HolderLookup.Provider lookup);

}
