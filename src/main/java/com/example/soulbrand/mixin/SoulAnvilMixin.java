package com.example.soulbrand.mixin;

import com.example.soulbrand.logic.SoulNbt;
import com.example.soulbrand.logic.SoulRules;
import com.example.soulbrand.registry.ModItems;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 铁砧注魂：把残魂碎晶变成魂值。
 *
 * <p><b>用法</b>：铁砧第一个格放<b>已刻印</b>的武器/护甲，第二个格放残魂碎晶，
 * 输出格直接显示注魂后的装备。每颗碎晶 +10 魂值。</p>
 *
 * <p><b>为什么注入而不是新写界面</b>：原版铁砧界面（{@code AnvilScreen}）已经处理了
 * 槽位拖拽、名字编辑、经验消耗等等一切交互。我们只需要改两处：</p>
 * <ol>
 *   <li>{@code updateResult()}：算出输出格显示什么 —— 我们改成「注入碎晶后的魂值」</li>
 *   <li>{@code onTakeOutput()}：玩家取走输出物时 —— 我们改成「扣碎晶、加魂值」</li>
 * </ol>
 *
 * <p><b>关键点</b>：{@code updateResult} 里原版会把 {@code input[0]} 复制一份写进
 * {@code output}，取出时 {@code onTakeOutput} 再把 input 清空、扣材料。
 * 所以魂值必须写在<b>输出的那份副本</b>上 —— 直接改 input 会在取出时被原版覆盖掉。</p>
 *
 * <p><b>不封顶</b>：与「魂值无上限、每 100 一级」的玩法一致。</p>
 *
 * <p><b>第二格限制</b>：原版第二格是「另一个材料」（比如同名武器合并、或者活体石头），
 * 我们把碎晶放进去时，走的是原版不认识的分支。为了让玩家放得进去，
 * 这里只在「第一格是已刻印装备」时接管；否则完全原版行为，不影响任何其他用法。</p>
 */
@Mixin(AnvilScreenHandler.class)
public abstract class SoulAnvilMixin {

    /**
     * 每颗残魂碎晶提供的魂值。
     *
     * <p><b>必须是 private</b>：Mixin 规范要求 mixin 类里的静态字段全部私有，
     * 否则启动时抛 {@code InvalidMixinException: contains non-private static field}，
     * 而且是启动期崩溃（实测踩到）。</p>
     */
    private static final int SOUL_PER_FRAGMENT = 10;

    /**
     * input / output / player 定义在父类 {@code ForgingScreenHandler} 上，
     * {@code @Shadow} 找不到继承来的字段，只能走 accessor。
     */
    /**
     * 允许取走输出物。
     *
     * <p><b>不接管就取不出来</b>：原版 {@code canTakeOutput} 的判定是
     * 「非创造 且 经验等级 &lt; levelCost → 拒绝；levelCost &gt; 0 → 允许；否则拒绝」。
     * 最后那条「否则拒绝」是原版用来表达「这个组合不花经验、但也不能白拿」的。
     * 我们取消了 {@code updateResult} 的原版逻辑，{@code levelCost} 就一直是 0
     * （从没被写过），于是永远落到最后那条 → 输出物取不出来（实测卡住）。</p>
     */
    @Inject(method = "canTakeOutput", at = @At("HEAD"), cancellable = true)
    private void soulbrand$canTakeOutput(PlayerEntity player, boolean partial,
                                          org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> ci) {
        ForgingScreenHandlerAccessor self = (ForgingScreenHandlerAccessor) (Object) this;
        Inventory input = self.soulbrand$getInput();

        ItemStack first = input.getStack(0);
        ItemStack second = input.getStack(1);
        if (!soulbrand$isSoulInjection(first, second)) {
            return;   // 不命中 → 走原版判定
        }
        // 注魂不消耗经验等级，所以直接放行。
        ci.setReturnValue(true);
    }

    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private void soulbrand$updateResult(CallbackInfo ci) {
        ForgingScreenHandlerAccessor self = (ForgingScreenHandlerAccessor) (Object) this;
        Inventory input = self.soulbrand$getInput();
        CraftingResultInventory output = self.soulbrand$getOutput();

        ItemStack first = input.getStack(0);
        ItemStack second = input.getStack(1);

        if (!soulbrand$isSoulInjection(first, second)) {
            return;   // 不命中 → 走原版逻辑
        }

        int fragments = second.getCount();
        int oldSoul = SoulNbt.getSoul(first);

        // 写进 output 的副本：原版取出时会把 input 清空，只保留 output 里的这份。
        //
        // <b>数量固定 1</b>：输出 N 件等于凭空复制装备（实测截图：5 颗碎晶
        // 变成 5 把剑），玩家会拿到 N 把完全相同的武器。
        // 所以输出 1 件；shift 一次扣光碎晶由 ForgingScreenHandlerMixin 单独实现。
        ItemStack result = first.copy();
        result.setCount(1);
        SoulNbt.setSoul(result, oldSoul + SOUL_PER_FRAGMENT);
        // 等级是魂值的派生副本，魂值变了必须重刷 Enchantments 条目，
        // 否则 JEI / 装备面板还显示旧等级。
        SoulNbt.syncEnchantmentLevel(result, SoulRules.level(oldSoul + SOUL_PER_FRAGMENT));

        output.setStack(0, result);

        // 把 levelCost 显式归零：它决定「取走输出物要花多少经验」，
        // 也是 canTakeOutput 的判定依据。注魂不额外收费，所以必须是 0。
        // 不显式清的话，玩家先试过别的材料组合再换过来时，levelCost 会残留。
        ((AnvilScreenHandlerAccessor) (Object) this).soulbrand$getLevelCost().set(0);

        ci.cancel();
    }

