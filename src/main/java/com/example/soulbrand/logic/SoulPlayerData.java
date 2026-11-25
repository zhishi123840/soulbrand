package com.example.soulbrand.logic;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;

/**
 * 玩家侧持久状态。1.0 去掉反噬之后，这里只剩一件事：<b>连杀计时</b>。
 *
 * <p>结构：{@code {SoulbrandPlayer:{V:1, LastKillTick:long, ComboCount:int}}}，随玩家存档走。
 * 缺字段按默认值补，版本整数留着以后迁移。</p>
 *
 * <p>已经清掉的东西（连同它们带来的所有坑）：</p>
 * <ul>
 *   <li>{@code CurseLevel} —— 不再扣最大生命，玩家属性完全归原版管</li>
 *   <li>固定 UUID 的 {@code EntityAttributeModifier} —— 不再需要幂等校正函数，
 *       也就没有「先 remove 再 add」「配置调低要把血还回去」这一整套复杂度</li>
 *   <li>{@code syncMaxHealth} 及其 4 个调用入口（Clone / LoggedIn / 装备变更 / 击杀）</li>
 * </ul>
 *
 * <p>旧存档里残留的 {@code CurseLevel} 字段会被忽略：本项目已经没有任何代码读它，
 * 也不会再往玩家身上挂扣血修饰符 —— 所以升级后玩家的最大生命是干净的 20。</p>
 */
public final class SoulPlayerData {

    public static final String ROOT_KEY = "SoulbrandPlayer";
    public static final int DATA_VERSION = 1;

    public static final String KEY_VERSION = "V";
    public static final String KEY_LAST_KILL = "LastKillTick";
    public static final String KEY_COMBO = "ComboCount";

    private static final int TAG_COMPOUND = 10;

    private SoulPlayerData() {}

    // ---------------------------------------------------------------- NBT

    /** 只读访问：缺 compound 就返回一个空标签，不往玩家 NBT 里写任何东西。 */
    private static NbtCompound read(PlayerEntity player) {
        NbtCompound root = ((SoulDataHolder) player).soulbrand$getPersistentData();
        if (!root.contains(ROOT_KEY, TAG_COMPOUND)) {
            return new NbtCompound();
        }
        return root.getCompound(ROOT_KEY);
    }

    /** 写路径：取现有 compound（没有就建）。 */
    private static NbtCompound write(PlayerEntity player) {
        NbtCompound root = ((SoulDataHolder) player).soulbrand$getPersistentData();
        if (!root.contains(ROOT_KEY, TAG_COMPOUND)) {
            root.put(ROOT_KEY, new NbtCompound());
        }
        return root.getCompound(ROOT_KEY);
    }

    /**
     * 死亡重生 / 跨维度：把连杀计时从旧 player 显式拷到新的。
     *
     * <p>不拷的话，重生或下界传送会把连杀记录清零 —— 玩家上一秒刚攒起来的 +4 加成
     * 会因为换了个世界而消失，观感上像是 bug。</p>
     */
    public static void copyFrom(PlayerEntity original, PlayerEntity clone) {
        NbtCompound src = read(original);
        NbtCompound dst = new NbtCompound();
        dst.putInt(KEY_VERSION, DATA_VERSION);
        dst.putLong(KEY_LAST_KILL, src.getLong(KEY_LAST_KILL));
        dst.putInt(KEY_COMBO, Math.max(0, src.getInt(KEY_COMBO)));
        // 生命魂晶的加成也必须跟着走：不拷的话死亡重生后白吃，玩家会认为丢东西。
        dst.putInt(SoulHeart.KEY_HEART_BONUS,
                Math.max(0, Math.min(SoulHeart.MAX_BONUS, src.getInt(SoulHeart.KEY_HEART_BONUS))));
        ((SoulDataHolder) clone).soulbrand$getPersistentData().put(ROOT_KEY, dst);
    }

    // -------------------------------------------------------------- 连杀

    /** 连杀计数：距上次击杀 ≤ 100 tick 时 +1，最多到 +4；超时清零。 */
    public static int bumpCombo(PlayerEntity player, long gameTime) {
        NbtCompound tag = write(player);
        long last = tag.getLong(KEY_LAST_KILL);
        int combo = Math.max(0, tag.getInt(KEY_COMBO));

        boolean inWindow = last > 0L && gameTime >= last
                && (gameTime - last) <= SoulRules.COMBO_WINDOW_TICKS;
        combo = inWindow ? Math.min(SoulRules.COMBO_MAX, combo + 1) : 0;

        tag.putInt(KEY_VERSION, DATA_VERSION);
        tag.putInt(KEY_COMBO, combo);
        tag.putLong(KEY_LAST_KILL, gameTime);
        return combo;
    }
}
