package com.example.soulbrand.logic;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;

/**
 * 纯函数区：等级、攻击力、破甲、掉落概率、硬怪判定。
 *
 * <p>这里没有任何状态、没有任何 NBT 操作，方便对照数值表逐条核对。</p>
 *
 * <h2>等级与伤害：无上限，线性累加</h2>
 *
 * <p>魂值每满 {@value #SOUL_PER_LEVEL} 点升一级，<b>没有封顶</b>。第 N 级的加成是：</p>
 * <pre>
 *   攻击力 = 1.0 × N      （护甲前追加，会被护甲削）
 *   破甲   = 1.5 × N      （护甲后追加，无视护甲）
 * </pre>
 *
 * <p>所以魂值 0-99 = I 级、100-199 = II 级、200-299 = III 级、300-399 = IV 级……
 * 杀得越多魂值越高，伤害一路线性往上。</p>
 *
 * <p><b>为什么用乘法而不是查表：</b>等级无上限时查表写不完。原来 I/II/III 三档的
 * 1.5 / 3.0 / 4.5 与现在的公式完全吻合（1×1.5、2×1.5、3×1.5），所以老存档的
 * 魂值含义不变，只是继续往上还能再涨。</p>
 */
public final class SoulRules {

    /** 每多少魂值升一级。 */
    public static final int SOUL_PER_LEVEL = 100;

    /** 每级攻击力加成（护甲前，和锋利同性质）。 */
    public static final double ATK_PER_LEVEL = 1.0D;

    /** 每级破甲加成（护甲后，无视护甲）。 */
    public static final double PEN_PER_LEVEL = 1.5D;

    /**
     * 每级护甲防护力加成（单件）。
     *
     * <p>取 0.5：一件钻甲 3 级（III）就是 +1.5 护甲值，四件齐全相当于多穿半套。
     * 定得太高会让护甲成长盖过武器成长，太低则玩家感知不到。</p>
     */
    public static final double ARMOR_PER_LEVEL = 0.5D;

    /** 追加破甲截断：不超过原伤害的 3 倍。 */
    public static final float PEN_CAP_RATIO = 3.0F;

    // 连杀窗口 100 tick，加成最多叠到 +4
    public static final long COMBO_WINDOW_TICKS = 100L;
    public static final int COMBO_MAX = 4;

    // 魂值增量
    public static final int SOUL_GAIN_NORMAL = 2;
    public static final int SOUL_GAIN_HARD = 5;

    private SoulRules() {}

    /**
     * 等级 = 纯函数 f(SoulValue)，无上限。
     *
     * <p>魂值 0-99 → 1 级（I）、100-199 → 2 级（II）、200-299 → 3 级（III）、往后继续往上。</p>
     */
    public static int level(int soul) {
        if (soul <= 0) {
            return 1;
        }
        return soul / SOUL_PER_LEVEL + 1;
    }

    /** 等级对应的攻击力加成（未乘配置倍率）。 */
    public static double levelAttackPower(int level) {
        return ATK_PER_LEVEL * level;
    }

    /** 等级对应的破甲加成（未乘配置倍率）。 */
    public static double levelPenetration(int level) {
        return PEN_PER_LEVEL * level;
    }

    /**
     * 罗马数字展示。
     *
     * <p>等级现在无上限，所以不能只认 I/II/III。常见的 1..20 用标准罗马数字，
     * 超出范围直接回阿拉伯数字 —— 反正这里只是给你看个阶数，不参与任何计算。</p>
     */
    public static String roman(int level) {
        if (level <= 0) {
            return "0";
        }
        String[] table = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
                "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};
        if (level < table.length) {
            return table[level];
        }
        return Integer.toString(level);
    }

