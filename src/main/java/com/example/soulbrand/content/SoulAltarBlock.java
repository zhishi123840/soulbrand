package com.example.soulbrand.content;

import com.example.soulbrand.registry.ModItems;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.inventory.Inventory;

/**
 * 附魂台。
 *
 * <p>状态：{@code facing} + {@code charged}（刻印成功后置 true）。</p>
 *
 * <p>右键<b>打开界面</b>（不再直接刻印）—— 刻印、查看魂值、净魂都在界面里完成，
 * 判定与扣除都在 {@link SoulAltarScreenHandler} 内，遵循「全部校验通过才扣」的约定。</p>
 *
 * <h2>为什么继承 BlockWithEntity</h2>
 * <p>之前给顶面加书动画时，方块只是 {@code implements BlockEntityProvider}，
 * 结果 {@code createBlockEntity} 一次都没被调用 —— 创建并挂载 BE 的逻辑在
 * {@link BlockWithEntity} 里，光实现接口不会触发。这次要真做 GUI（容器必须有 BE 锚点），
 * 所以改成继承它，顺带把那个坑填了。</p>
 *
 * <p>代价：{@code BlockWithEntity extends Block}，不是 {@code HorizontalFacingBlock} 的子类，
 * 所以朝向属性、{@code rotate}/{@code mirror} 要手工复刻。</p>
 *
 * <p><b>{@code getRenderType} 必须显式返回 MODEL</b>：BlockWithEntity 默认给 INVISIBLE
 * （给原版那种「形状全靠 BER 画」的方块用），照抄的话整个阶梯台会消失。</p>
 *
 * <p><b>不注册 ticker</b>：本方块没有任何 per-tick 逻辑，GUI 靠事件驱动。
 * 性能红线里「附魂台不注册 ticker」这条依然成立。</p>
 */
public class SoulAltarBlock extends BlockWithEntity {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty CHARGED = BooleanProperty.of("charged");

    /** 刻印消耗的碎晶数量（固定 3 颗，不随配置变）。 */
    public static final int FRAGMENT_COST = 3;

    /**
     * 碰撞箱：按 Blockbench 模型的实测包围盒分四层。
     *
     * <pre>
     *   底座   0..16 × 0.0..1.385  × 0..16
     *   台阶   1..15 × 1.385..2.769 × 1..15
     *   柱身   3..13 × 2.769..9.0  × 3..13
     *   顶板   0..16 × 9.0..10.615 × 0..16
     * </pre>
     *
     * <p>四根装饰柱与宝石不纳入碰撞：它们细、靠边，做成碰撞会让玩家站在旁边被莫名挡住。
     * 顶板当台面，玩家能踩上去就够。</p>
     *
     * <p>各段在 X/Z 上居中对称，四个 facing 共用同一形状，不需要旋转表。</p>
     */
    private static final VoxelShape SHAPE;

    static {
        VoxelShape plinth = Block.createCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 1.385D, 16.0D);
        VoxelShape step = Block.createCuboidShape(1.0D, 1.385D, 1.0D, 15.0D, 2.769D, 15.0D);
        VoxelShape shaft = Block.createCuboidShape(3.0D, 2.769D, 3.0D, 13.0D, 9.0D, 13.0D);
        VoxelShape slab = Block.createCuboidShape(0.0D, 9.0D, 0.0D, 16.0D, 10.615D, 16.0D);
        SHAPE = VoxelShapes.union(plinth, step, shaft, slab);
    }

    public SoulAltarBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(CHARGED, Boolean.FALSE));
    }

    // ------------------------------------------------------------ 状态

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, CHARGED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState()
                .with(FACING, ctx.getHorizontalPlayerFacing().getOpposite())
                .with(CHARGED, Boolean.FALSE);
    }

    /** 手工复刻 HorizontalFacingBlock 的朝向变换（父类不是它）。 */
    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    // ------------------------------------------------------------ 渲染与形状

    /** 阶梯台靠方块模型渲染，不能沿用 BlockWithEntity 默认的 INVISIBLE。 */
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
        return SHAPE;
    }

    // ------------------------------------------------------------ 可刻印判据

    /**
     * 可刻印范围：有耐久度的工具 / 武器 / 护甲。
     *
     * <p><b>判据只用 {@code getMaxDamage() > 0}，不用 {@code isDamageable()}。</b>
     * 反编译 {@code ItemStack.isDamageable()} 可见它最后还有一步
     * {@code getNbt().getBoolean("Unbreakable")} —— 用那个判据的话，
     * 不可破坏的装备会被判成「不可损伤」而刻不上。
     * 而 {@code getMaxDamage()} 只是透传 {@code Item.getMaxDamage()}，不受 Unbreakable 影响。</p>
     */
    public static boolean isBrandable(ItemStack stack) {
        return !stack.isEmpty() && stack.getMaxDamage() > 0;
    }

    // ------------------------------------------------------------ 交互

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayerEntity)) {
            return ActionResult.PASS;
        }
        // BlockWithEntity.createScreenHandlerFactory 会去 world 取 BE 并检查
        // 是否 instanceof NamedScreenHandlerFactory —— 我们的 BE 是。
        NamedScreenHandlerFactory factory = state.createScreenHandlerFactory(world, pos);
        if (factory != null) {
            player.openHandledScreen(factory);
        }
        return ActionResult.CONSUME;
    }

    @Override
    public net.minecraft.block.entity.BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SoulAltarBlockEntity(pos, state);
    }

    /** 显式返回 null：本方块没有任何 per-tick 逻辑（性能红线要求）。 */
    @Override
    public <T extends net.minecraft.block.entity.BlockEntity>
    net.minecraft.block.entity.BlockEntityTicker<T> getTicker(
            World world, BlockState state,
            net.minecraft.block.entity.BlockEntityType<T> type) {
        return null;
    }

    /**
     * 方块被替换（含被破坏）时，把槽里的装备吐出来。
     *
     * <p><b>不能用 {@code Block.dropStacks}：</b>反编译确认它走
     * {@code getDroppedStacks} → loot table，掉的是<b>附魂台方块本身</b>，
     * 而且完全不碰 BE 的容器内容。在破坏路径上再调它一次 = 祭台掉两个，
     * 槽里的装备照样丢（实测翻车过）。</p>
     *
     * <p>所以这里只手动吐槽位内容；祭台方块自身的掉落交给 loot table 的正常破坏流程。</p>
     */
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        if (!moved && !state.isOf(newState.getBlock()) && !world.isClient) {
            if (world.getBlockEntity(pos) instanceof SoulAltarBlockEntity altar) {
                ItemStack leftover = altar.inventory.removeStack(0);
                if (!leftover.isEmpty()) {
                    Block.dropStack(world, pos, leftover);
                }
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /** 刻印成功后把顶面贴图切到充能态。由 ScreenHandler 通过 BE 调用。 */
    void markCharged(World world, BlockPos pos, BlockState state) {
        if (state.get(CHARGED)) {
            return;
        }
        world.setBlockState(pos, state.with(CHARGED, Boolean.TRUE), Block.NOTIFY_ALL);
    }
}
