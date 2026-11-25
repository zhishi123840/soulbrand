package com.example.soulbrand.mixin;

import com.example.soulbrand.event.SoulEvents;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 攻击力加成：附魂等级提供的额外攻击伤害，<b>吃护甲减免</b>，和锋利同一性质。
 *
 * <p>注入点在 {@code applyDamage} 的 <b>HEAD</b>，也就是 {@code applyArmorToDamage} 之前。
 * 反编译确认的方法体顺序：</p>
 * <pre>
 *   HEAD  ← 本 mixin 在这里加攻击力（护甲前）
 *    9: applyArmorToDamage(...)      ← 护甲减免，会削掉一部分
 *   16: modifyAppliedDamage(...)     ← 抗性减免
 *   22: SoulDamageMixin 在这里加破甲（护甲后，不会被削）
 * </pre>
 *
 * <p>两个 mixin 的分工：</p>
 * <ul>
 *   <li>{@link SoulDamageMixin} —— 破甲，护甲<b>之后</b>加，无视护甲</li>
 *   <li>本类（{@code SoulAttackMixin}）—— 攻击力，护甲<b>之前</b>加，会被护甲削</li>
 * </ul>
 *
 * <p>为什么不用 {@code @ModifyExpressionValue} 挂在 {@code PlayerEntity.attack} 里：
 * 那里是原版锋利加成的计算处，混进去容易和别的战斗模组冲突；而
 * {@code applyDamage} 的 HEAD 是所有伤害的统一入口，两个注入点一前一后语义互不干扰。</p>
 */
@Mixin(LivingEntity.class)
public abstract class SoulAttackMixin {

    @ModifyVariable(
            method = "applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private float soulbrand$addAttackPower(float amount, DamageSource source) {
        return SoulEvents.onIncomingDamage((LivingEntity) (Object) this, source, amount);
    }
}