    /**
     * 硬怪：属于 MONSTER（Monster 接口）、非被动，且不属于僵尸 / 骷髅 / 蜘蛛 / 苦力怕四类。
     * 凋零骷髅属于骷髅一类，因此按普通怪计（它的碎晶掉率是 100%，走另一条线）。
     */
    public static boolean isHardMob(LivingEntity entity) {
        if (!(entity instanceof Monster)) {
            return false;
        }
        return !(entity instanceof ZombieEntity)
                && !(entity instanceof AbstractSkeletonEntity)
                && !(entity instanceof SpiderEntity)
                && !(entity instanceof CreeperEntity);
    }

    public static int soulGainBase(LivingEntity entity) {
        return isHardMob(entity) ? SOUL_GAIN_HARD : SOUL_GAIN_NORMAL;
    }

    /** 碎晶基础掉率（一次 1 颗的基准概率）。不在表里的一律 0。 */
    public static double fragmentDropChance(LivingEntity entity) {        if (entity instanceof WitherSkeletonEntity) {
            return 1.0D; // 100%
        }
        if (entity instanceof CreeperEntity) {
            return 0.5D;
        }
        if (entity instanceof EndermanEntity) {
            return 0.5D;
        }
        if (entity instanceof ZombieEntity) {
            return 0.25D;
        }
        if (entity instanceof AbstractSkeletonEntity) {
            return 0.25D;
        }
        if (entity instanceof SpiderEntity) {
            return 0.25D;
        }
        return 0.0D;
    }

    /** 掉落率倍率语义：期望 = 基础概率 × 倍率，整数部分必掉，小数部分再 roll 一次。 */
    public static int rollFragmentCount(double baseChance, double multiplier, double roll) {
        double expected = baseChance * multiplier;
        if (expected <= 0.0D) {
            return 0;
        }
        int guaranteed = (int) Math.floor(expected);
        double fraction = expected - guaranteed;
        return guaranteed + (roll < fraction ? 1 : 0);
    }

    /**
     * 抢夺加成：每级 +1 颗保底。
     *
     * <p>语义参照原版对稀有掉落物的处理 —— 抢夺不是提高概率，而是<b>额外多掉一份</b>。
     * 这里也照做：先在「基础概率 × 倍率」上算出正常掉落，再直接叠加抢夺等级。</p>
     *
     * <p>所以抢夺 III 杀僵尸 = 正常 25% 那颗（可能 0 颗）+ 保底 3 颗 = 至少 3 颗；
     * 抢夺 III 杀凋零骷髅 = 1 颗必掉 + 3 颗 = 4 颗。</p>
     */
    public static int lootingBonus(int lootingLevel) {
        return Math.max(0, lootingLevel);
    }

    /**
     * 破甲追加值（护甲后，无视护甲）。
     *
     * <p>截断仍是「不超过原伤害的 3 倍」—— 不看这条，魂值堆到几千时会一击秒掉任何东西，
     * 连凋灵都留不住。基数用的是<b>护甲减免之前</b>的原始伤害（调用方传 preArmor），
     * 所以护甲厚的目标不会因为「postArmor 很小 → cap 很小」而吃不到破甲，
     * 这条防的是「破甲把结果炸穿」，不是防「玩家变强」。</p>
     */
    public static float penetrationDamage(int soul, double multiplier, float originalDamage) {
        if (originalDamage <= 0.0F) {
            return 0.0F;
        }
        double raw = levelPenetration(level(soul)) * multiplier;
        if (raw <= 0.0D) {
            return 0.0F;
        }
        float cap = originalDamage * PEN_CAP_RATIO;
        return (float) Math.min(raw, cap);
    }

    /**
     * 攻击力加成值（护甲前追加，和锋利同性质）。
     *
     * <p>不做截断 —— 这个值随后要经过护甲减免，本来就打折。截断只用在破甲那边。</p>
     */
    public static float attackPowerBonus(int soul, double multiplier) {
        double raw = levelAttackPower(level(soul)) * multiplier;
        return raw <= 0.0D ? 0.0F : (float) raw;
    }
}
