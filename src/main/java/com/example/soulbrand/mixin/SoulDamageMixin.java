package com.example.soulbrand.mixin;

import com.example.soulbrand.event.SoulEvents;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 破甲追加：Fabric 没有 Forge 的 {@code LivingDamageEvent}，这里钉在它的等价点上。
 *
 * <h2>为什么需要两个注入点</h2>
 * <p>破甲要满足两件互相冲突的事：</p>
 * <ol>
 *   <li><b>加在护甲之后</b> —— 否则会被护甲削掉，等于没破甲</li>
 *   <li><b>截断基数用护甲之前</b> —— 否则打重甲目标时残值只剩 1~2 点，
 *       3 倍截断会把破甲压到几乎没有，与「无视护甲」自相矛盾</li>
 * </ol>
 *
 * <p>单个注入点拿不到这两个值。反编译确认过：{@code @ModifyExpressionValue} 在
 * {@code modifyAppliedDamage} 之后能拿到其返回值（护甲后），但此时方法参数
 * {@code amount} 的局部变量槽<b>已被写回成护甲后的值</b>，捕获不到原始伤害。</p>
 *
 * <p>所以拆成两步：</p>
 * <ul>
 *   <li>{@code @Inject at HEAD} —— 把护甲前的原始伤害存进 {@link #soulbrand$preArmorDamage}</li>
 *   <li>{@code @ModifyExpressionValue} —— 用存下来的值当截断基数，把破甲加到护甲后的结果上</li>
 * </ul>
 *
 * <p>{@code @Unique} 字段挂在 LivingEntity 实例上，每次伤害都会先写后读，
 * 所以不存在读到上一次残留值的问题。多线程下每个实体是独立对象，也不会串。</p>
 */
@Mixin(LivingEntity.class)
public abstract class SoulDamageMixin {

    /** 本次伤害在护甲减免前的原始值，只在 applyDamage 一次调用内有效。 */
    @Unique
    private float soulbrand$preArmorDamage;

    @Inject(method = "applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V", at = @At("HEAD"))
    private void soulbrand$capturePreArmorDamage(DamageSource source, float amount, CallbackInfo ci) {
        this.soulbrand$preArmorDamage = amount;
    }

    @ModifyExpressionValue(
            method = "applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;modifyAppliedDamage(Lnet/minecraft/entity/damage/DamageSource;F)F")
    )
    private float soulbrand$addPenetrationDamage(float postArmor, DamageSource source) {
        return SoulEvents.onApplyDamage((LivingEntity) (Object) this, source,
                postArmor, this.soulbrand$preArmorDamage);
    }
}
