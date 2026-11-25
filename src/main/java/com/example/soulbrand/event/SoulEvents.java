package com.example.soulbrand.event;

import com.example.soulbrand.SoulConfig;
import com.example.soulbrand.logic.SoulArmor;
import com.example.soulbrand.logic.SoulAttribution;
import com.example.soulbrand.logic.SoulHeart;
import com.example.soulbrand.logic.SoulNbt;
import com.example.soulbrand.logic.SoulPlayerData;
import com.example.soulbrand.logic.SoulProjectileWeapon;
import com.example.soulbrand.logic.SoulRules;
import com.example.soulbrand.registry.ModItems;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * 全部状态入口。
 *
 * <p>Fabric 没有 Forge 那套事件总线，逐项对应关系：</p>
 * <ul>
 *   <li>击杀结算 → {@code ServerLivingEntityEvents.AFTER_DEATH}</li>
 *   <li>登录 / 重生 / 换维度 → {@code ServerPlayerEvents.COPY_FROM / AFTER_RESPAWN}</li>
 *   <li>破甲追加 → 见 {@link com.example.soulbrand.mixin.SoulDamageMixin}（Fabric 没有 LivingDamageEvent）</li>
 *   <li>碎晶掉落 → 见 {@link com.example.soulbrand.mixin.SoulDropsMixin}（Fabric 没有 LivingDropsEvent）</li>
 *   <li>换装备 → 见 {@link com.example.soulbrand.mixin.SoulEquipmentMixin}</li>
 * </ul>
 *
 * <p>反噬已经删除，但护甲加成需要用属性修饰符，所以玩家属性入口回来了 ——
 * 这次是<b>加</b>护甲值（{@link SoulArmor}），不是扣血。</p>
 */
public final class SoulEvents {

