package com.example.soulbrand.mixin;

import com.example.soulbrand.logic.SoulTooltip;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * tooltip 三行：Fabric 没有全局 ItemTooltipEvent，钉在 {@code ItemStack#getTooltip} 的 RETURN。
 *
 * <p><b>为什么是 ItemStack#getTooltip 而不是 Item#appendTooltip：</b></p>
 * <ul>
 *   <li>{@code getTooltip(PlayerEntity, TooltipContext)} <b>自带 player 参数</b>，
 *       直接就是「看着这件物品的人」。原来注入 Item#appendTooltip 时只能靠
 *       {@code MinecraftClient.getInstance().player} 猜，在服务端 GUI 或某些渲染路径下会是 null，
 *       导致 tooltip 的附魂行永远不显示。</li>
 *   <li>它是所有物品 tooltip 的<b>总出口</b>（字节码里 147 行才调 Item#appendTooltip，
 *       之后还有附魔、护甲纹饰等），在 RETURN 追加能保证排在最后。</li>
 *   <li>原版装备类（SwordItem/ToolItem/ArmorItem/PickaxeItem/AxeItem）都<b>没有覆写</b>
 *       Item#appendTooltip，但注入总出口就不需要依赖这个事实。</li>
 * </ul>
 *
 * <p>性能：只有 tooltip 真的打开时才走这里，且 {@link SoulTooltip#append} 第一句就是
 * isBranded 早退 —— 未刻印物品零成本。</p>
 */
@Mixin(ItemStack.class)
public abstract class SoulTooltipMixin {

    @Inject(
            method = "getTooltip(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/client/item/TooltipContext;)Ljava/util/List;",
            at = @At("RETURN")
    )
    private void soulbrand$appendTooltip(PlayerEntity player, TooltipContext context,
                                        CallbackInfoReturnable<List<Text>> cir) {
        List<Text> tooltip = cir.getReturnValue();
        if (tooltip == null) {
            return;
        }
        SoulTooltip.append((ItemStack) (Object) this, player, tooltip, context);
    }
}
