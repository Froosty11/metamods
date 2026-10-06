package se.metacraft.config.util;

import com.mojang.serialization.MapCodec;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CapturedCodecs {

	public static final String COMPONENT_TYPE = "component_type";

	public static final Map<String, MapCodec<?>> CAPTURED_CODECS = new ConcurrentHashMap<>();

}
