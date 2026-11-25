package com.example.soulbrand.logic;

import java.util.List;

import com.example.soulbrand.SoulConfig;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * tooltip 自绘（补充行）。
 *
 * <p><b>「附魂 II」这一行已经不由这里负责了</b> —— 等级现在写进物品的 {@code Enchantments}
 * 列表（见 {@link SoulNbt#syncEnchantmentLevel}），由原版附魔渲染路径输出。
 * 好处是 JEI、玉（Jade）、装备面板这些自己读附魔列表的界面也能看到；
 * 代价是这里再打一遍就会重复，所以只保留原版给不了的信息。</p>
 *
 * <p>剩下的行：</p>
 * <ol>
 *   <li><b>魂值 N</b> —— 具体数值，附魔行只有等级</li>
 *   <li><b>攻击力 +X · 破甲 +Y</b>（武器）或 <b>防护力 +Z</b>（护甲）</li>
 *   <li><b>距下一级还差 M 魂值</b></li>
 * </ol>
 *
 * <p>红线不变：禁止 NBT 深拷贝、禁止字符串循环。读法固定为
 * 「getNbt → contains → getCompound → getInt」。
 * {@code String.format} 每行一次、总量固定 3 次，不是循环。</p>
 */
public final class SoulTooltip {

    private SoulTooltip() {}

    /** 由 {@code getTooltip} 的 mixin 调用；只有已刻印物品才加行。 */
    public static void append(ItemStack stack, PlayerEntity player, List<Text> tooltip, TooltipContext context) {
        if (!SoulNbt.isBranded(stack)) {
            return;
        }
        int soul = SoulNbt.getSoul(stack);
        int level = SoulRules.level(soul);

        // 老存档兼容：用旧版本（等级不写附魔列表）刻印的物品，附魔行里不会有「附魂」。
        // 那种情况下由我们补打一行等级，避免出现「有魂值但没有附魂标题」的割裂显示。
        // 一旦这件装备再击杀一次，SoulNbt.setSoul 就会把附魔条目补上，此后不再走这个分支。
        if (!SoulNbt.hasSoulBrandEnchantment(stack)) {
            tooltip.add(Text.translatable("tooltip.soulbrand.branded", SoulRules.roman(level))
                    .formatted(Formatting.AQUA));
        }

        tooltip.add(Text.translatable("tooltip.soulbrand.soul", soul)
                .formatted(Formatting.GRAY));

        // 加成行按物品类型分开：护甲穿上后攻击力与破甲根本不生效（那两项只从主手读），
        // 给护甲显示它们会误导玩家。
        if (stack.getItem() instanceof ArmorItem) {
            double armor = level * SoulRules.ARMOR_PER_LEVEL * SoulConfig.armorBonusMultiplier();
            tooltip.add(Text.translatable("tooltip.soulbrand.armor", String.format("%.1f", armor))
                    .formatted(Formatting.DARK_AQUA));
        } else {
            double attack = SoulRules.levelAttackPower(level) * SoulConfig.attackPowerMultiplier();
            double pen = SoulRules.levelPenetration(level) * SoulConfig.penetrationMultiplier();
            tooltip.add(Text.translatable("tooltip.soulbrand.bonus",
                            String.format("%.1f", attack), String.format("%.1f", pen))
                    .formatted(Formatting.DARK_AQUA));
        }

        int toNext = SoulRules.SOUL_PER_LEVEL - (soul % SoulRules.SOUL_PER_LEVEL);
        tooltip.add(Text.translatable("tooltip.soulbrand.next", toNext)
                .formatted(Formatting.DARK_GRAY));
    }
}
