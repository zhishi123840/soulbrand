package com.example.soulbrand.logic;

import java.util.UUID;

import com.example.soulbrand.SoulConfig;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * 护甲附魂的「防护力」加成。
 *
 * <p>四件护甲各自独立记魂值（存在物品 NBT 里，与武器同一套结构），玩家身上只汇总
 * <b>当前穿着的、已刻印的</b>护甲所对应的防护值，用一个固定 UUID 的修饰符加到
 * {@code GENERIC_ARMOR} 上。</p>
 *
 * <h2>为什么用属性修饰符而不是直接改物品</h2>
 * <p>护甲值来自物品本身（{@code ArmorItem} 的槽位属性），物品侧不提供「可变的护甲值」。
 * 唯一能动态改的手段就是给玩家加一个属性修饰符。用固定 UUID + 先 remove 再 add，
 * 保证幂等 —— 和之前反噬那套结构相同，只是这次是<b>加</b>而不是减。</p>
 *
 * <h2>刷新时机</h2>
 * <p>任何会让「穿着哪些护甲」或「护甲魂值」变化的时候都要重算：</p>
 * <ul>
 *   <li>击杀涨魂之后（魂值变了）</li>
 *   <li>玩家登录、重生、换维度之后（修饰符不会自动跟着走）</li>
 *   <li>换装备时（穿过/脱下某件）</li>
 * </ul>
 *
 * <h2>性能</h2>
 * <p>{@link #refresh} 只读 4 个护甲槽 + 每个一次 NBT 读取，没有背包遍历、没有实体遍历、
 * 没有 ticker。击杀路径上调用一次完全可接受。</p>
 */
public final class SoulArmor {

    /** 固定 UUID：所有护甲的防护力共用这一个修饰符，remove + add 才不会叠加。 */
    public static final UUID ARMOR_MODIFIER_ID =
            UUID.fromString("a7c41e0b-5d92-4f38-8c16-2e9b7a34d5f1");
    private static final String ARMOR_MODIFIER_NAME = "soulbrand:armor_bonus";

    /** 参与统计的护甲槽，顺序固定（头盔→胸甲→护腿→靴子）。 */
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private SoulArmor() {}

    /**
     * 汇总玩家身上所有「已刻印护甲」提供的防护力。
     *
     * <p>单件护甲提供的防护值 = 该装备的等级 × {@link SoulRules#ARMOR_PER_LEVEL}。
     * 四件叠加（比如四件都是 III 级 = 4 × 3 × 0.5 = 6 点护甲）。</p>
     */
    public static double computeArmorBonus(PlayerEntity player) {
        double total = 0.0D;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getEquippedStack(slot);
            if (stack.isEmpty() || !SoulNbt.isBranded(stack)) {
                continue;
            }
            int level = SoulRules.level(SoulNbt.getSoul(stack));
            total += level * SoulRules.ARMOR_PER_LEVEL;
        }
        return total * SoulConfig.armorBonusMultiplier();
    }

    /**
     * 幂等刷新：抹掉旧修饰符，再按当前穿着重算加回去。
     *
     * <p>任何入口调用多少次结果都一样，不会叠加。</p>
     */
    public static void refresh(PlayerEntity player) {
        if (player == null || player.getWorld().isClient) {
            return;
        }
        EntityAttributeInstance armorAttr = player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        if (armorAttr == null) {
            return;
        }

        armorAttr.removeModifier(ARMOR_MODIFIER_ID);

        double bonus = computeArmorBonus(player);
        if (bonus > 0.0D) {
            armorAttr.addPersistentModifier(new EntityAttributeModifier(
                    ARMOR_MODIFIER_ID,
                    ARMOR_MODIFIER_NAME,
                    bonus,
                    EntityAttributeModifier.Operation.ADDITION));
        }
    }
}
