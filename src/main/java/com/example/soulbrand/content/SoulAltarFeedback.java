package com.example.soulbrand.content;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 刻印 / 净魂的反馈与经验扣除。
 *
 * <p>从方块里抽出来，让 {@link SoulAltarBlock}（右键路径已改为只开界面）和
 * {@link SoulAltarScreenHandler}（界面按钮）共用同一套提示文案与扣经验逻辑，
 * 避免两处实现漂移。</p>
 */
final class SoulAltarFeedback {

    private SoulAltarFeedback() {}

    static void fail(PlayerEntity player, String key) {
        player.sendMessage(Text.translatable(key).formatted(Formatting.RED), false);
    }

    static void failXp(PlayerEntity player, int xpCost) {
        player.sendMessage(
                Text.translatable("message.soulbrand.altar.no_xp", xpCost).formatted(Formatting.RED),
                false);
    }

    static void ok(PlayerEntity player, String key) {
        player.sendMessage(Text.translatable(key).formatted(Formatting.AQUA), false);
    }

    /**
     * 按经验点扣、不掉等级。
     *
     * <p>不能用 {@code player.addExperience(-n)}：反编译确认 experienceProgress 变负时
     * 它会调 {@code addExperienceLevels(-1)} 借一级，而 addExperienceLevels 在等级 &lt; 0 时
     * 把 level/progress/totalExperience <b>全部清零</b> —— 一次刻印清空玩家所有经验。</p>
     *
     * <p>这里直接扣 totalExperience，progress 按「当前级内点数」同步递减。</p>
     */
    static void deductXpPoints(PlayerEntity player, int xpCost) {
        if (xpCost <= 0) {
            return;
        }
        player.totalExperience = Math.max(0, player.totalExperience - xpCost);
        float progress = player.experienceProgress
                - (float) xpCost / (float) Math.max(1, player.getNextLevelExperience());
        player.experienceProgress = Math.max(0.0F, progress);
    }
}
