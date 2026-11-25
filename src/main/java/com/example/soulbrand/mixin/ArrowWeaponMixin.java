package com.example.soulbrand.mixin;

import com.example.soulbrand.logic.SoulProjectileWeapon;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在箭被创造出来的那一刻，记下「射出它用的武器」的魂值。
 *
 * <h2>为什么注入 ArrowItem.createArrow 而不是 ProjectileUtil.createArrowProjectile</h2>
 * <p>第一版注入的是 {@code ProjectileUtil.createArrowProjectile}，实测<b>完全没生效</b> ——
 * 反编译 {@code BowItem.onStoppedUsing} 确认弓走的是
 * {@code ArrowItem.createArrow(World, ItemStack, LivingEntity)}，
 * 那条 {@code ProjectileUtil} 的辅助方法是给弩/别的路径用的，弓根本不经过它。</p>
 *
 * <h2>为什么在这里读 shooter 的主手是安全的</h2>
 * <p>{@code createArrow} 发生在 {@code onStoppedUsing} 内部、弓还握在手里的那一瞬间，
 * 此时 {@code getMainHandStack()} 必然是弓本身。等到箭命中时再读主手就会出错
 * （玩家常常射完立刻切槽），所以必须在<b>射击这一刻</b>把魂值快照下来。</p>
 *
 * <h2>为什么必须判玩家</h2>
 * <p>骷髅也用 {@code ArrowItem.createArrow}。不判玩家的话，骷髅射出的箭会读到
 * 它自己的主手（骷髅持弓，没有 Soulbrand NBT），结果是 0 —— 恰好无害，
 * 但显式判一下更清楚，也避免以后给怪物加附魂时误生效。</p>
 */
@Mixin(net.minecraft.item.ArrowItem.class)
public abstract class ArrowWeaponMixin {

    @Inject(method = "createArrow", at = @At("RETURN"))
    private void soulbrand$recordWeapon(net.minecraft.world.World world, ItemStack arrowStack,
                                        LivingEntity shooter,
                                        CallbackInfoReturnable<PersistentProjectileEntity> ci) {
        if (world.isClient) {
            return;
        }
        PersistentProjectileEntity arrow = ci.getReturnValue();
        if (arrow == null || !(shooter instanceof net.minecraft.entity.player.PlayerEntity player)) {
            return;
        }
        // 射击的这一刻，弓就在主手上
        ItemStack weapon = player.getMainHandStack();
        if (weapon.isEmpty()) {
            weapon = player.getOffHandStack();
        }
        if (weapon.isEmpty()) {
            return;
        }
        SoulProjectileWeapon.stampArrow(arrow, weapon);
    }
}
