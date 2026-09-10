package com.campuspulse.support;

import java.util.List;
import java.util.Locale;

/** Small emergency guide used when the internal graph is unavailable. Never creates tickets. */
final class LocalSupportKnowledge {
    private LocalSupportKnowledge() {}
    private record Guide(String id, String terms, String titleEn, String titleZh, String url, String en, String zh) {}
    private static final List<Guide> GUIDES = List.of(
        new Guide("account", "register|sign up|login|log in|password|email|verification|注册|登录|密码|邮箱|验证码", "Accounts and verification", "账号与验证", "/login.html",
            "Register with an email verification code. Passwords need uppercase and lowercase letters, a number and a symbol. Codes expire after five minutes and are single-use. Use Forgot password to reset a password; existing sessions are revoked. Upload your avatar after signing in.",
            "请使用邮箱验证码注册，密码需要包含大小写字母、数字和特殊字符。验证码五分钟有效且只能使用一次。忘记密码时可通过邮箱重置，旧登录会话将失效。登录后可以上传头像。"),
        new Guide("teams", "team|captain|组队|队伍|队长|队员", "Teams and membership", "队伍与成员", "/team-lobby.html",
            "Open Teams, select a team and apply to join. The captain reviews requests while places remain. Check My teams for status. Captains can transfer leadership or close a team; transfer leadership before leaving. Applications and membership changes require an active team.",
            "在组队大厅选择队伍并申请加入，队长在名额允许时审核申请。在我的队伍中查看进度。队长可转让队长或关闭队伍，退出前需先转让。申请与成员变更需要队伍处于有效状态。"),
        new Guide("activities", "activity|event|registration|organizer|活动|报名|发布|组织者", "Activity registration", "活动报名", "/activity-lobby.html",
            "Browse Activities and open the details to register before the activity ends. Organizers review registrations within capacity. Check My registrations for decisions or cancel a pending/approved registration. Archived or ended activities cannot accept registrations.",
            "在活动大厅打开详情并在结束前报名，组织者在容量范围内审核申请。可在我的报名中查看结果或取消待审核、已通过的报名。已归档或结束的活动不再接受报名。"),
        new Guide("messages", "message|chat|image|notification|消息|聊天|群聊|图片|通知", "Messages and notifications", "消息与通知", "/messages.html",
            "Messages contains direct, team and activity conversations. Approved participants or active team members can access their group chat. Private image attachments require conversation access. The app reconnects and loads missed messages; use history to read older messages. Notifications include decisions and support replies.",
            "消息页包含私聊、队伍和活动会话。活动群聊要求报名通过，队伍群聊要求有效成员身份。私聊图片需具备会话权限才能读取。断线后会重连并补取消息，可加载更早历史。审核结果和客服回复可在通知中查看。"),
        new Guide("recommendations", "recommend|interest|推荐|兴趣", "Recommendations", "推荐", "/interest-tags.html",
            "Set your interests to improve rule-based ranking. Rankings also consider popularity, time and availability. A trained model is used only after enough genuine observations and successful evaluation; a fresh demo normally uses rules.",
            "设置兴趣标签可改善规则排序，系统还考虑热度、时间和可参与性。只有积累足够真实观察并通过评估后才启用训练模型，新演示库通常使用规则推荐。"),
        new Guide("language", "language|english|chinese|translate|语言|英文|中文|翻译", "Language and content", "语言与内容", "/home.html",
            "Use the language selector to switch the interface and available demo content translations. Original user content and uploaded image text are preserved. Editing demo text may invalidate its saved translation until a matching translation is available.",
            "可通过语言选择器切换界面和已有演示内容译文。用户原始内容及上传图片内文字保留原样。修改演示文字后，旧译文会失效，直到有对应新译文。")
    );

    static List<String> topics() { return GUIDES.stream().map(Guide::id).toList(); }
    static SupportAnswer answer(String question, String locale) {
        boolean zh = locale.startsWith("zh");
        String q = question.toLowerCase(Locale.ROOT);
        if (!q.matches("(?s).*(human|support agent|人工|真人|转接).*")) {
            for (Guide guide : GUIDES) {
                if (q.matches("(?s).*(" + guide.terms() + ").*"))
                    return new SupportAnswer(zh ? guide.zh() : guide.en(), "LOCAL_KNOWLEDGE",
                        List.of(new SupportAnswer.Citation(guide.id(), zh ? guide.titleZh() : guide.titleEn(), guide.url())), false);
            }
        }
        return new SupportAnswer(zh ? "现有平台指南不足以确认这个问题。你可以补充具体操作步骤，或点击转人工提交工单。"
                : "The platform guides do not contain enough information to confirm this. Describe the steps, or choose Contact support to create a ticket.",
                "LOCAL_KNOWLEDGE", List.of(), true);
    }
}
