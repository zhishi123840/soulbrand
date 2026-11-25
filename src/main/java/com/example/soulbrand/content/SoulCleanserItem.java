package com.example.soulbrand.content;

import com.example.soulbrand.logic.SoulNbt;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * 净魂粉：右键把另一只手那件已刻印装备的<b>魂值清零</b>。
 *
 * <p>1.0 去掉反噬后它只剩这一件功能：把装备打回 0/100，重新从 I 阶往上攒。
 * 不再动玩家血量、不再碰任何玩家属性 —— 它现在是一件纯物品级道具。</p>
 *
 * <p>对空手 / 未刻印装备：无副作用、不消耗、只给一行提示。</p>
 */
public class SoulCleanserItem extends Item {

    public SoulCleanserItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);

        // 客户端只播动作，全部判定与消耗在服务端。
        if (world.isClient) {
            return TypedActionResult.success(held);
        }

        ItemStack target = pickTarget(player, hand);
        if (target == null) {
            player.sendMessage(Text.translatable("message.soulbrand.cleanser.no_item")
                    .formatted(Formatting.RED), false);
            return TypedActionResult.fail(held);
        }
        if (!SoulNbt.isBranded(target)) {
            player.sendMessage(Text.translatable("message.soulbrand.cleanser.not_branded")
                    .formatted(Formatting.RED), false);
            return TypedActionResult.fail(held);
        }

        // 判定全过之后才动手。只有一件事：魂值归零。
        SoulNbt.clearSoul(target);

        if (!player.getAbilities().creativeMode) {
            held.decrement(1);
        }

        player.sendMessage(Text.translatable("message.soulbrand.cleanser.done")
                .formatted(Formatting.AQUA), false);
        return TypedActionResult.success(held);
    }

    /**
     * 净化对象 = 另一只手里的那件装备。
     *
     * <p>净魂粉必须拿在手里才能右键（{@code use} 只会对拿着它的那只手触发），
     * 所以「被净化的装备」永远在另一只手上：粉在副手看主手，粉在主手看副手。
     * 另一只手空着或也是粉 → 返回 null，走「无副作用、不消耗」的提示分支。</p>
     *
     * <p>注：不要试图把「拿粉那只手」也纳入候选 —— 那格里放的就是粉本身，
     * 一定会被 isEligible 排除，写进去只是死代码。</p>
     */
    private static ItemStack pickTarget(PlayerEntity player, Hand hand) {
        Hand other = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
        ItemStack stack = player.getStackInHand(other);
        return isEligible(stack) ? stack : null;
    }

    /** 是装备且不是净魂粉本身。 */
    private static boolean isEligible(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof SoulCleanserItem);
    }
}
