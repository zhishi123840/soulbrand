package com.example.soulbrand.logic;

/**
 * 让箭实体能暂存「射出它的武器魂值」。
 *
 * <p>由 {@code ArrowSoulFieldMixin} 给 {@code PersistentProjectileEntity} 实现，
 * 外部通过 {@link SoulProjectileWeapon} 读写，不要直接 cast 到实现类。</p>
 */
public interface SoulArrowSoulHolder {

    int soulbrand$getArrowSoul();

    void soulbrand$setArrowSoul(int soul);
}
