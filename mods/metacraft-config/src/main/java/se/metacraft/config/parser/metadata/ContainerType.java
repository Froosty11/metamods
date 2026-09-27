package se.metacraft.config.parser.metadata;

public record ContainerType<T extends Container.ContainerData>(String name, Class<T> clazz) {
	public static final ContainerType<?> RECORD = simple("record");
	public static final ContainerType<?> LIST = simple("list");
	public static final ContainerType<?> MAP = simple("map");
	public static final ContainerType<Container.DispatchedMap> DISPATCHED_MAP = new ContainerType<>("dispatched_map", Container.DispatchedMap.class);
	public static final ContainerType<?> EITHER = simple("either");
	public static final ContainerType<Container.DispatchedEither> DISPATCHED_EITHER = new ContainerType<>("dispatched_either", Container.DispatchedEither.class);
	public static final ContainerType<?> UNIT = simple("unit");
	public static final ContainerType<Container.Recursive> RECURSIVE = new ContainerType<>("recursive", Container.Recursive.class);

	public static ContainerType<Container.ContainerData> simple(String name) {
		return new ContainerType<>(name, Container.ContainerData.class);
	}
}
