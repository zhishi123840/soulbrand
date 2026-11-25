package com.example.soulbrand.logic;

import net.minecraft.item.ItemStack;

/**
 * 把「射出箭时武器的魂值」暂存到箭实体上。
 *
 * <p><b>为什么不存 NBT</b>：实体没有「随时可读写的自定义 NBT」入口
 * （只有 {@code writeNbt/readNbt} 在存盘时调用），而
 * {@code getCustomName()} 那条路是给命名用的，塞数字进去很脏。</p>
 *
 * <p><b>为什么不用 DataTracker</b>：那是需要登记 {@code TrackedData} 并同步给客户端的机制，
 * 而伤害结算<b>只在服务端</b>发生。用普通字段足够，且零网络开销。</p>
 *
 * <p><b>为什么箭丢了记录也无所谓</b>：这是瞬时数据 —— 射出时写、命中时读，
 * 箭插在地上过很久再被踩到，读不到就当 0 处理（退回原版伤害），不会崩也不会算错。</p>
 *
 * <p>实现方式是 {@code SoulArrowSoulHolder} 接口 + 一个 mixin 给
 * {@code PersistentProjectileEntity} 加字段（见 {@code ArrowSoulFieldMixin}）。</p>
 */
public final class SoulProjectileWeapon {

    private SoulProjectileWeapon() {}

    /** 射击瞬间把武器的魂值写到箭上。未刻印 → 0（必须写，否则复用旧值）。 */
    public static void stampArrow(net.minecraft.entity.projectile.PersistentProjectileEntity arrow,
                                  ItemStack weapon) {
        if (!(arrow instanceof SoulArrowSoulHolder holder)) {
            return;
        }
        holder.soulbrand$setArrowSoul(SoulNbt.isBranded(weapon) ? SoulNbt.getSoul(weapon) : 0);
    }

    /** 读箭上记录的魂值；没有记录（原版箭、别的 mod 的箭）返回 0。 */
    public static int readArrowSoul(net.minecraft.entity.projectile.PersistentProjectileEntity arrow) {
        if (!(arrow instanceof SoulArrowSoulHolder holder)) {
            return 0;
        }
        return Math.max(0, holder.soulbrand$getArrowSoul());
    }
}
