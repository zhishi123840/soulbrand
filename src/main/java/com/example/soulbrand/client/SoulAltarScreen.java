package com.example.soulbrand.client;

import com.example.soulbrand.SoulConfig;
import com.example.soulbrand.content.SoulAltarScreenHandler;
import com.example.soulbrand.logic.SoulRules;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 附魂台界面。
 *
 * <p><b>背景直接借用原版附魔台贴图</b>（{@code minecraft:textures/gui/container/enchanting_table.png}）：
 * 那张图里本来就画着摊开的书与左右两块面板，所以外观与附魔台一致而不用自己画贴图。
 * 之所以不直接复用 {@code EnchantmentScreen} 类：它硬绑 {@code EnchantmentScreenHandler}，
 * 那个 handler 的按钮逻辑是原版附魔（随机三选一、扣青金石、扣经验等级），与附魂无关。</p>
 *
 * <p>右侧面板放两个按钮：刻印 / 净魂。左侧书面板上方显示当前装备的魂值与等级。</p>
 */
public class SoulAltarScreen extends HandledScreen<SoulAltarScreenHandler> {

    /**
     * 界面背景贴图。
     *
     * <p>现在指向<b>工程自己的</b> {@code soulbrand:textures/gui/container/soul_altar.png}，
     * 内容是原版附魔台那张图的副本 —— 这样你可以直接改这张 PNG 来换面板外观，
     * 不必去动原版资源。</p>
     *
     * <p>尺寸约定：贴图是 256×256 的画布（原版附魔台就是这样），
     * 但只有左上角 <b>176×167</b> 那一块会被画出来（见 {@link #WIDTH}/{@link #HEIGHT} 与
     * {@code drawBackground} 的 UV 参数）。你画的时候把面板内容放在左上 176×167 内即可，
     * 右侧/下方的空白会被忽略。</p>
     */
    private static final Identifier TEXTURE =
            new Identifier("soulbrand:textures/gui/container/soul_altar.png");

    /** 原版附魔台背景尺寸。 */
    private static final int WIDTH = 176;
    private static final int HEIGHT = 167;

    public SoulAltarScreen(SoulAltarScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        // 背景图上方有书本图案，标题往下挪避免压住
        titleY = 6;
        playerInventoryTitleY = HEIGHT - 94;
    }

    @Override
    protected void init() {
        super.init();
        int left = this.x;
        int top = this.y;

        // 按钮对齐用户画的棕色区：实测 x 59..167、y 14..69（宽 109、高 56）。
        // 两个按钮上下各占一半，中间留 2px 缝：
        //   刻印  (62, 17, 102, 22)   → y 17..39
        //   净魂  (62, 44, 102, 22)   → y 44..66
        addDrawableChild(ButtonWidget
                .builder(Text.translatable("gui.soulbrand.brand"), button ->
                        sendButton(SoulAltarScreenHandler.BUTTON_BRAND))
                .dimensions(left + 62, top + 17, 102, 22)
                .tooltip(Tooltip.of(Text.translatable("gui.soulbrand.brand.cost")))
                .build());

        addDrawableChild(ButtonWidget
                .builder(Text.translatable("gui.soulbrand.cleanse"), button ->
                        sendButton(SoulAltarScreenHandler.BUTTON_CLEANSE))
                .dimensions(left + 62, top + 44, 102, 22)
                .tooltip(Tooltip.of(Text.translatable("gui.soulbrand.cleanse.cost")))
                .build());
    }

    /**
     * 按钮动作发回服务端走 {@code ScreenHandler#onButtonClick}，
     * 与原版附魔台点右侧三行是同一套机制（客户端不碰任何数值判定）。
     */
    private void sendButton(int buttonId) {
        if (client != null && client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(
                    new ButtonClickC2SPacket(handler.syncId, buttonId));
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(TEXTURE, x, y, 0, 0, WIDTH, HEIGHT);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        super.drawForeground(context, mouseX, mouseY);

        // 布局（对应用户新画的贴图）：
        //   左面板  x 0..58     → 装备槽 (15,47)、加成数值、魂值等级
        //   右棕区  x 59..167   → 两个按钮（画在 init 里）
        //
        // 魂值/等级原来在 (80,60)，那个位置现在被「净魂」按钮占了，所以整体挪到左面板。
        int soul = handler.soulValue();
        if (soul < 0) {
            context.drawText(textRenderer,
                    Text.translatable("gui.soulbrand.hint.short"),
                    4, 30, 0xA0A0A0, false);
            return;
        }

        int level = handler.soulLevel();

        // 加成数值：左面板顶部（槽位上方）
        if (handler.isArmorInSlot()) {
            double armor = level * SoulRules.ARMOR_PER_LEVEL * SoulConfig.armorBonusMultiplier();
            context.drawText(textRenderer,
                    Text.translatable("gui.soulbrand.def", String.format("%.1f", armor)),
                    4, 14, 0x7FD4FF, false);
        } else {
            double attack = SoulRules.levelAttackPower(level) * SoulConfig.attackPowerMultiplier();
            double pen = SoulRules.levelPenetration(level) * SoulConfig.penetrationMultiplier();
            context.drawText(textRenderer,
                    Text.translatable("gui.soulbrand.atk", String.format("%.1f", attack)),
                    4, 14, 0xFF8080, false);
            context.drawText(textRenderer,
                    Text.translatable("gui.soulbrand.pen", String.format("%.1f", pen)),
                    4, 24, 0xC0A060, false);
        }

        // 魂值 · 等级：左面板底部（槽位下方，y=70 起）
        context.drawText(textRenderer,
                Text.translatable("gui.soulbrand.info", soul, SoulRules.roman(level)),
                4, 70, 0x7FD4FF, false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
