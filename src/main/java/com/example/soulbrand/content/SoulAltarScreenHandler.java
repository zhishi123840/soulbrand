package com.example.soulbrand.content;

import com.example.soulbrand.SoulConfig;
import com.example.soulbrand.logic.SoulArmor;
import com.example.soulbrand.logic.SoulNbt;
import com.example.soulbrand.logic.SoulRules;
import com.example.soulbrand.registry.ModItems;
import com.example.soulbrand.registry.ModScreenHandlers;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 附魂台界面容器。
 *
 * <p>一个装备槽 + 两个动作（{@code onButtonClick}）：</p>
 * <ul>
 *   <li>buttonId 0 = <b>刻印</b>：3 碎晶 + 55 经验，写 Soulbrand NBT 与附魔条目</li>
 *   <li>buttonId 1 = <b>净魂</b>：扣 1 张净魂粉，魂值归零</li>
 * </ul>
 *
 * <p>魂值/等级不额外做同步通道 —— 装备槽里的 ItemStack 本身带 NBT，
 * 原版会把槽位内容同步给客户端，界面直接读 {@code inventory.getStack(0)} 即可。</p>
 *
 * <p><b>判定与扣除分离</b>：所有校验通过之前不扣任何东西，失败只回一行提示。
 * 这条沿用原版方块 onUse 的既有约定。</p>
 */
public class SoulAltarScreenHandler extends ScreenHandler {

    public static final int BUTTON_BRAND = 0;
    public static final int BUTTON_CLEANSE = 1;

    /** 装备槽在 slots 列表里的下标（我们自己加的第一个）。 */
    public static final int SLOT_INDEX = 0;

    private final Inventory inventory;

    /**
     * 台面所在世界与坐标。
     *
     * <p>客户端 factory 没有这两个信息（原版靠 {@code ScreenHandlerContext} 传，
     * 但那个 context 只在服务端 resolve），所以这里允许为 null ——
     * 所有用到它的地方都先判空，客户端那份 handler 只负责显示。</p>
     */
    private final net.minecraft.world.World world;
    private final net.minecraft.util.math.BlockPos pos;

