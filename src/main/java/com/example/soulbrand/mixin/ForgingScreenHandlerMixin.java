package com.example.soulbrand.mixin;

import com.example.soulbrand.logic.SoulNbt;
import com.example.soulbrand.logic.SoulRules;
import com.example.soulbrand.registry.ModItems;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 铁砧 shift 取走的接管。
 *
 * <p><b>为什么要挂在父类</b>：{@code quickMove} 定义在 {@link ForgingScreenHandler} 上
 * （扫描过 1.20.1 全部 20 个 ScreenHandler，<b>没有任何子类 override 它</b>），
 * 所以 {@code @Mixin(AnvilScreenHandler.class)} 找不到注入目标 ——
 * 报 {@code InvalidInjectionException: find any targets matching 'quickMove'}（实测踩到）。</p>
 *
 * <p><b>为什么不会影响其他锻造台</b>：父类有 17 个子类容器（铁砧、砂轮、织布、
 * 锻造台…）。我们用 {@code instanceof AnvilScreenHandler} 做运行时守卫，
 * 非铁砧一律直接返回，走原版逻辑。</p>
 */
@Mixin(ForgingScreenHandler.class)
public abstract class ForgingScreenHandlerMixin {

    /** 每颗残魂碎晶提供的魂值。 */
    private static final int SOUL_PER_FRAGMENT = 10;

    /**
     * shift+点击输出格：一次扣光碎晶，给一件最终属性的装备。
     *
     * <p><b>为什么必须单独接管</b>：原版 shift 走
     * {@code quickMove → onQuickTransfer → onCrafted(stack,int) → onCrafted(stack) → onTakeOutput}，
     * 而输出格只有 1 件，所以 shift 也只能搬走 1 件 —— 用户反馈的
     * 「按 shift 也只是消耗一个」。</p>
     *
     * <p>这里的语义：<b>shift = 一次性把所有碎晶注入这一件装备</b>。
     * 拿到的还是 1 件装备，魂值 = 当前 + 碎晶总数 × 10，等级一次到位。</p>
     */
    @Inject(method = "quickMove", at = @At("HEAD"), cancellable = true)
    private void soulbrand$quickMove(PlayerEntity player, int slotIndex,
                                     CallbackInfoReturnable<ItemStack> ci) {
        // 运行时守卫：只有铁砧走注魂逻辑
        if (!((Object) this instanceof AnvilScreenHandler)) {
            return;
        }
        ForgingScreenHandlerAccessor self = (ForgingScreenHandlerAccessor) (Object) this;
        Inventory input = self.soulbrand$getInput();
        CraftingResultInventory output = self.soulbrand$getOutput();

        // 只有 shift 输出格才走这条路
        if (slotIndex != self.soulbrand$getResultSlotIndex()) {
            return;
        }
        ItemStack first = input.getStack(0);
        ItemStack second = input.getStack(1);
        if (!soulbrand$isSoulInjection(first, second)) {
            return;
        }

        int fragments = second.getCount();
        int newSoul = SoulNbt.getSoul(first) + fragments * SOUL_PER_FRAGMENT;

        // 产出 1 件，魂值一次到位
        ItemStack result = first.copy();
        result.setCount(1);
        SoulNbt.setSoul(result, newSoul);
        SoulNbt.syncEnchantmentLevel(result, SoulRules.level(newSoul));

        // 扣光碎晶 + 消耗第一格
        input.setStack(0, ItemStack.EMPTY);
        input.setStack(1, ItemStack.EMPTY);
        output.setStack(0, ItemStack.EMPTY);

        // 给玩家。背包放不下就掉在脚下，不能凭空消失。
        if (!player.getInventory().insertStack(result)) {
            player.dropItem(result, false);
        }
        ci.setReturnValue(result);
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
