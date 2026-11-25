package com.example.soulbrand.logic;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;

/**
 * 击杀归属：只认「玩家本人」与「玩家射出的投射物」。
 *
 * <p>宠物（狼/猫）、铁傀儡、其它模组的 AoE 代打一律不算 —— 判据是<b>直接造成伤害的实体</b>，
 * 只有它是玩家、或是 owner 链上溯到玩家的投射物时才放行。</p>
 */
public final class SoulAttribution {

    private SoulAttribution() {}

    /** 从伤害来源解析出「操作这件击杀的玩家」。 */
    public static PlayerEntity resolvePlayer(Entity sourceEntity, Entity directEntity) {
        // 1) 投射物：owner 链上溯
        if (directEntity instanceof ProjectileEntity projectile) {
            Entity owner = projectile.getOwner();
            if (owner instanceof PlayerEntity player) {
                return player;
            }
            return null; // 宠物射出的箭、发射器射出的箭 → 不算
        }

        // 2) 近战：直接就是玩家
        if (sourceEntity instanceof PlayerEntity player) {
            return player;
        }

        // 3) 少数路径下玩家出现在 directEntity 上
        if (directEntity instanceof PlayerEntity player) {
            return player;
        }

        return null;
    }

    /**
     * 便捷重载：直接吃 DamageSource。
     *
     * <p>yarn 的 DamageSource 只有两个实体入口：</p>
     * <ul>
     *   <li>{@code getAttacker()} —— 归属者（弓箭手、施法者、火球的发射者），用于「谁算击杀」；</li>
     *   <li>{@code getSource()} —— 直接来源（箭实体本身）。</li>
     * </ul>
     */
    public static PlayerEntity resolvePlayer(DamageSource source) {
        return resolvePlayer(source.getAttacker(), source.getSource());
    }

    /** 击杀者必须是玩家；目标不能是自己。 */
    public static PlayerEntity validKiller(DamageSource source, LivingEntity victim) {
        PlayerEntity player = resolvePlayer(source);
        if (player == null || victim == player) {
            return null;
        }
        return player;
    }

    /**
     * 伤害来源是不是「玩家自己的攻击」——近战挥击<b>或</b>玩家射出的投射物。
     *
     * <p><b>为什么把投射物放进来</b>：用户要求「弓射出的箭伤害和近战一样算」。
     * 早期版本一律排除投射物，导致弓刻了附魂却没有加成。</p>
     *
     * <p><b>为什么不是空泛的「所有投射物」</b>：{@link #resolvePlayer(DamageSource)}
     * 已经把 owner 不是玩家的排除掉了（发射器、骷髅、宠物射出的箭都不算），
     * 两者合起来就是「玩家本人用自己手上的东西打出的伤害」。</p>
     */
    public static boolean isPlayerStrike(DamageSource source) {
        // 近战：直接来源就是玩家
        if (source.getSource() instanceof PlayerEntity) {
            return true;
        }
        // 非玩家的其它实体（宠物、铁傀儡、AoE）→ 不算
        if (source.getSource() != null) {
            // 投射物：owner 必须是玩家
            if (source.getSource() instanceof ProjectileEntity) {
                return source.getAttacker() instanceof PlayerEntity;
            }
            return false;
        }
        // 无直接来源（环境伤害、魔法）→ 不算
        return source.getAttacker() instanceof PlayerEntity;
    }
}
