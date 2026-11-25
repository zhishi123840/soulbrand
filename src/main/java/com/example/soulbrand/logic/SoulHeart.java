package com.example.soulbrand.logic;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

/**
 * 生命魂晶的加成状态：每吃一颗 +2 点生命上限（1 颗心），总计最多 +40 点（20 颗心）。
 *
 * <p><b>为什么用属性修饰符而不是直接改血量</b>：
 * {@code getMaxHealth()} 是从 {@code GENERIC_MAX_HEALTH} 属性算出来的只读值。
 * 要永久提高只能给属性加修饰符 —— 它会随玩家 NBT 持久化，跨登录、跨维度、死亡重生都自动带着。</p>
 *
 * <p><b>固定 UUID + 先删后加</b>：同一 UUID 的修饰符重复 add 会叠加，
 * 所以每次施加前先 remove。这也让「读档后重算」变成幂等操作。</p>
 *
 * <p><b>最大生命 +40 的视觉含义</b>：原版 20 点 = 10 颗心，+40 点 = 多 20 颗心，
 * 血条会变成两排。这是设计选择，不是 bug。</p>
 */
public final class SoulHeart {

    /**
     * 每颗生命魂晶提供的生命上限（点）。
     *
     * <p><b>Minecraft 里 1 颗心 = 2 点生命。</b>用户要求「一次加一颗心」，
     * 所以这里是 2 而不是 10 —— 10 点会一次加 5 颗心（实测被指出来了）。</p>
     */
    public static final int HEARTS_PER_ITEM = 2;

    /**
     * 生命上限的总加成上限（点）。
     *
     * <p>40 点 = 20 颗心 = 20 颗魂晶。原版 20 点（10 心）+ 40 点 = 60 点（30 心），
     * 血条会变成两排多，这是设计上的「最多翻三倍」。</p>
     */
    public static final int MAX_BONUS = 40;

    /** 还差多少颗能吃满（用于提示）。 */
    public static int remainingItems(PlayerEntity player) {
        return Math.max(0, (MAX_BONUS - getBonus(player)) / HEARTS_PER_ITEM);
    }

    /** 玩家 NBT 里记录已生效加成的键。 */
    public static final String KEY_HEART_BONUS = "HeartBonus";

    /**
     * 固定 UUID：所有生命魂晶的加成共用这一个修饰符。
     * remove + add 才不会叠加，也不会和别的 mod 的修饰符撞车。
     */
    public static final UUID HEALTH_MODIFIER_ID =
            UUID.fromString("b8d52f1c-6e03-4a97-9d28-3f7c5b19e6a4");

    private SoulHeart() {}

    /** 当前已获得的生命上限加成（点）。 */
    public static int getBonus(PlayerEntity player) {
        NbtCompound root = ((SoulDataHolder) player).soulbrand$getPersistentData();
        NbtCompound tag = root.getCompound(SoulPlayerData.ROOT_KEY);
        int bonus = tag.getInt(KEY_HEART_BONUS);
        // 夹到合法区间：老存档 / 手改 NBT 都不能突破上限
        return Math.max(0, Math.min(MAX_BONUS, bonus));
    }

    /** 还能不能再吃一颗。 */
    public static boolean canIncrease(PlayerEntity player) {
        return getBonus(player) < MAX_BONUS;
    }

    /**
     * 吃一颗：计数 +2，施加修饰符，当前生命同步 +2。
     *
     * <p><b>为什么要同步当前生命</b>：只涨上限的话，血条会从「满」变成「未满」，
     * 玩家看到的是血变少了。所以加完上限顺手把当前值也加 2，补到与新上限的比例。
     */
    public static void apply(PlayerEntity player) {
        int next = Math.min(MAX_BONUS, getBonus(player) + HEARTS_PER_ITEM);

        NbtCompound root = ((SoulDataHolder) player).soulbrand$getPersistentData();
        if (!root.contains(SoulPlayerData.ROOT_KEY, 10)) {
            root.put(SoulPlayerData.ROOT_KEY, new NbtCompound());
        }
        root.getCompound(SoulPlayerData.ROOT_KEY).putInt(KEY_HEART_BONUS, next);

        refresh(player);

        // 当前生命 +10，但不越过新上限
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + HEARTS_PER_ITEM));
    }

    /**
     * 按 NBT 里的计数重建属性修饰符。
     *
     * <p>登录 / 重生 / 换维度 / 读档后都要调用一次：属性修饰符虽然持久化，
     * 但玩家实体重建时属性实例是新的，得重新挂上去。</p>
     */
    public static void refresh(PlayerEntity player) {
        if (player.getWorld().isClient) {
            return;
        }
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attr == null) {
            return;
        }
        // 先删再判：计数为 0 时也要删掉残留修饰符
        attr.removeModifier(HEALTH_MODIFIER_ID);

        int bonus = getBonus(player);
        if (bonus <= 0) {
            // 上限退回原版；当前生命可能超上限，夹一下
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
            return;
        }
        attr.addPersistentModifier(new EntityAttributeModifier(
                HEALTH_MODIFIER_ID,
                "soulbrand:heart_bonus",
                bonus,
                EntityAttributeModifier.Operation.ADDITION));
    }
}
