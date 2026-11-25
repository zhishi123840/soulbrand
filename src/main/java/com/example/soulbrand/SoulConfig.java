package com.example.soulbrand;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 附魂配置。
 *
 * <p>Fabric 没有 Forge 的 SERVER 配置类型，这里手写 {@code config/soulbrand.json}。
 * 六个字段（五个倍率 + 刻印经验消耗），语义与 Forge 版一一对应。</p>
 *
 * <p>文件缺失时写一份默认值出来；解析失败时退回默认值并保留原文件，
 * 免得玩家改错一个逗号就把模组搞崩。</p>
 */
public final class SoulConfig {

    /** 残魂碎晶掉落率倍率：期望 = 基础概率 × 倍率，整数部分必掉、小数部分再 roll。 */
    public double fragmentDropMultiplier = 1.0D;

    /** 魂值增速倍率。 */
    public double soulGainMultiplier = 1.0D;

    /** 破甲伤害倍率。 */
    public double penetrationMultiplier = 1.0D;

    /** 攻击力加成倍率（护甲前追加，和锋利同性质）。 */
    public double attackPowerMultiplier = 1.0D;

    /** 护甲防护力加成的倍率。 */
    public double armorBonusMultiplier = 1.0D;

    /** 刻印消耗的经验点数（按点扣，不掉等级）。 */
    public int brandXpCost = 55;

    // ------------------------------------------------------------------ 单例

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SoulConfig instance = new SoulConfig();

    private SoulConfig() {}

    public static SoulConfig get() {
        return instance;
    }

    /** 由 {@link Soulbrand#onInitialize()} 调用。 */
    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(Soulbrand.MOD_ID + ".json");

        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                SoulConfig parsed = GSON.fromJson(r, SoulConfig.class);
                if (parsed != null) {
                    instance = parsed.sanitized();
                    return;
                }
                // 空文件 / 只有空白：当成「玩家清空了」，按默认值继续，但不覆盖他的文件。
                Soulbrand.LOGGER.warn("[soulbrand] config is empty, using defaults (file left untouched)");
                instance = new SoulConfig();
                return;
            } catch (RuntimeException | IOException e) {
                // 解析失败：**绝不覆盖原文件**。玩家手改出一个逗号错误时，
                // 如果把默认值写回去，他刚才改的内容就全没了。
                Soulbrand.LOGGER.warn("[soulbrand] config parse failed, using defaults "
                        + "(file left untouched so you can fix it): {}", e.toString());
                instance = new SoulConfig();
                return;
            }
        }

        // 只有「文件不存在」才落盘一份默认值。
        instance = new SoulConfig();
        try {
            write(path, instance);
        } catch (IOException e) {
            Soulbrand.LOGGER.warn("[soulbrand] cannot write default config: {}", e.toString());
        }
    }

    private static void write(Path path, SoulConfig cfg) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(cfg, w);
        }
    }

    /** 手改配置文件可能写出负数或天文数字，读进来先夹一遍。 */
    private SoulConfig sanitized() {
        SoulConfig c = new SoulConfig();
        c.fragmentDropMultiplier = clamp(fragmentDropMultiplier, 0.0D, 64.0D, 1.0D);
        c.soulGainMultiplier = clamp(soulGainMultiplier, 0.0D, 64.0D, 1.0D);
        c.penetrationMultiplier = clamp(penetrationMultiplier, 0.0D, 64.0D, 1.0D);
        c.attackPowerMultiplier = clamp(attackPowerMultiplier, 0.0D, 64.0D, 1.0D);
        c.armorBonusMultiplier = clamp(armorBonusMultiplier, 0.0D, 64.0D, 1.0D);
        c.brandXpCost = Math.max(0, Math.min(brandXpCost, 100000));
        return c;
    }

    private static double clamp(double v, double lo, double hi, double fallback) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return fallback;
        }
        return Math.max(lo, Math.min(v, hi));
    }

    // ------------------------------------------------------------ 便捷读取

    public static double dropMultiplier() {
        return instance.fragmentDropMultiplier;
    }

    public static double soulGainMultiplier() {
        return instance.soulGainMultiplier;
    }

    public static double penetrationMultiplier() {
        return instance.penetrationMultiplier;
    }

    public static double attackPowerMultiplier() {
        return instance.attackPowerMultiplier;
    }

    public static double armorBonusMultiplier() {
        return instance.armorBonusMultiplier;
    }

    public static int brandXpCost() {
        return instance.brandXpCost;
    }
}
