package com.example.soulbrand.content;

import com.example.soulbrand.logic.SoulHeart;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * 生命魂晶：右键使用，永久提高生命上限 2 点（1 颗心）。
 *
 * <p><b>单位</b>：MC 里 1 颗心 = 2 点生命。用户要求「一次加一颗心」，
 * 所以每颗 +2 点，满 40 点 = 20 颗心 = 20 颗魂晶。</p>
 *
 * <p><b>提示走 overlay 不进聊天栏</b>：{@code sendMessage(text, true)} 的第二个参数
 * {@code overlay=true} 会让文字出现在快捷栏上方并自动淡出，
 * 与「捡到物品」「获得经验」一致；{@code false} 会写进聊天记录（用户明确不要）。</p>
 */
public class SoulHeartItem extends Item {

    public SoulHeartItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);

        // 客户端只播动作，全部判定与消耗在服务端。
        if (world.isClient) {
            return TypedActionResult.success(held);
        }

        if (!SoulHeart.canIncrease(player)) {
            player.sendMessage(Text.translatable("message.soulbrand.heart.maxed")
                    .formatted(Formatting.RED), true);
            return TypedActionResult.fail(held);
        }

        // 判定全过之后才动手
        SoulHeart.apply(player);
        if (!player.getAbilities().creativeMode) {
            held.decrement(1);
        }

        // 显示「已加成的总心数 / 上限心数」，比显示点数直观
        player.sendMessage(Text.translatable("message.soulbrand.heart.used",
                        SoulHeart.getBonus(player) / 2,
                        SoulHeart.MAX_BONUS / 2)
                .formatted(Formatting.RED), true);
        return TypedActionResult.success(held);
    }
}
