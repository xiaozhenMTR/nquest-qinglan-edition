package com.xz.nquestql.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.xz.nquestql.NQuestQinglanEdition;
import com.xz.nquestql.data.QuestData;
import com.xz.nquestql.gui.GuiManager;
import com.xz.nquestql.manager.CdkManager;
import com.xz.nquestql.manager.QuestManager;
import com.xz.nquestql.manager.TitleManager;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

import static net.minecraft.server.command.CommandManager.*;

public class QuestCommand {

    private static final SuggestionProvider<ServerCommandSource> QUEST_ID_SUGGESTIONS =
            (ctx, builder) -> {
                for (QuestData q : QuestManager.getAll()) {
                    builder.suggest(q.questId);
                }
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> TITLE_ID_SUGGESTIONS =
            (ctx, builder) -> {
                for (TitleManager.TitleDef t : TitleManager.getTitles()) {
                    builder.suggest(t.id);
                }
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> UNLOCKED_TITLE_SUGGESTIONS =
            (ctx, builder) -> {
                try {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    TitleManager.PlayerTitleData data = TitleManager.getOrCreate(p.getUuid());
                    for (String id : data.unlocked) {
                        builder.suggest(id);
                    }
                    for (String name : data.customTitles) {
                        // 把 § 转成 &，否则客户端命令行输入 § 会被判定为非法字符
                        builder.suggest(name.replace('\u00A7', '&'));
                    }
                } catch (Exception ignored) {}
                return builder.buildFuture();
            };

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("qlquest")
                // ★ /qlquest start <questId>
                .then(literal("start")
                        .then(argument("questId", StringArgumentType.word())
                                .suggests(QUEST_ID_SUGGESTIONS)
                                .executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    QuestData q = QuestManager.getQuest(StringArgumentType.getString(ctx, "questId"));
                                    if (q == null) {
                                        ctx.getSource().sendFeedback(() -> Text.literal("§c任务不存在"), false);
                                        return 0;
                                    }
                                    if (q.points == null || q.points.isEmpty()) {
                                        ctx.getSource().sendFeedback(() -> Text.literal("§c该任务没有步骤"), false);
                                        return 0;
                                    }
                                    return QuestManager.start(p, q) ? 1 : 0;
                                })))
                // ★ /qlquest trigger <questId> <code> [player]
                .then(literal("trigger")
                        .requires(s -> s.hasPermissionLevel(2))
                        .then(argument("questId", StringArgumentType.word())
                                .suggests(QUEST_ID_SUGGESTIONS)
                                .then(argument("code", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                            QuestData q = QuestManager.getQuest(StringArgumentType.getString(ctx, "questId"));
                                            if (q == null) {
                                                ctx.getSource().sendFeedback(() -> Text.literal("§c任务不存在"), false);
                                                return 0;
                                            }
                                            int code = IntegerArgumentType.getInteger(ctx, "code");
                                            return QuestManager.trigger(p, q, code) ? 1 : 0;
                                        })
                                        .then(argument("player", EntityArgumentType.player())
                                                .executes(ctx -> {
                                                    ServerPlayerEntity t = EntityArgumentType.getPlayer(ctx, "player");
                                                    QuestData q = QuestManager.getQuest(StringArgumentType.getString(ctx, "questId"));
                                                    if (q == null) {
                                                        ctx.getSource().sendFeedback(() -> Text.literal("§c任务不存在"), false);
                                                        return 0;
                                                    }
                                                    int code = IntegerArgumentType.getInteger(ctx, "code");
                                                    boolean ok = QuestManager.trigger(t, q, code);
                                                    if (ok) ctx.getSource().sendFeedback(
                                                            () -> Text.literal("§a已为 " + t.getName().getString() + " 推进"), true);
                                                    return ok ? 1 : 0;
                                                })))))
                // ★ /qlquest bind web <后台地址> - 绑定后台（自动推导 quests.json / qp-upload.php / admin.html）
                .then(literal("bind")
                        .requires(s -> s.hasPermissionLevel(2))
                        .then(literal("web")
                                .then(argument("base", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String base = StringArgumentType.getString(ctx, "base").trim()
                                                    .replaceAll("^https?://", "").replaceAll("/+$", "");
                                            if (base.isEmpty()) {
                                                ctx.getSource().sendError(Text.literal("§c地址不能为空"));
                                                return 0;
                                            }
                                            com.xz.nquestql.storage.WebBind.save(base);
                                            NQuestQinglanEdition.getInstance().applyWebBind(base);
                                            String b = base;
                                            ctx.getSource().sendFeedback(() -> Text.literal("§a✔ 已绑定后台: §f" + b), true);
                                            ctx.getSource().sendFeedback(() -> Text.literal("§7任务数据: §fhttps://" + b + "/quests.json"), false);
                                            ctx.getSource().sendFeedback(() -> Text.literal("§7QP 上传:  §fhttps://" + b + "/qp-upload.php"), false);
                                            ctx.getSource().sendFeedback(() -> Text.literal("§7管理后台: §fhttps://" + b + "/admin.html"), false);
                                            ctx.getSource().sendFeedback(() -> Text.literal("§7排行榜:   §fhttps://" + b + "/index.html"), false);
                                            ctx.getSource().sendFeedback(() -> Text.literal("§e执行 /qlquest reload 立即从新地址拉取任务"), false);
                                            return 1;
                                        }))))
                // ★ /qlquest abort
                .then(literal("abort")
                        .executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            return QuestManager.abort(p) ? 1 : 0;
                        }))
                // ★ /qlquest list
                .then(literal("list")
                        .executes(ctx -> {
                            var list = QuestManager.getAll();
                            ctx.getSource().sendFeedback(
                                    () -> Text.literal("§7=== 可用任务 (" + list.size() + ") ==="), false);
                            for (QuestData q : list)
                                ctx.getSource().sendFeedback(
                                        () -> Text.literal("§e- " + q.questId + ": " + q.questName + " §7(+" + q.qpReward + " QP, " + (q.points != null ? q.points.size() : 0) + "步)"), false);
                            return 1;
                        }))
                // ★ /qlquest reload
                .then(literal("reload")
                        .requires(s -> s.hasPermissionLevel(2))
                        .executes(ctx -> {
                            NQuestQinglanEdition.getInstance().loadQuests();
                            ctx.getSource().sendFeedback(() -> Text.literal("§a已重载"), true);
                            return 1;
                        }))
                // ★ /qlquest qp
                .then(literal("qp")
                        .executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            int qp = NQuestQinglanEdition.getInstance().getQPStorage().getQP(p.getUuid());
                            p.sendMessage(Text.literal("§6你的 QP: " + qp), false);
                            return 1;
                        }))
                // ★ /qlquest ui - 打开箱子 UI
                .then(literal("ui")
                        .executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            GuiManager.openMainMenu(p);
                            return 1;
                        }))
                // ★ /qlquest redeem <cdk> - 兑换 CDK
                .then(literal("redeem")
                        .then(argument("cdk", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    String cdk = StringArgumentType.getString(ctx, "cdk").toUpperCase();

                                    p.sendMessage(Text.literal("§7正在兑换..."), false);

                                    // 调用远程 API 兑换
                                    CdkManager.ClaimResult result = CdkManager.claim(cdk, p.getUuid());

                                    if (!result.success) {
                                        p.sendMessage(Text.literal("§c兑换失败: " + (result.error != null ? result.error : "未知错误")), false);
                                        return 0;
                                    }

                                    // 扣 QP
                                    boolean deducted = NQuestQinglanEdition.getInstance().getQPStorage().deductQP(p.getUuid(), result.price);
                                    if (!deducted) {
                                        p.sendMessage(Text.literal("§cQP 不足，需要 " + result.price + " QP（兑换码已标记，请联系管理员）"), false);
                                        return 0;
                                    }

                                    // 添加自定义称号
                                    TitleManager.addCustomTitle(p, result.titleName);
                                    p.sendMessage(Text.literal("§a✔ 兑换成功！已扣除 " + result.price + " QP"), false);
                                    p.sendMessage(Text.literal("§7使用 /qlquest title list 查看你的称号"), false);
                                    return 1;
                                })))
                // ★ /qlquest titles - 查看称号商店
                .then(literal("titles")
                        .executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            List<TitleManager.TitleDef> list = TitleManager.getTitles();
                            p.sendMessage(Text.literal("§6§l===== 称号商店 ====="), false);
                            for (TitleManager.TitleDef t : list) {
                                p.sendMessage(Text.literal("§e" + t.name + " §7- " + t.price + " QP  §8(/qlquest title buy " + t.id + ")"), false);
                            }
                            p.sendMessage(Text.literal("§7自定义称号请前往网页生成兑换码，再用 /qlquest redeem 兑换"), false);
                            return 1;
                        }))
                // ★ /qlquest title <子命令>
                .then(literal("title")
                        .then(literal("list")
                                .executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    TitleManager.PlayerTitleData data = TitleManager.getOrCreate(p.getUuid());
                                    String active = TitleManager.getActiveTitleName(p.getUuid());
                                    p.sendMessage(Text.literal("§6§l===== 我的称号 ====="), false);
                                    if (active != null) p.sendMessage(Text.literal("§7当前佩戴: " + active), false);
                                    else p.sendMessage(Text.literal("§7当前佩戴: §8无"), false);

                                    p.sendMessage(Text.literal("§7模板称号:"), false);
                                    for (String id : data.unlocked) {
                                        TitleManager.TitleDef t = TitleManager.getTitle(id);
                                        String name = t != null ? t.name : id;
                                        boolean isActive = id.equals(data.active);
                                        p.sendMessage(Text.literal((isActive ? " §a▶ " : "  ") + name + "  §8(/qlquest title set " + id + ")"), false);
                                    }

                                    p.sendMessage(Text.literal("§7自定义称号:"), false);
                                    for (String name : data.customTitles) {
                                        boolean isActive = name.equals(data.active);
                                        p.sendMessage(Text.literal((isActive ? " §a▶ " : "  ") + name + "  §8(/qlquest title set " + name + ")"), false);
                                    }

                                    if (data.unlocked.isEmpty() && data.customTitles.isEmpty()) {
                                        p.sendMessage(Text.literal(" §8（暂未解锁任何称号，/qlquest titles 查看商店）"), false);
                                    }
                                    return 1;
                                }))
                        .then(literal("buy")
                                .then(argument("titleId", StringArgumentType.word())
                                        .suggests(TITLE_ID_SUGGESTIONS)
                                        .executes(ctx -> {
                                            String titleId = StringArgumentType.getString(ctx, "titleId");
                                            TitleManager.buyTitle(ctx.getSource().getPlayerOrThrow(), titleId);
                                            return 1;
                                        })))
                        .then(literal("set")
                                .then(argument("titleId", StringArgumentType.greedyString())
                                        .suggests(UNLOCKED_TITLE_SUGGESTIONS)
                                        .executes(ctx -> {
                                            String titleId = StringArgumentType.getString(ctx, "titleId");
                                            TitleManager.setActive(ctx.getSource().getPlayerOrThrow(), titleId);
                                            return 1;
                                        })))
                        .then(literal("clear")
                                .executes(ctx -> {
                                    TitleManager.setActive(ctx.getSource().getPlayerOrThrow(), "");
                                    return 1;
                                })))
        );
    }
}