    /** 参与涨魂的护甲槽，顺序固定。 */
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private SoulEvents() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(SoulEvents::afterDeath);
        // 重生后与换维度后：属性修饰符不会自动跟着走，必须重算一次。
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            migrateEnchantmentEntries(newPlayer);
            SoulArmor.refresh(newPlayer);
            SoulHeart.refresh(newPlayer);
        });
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            SoulPlayerData.copyFrom(oldPlayer, newPlayer);
            SoulArmor.refresh(newPlayer);
            SoulHeart.refresh(newPlayer);
        });
        // 进服时：属性修饰符不落盘，每次登录都得按当前身上的护甲重建。
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            migrateEnchantmentEntries(handler.player);
            SoulArmor.refresh(handler.player);
            SoulHeart.refresh(handler.player);
        });
    }

    /**
     * 老存档迁移：给旧版刻印的装备补上附魔条目。
     *
     * <p>「等级写进 Enchantments」是后来才加的改动。在那之前刻印的装备只有
     * {@code Soulbrand} compound、没有附魔条目，于是 JEI / 玉 / 装备面板这些
     * 读附魔列表的界面看不到「附魂」。</p>
     *
     * <p>原本指望「杀一次怪就自动补上」，但那条路径在<b>创造模式直接 return</b>
     * （M4 的规格：创造不计魂），所以创造模式下永远补不上。这里把迁移独立出来，
     * 在登录和换维度时执行，两种游戏模式都能修好。</p>
     *
     * <p><b>为什么要扫整个背包而不是只看主手：</b>旧版刻印的装备可能躺在背包任何一格，
     * 玩家当前选中的往往是别的东西。只查主手会漏掉绝大多数情况。</p>
     *
     * <p><b>性能</b>：只在进服 / 换维度时跑一次，扫 41 格 + 读一次 NBT。
     * 性能红线禁的是 {@code LivingDamageEvent} 那种每击都走的路径上遍历背包，
     * 一次性迁移不受此限。</p>
     *
     * <p>只补不删：已经有附魔条目的装备一次 NBT 写入都不会发生。</p>
     */
    private static void migrateEnchantmentEntries(PlayerEntity player) {
        if (player.getWorld().isClient) {
            return;
        }
        net.minecraft.entity.player.PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty() || !SoulNbt.isBranded(stack)
                    || SoulNbt.hasSoulBrandEnchantment(stack)) {
                continue;
            }
            SoulNbt.syncEnchantmentLevel(stack, SoulRules.level(SoulNbt.getSoul(stack)));
        }
        inv.markDirty();
    }

    // ============================================================ 击杀结算

    /**
     * 击杀：主手武器 + 身上四件已刻印护甲，全部涨魂值。
     *
     * <p>性能：读 1 个主手 + 4 个护甲槽，每个最多一次 NBT 读取。没有背包遍历、
     * 没有实体遍历、没有 ticker —— 这条路径每次击杀只走一次，开销可忽略。</p>
     */
    private static void afterDeath(LivingEntity victim, net.minecraft.entity.damage.DamageSource source) {
        if (victim.getWorld().isClient) {
            return;
        }
        PlayerEntity player = SoulAttribution.validKiller(source, victim);
        if (player == null) {
            return;
        }

        // M4：创造模式不计魂；PVP 击杀不计。
        if (player.getAbilities().creativeMode) {
            return;
        }
        if (victim instanceof PlayerEntity) {
            return;
        }

        ItemStack weapon = player.getMainHandStack();

        // ---- 先算这次涨多少魂 ----
        //
        // 连杀计时是「玩家的」，所以无论喂几件装备，都只算一次、用同一个增量。
        long now = player.getWorld().getTime();
        int combo = SoulPlayerData.bumpCombo(player, now);
        int base = SoulRules.soulGainBase(victim) + combo;
        int gain = Math.max(0, (int) Math.round(base * SoulConfig.soulGainMultiplier()));
        if (gain <= 0) {
            return;
        }

        // ---- 喂魂：主手武器 + 身上四件已刻印护甲 ----
        //
        // 魂值没有上限：每满 100 升一级，等级越高攻击力 / 破甲 / 护甲值线性增长。
        // 反噬已删除，魂值只涨不跌，除非玩家主动用净魂粉清零。
        boolean fedArmor = false;

        if (SoulNbt.isBranded(weapon)) {
            SoulNbt.setSoul(weapon, SoulNbt.getSoul(weapon) + gain);
        }
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack armor = player.getEquippedStack(slot);
            if (armor.isEmpty() || !SoulNbt.isBranded(armor)) {
                continue;
            }
            SoulNbt.setSoul(armor, SoulNbt.getSoul(armor) + gain);
            fedArmor = true;
        }

        // ---- 护甲魂值变了，防护力要立刻重算 ----
        //
        // 只在真的有护甲被喂过时才刷新，没穿附魂护甲的玩家不会多走一次属性路径。
        if (fedArmor) {
            SoulArmor.refresh(player);
        }
    }

    // ============================================================ 换装备

    /**
     * 由 {@link com.example.soulbrand.mixin.SoulEquipmentMixin} 在 {@code LivingEntity#onEquipStack} 调用。
     *
     * <p>穿上 / 脱下附魂护甲时重算防护力。这个回调对每个 LivingEntity 都会触发，
     * 所以在 mixin 那边先用 {@code instanceof PlayerEntity} 挡掉了非玩家。</p>
     */
    public static void onEquipmentChanged(PlayerEntity player) {
        SoulArmor.refresh(player);
    }

    // ============================================================ 碎晶掉落

    /**
     * 由 {@link com.example.soulbrand.mixin.SoulDropsMixin} 在 {@code LivingEntity#dropLoot} 里调用。
     *
     * <p>掉落计算分两步：</p>
     * <ol>
     *   <li><b>基础掉落</b>：期望 = 基础概率 × 配置倍率；整数部分必掉，小数部分再 roll 一次</li>
     *   <li><b>抢夺加成</b>：每级 +1 颗保底，直接叠在基础掉落之上</li>
     * </ol>
     *
     * <p>所以抢夺不是「提高概率」，而是「额外多掉」—— 和原版对稀有掉落的处理一致。
     * 抢夺 III 杀僵尸 = 25% 那颗（可能 0）+ 3 颗保底；杀凋零骷髅 = 1 + 3 = 4 颗。</p>
     */
    public static void onLoot(LivingEntity victim, net.minecraft.entity.damage.DamageSource source,
                              boolean causedByPlayer) {
        if (victim.getWorld().isClient) {
            return;
        }
        PlayerEntity player = SoulAttribution.validKiller(source, victim);
        if (player == null) {
            return;
        }
        double base = SoulRules.fragmentDropChance(victim);
        if (base <= 0.0D) {
            return;
        }

        // 第一步：基础掉落（概率 × 倍率，拆成必掉 + 再 roll）
        int count = SoulRules.rollFragmentCount(base, SoulConfig.dropMultiplier(),
                victim.getRandom().nextDouble());

        // 第二步：抢夺保底。只要怪在被认的名单里，抢夺就一定生效，
        // 哪怕基础那一次没 roll 中。
        count += SoulRules.lootingBonus(net.minecraft.enchantment.EnchantmentHelper.getLooting(player));

        if (count <= 0) {
            return;
        }
        ItemStack stack = new ItemStack(ModItems.SOUL_FRAGMENT, count);
        victim.getWorld().spawnEntity(new ItemEntity(
                victim.getWorld(), victim.getX(), victim.getY() + 0.5D, victim.getZ(), stack));
    }

    // ============================================================ 攻击力加成（护甲前）

    /**
     * 由 {@link com.example.soulbrand.mixin.SoulAttackMixin} 在 {@code LivingEntity#applyDamage} 的
     * <b>HEAD</b> 调用 —— 也就是护甲减免之前。
     *
     * <p>这里加的是「攻击力」，性质等同于锋利：会被护甲吃掉一部分。
     * 与 {@link #onApplyDamage} 的破甲是两个独立加成，前者给攻击力、后者给无视护甲的裸伤害。</p>
     *
     * <p>为什么不在 {@code PlayerEntity.attack} 里改：那是原版锋利加成的计算处，
     * 混进去容易和其它战斗模组抢同一个局部变量。{@code applyDamage} 的 HEAD 是统一入口。</p>
     */
    public static float onIncomingDamage(LivingEntity victim, net.minecraft.entity.damage.DamageSource source,
                                         float incoming) {
        if (victim.getWorld().isClient || incoming <= 0.0F) {
            return incoming;
        }
        // 近战挥击 与 玩家射出的箭 都算（用户要求「弓射出的箭伤害和近战一样算」）。
        if (!SoulAttribution.isPlayerStrike(source)) {
            return incoming;
        }
        PlayerEntity player = SoulAttribution.validKiller(source, victim);
        if (player == null) {
            return incoming;
        }

        int soul = resolveSoul(source, player);
        if (soul <= 0) {
            return incoming;
        }

        float bonus = SoulRules.attackPowerBonus(soul, SoulConfig.attackPowerMultiplier());
        if (bonus <= 0.0F) {
            return incoming;
        }
        return incoming + bonus;
    }

    /**
     * 取「这次伤害所用的魂值」。
     *
     * <p><b>近战</b>：读玩家当前主手 —— 挥击与命中是同一 tick，切不了槽。</p>
     *
     * <p><b>投射物</b>：读<b>箭上记录的魂值</b>（射击瞬间打在箭上的）。
     * 不能读主手：玩家射完可能立刻切槽，那时主手已经没有弓了，
     * 读到的会是别的武器或空手，加成直接消失或算错。</p>
     *
     * @return 魂值；没有加成时为 0
     */
    private static int resolveSoul(net.minecraft.entity.damage.DamageSource source, PlayerEntity player) {
        net.minecraft.entity.Entity direct = source.getSource();

        // 投射物：用射击瞬间快照下来的魂值
        if (direct instanceof net.minecraft.entity.projectile.PersistentProjectileEntity arrow) {
            return SoulProjectileWeapon.readArrowSoul(arrow);
        }

        // 近战：当前主手
        ItemStack weapon = player.getMainHandStack();
        if (weapon.isEmpty() || !SoulNbt.isBranded(weapon)) {
            return 0;
        }
        return SoulNbt.getSoul(weapon);
    }
    // ============================================================ 破甲追加

    /**
     * 由 {@link com.example.soulbrand.mixin.SoulDamageMixin} 在 {@code LivingEntity#applyDamage} 里调用。
     *
     * <p>这是 Forge {@code LivingDamageEvent} 的 Fabric 等价点：{@code applyDamage} 在护甲与附魔减免
     * <b>之后</b>、真正 {@code setHealth} <b>之前</b>，所以这里加的数值不会被护甲削掉。</p>
     *
     * @param postArmor 护甲与抗性结算后的伤害（即将写入 setHealth 的值）
     * @param preArmor  护甲减免之前的原始伤害，只用来当截断基数
     * @return 追加破甲后的伤害
     */
    public static float onApplyDamage(LivingEntity victim, net.minecraft.entity.damage.DamageSource source,
                                      float postArmor, float preArmor) {
        if (victim.getWorld().isClient || postArmor <= 0.0F) {
            return postArmor;
        }
        // 近战挥击 与 玩家射出的箭 都吃破甲（与攻击力保持同一套来源判定）。
        if (!SoulAttribution.isPlayerStrike(source)) {
            return postArmor;
        }
        PlayerEntity player = SoulAttribution.validKiller(source, victim);
        if (player == null) {
            return postArmor;
        }

        int soul = resolveSoul(source, player);
        if (soul <= 0) {
            return postArmor;
        }

        float extra = SoulRules.penetrationDamage(soul, SoulConfig.penetrationMultiplier(), preArmor);
        if (extra <= 0.0F) {
            return postArmor;
        }
        return postArmor + extra;
    }
}