    /**
     * 玩家取走输出物：扣碎晶、把魂值加到取走的那份装备上。
     *
     * <p>原版实现会把 input 清空、扣经验等级。我们这里接管整个流程：
     * 碎晶从 input[1] 扣，魂值写进玩家真正拿到手的物品。</p>
     */
    @Inject(method = "onTakeOutput", at = @At("HEAD"), cancellable = true)
    private void soulbrand$onTakeOutput(PlayerEntity player, ItemStack taken, CallbackInfo ci) {
        ForgingScreenHandlerAccessor self = (ForgingScreenHandlerAccessor) (Object) this;
        Inventory input = self.soulbrand$getInput();

        ItemStack first = input.getStack(0);
        ItemStack second = input.getStack(1);
        if (!soulbrand$isSoulInjection(first, second)) {
            return;
        }

        int fragments = second.getCount();
        // taken 是玩家真正拿到手的那份物品：普通点击 count=1，shift 拿走整叠 count=N。
        // 魂值按<b>实际拿走件数</b>算：原始魂值 + 件数 × 单颗加成。
        int takenCount = Math.max(1, taken.getCount());
        int newSoul = SoulNbt.getSoul(first) + takenCount * SOUL_PER_FRAGMENT;

        // 拿到手的物品加魂值 + 刷等级
        SoulNbt.setSoul(taken, newSoul);
        SoulNbt.syncEnchantmentLevel(taken, SoulRules.level(newSoul));

        // 扣碎晶：按实际拿走的件数扣
        int consume = Math.min(fragments, takenCount);
        if (consume >= fragments) {
            input.setStack(1, ItemStack.EMPTY);
        } else {
            ItemStack left = second.copy();
            left.decrement(consume);
            input.setStack(1, left);
        }

        // 消耗第一格（原版也是清空它）
        input.setStack(0, ItemStack.EMPTY);

        // <b>不清 output</b>：输出格的数量递减由 CraftingResultSlot.takeStack 自己处理
        // （原版 onTakeOutput 也不清 output —— 清了的话取走 1 件后输出格就空了，
        //  玩家还得重新触发一次 updateResult 才能看到剩下的）。
        ci.cancel();
    }

    /**
     * 是否是「给已刻印装备注魂」这个场景。
     *
     * <p>两个条件都要满足：</p>
     * <ul>
     *   <li>第一格是<b>已刻印</b>的装备（没刻印的不能注魂 —— 那是附魂台的活）</li>
     *   <li>第二格是残魂碎晶</li>
     * </ul>
     */
    private static boolean soulbrand$isSoulInjection(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) {
            return false;
        }
        if (!SoulNbt.isBranded(first)) {
            return false;
        }
        return second.isOf(ModItems.SOUL_FRAGMENT);
    }
}
