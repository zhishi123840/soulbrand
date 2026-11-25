package com.example.soulbrand.logic;

import com.example.soulbrand.registry.ModEnchantments;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * 装备侧存档（物品 NBT）。
 *
 * <p>结构：
 * <pre>
 *   {
 *     Soulbrand: { V: 1, Branded: 1b, SoulValue: int },   ← 唯一真值
 *     Enchantments: [ { id: "soulbrand:soul_brand", lvl: short } ]  ← 派生副本
 *   }
 * </pre>
 * </p>
 *
 * <p><b>为什么两处都要写：</b>等级写进 {@code Enchantments} 后，走的是原版附魔渲染路径，
 * 于是 JEI、玉（Jade）、装备面板这些<b>自己读附魔列表</b>的 mod 也能显示「附魂 II」。
 * 之前只靠自定义 tooltip 行，那些绕过 {@code ItemStack#getTooltip} 的界面就什么都看不到。</p>
 *
 * <p><b>真值仍是 SoulValue。</b>所有玩法计算（攻击力/破甲/防护力）读 SoulValue，
 * {@code Enchantments} 里的等级每次魂值变化都重新覆写，不可能不同步。</p>
 *
 * <p><b>为什么不用 {@code ItemStack.addEnchantment}：</b>反编译确认它把等级
 * {@code i2b} 转成 <b>byte</b> 存进 NBT。附魂等级无上限，超过 127 会溢出成负数。
 * 这里自己写 short（原版 {@code EnchantmentHelper.createNbt} 也是 {@code i2s}）。</p>
 *
 * <p>性能：所有读取都只做一次 getCompound + getInt，没有 NBT 深拷贝，也没有字符串循环。</p>
 */
public final class SoulNbt {

    public static final String ROOT_KEY = "Soulbrand";
    public static final int DATA_VERSION = 1;

    public static final String KEY_VERSION = "V";
    public static final String KEY_BRANDED = "Branded";
    public static final String KEY_SOUL = "SoulValue";

    public static final int SOUL_MIN = 0;

    /**
     * 魂值软上限：只防 NBT 被写坏（int 溢出 / 手改存档写进天文数字）。
     *
     * <p>这不是玩法上限 —— 正常游玩根本涨不到这里。</p>
     */
    public static final int SOUL_SAFE_CAP = Integer.MAX_VALUE / 2;

    private static final int TAG_COMPOUND = 10;

    private static final String ENCHANTMENTS_KEY = "Enchantments";
    private static final String FIELD_ID = "id";
    private static final String FIELD_LVL = "lvl";

    private SoulNbt() {}

    // ---------------------------------------------------------------- 读取

    /** 是否已刻印。未刻印一律 false，不创建任何 NBT。 */
    public static boolean isBranded(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasNbt()) {
            return false;
        }
        NbtCompound root = stack.getNbt();
        if (root == null || !root.contains(ROOT_KEY, TAG_COMPOUND)) {
            return false;
        }
        return root.getCompound(ROOT_KEY).getBoolean(KEY_BRANDED);
    }

    /** 魂值。未刻印/缺字段一律 0。没有玩法上限，只夹掉负数。 */
    public static int getSoul(ItemStack stack) {
        if (!isBranded(stack)) {
            return SOUL_MIN;
        }
        NbtCompound data = stack.getNbt().getCompound(ROOT_KEY);
        int value = data.getInt(KEY_SOUL);
        return Math.max(SOUL_MIN, Math.min(value, SOUL_SAFE_CAP));
    }

    // ---------------------------------------------------------------- 写入

    /**
     * 写回魂值，并同步附魔列表里的等级。
     *
     * <p>调用者必须已经确认 isBranded()，否则会凭空给物品挂上魂值字段。</p>
     */
    public static void setSoul(ItemStack stack, int value) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int clamped = Math.max(SOUL_MIN, Math.min(value, SOUL_SAFE_CAP));
        NbtCompound root = stack.getOrCreateNbt();
        NbtCompound data = root.contains(ROOT_KEY, TAG_COMPOUND)
                ? root.getCompound(ROOT_KEY)
                : new NbtCompound();
        data.putInt(KEY_VERSION, DATA_VERSION);
        data.putBoolean(KEY_BRANDED, true);
        data.putInt(KEY_SOUL, clamped);
        root.put(ROOT_KEY, data);

        syncEnchantmentLevel(stack, SoulRules.level(clamped));
    }

    /** 刻印：写入版本 + 标记 + 0 魂值，并在附魔列表挂上 1 级附魂。 */
    public static void brand(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        NbtCompound root = stack.getOrCreateNbt();
        NbtCompound data = new NbtCompound();
        data.putInt(KEY_VERSION, DATA_VERSION);
        data.putBoolean(KEY_BRANDED, true);
        data.putInt(KEY_SOUL, SOUL_MIN);
        root.put(ROOT_KEY, data);

        syncEnchantmentLevel(stack, 1);
    }

    /** 净魂：魂值归零（附魔等级跟着回到 1），标记保留（刻印不可撤销）。 */
    public static void clearSoul(ItemStack stack) {
        setSoul(stack, SOUL_MIN);
    }

    /**
     * 附魔列表里是否已有附魂条目。
     *
     * <p>用途是老存档兼容：旧版本刻印的物品只有 {@code Soulbrand} compound、
     * 没有 {@code Enchantments} 条目，tooltip 需要据此决定是否自己补打一行等级。</p>
     *
     * <p>纯读，不写 NBT —— 这个方法会在客户端的 tooltip 渲染路径上调用，
     * 客户端绝不能修改物品数据。</p>
     */
    public static boolean hasSoulBrandEnchantment(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasNbt()) {
            return false;
        }
        Identifier id = Registries.ENCHANTMENT.getId(ModEnchantments.SOUL_BRAND);
        if (id == null) {
            return false;
        }
        NbtCompound root = stack.getNbt();
        if (!root.contains(ENCHANTMENTS_KEY, NbtElement.LIST_TYPE)) {
            return false;
        }
        NbtList list = root.getList(ENCHANTMENTS_KEY, NbtElement.COMPOUND_TYPE);
        String wanted = id.toString();
        for (int i = 0; i < list.size(); i++) {
            if (wanted.equals(list.getCompound(i).getString(FIELD_ID))) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------- 附魔等级派生同步

    /**
     * 把附魂等级写进 {@code Enchantments}，让原版与第三方界面都能显示。
     *
     * <p>只操作我们自己的那一条：已有的更新 lvl，没有就追加；其它 mod 的附魔条目一律不碰。</p>
     */
    public static void syncEnchantmentLevel(ItemStack stack, int level) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Identifier id = Registries.ENCHANTMENT.getId(ModEnchantments.SOUL_BRAND);
        if (id == null) {
            // 注册尚未完成（理论上不会发生，刻印一定在注册之后）。
            return;
        }

        NbtCompound root = stack.getOrCreateNbt();
        NbtList list = root.getList(ENCHANTMENTS_KEY, NbtElement.COMPOUND_TYPE);
        short lvl = (short) Math.max(1, Math.min(level, Short.MAX_VALUE));

        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            if (id.toString().equals(entry.getString(FIELD_ID))) {
                entry.putShort(FIELD_LVL, lvl);
                root.put(ENCHANTMENTS_KEY, list);
                return;
            }
        }

        list.add(EnchantmentHelper.createNbt(id, lvl));
        root.put(ENCHANTMENTS_KEY, list);
    }
}