    public SoulAltarScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        this(syncId, playerInventory, inventory, null, null);
    }

    public SoulAltarScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory,
                                  net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos) {
        super(ModScreenHandlers.SOUL_ALTAR, syncId);
        this.inventory = inventory;
        this.world = world;
        this.pos = pos;
        inventory.onOpen(playerInventory.player);

        // 装备槽：原版附魔台两个槽是 (15,47) 和 (35,47)（字节码实测），
        // 背景贴图上的两个框就画在这两处。用左边那个 —— 之前写 (25,45)
        // 正好落在两框中间，物品看起来"卡在框外"。
        addSlot(new Slot(inventory, 0, 15, 47) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return SoulAltarBlock.isBrandable(stack);
            }
        });

        // 玩家背包：原版标准布局
        addPlayerInventory(playerInventory);
    }

    private void addPlayerInventory(PlayerInventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int buttonId) {
        ItemStack target = inventory.getStack(SLOT_INDEX);

        switch (buttonId) {
            case BUTTON_BRAND:
                doBrand(player, target);
                break;
            case BUTTON_CLEANSE:
                doCleanse(player, target);
                break;
            default:
                break;
        }
        // 无论成功失败都要重算防护力：槽位里的装备可能刚被换过。
        SoulArmor.refresh(player);
        return true;
    }

    // ------------------------------------------------------------ 刻印

    private void doBrand(PlayerEntity player, ItemStack target) {
        if (target.isEmpty()) {
            SoulAltarFeedback.fail(player, "message.soulbrand.altar.no_item");
            return;
        }
        if (!SoulAltarBlock.isBrandable(target)) {
            SoulAltarFeedback.fail(player, "message.soulbrand.altar.not_brandable");
            return;
        }
        if (SoulNbt.isBranded(target)) {
            SoulAltarFeedback.fail(player, "message.soulbrand.altar.already");
            return;
        }
        int fragments = countFragments(player);
        if (fragments < SoulAltarBlock.FRAGMENT_COST) {
            SoulAltarFeedback.fail(player, "message.soulbrand.altar.no_fragment");
            return;
        }
        int xpCost = Math.max(0, SoulConfig.brandXpCost());
        if (player.totalExperience < xpCost) {
            SoulAltarFeedback.failXp(player, xpCost);
            return;
        }

        // 判定全过才扣
        SoulNbt.brand(target);
        consumeFragments(player, SoulAltarBlock.FRAGMENT_COST);
        if (xpCost > 0) {
            SoulAltarFeedback.deductXpPoints(player, xpCost);
        }
        inventory.markDirty();
        // 台面切到充能贴图。客户端那份 handler 没有 world/pos，判空跳过。
        if (world != null && pos != null
                && world.getBlockState(pos).getBlock() instanceof SoulAltarBlock altar) {
            altar.markCharged(world, pos, world.getBlockState(pos));
        }
        SoulAltarFeedback.ok(player, "message.soulbrand.altar.success");
    }

    // ------------------------------------------------------------ 净魂

    private void doCleanse(PlayerEntity player, ItemStack target) {
        if (target.isEmpty()) {
            SoulAltarFeedback.fail(player, "message.soulbrand.cleanser.no_item");
            return;
        }
        if (!SoulNbt.isBranded(target)) {
            SoulAltarFeedback.fail(player, "message.soulbrand.cleanser.not_branded");
            return;
        }
        if (!consumeCleanser(player)) {
            SoulAltarFeedback.fail(player, "message.soulbrand.cleanser.no_powder");
            return;
        }

        SoulNbt.clearSoul(target);
        inventory.markDirty();
        SoulAltarFeedback.ok(player, "message.soulbrand.cleanser.done");
    }

    // ------------------------------------------------------------ 背包辅助

    private static int countFragments(PlayerEntity player) {
        net.minecraft.entity.player.PlayerInventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && stack.isOf(ModItems.SOUL_FRAGMENT)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void consumeFragments(PlayerEntity player, int amount) {
        net.minecraft.entity.player.PlayerInventory inv = player.getInventory();
        int remaining = amount;
        for (int i = 0; i < inv.size() && remaining > 0; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty() || !stack.isOf(ModItems.SOUL_FRAGMENT)) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.decrement(take);
            remaining -= take;
        }
        inv.markDirty();
    }

    /** 返回是否成功扣到 1 张净魂粉。找不到就什么都不做。 */
    private static boolean consumeCleanser(PlayerEntity player) {
        net.minecraft.entity.player.PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && stack.isOf(ModItems.SOUL_CLEANSER)) {
                stack.decrement(1);
                inv.markDirty();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ 通用

    /**
     * Shift+点击在装备槽与背包之间搬运。
     *
     * <p>只处理「从背包 Shift 进装备槽」这一个方向 —— 装备槽只有一格，
     * 反向搬运（从槽里 Shift）交给原版默认行为即可。</p>
     */
    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (!slot.hasStack()) {
            return result;
        }
        ItemStack stack = slot.getStack();
        result = stack.copy();

        if (slotIndex == SLOT_INDEX) {
            // 从装备槽取出：放回玩家背包
            if (!insertItem(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (SoulAltarBlock.isBrandable(stack) && inventory.getStack(SLOT_INDEX).isEmpty()) {
            // 从背包 Shift 进装备槽
            if (!insertItem(stack, SLOT_INDEX, SLOT_INDEX + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        return result;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        // 界面关闭：槽里的装备退回玩家背包，绝不留在 BE 里（否则拆台会丢装备）。
        if (!player.isAlive() || !(player instanceof ServerPlayerEntity)) {
            inventory.clear();
            return;
        }
        ItemStack leftover = inventory.removeStack(SLOT_INDEX);
        if (!leftover.isEmpty()) {
            if (!player.getInventory().insertStack(leftover)) {
                player.dropItem(leftover, false);
            }
        }
        inventory.onClose(player);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    /** 供界面显示：槽里装备的魂值与等级。 */
    public int soulValue() {
        ItemStack stack = inventory.getStack(SLOT_INDEX);
        return SoulNbt.isBranded(stack) ? SoulNbt.getSoul(stack) : -1;
    }

    public int soulLevel() {
        ItemStack stack = inventory.getStack(SLOT_INDEX);
        if (!SoulNbt.isBranded(stack)) {
            return -1;
        }
        return SoulRules.level(SoulNbt.getSoul(stack));
    }

    public boolean isBrandedInSlot() {
        return SoulNbt.isBranded(inventory.getStack(SLOT_INDEX));
    }

    /** 界面用它区分加成行怎么写：武器显示攻/破，护甲显示防护。 */
    public boolean isArmorInSlot() {
        return inventory.getStack(SLOT_INDEX).getItem() instanceof net.minecraft.item.ArmorItem;
    }
}
