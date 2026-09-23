package se.metacraft.playertrading.block.blocks;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

// Heavily copied from SkullBlock
public class ShopBlock extends BaseShopBlock {

	public static final int MAX = RotationSegment.getMaxSegmentIndex();
	private static final int ROTATIONS = MAX + 1;
	public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
	private static final VoxelShape SHAPE = Block.column(8.0, 0.0, 8.0);

	public ShopBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.defaultBlockState().setValue(ROTATION, 0));
	}

	@Override
	public Direction getContainerDirection(BlockState state, LevelReader level, BlockPos pos) {
		return Direction.DOWN;
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return Blocks.PLAYER_HEAD.defaultBlockState().withPropertiesOf(state);
	}

	@Override
	protected @NonNull VoxelShape getShape(
		final @NonNull BlockState state, final @NonNull BlockGetter level,
		final @NonNull BlockPos pos, final @NonNull CollisionContext context
	) {
		return this.getCollisionShape(state, level, pos, context);
	}

	@Override
	protected @NonNull VoxelShape getCollisionShape(
		final @NonNull BlockState state, final @NonNull BlockGetter level,
		final @NonNull BlockPos pos, final @NonNull CollisionContext context
	) {
		return SHAPE;
	}

	@Override
	public BlockState getStateForPlacement(final @NonNull BlockPlaceContext context) {
		return super.getStateForPlacement(context).setValue(ROTATION, RotationSegment.convertToSegment(context.getRotation()));
	}

	@Override
	protected @NonNull BlockState rotate(final BlockState state, final Rotation rotation) {
		return state.setValue(ROTATION, rotation.rotate(state.getValue(ROTATION), ROTATIONS));
	}

	@Override
	protected @NonNull BlockState mirror(final BlockState state, final Mirror mirror) {
		return state.setValue(ROTATION, mirror.mirror(state.getValue(ROTATION), ROTATIONS));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.@NonNull Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(ROTATION);
	}

}
