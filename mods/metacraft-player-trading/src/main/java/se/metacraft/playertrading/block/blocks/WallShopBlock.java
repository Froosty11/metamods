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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

// Heavily copied from WallSkullBlock
public class WallShopBlock extends BaseShopBlock {

	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.boxZ(8.0, 8.0, 16.0));

	public WallShopBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	public Direction getContainerDirection(BlockState state, LevelReader level, BlockPos pos) {
		return state.getValue(FACING).getOpposite();
	}

	@Override
	protected @NonNull VoxelShape getShape(
		final BlockState state, final @NonNull BlockGetter level,
		final @NonNull BlockPos pos, final @NonNull CollisionContext context
	) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return Blocks.PLAYER_WALL_HEAD.defaultBlockState().withPropertiesOf(state);
	}

	@Override
	public BlockState getStateForPlacement(final @NonNull BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);
		BlockGetter level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction[] directions = context.getNearestLookingDirections();

		for (Direction direction : directions) {
			if (direction.getAxis().isHorizontal()) {
				Direction facing = direction.getOpposite();
				state = state.setValue(FACING, facing);
				if (!level.getBlockState(pos.relative(direction)).canBeReplaced(context)) {
					return state;
				}
			}
		}

		return null;
	}

	@Override
	protected @NonNull BlockState rotate(final BlockState state, final Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected @NonNull BlockState mirror(final BlockState state, final Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.@NonNull Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FACING);
	}
}
