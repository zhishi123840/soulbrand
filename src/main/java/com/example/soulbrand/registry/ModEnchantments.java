package com.example.soulbrand.registry;

import com.example.soulbrand.Soulbrand;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;

/**
 * 附魂附魔。
 *
 * <p>与早期版本的区别：现在它<b>不只是标记位</b>，而是真正写进物品的 {@code Enchantments} 列表，
 * 等级 = 由魂值算出的附魂等级。这样走的是原版附魔的渲染路径，于是：</p>
 * <ul>
 *   <li>原版 tooltip 会像显示「锋利 V」那样显示「附魂 II」</li>
 *   <li>JEI、玉（Jade）、各类装备/图鉴面板这些<b>自己读附魔列表</b>的 mod 也能看到</li>
 * </ul>
 *
 * <p>之前只靠自定义 tooltip 行，导致那些绕过 {@code ItemStack#getTooltip} 的面板看不到附魂信息。</p>
 *
 * <h2>唯一真值仍然是 SoulValue</h2>
 * <p>写进 Enchantments 的等级只是 SoulValue 的<b>派生副本</b>，每次魂值变化都由
 * {@link com.example.soulbrand.logic.SoulNbt#syncEnchantmentLevel} 覆写。
 * 所有玩法计算（攻击力/破甲/防护力）读的仍是 SoulValue，不读这里。</p>
 *
 * <h2>等级显示为什么要自己实现</h2>
 * <p>原版 {@code Enchantment.getName} 走的是 {@code enchantment.level.N} 翻译键，
 * 而原版只定义到 X（10）。魂值无上限意味着等级会远超 10，届时会显示成
 * {@code enchantment.level.11} 这样的裸键名。所以这里覆写 {@code getName}，
 * 10 级以内用原版罗马数字，超出后直接显示阿拉伯数字。</p>
 */
public final class ModEnchantments {

    public static final Enchantment SOUL_BRAND = new SoulBrandEnchantment();

    private ModEnchantments() {}

    public static void register() {
        Registry.register(Registries.ENCHANTMENT,
                new Identifier(Soulbrand.MOD_ID, "soul_brand"), SOUL_BRAND);
    }

    /**
     * 注册层面的等级上限，<b>必须保持小值</b>。
     *
     * <p>原因：JEI 的铁砧配方模块会枚举「每个附魔 × 它的每一级」来生成合成表。
     * 把它设成 32767 会让 JEI 在 {@code Registering recipes: jei:minecraft} 阶段
     * 对这一个附魔空转三万多次循环，实测直接把游戏卡在加载界面（15 秒以上无进展）。</p>
     *
     * <p><b>这不影响实际显示等级。</b>tooltip 渲染附魔走的是
     * {@code ItemStack.appendEnchantments}：从 NBT 读 {@code lvl}，再<b>虚调用</b>
     * {@link Enchantment#getName(int)}。等级来自 NBT，不受本方法限制；
     * 显示格式由我们覆写的 getName 决定，任意等级都能正确显示。</p>
     */
    public static final int REGISTERED_MAX_LEVEL = 5;

    /** 原版罗马数字表，超出后改用阿拉伯数字。 */
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    /** 只作标记位：不能在附魔台获得、不可发现、不可交易、不可上书。 */
    public static final class SoulBrandEnchantment extends Enchantment {

        private SoulBrandEnchantment() {
            super(Rarity.VERY_RARE, EnchantmentTarget.BREAKABLE, new EquipmentSlot[] {
                    EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
            });
        }

        @Override
        public int getMaxLevel() {
            return REGISTERED_MAX_LEVEL;
        }

        @Override
        public int getMinLevel() {
            return 1;
        }

        /**
         * 自己生成「附魂 II」这样的名字，不复用原版 {@code enchantment.level.N} 翻译键
         * （原版只到 X，我们的等级无上限）。
         */
        /**
         * 生成「附魂 II」这样的名字，不复用原版 {@code enchantment.level.N} 翻译键
         * （原版只到 X，我们的等级无上限）。
         *
         * <p><b>1 级也要带「I」。</b>原版 {@code getName} 的条件是
         * {@code level == 1 && getMaxLevel() == 1} —— 只有单级附魔（如「修补」）才省略数字。
         * 锋利 1 级照样显示「锋利 I」。附魂是多级附魔，省掉数字会让人以为它没有等级，
         * 所以这里无条件带上。</p>
         */
        @Override
        public Text getName(int level) {
            net.minecraft.text.MutableText base = Text.translatable(getTranslationKey())
                    .formatted(Formatting.GRAY);
            String numeral = level < ROMAN.length ? ROMAN[level] : Integer.toString(level);
            return base.append(ScreenTexts.SPACE).append(Text.literal(numeral));
        }

        /**
         * 不参与任何原版计算 —— 攻击力/减伤全部为 0，避免被别的系统误用。
         *
         * <p>这一条很关键：现在等级真的写进了 {@code Enchantments}，如果这里返回非 0，
         * 原版 {@code EnchantmentHelper.getAttackDamage} 会把附魂也算进基础伤害，
         * 和我们的攻击力加成重复叠加。</p>
         */
        @Override
        public float getAttackDamage(int level, net.minecraft.entity.EntityGroup group) {
            return 0.0F;
        }

        @Override
        public int getProtectionAmount(int level, net.minecraft.entity.damage.DamageSource source) {
            return 0;
        }

        /**
         * 不进附魔台、不出附魔书、不参与随机选取。
         *
         * <p>注意：{@code isTradeable} / {@code isAllowedOnBooks} / {@code canApplyAtEnchantingTable}
         * 是 Forge 的 API，yarn 的 {@code Enchantment} 里没有这几个方法。
         * Fabric 侧的等价控制就是下面这两个 + {@link #isTreasure()}。</p>
         */
        @Override
        public boolean isAvailableForEnchantedBookOffer() {
            return false;
        }

        @Override
        public boolean isAvailableForRandomSelection() {
            return false;
        }

        @Override
        public boolean isCursed() {
            return false;
        }

        @Override
        public boolean isTreasure() {
            return false;
        }
    }
}
