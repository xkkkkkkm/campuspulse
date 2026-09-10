package com.campuspulse.bootstrap;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.campuspulse.security.PasswordHasher;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@Profile("demo")
public class Bootstrap implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final com.campuspulse.content.ContentTranslationService contentTranslations;

    public Bootstrap(JdbcTemplate jdbcTemplate, PasswordHasher passwordHasher) {
        this.contentTranslations = new com.campuspulse.content.ContentTranslationService(jdbcTemplate);
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Integer done=jdbcTemplate.queryForObject("SELECT completed FROM app_initialization WHERE name='demo-v1' FOR UPDATE",Integer.class);
        if(done!=null && done==1) return;
        ensureAdmin();
        ensureHomeTags();
        ensureDemoData();
        jdbcTemplate.update("UPDATE app_initialization SET completed=1,completed_at=NOW() WHERE name='demo-v1'");
    }

    private void ensureHomeTags() {
        List<String> home = List.of("运动健身", "学术讲座", "文艺演出", "志愿服务", "竞赛组队", "兼职实习", "兴趣社交", "考研搭子", "校园生活");
        for (String name : home) {
            jdbcTemplate.update("INSERT IGNORE INTO tags (name, type) VALUES (?, 'HOME')", name);
        }
        jdbcTemplate.update("INSERT IGNORE INTO tags (name, type) VALUES ('其他', 'OTHER')");
    }

    private void ensureAdmin() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE username = 'admin'", Long.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO users (username, password_hash, nickname, email, email_verified, role, status, campus)
                VALUES (?, ?, ?, ?, 1, 'ADMIN', 1, ?)
                """, "admin", passwordHasher.hash("admin123"), "系统管理员", "admin@campuspulse.local", "天赐庄校区");
    }

    private void ensureDemoData() {
        long orgId = ensureOrganizer();
        Map<String, Long> users = new LinkedHashMap<>();
        users.put("org", orgId);
        users.put("linzhixia", ensureSeedUser(new SeedUser("linzhixia", "20231001", "林知夏", "lin.zhixia@campuspulse.local", true, true, "USER", 1,
                "计算机科学与技术学院", "独墅湖校区", "软件工程", "2023", "UNDERGRAD", "偏好讲座、竞赛和校园产品设计，正在准备课程大作业答辩。", "13812345678")));
        users.put("chenze", ensureSeedUser(new SeedUser("chenze", "20212032", "陈泽", "chenze@campuspulse.local", true, true, "USER", 1,
                "体育学院", "阳澄湖校区", "社会体育指导", "2021", "UNDERGRAD", "篮球和羽毛球爱好者，也参与社团活动策划。", "13722223333")));
        users.put("xuan", ensureSeedUser(new SeedUser("xuan", "20231127", "许安", "xuan@campuspulse.local", true, true, "USER", 1,
                "文学院", "天赐庄校区", "汉语言文学", "2023", "UNDERGRAD", "偏好演出、志愿和校园社交活动。", "13688886666")));
        users.put("shenyi", ensureSeedUser(new SeedUser("shenyi", "20241008", "沈奕", "shenyi@campuspulse.local", false, true, "USER", 1,
                "管理学院", "未来校区", "信息管理与信息系统", "2024", "UNDERGRAD", "喜欢志愿、活动运营和数据整理。", "13566667777")));
        users.put("gumingzhe", ensureSeedUser(new SeedUser("gumingzhe", "20221017", "顾明哲", "gumingzhe@campuspulse.local", true, true, "USER", 1,
                "电子信息学院", "独墅湖校区", "电子信息工程", "2022", "UNDERGRAD", "常年混迹讲座、竞赛和技术社群，也经常主动私聊联系活动发起人。", "13511112222")));
        users.put("tangshuning", ensureSeedUser(new SeedUser("tangshuning", "20232015", "唐舒宁", "tangshuning@campuspulse.local", true, true, "USER", 1,
                "教育学院", "天赐庄校区", "应用心理学", "2023", "UNDERGRAD", "对心理成长、学习搭子和线下工作坊都很感兴趣。", "13511113333")));
        users.put("hezhiyuan", ensureSeedUser(new SeedUser("hezhiyuan", "20221088", "贺知远", "hezhiyuan@campuspulse.local", true, true, "USER", 1,
                "计算机科学与技术学院", "独墅湖校区", "人工智能", "2022", "MASTER", "喜欢算法竞赛与项目路演，但报名信息经常写得很简略。", "13511114444")));
        users.put("luqinghe", ensureSeedUser(new SeedUser("luqinghe", "20212106", "陆青禾", "luqinghe@campuspulse.local", true, true, "USER", 1,
                "商学院", "未来校区", "金融学", "2021", "UNDERGRAD", "偏好实习分享、校友交流和高质量私聊沟通。", "13511115555")));
        users.put("songyutong", ensureSeedUser(new SeedUser("songyutong", "20201118", "宋雨桐", "songyutong@campuspulse.local", true, true, "ORGANIZER", 1,
                "学生发展中心", "天赐庄校区", "活动运营", "2020", "OTHER", "高频发布活动，负责多场跨校区线下交流。", "13511116666")));
        users.put("baikexin", ensureSeedUser(new SeedUser("baikexin", "20241032", "白可心", "baikexin@campuspulse.local", true, false, "USER", 1,
                "社会学院", "独墅湖校区", "社会工作", "2024", "UNDERGRAD", "资料完整但手机号未验证，喜欢先收藏后再决定是否报名。", "13511117777")));
        users.put("gubeichuan", ensureSeedUser(new SeedUser("gubeichuan", "20222109", "顾北川", "gubeichuan@campuspulse.local", true, false, "USER", 1,
                "计算机科学与技术学院", "独墅湖校区", null, "2022", "MASTER", null, null)));
        users.put("qiaoyiming", ensureSeedUser(new SeedUser("qiaoyiming", "20211111", "乔一鸣", "qiaoyiming@campuspulse.local", true, true, "USER", 0,
                "法学院", "天赐庄校区", "法学", "2021", "UNDERGRAD", "已被停用的测试账号，用于后台状态和历史数据观察。", "13511118888")));

        ensureInterests(users.get("linzhixia"), List.of("学术讲座", "竞赛组队", "校园生活"));
        ensureInterests(users.get("chenze"), List.of("运动健身", "校园生活"));
        ensureInterests(users.get("xuan"), List.of("文艺演出", "兴趣社交"));
        ensureInterests(users.get("shenyi"), List.of("志愿服务", "校园生活"));
        ensureInterests(users.get("gumingzhe"), List.of("学术讲座", "竞赛组队", "兼职实习"));
        ensureInterests(users.get("tangshuning"), List.of("考研搭子", "兴趣社交", "校园生活"));
        ensureInterests(users.get("luqinghe"), List.of("兼职实习", "兴趣社交"));
        ensureInterests(users.get("songyutong"), List.of("校园生活", "学术讲座"));
        ensureInterests(users.get("baikexin"), List.of("志愿服务", "兴趣社交"));
        ensureInterests(users.get("gubeichuan"), List.of("竞赛组队", "学术讲座"));
        ensureAdditionalDemoUsers(users);

        Map<String, Long> activities = new LinkedHashMap<>();
        activities.put("ai", ensureActivity(new SeedActivity("人工智能与未来社会",
                "围绕生成式 AI、校园场景应用与未来职业方向展开分享，现场设有提问互动与交流环节。",
                "独墅湖校区图书馆报告厅",
                LocalDateTime.now().plusDays(2).withHour(19).withMinute(0),
                LocalDateTime.now().plusDays(2).withHour(21).withMinute(0),
                users.get("org"),
                "/assets/images/campuspulse.svg",
                120, "PUBLISHED", "APPROVED", true, true,
                List.of("学术讲座", "校园生活"))));
        activities.put("basketball", ensureActivity(new SeedActivity("校园 3v3 篮球巅峰赛",
                "面向全校开放的 3v3 篮球赛，欢迎班级或自由组队报名参加，现场有啦啦队与摄影记录。",
                "阳澄湖校区东区体育馆",
                LocalDateTime.now().minusDays(1).withHour(14).withMinute(0),
                LocalDateTime.now().minusDays(1).withHour(18).withMinute(0),
                users.get("chenze"),
                "/assets/images/campuspulse.svg",
                40, "PUBLISHED", "APPROVED", false, true,
                List.of("运动健身", "校园生活"))));
        activities.put("volunteer", ensureActivity(new SeedActivity("春季志愿者招募行动",
                "面向校内外社区服务点招募志愿者，主要负责秩序维护、引导和信息登记。",
                "天赐庄校区学生事务中心一楼",
                LocalDateTime.now().plusDays(4).withHour(13).withMinute(30),
                LocalDateTime.now().plusDays(4).withHour(17).withMinute(30),
                users.get("org"),
                "/assets/images/campuspulse.svg",
                60, "PUBLISHED", "APPROVED", true, true,
                List.of("志愿服务", "校园生活"))));
        activities.put("music", ensureActivity(new SeedActivity("校园音乐夜",
                "草坪开放式音乐演出，欢迎同学们带上朋友一起参加，也欢迎报名成为现场志愿者。",
                "天赐庄校区钟楼前草坪",
                LocalDateTime.now().plusDays(6).withHour(18).withMinute(30),
                LocalDateTime.now().plusDays(6).withHour(21).withMinute(0),
                users.get("xuan"),
                "/assets/images/campuspulse.svg",
                200, "PUBLISHED", "APPROVED", false, true,
                List.of("文艺演出", "兴趣社交"))));
        activities.put("startup_pending", ensureActivity(new SeedActivity("创新创业训练营",
                "两天一夜的项目路演工作坊，包含选题、答辩、BP 打磨和团队协作训练。",
                "独墅湖创新港 302",
                LocalDateTime.now().plusDays(9).withHour(9).withMinute(0),
                LocalDateTime.now().plusDays(10).withHour(17).withMinute(0),
                users.get("songyutong"),
                "/assets/images/campuspulse.svg",
                80, "PUBLISHED", "PENDING", false, true,
                List.of("竞赛组队", "学术讲座"))));
        activities.put("resume", ensureActivity(new SeedActivity("简历门诊与实习分享",
                "面向求职与实习准备同学的线下交流，包含简历一对一建议和岗位投递经验分享。",
                "天赐庄校区就业中心",
                LocalDateTime.now().plusDays(8).withHour(15).withMinute(0),
                LocalDateTime.now().plusDays(8).withHour(17).withMinute(0),
                users.get("linzhixia"),
                "/assets/images/campuspulse.svg",
                50, "PUBLISHED", "APPROVED", true, true,
                List.of("兼职实习", "校园生活"))));
        activities.put("running", ensureActivity(new SeedActivity("苏大夜跑打卡计划",
                "今晚开始的夜跑打卡活动，欢迎想规律运动、互相监督的同学加入。",
                "未来校区操场北门",
                LocalDateTime.now().plusHours(8).withMinute(0),
                LocalDateTime.now().plusHours(11).withMinute(0),
                users.get("chenze"),
                "/assets/images/campuspulse.svg",
                30, "PUBLISHED", "APPROVED", false, true,
                List.of("运动健身", "兴趣社交"))));
        activities.put("postgrad_rejected", ensureActivity(new SeedActivity("保研经验面对面",
                "邀请往届学长姐分享保研材料准备、夏令营与面试经验。",
                "独墅湖校区学生活动中心 208",
                LocalDateTime.now().plusDays(12).withHour(19).withMinute(30),
                LocalDateTime.now().plusDays(12).withHour(21).withMinute(0),
                users.get("songyutong"),
                "/assets/images/campuspulse.svg",
                90, "PUBLISHED", "REJECTED", false, true,
                List.of("学术讲座", "校园生活"))));
        activities.put("mind_cancelled", ensureActivity(new SeedActivity("心理减压工作坊",
                "面向期中周的减压工作坊，包含正念、呼吸练习和小组陪伴交流。",
                "天赐庄校区心理中心 302",
                LocalDateTime.now().plusDays(5).withHour(14).withMinute(0),
                LocalDateTime.now().plusDays(5).withHour(16).withMinute(0),
                users.get("songyutong"),
                "/assets/images/campuspulse.svg",
                40, "CANCELLED", "APPROVED", false, true,
                List.of("校园生活", "兴趣社交"))));
        activities.put("long_title", ensureActivity(new SeedActivity("这是一场标题特别长特别长用于观察活动卡片和详情排版是否会出现换行错位与按钮挤压的跨校区交流工作坊",
                "这条活动数据专门用于观察超长标题、超长描述、跨校区地点与无封面图时，首页卡片、活动详情、管理页和通知里是否还保持清晰排版。活动内容包括跨校区交通、主持串场、资料领取和问答交流。",
                "天赐庄校区主会场 + 独墅湖校区分会场线上联动",
                LocalDateTime.now().plusDays(14).withHour(13).withMinute(30),
                LocalDateTime.now().plusDays(14).withHour(17).withMinute(30),
                users.get("songyutong"),
                null,
                35, "PUBLISHED", "APPROVED", false, true,
                List.of("校园生活", "学术讲座", "兴趣社交"))));
        activities.put("cv_teaming_closed", ensureActivity(new SeedActivity("计算机视觉竞赛冲刺分享会",
                "围绕计算机视觉竞赛赛题复现、答辩准备和时间安排做最后冲刺分享。",
                "独墅湖校区创新港 501",
                LocalDateTime.now().plusDays(3).withHour(18).withMinute(30),
                LocalDateTime.now().plusDays(3).withHour(21).withMinute(0),
                users.get("org"),
                "/assets/images/campuspulse.svg",
                24, "PUBLISHED", "APPROVED", false, false,
                List.of("竞赛组队", "学术讲座"))));
        activities.put("old_chat_closed", ensureActivity(new SeedActivity("旧群聊回看测试活动",
                "这个活动用于观察活动群聊关闭后，历史消息是否被保留，以及入口是否按预期消失。",
                "独墅湖校区公共教学楼 C102",
                LocalDateTime.now().plusDays(7).withHour(18).withMinute(0),
                LocalDateTime.now().plusDays(7).withHour(20).withMinute(0),
                users.get("linzhixia"),
                "/assets/images/campuspulse.svg",
                18, "PUBLISHED", "APPROVED", false, true,
                List.of("校园生活", "兴趣社交"))));
        activities.put("math_full", ensureActivity(new SeedActivity("数学建模赛前密训",
                "比赛前的集中训练，包含题型拆解、LaTeX 写作和展示答辩。",
                "独墅湖校区理工楼 406",
                LocalDateTime.now().plusDays(1).withHour(19).withMinute(0),
                LocalDateTime.now().plusDays(1).withHour(22).withMinute(0),
                users.get("songyutong"),
                "/assets/images/campuspulse.svg",
                3, "PUBLISHED", "APPROVED", false, true,
                List.of("竞赛组队", "学术讲座"))));
        activities.put("intl_lunch", ensureActivity(new SeedActivity("国际交流分享午餐会",
                "轻松的小型午餐交流，适合想练口语、结识交换生或了解留学申请的同学。",
                "天赐庄校区留学生之家",
                LocalDateTime.now().plusDays(21).withHour(12).withMinute(0),
                LocalDateTime.now().plusDays(21).withHour(13).withMinute(30),
                users.get("songyutong"),
                "/assets/images/campuspulse.svg",
                20, "PUBLISHED", "APPROVED", false, true,
                List.of("兴趣社交", "校园生活"))));
        activities.put("book_drive", ensureActivity(new SeedActivity("24 小时公益捐书接力",
                "跨校区连续 24 小时捐书接力，适合验证正在进行中的活动展示和提醒逻辑。",
                "未来校区图书馆门口",
                LocalDateTime.now().minusHours(3),
                LocalDateTime.now().plusHours(18),
                users.get("org"),
                "/assets/images/campuspulse.svg",
                80, "PUBLISHED", "APPROVED", true, true,
                List.of("志愿服务", "校园生活"))));
        ensureAdditionalDemoActivities(users, activities);

        ensureFeatured(activities.get("ai"), 90, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7));
        ensureFeatured(activities.get("music"), 78, LocalDateTime.now(), LocalDateTime.now().plusDays(12));
        ensureFeatured(activities.get("math_full"), 95, LocalDateTime.now(), LocalDateTime.now().plusDays(3));
        ensureFeatured(activities.get("intl_lunch"), 40, LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(25));

        Map<String, Long> teams = new LinkedHashMap<>();
        teams.put("ai_team", ensureTeam(new SeedTeam(activities.get("ai"), "学术讲座｜AI 讲座志愿接待小组",
                "想一起负责签到、引导和提问整理的同学可以加入，适合沟通耐心好、时间稳定的成员。",
                users.get("org"), 5, "OPEN",
                LocalDateTime.now().plusDays(2).withHour(17).withMinute(30),
                LocalDateTime.now().plusDays(2).withHour(21).withMinute(30))));
        teams.put("startup_team", ensureTeam(new SeedTeam(activities.get("startup_pending"), "竞赛组队｜服务外包赛缺前端",
                "已有产品和算法同学，想再找 1 名能做展示型页面的前端同学一起冲刺答辩。",
                users.get("chenze"), 5, "OPEN",
                LocalDateTime.now().plusDays(7).withHour(19).withMinute(0),
                LocalDateTime.now().plusDays(10).withHour(22).withMinute(0))));
        teams.put("badminton", ensureTeam(new SeedTeam(null, "运动健身｜周三羽毛球搭子",
                "每周三晚上打两个小时，水平不限，主要是规律运动、互相拉练。",
                users.get("xuan"), 4, "OPEN",
                LocalDateTime.now().plusDays(1).withHour(20).withMinute(0),
                LocalDateTime.now().plusDays(1).withHour(22).withMinute(0))));
        teams.put("study", ensureTeam(new SeedTeam(null, "考研搭子｜图书馆晚间互相监督",
                "工作日晚间固定打卡，适合想找稳定学习节奏和互相监督的同学。",
                users.get("shenyi"), 3, "OPEN",
                LocalDateTime.now().plusHours(6),
                LocalDateTime.now().plusHours(10))));
        teams.put("resume_team", ensureTeam(new SeedTeam(activities.get("resume"), "兼职实习｜简历互改答辩组",
                "围绕简历门诊活动提前准备展示稿和自我介绍，顺带互看简历和项目经历。",
                users.get("linzhixia"), 6, "OPEN",
                LocalDateTime.now().plusDays(7).withHour(19).withMinute(0),
                LocalDateTime.now().plusDays(8).withHour(17).withMinute(30))));
        teams.put("cv_team", ensureTeam(new SeedTeam(activities.get("cv_teaming_closed"), "竞赛组队｜计算机视觉复现答辩组",
                "这个队伍保留用于验证活动关闭联动组队后，历史队伍仍可在组队页和我的队伍中继续访问。",
                users.get("gumingzhe"), 4, "OPEN",
                LocalDateTime.now().plusDays(2).withHour(18).withMinute(0),
                LocalDateTime.now().plusDays(4).withHour(23).withMinute(0))));
        teams.put("running_team", ensureTeam(new SeedTeam(activities.get("running"), "运动健身｜夜跑配速小队",
                "今晚一起跑 5km，配速相近的同学欢迎加入，结束后顺便拉伸交流。",
                users.get("chenze"), 6, "OPEN",
                LocalDateTime.now().plusHours(7),
                LocalDateTime.now().plusHours(10))));
        teams.put("math_team", ensureTeam(new SeedTeam(activities.get("math_full"), "竞赛组队｜数学建模竞赛缺一个写作手",
                "队伍已经接近满员，用来测试满员后仍可继续管理申请。",
                users.get("songyutong"), 3, "OPEN",
                LocalDateTime.now().plusHours(18),
                LocalDateTime.now().plusDays(1).withHour(23).withMinute(0))));
        teams.put("intl_team", ensureTeam(new SeedTeam(activities.get("intl_lunch"), "兴趣社交｜国际交流饭搭子",
                "想找一起吃饭、练口语和了解交换项目的同学，欢迎轻松加入。",
                users.get("baikexin"), 5, "OPEN",
                LocalDateTime.now().plusDays(20).withHour(12).withMinute(0),
                LocalDateTime.now().plusDays(20).withHour(14).withMinute(0))));
        ensureAdditionalDemoTeams(users, activities, teams);

        ensureTeamMember(teams.get("ai_team"), users.get("org"), "CREATOR", "ACTIVE");
        ensureTeamMember(teams.get("ai_team"), users.get("linzhixia"), "MEMBER", "ACTIVE");
        ensureTeamMember(teams.get("ai_team"), users.get("xuan"), "MEMBER", "ACTIVE");
        ensureTeamMember(teams.get("startup_team"), users.get("chenze"), "CREATOR", "ACTIVE");
        ensureTeamMember(teams.get("startup_team"), users.get("shenyi"), "MEMBER", "ACTIVE");
        ensureTeamMember(teams.get("badminton"), users.get("xuan"), "CREATOR", "ACTIVE");
        ensureTeamMember(teams.get("study"), users.get("shenyi"), "CREATOR", "ACTIVE");
        ensureTeamMember(teams.get("resume_team"), users.get("linzhixia"), "CREATOR", "ACTIVE");
        ensureTeamMember(teams.get("resume_team"), users.get("chenze"), "MEMBER", "ACTIVE");
        ensureTeamMember(teams.get("cv_team"), users.get("gumingzhe"), "CREATOR", "ACTIVE");
        ensureTeamMember(teams.get("cv_team"), users.get("luqinghe"), "MEMBER", "ACTIVE");
        ensureTeamMember(teams.get("running_team"), users.get("chenze"), "CREATOR", "ACTIVE");
        ensureTeamMember(teams.get("running_team"), users.get("tangshuning"), "MEMBER", "ACTIVE");
        ensureTeamMember(teams.get("math_team"), users.get("songyutong"), "CREATOR", "ACTIVE");
        ensureTeamMember(teams.get("math_team"), users.get("gumingzhe"), "MEMBER", "ACTIVE");
        ensureTeamMember(teams.get("math_team"), users.get("hezhiyuan"), "MEMBER", "ACTIVE");
        ensureTeamMember(teams.get("intl_team"), users.get("baikexin"), "CREATOR", "ACTIVE");
        ensureTeamMember(teams.get("intl_team"), users.get("xuan"), "MEMBER", "ACTIVE");

        ensureTeamJoinRequest(teams.get("ai_team"), users.get("linzhixia"), "可以负责签到和现场提问整理，希望一起把讲座流程做顺。", "APPROVED", LocalDateTime.now().minusDays(4));
        ensureTeamJoinRequest(teams.get("ai_team"), users.get("xuan"), "我愿意协助串场、整理问题和现场秩序维护。", "APPROVED", LocalDateTime.now().minusDays(4).plusMinutes(25));
        ensureTeamJoinRequest(teams.get("study"), users.get("gumingzhe"), "想找晚间固定自习搭子，互相监督效率和进度。", "PENDING", LocalDateTime.now().minusDays(3));
        ensureTeamJoinRequest(teams.get("resume_team"), users.get("shenyi"), "想一起准备简历展示，顺带请教项目经历怎么讲。", "REJECTED", LocalDateTime.now().minusDays(2));
        ensureTeamJoinRequest(teams.get("math_team"), users.get("baikexin"), "我可以补写作和摘要整理，想冲一下国奖。", "PENDING", LocalDateTime.now().minusHours(16));
        ensureTeamJoinRequest(teams.get("intl_team"), users.get("tangshuning"), "我想练口语，也愿意协助破冰互动。", "PENDING", LocalDateTime.now().minusHours(8));

        ensureRegistration(activities.get("ai"), users.get("linzhixia"), "林知夏", "13812345678", "计算机科学与技术学院", "想了解 AI 校园应用，也愿意协助现场提问整理。", "APPROVED", LocalDateTime.now().minusDays(6));
        ensureRegistration(activities.get("ai"), users.get("xuan"), "许安", "13688886666", "文学院", "对讲座主题感兴趣，也想认识更多做产品设计的同学。", "APPLIED", LocalDateTime.now().minusDays(5));
        ensureRegistration(activities.get("ai"), users.get("hezhiyuan"), "贺知远", "13511114444", "计算机科学与技术学院", "想看看有没有 CV 和多模态方向的分享。", "REJECTED", LocalDateTime.now().minusDays(4));
        ensureRegistration(activities.get("ai"), users.get("baikexin"), "白可心", "13511117777", "社会学院", "先报个名，如果时间冲突可能会取消。", "CANCELLED", LocalDateTime.now().minusDays(3));
        ensureRegistration(activities.get("ai"), users.get("gumingzhe"), "顾明哲", "13511112222", "电子信息学院", "想提前和讲者沟通一些研究生阶段的技术路线问题。", "APPROVED", LocalDateTime.now().minusDays(2));
        ensureRegistration(activities.get("volunteer"), users.get("shenyi"), "沈奕", "13566667777", "管理学院", "愿意做现场引导和数据统计。", "APPROVED", LocalDateTime.now().minusDays(4));
        ensureRegistration(activities.get("volunteer"), users.get("tangshuning"), "唐舒宁", "13511113333", "教育学院", "想参加陪伴式志愿服务。", "APPLIED", LocalDateTime.now().minusDays(2));
        ensureRegistration(activities.get("volunteer"), users.get("luqinghe"), "陆青禾", "13511115555", "商学院", "如果流程顺畅，也可以协助物资整理。", "REJECTED", LocalDateTime.now().minusDays(1));
        ensureRegistration(activities.get("resume"), users.get("shenyi"), "沈奕", "13566667777", "管理学院", "主要想看看简历结构怎么优化，顺便听学长姐分享。", "APPLIED", LocalDateTime.now().minusDays(3));
        ensureRegistration(activities.get("resume"), users.get("gubeichuan"), "顾北川", "13511119999", "计算机科学与技术学院", "想请教研究生投实习时如何讲项目经历。", "APPROVED", LocalDateTime.now().minusDays(2));
        ensureRegistration(activities.get("cv_teaming_closed"), users.get("chenze"), "陈泽", "13722223333", "体育学院", "虽然不是专业方向，但想了解一下答辩节奏。", "APPROVED", LocalDateTime.now().minusHours(30));
        ensureRegistration(activities.get("cv_teaming_closed"), users.get("baikexin"), "白可心", "13511117777", "社会学院", "想旁听分享会，也顺便看队伍协作流程。", "APPROVED", LocalDateTime.now().minusHours(27));
        ensureRegistration(activities.get("old_chat_closed"), users.get("xuan"), "许安", "13688886666", "文学院", "想验证群聊关闭后入口和历史消息是否一致。", "APPROVED", LocalDateTime.now().minusHours(20));
        ensureRegistration(activities.get("old_chat_closed"), users.get("tangshuning"), "唐舒宁", "13511113333", "教育学院", "之前通过了，后来又撤回。", "CANCELLED", LocalDateTime.now().minusHours(18));
        ensureRegistration(activities.get("math_full"), users.get("luqinghe"), "陆青禾", "13511115555", "商学院", "我可以负责展示和金融建模部分。", "APPROVED", LocalDateTime.now().minusHours(12));
        ensureRegistration(activities.get("math_full"), users.get("gumingzhe"), "顾明哲", "13511112222", "电子信息学院", "愿意负责代码和结果可视化。", "APPROVED", LocalDateTime.now().minusHours(11));
        ensureRegistration(activities.get("math_full"), users.get("hezhiyuan"), "贺知远", "13511114444", "计算机科学与技术学院", "可以补算法与论文阅读。", "APPROVED", LocalDateTime.now().minusHours(10));
        ensureRegistration(activities.get("math_full"), users.get("tangshuning"), "唐舒宁", "13511113333", "教育学院", "我擅长文字表达和演示梳理。", "APPLIED", LocalDateTime.now().minusHours(9));
        ensureRegistration(activities.get("math_full"), users.get("baikexin"), "白可心", "13511117777", "社会学院", "我能补摘要写作和答辩排练。", "APPLIED", LocalDateTime.now().minusHours(8));
        ensureRegistration(activities.get("math_full"), users.get("gubeichuan"), "顾北川", "13511119999", "计算机科学与技术学院", "可以补 LaTeX 和建模复盘。", "APPLIED", LocalDateTime.now().minusHours(7));
        ensureRegistration(activities.get("mind_cancelled"), users.get("tangshuning"), "唐舒宁", "13511113333", "教育学院", "本来很期待参加，后来活动取消。", "APPROVED", LocalDateTime.now().minusDays(1));
        ensureRegistration(activities.get("intl_lunch"), users.get("baikexin"), "白可心", "13511117777", "社会学院", "想认识国际交流方向的同学。", "APPROVED", LocalDateTime.now().minusHours(5));

        ensureFavorite(users.get("linzhixia"), activities.get("ai"));
        ensureFavorite(users.get("linzhixia"), activities.get("music"));
        ensureFavorite(users.get("linzhixia"), activities.get("resume"));
        ensureFavorite(users.get("luqinghe"), activities.get("resume"));
        ensureFavorite(users.get("luqinghe"), activities.get("intl_lunch"));
        ensureFavorite(users.get("baikexin"), activities.get("mind_cancelled"));
        ensureFavorite(users.get("baikexin"), activities.get("cv_teaming_closed"));
        ensureFavorite(users.get("gumingzhe"), activities.get("math_full"));
        ensureFavorite(users.get("tangshuning"), activities.get("volunteer"));

        ensureTeamMessage(teams.get("ai_team"), users.get("org"), "欢迎大家进组，周五晚一起确认分工。", LocalDateTime.now().minusDays(3));
        ensureTeamMessage(teams.get("ai_team"), users.get("linzhixia"), "我可以负责签到和简单拍照。", LocalDateTime.now().minusDays(3).plusMinutes(6));
        ensureTeamMessage(teams.get("ai_team"), users.get("xuan"), "那我来整理现场问题和主持串场。", LocalDateTime.now().minusDays(3).plusMinutes(12));
        ensureTeamMessage(teams.get("resume_team"), users.get("linzhixia"), "这周先把每个人的简历版本传一下，我统一整理。", LocalDateTime.now().minusDays(2));
        ensureTeamMessage(teams.get("resume_team"), users.get("chenze"), "收到，我今晚把项目经历那部分再润色一下。", LocalDateTime.now().minusDays(2).plusMinutes(8));
        ensureTeamMessage(teams.get("math_team"), users.get("songyutong"), "队伍已满员，但还可以继续调整名单；如果成员沟通后不合适，随时可以再协商调整。", LocalDateTime.now().minusHours(6));

        ensureDmMessage(users.get("org"), users.get("linzhixia"), "你好，看到你报名了讲座，后续有空可以一起准备提问。", LocalDateTime.now().minusDays(2).plusHours(1));
        ensureDmMessage(users.get("linzhixia"), users.get("org"), "好呀，我也想提前了解一下讲座流程。", LocalDateTime.now().minusDays(2).plusHours(1).plusMinutes(4));
        ensureDmMessage(users.get("org"), users.get("linzhixia"), "今晚我会把流程发在群里，你也可以先看看活动详情页。", LocalDateTime.now().minusDays(2).plusHours(1).plusMinutes(8));
        ensureDmMessage(users.get("xuan"), users.get("linzhixia"), "音乐夜你去吗？要不要一起提前占位置。", LocalDateTime.now().minusDays(1).plusHours(2));
        ensureDmMessage(users.get("linzhixia"), users.get("xuan"), "可以，我大概六点半到。", LocalDateTime.now().minusDays(1).plusHours(2).plusMinutes(3));
        ensureDmMessage(users.get("gumingzhe"), users.get("songyutong"), "建模密训还有名额吗？我想先沟通一下分工。", LocalDateTime.now().minusHours(9));

        ensureActivityChatMessage(activities.get("ai"), users.get("org"), "欢迎进入活动群聊，有问题可以直接在这里问。", LocalDateTime.now().minusDays(2).plusHours(9));
        ensureActivityChatMessage(activities.get("ai"), users.get("linzhixia"), "想问一下讲座结束后是否有线下交流环节？", LocalDateTime.now().minusDays(2).plusHours(9).plusMinutes(5));
        ensureActivityChatMessage(activities.get("ai"), users.get("org"), "有的，最后会留 20 分钟交流。", LocalDateTime.now().minusDays(2).plusHours(9).plusMinutes(8));
        ensureActivityChatMessage(activities.get("resume"), users.get("linzhixia"), "简历门诊活动当天请大家带纸质版简历。", LocalDateTime.now().minusDays(1).plusHours(10));
        ensureActivityChatMessage(activities.get("old_chat_closed"), users.get("linzhixia"), "这个群聊稍后会关闭，但历史消息需要保留。", LocalDateTime.now().minusHours(15));
        ensureActivityChatMessage(activities.get("old_chat_closed"), users.get("xuan"), "收到，我先把问题清单整理一下。", LocalDateTime.now().minusHours(14).plusMinutes(8));

        ensureNotification(users.get("linzhixia"), "TEAM_JOIN", "你的队伍有新动态", "“学术讲座｜AI 讲座志愿接待小组” 已有 3 名成员加入，可以进入队伍聊天继续沟通。");
        ensureNotification(users.get("linzhixia"), "SYSTEM", "活动提醒", "你报名的“人工智能与未来社会”将于后天 19:00 开始。");
        ensureNotification(users.get("linzhixia"), "AUDIT", "发布成功", "你发布的“简历门诊与实习分享”已经审核通过并上线展示。");
        ensureNotification(users.get("org"), "ACTIVITY_REGISTER", "有新报名申请", "顾明哲 申请报名《人工智能与未来社会》，请及时审批。");
        ensureNotification(users.get("songyutong"), "ADMIN_ACTIVITY_AUDIT", "管理员已更新你的活动审核结果", "活动《创新创业训练营》当前审核状态已变更为 PENDING。");
        ensureNotification(users.get("songyutong"), "ACTIVITY_REGISTER_CANCELLED", "报名已取消", "白可心 取消了《人工智能与未来社会》的报名。");

        ensureEvent(users.get("linzhixia"), activities.get("ai"), "CLICK", null, LocalDateTime.now().minusDays(2));
        ensureEvent(users.get("linzhixia"), activities.get("resume"), "FAVORITE", null, LocalDateTime.now().minusDays(1));
        ensureEvent(users.get("linzhixia"), activities.get("resume"), "REGISTER", null, LocalDateTime.now().minusHours(20));
        ensureEvent(users.get("gumingzhe"), activities.get("math_full"), "CLICK", null, LocalDateTime.now().minusHours(11));
        ensureEvent(users.get("gumingzhe"), activities.get("math_full"), "CLICK", null, LocalDateTime.now().minusHours(10));
        ensureEvent(users.get("gumingzhe"), activities.get("math_full"), "FAVORITE", null, LocalDateTime.now().minusHours(9));
        ensureEvent(users.get("luqinghe"), activities.get("intl_lunch"), "CLICK", null, LocalDateTime.now().minusHours(6));
        ensureEvent(users.get("luqinghe"), activities.get("intl_lunch"), "FAVORITE", null, LocalDateTime.now().minusHours(5));
        ensureEvent(users.get("chenze"), activities.get("running"), "CLICK", null, LocalDateTime.now().minusHours(4));

        ensureRecommendationDemoData(users, activities, teams);
        contentTranslations.bindSeeds("ACTIVITY", activities);
        contentTranslations.bindSeeds("TEAM", teams);
    }

    private long ensureOrganizer() {
        Long orgId = null;
        try {
            orgId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = 'org' LIMIT 1", Long.class);
        } catch (Exception ignored) {
        }
        if (orgId == null) {
            jdbcTemplate.update("""
                    INSERT INTO users (username, password_hash, nickname, email, email_verified, phone_verified, role, status, college, campus, phone)
                    VALUES (?, ?, ?, ?, 1, 1, 'ORGANIZER', 1, ?, ?, ?)
                    """, "org", passwordHasher.hash("org123"), "活动组织者", "org@campuspulse.local", "学生会", "独墅湖校区", "13800000000");
        } else {
            jdbcTemplate.update("""
                    UPDATE users
                    SET nickname = ?, email = ?, email_verified = 1, phone = ?, phone_verified = 1, role = 'ORGANIZER', status = 1, college = ?, campus = ?
                    WHERE username = 'org'
                    """, "活动组织者", "org@campuspulse.local", "13800000000", "学生会", "独墅湖校区");
        }
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = 'org' LIMIT 1", Long.class);
    }

    private long ensureSeedUser(SeedUser seed) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id FROM users WHERE username = ? LIMIT 1", seed.username());
        if (rows.isEmpty()) {
            jdbcTemplate.update("""
                    INSERT INTO users (username, student_no, password_hash, nickname, college, campus, major, grade, education_level, bio,
                                       email, email_verified, phone, phone_verified, role, status, avatar_url)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    seed.username(),
                    seed.studentNo(),
                    passwordHasher.hash("demo12345"),
                    seed.nickname(),
                    seed.college(),
                    seed.campus(),
                    seed.major(),
                    seed.grade(),
                    seed.educationLevel(),
                    seed.bio(),
                    seed.email(),
                    seed.emailVerified() ? 1 : 0,
                    seed.phone(),
                    seed.phoneVerified() ? 1 : 0,
                    seed.role(),
                    seed.status(),
                    null
            );
        } else {
            jdbcTemplate.update("""
                    UPDATE users
                    SET student_no = ?, nickname = ?, college = ?, campus = ?, major = ?, grade = ?, education_level = ?, bio = ?,
                        email = ?, email_verified = ?, phone = ?, phone_verified = ?, role = ?, status = ?
                    WHERE username = ?
                    """,
                    seed.studentNo(),
                    seed.nickname(),
                    seed.college(),
                    seed.campus(),
                    seed.major(),
                    seed.grade(),
                    seed.educationLevel(),
                    seed.bio(),
                    seed.email(),
                    seed.emailVerified() ? 1 : 0,
                    seed.phone(),
                    seed.phoneVerified() ? 1 : 0,
                    seed.role(),
                    seed.status(),
                    seed.username()
            );
        }
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ? LIMIT 1", Long.class, seed.username());
    }

    private void ensureInterests(long userId, List<String> interests) {
        if (interests == null) return;
        for (String interest : interests) {
            Long tagId = jdbcTemplate.queryForObject("SELECT id FROM tags WHERE name = ? LIMIT 1", Long.class, interest);
            if (tagId != null) {
                jdbcTemplate.update("INSERT IGNORE INTO user_interest (user_id, tag_id) VALUES (?, ?)", userId, tagId);
            }
        }
    }

    private long ensureActivity(SeedActivity seed) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id FROM activities WHERE title = ? AND organizer_id = ? LIMIT 1", seed.title(), seed.organizerId());
        if (rows.isEmpty()) {
            jdbcTemplate.update("""
                    INSERT INTO activities (title, description, location, start_time, end_time, organizer_id, cover_url, max_participants, status, audit_status, chat_enabled, teaming_enabled)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    seed.title(), seed.description(), seed.location(),
                    Timestamp.valueOf(seed.startTime()),
                    seed.endTime() == null ? null : Timestamp.valueOf(seed.endTime()),
                    seed.organizerId(), seed.coverUrl(), seed.maxParticipants(),
                    seed.status(), seed.auditStatus(), seed.chatEnabled() ? 1 : 0, seed.teamingEnabled() ? 1 : 0
            );
        } else {
            jdbcTemplate.update("""
                    UPDATE activities
                    SET description = ?, location = ?, start_time = ?, end_time = ?, cover_url = ?, max_participants = ?, status = ?, audit_status = ?, chat_enabled = ?, teaming_enabled = ?
                    WHERE id = ?
                    """,
                    seed.description(), seed.location(),
                    Timestamp.valueOf(seed.startTime()),
                    seed.endTime() == null ? null : Timestamp.valueOf(seed.endTime()),
                    seed.coverUrl(), seed.maxParticipants(),
                    seed.status(), seed.auditStatus(), seed.chatEnabled() ? 1 : 0, seed.teamingEnabled() ? 1 : 0,
                    ((Number) rows.get(0).get("id")).longValue()
            );
        }
        long activityId = jdbcTemplate.queryForObject("SELECT id FROM activities WHERE title = ? AND organizer_id = ? LIMIT 1", Long.class, seed.title(), seed.organizerId());
        jdbcTemplate.update("DELETE FROM activity_tag WHERE activity_id = ?", activityId);
        for (String tagName : seed.tags()) {
            Long tagId = jdbcTemplate.queryForObject("SELECT id FROM tags WHERE name = ? LIMIT 1", Long.class, tagName);
            if (tagId != null) {
                jdbcTemplate.update("INSERT IGNORE INTO activity_tag (activity_id, tag_id) VALUES (?, ?)", activityId, tagId);
            }
        }
        return activityId;
    }

    private long ensureTeam(SeedTeam seed) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id FROM teams WHERE title = ? AND creator_id = ? LIMIT 1", seed.title(), seed.creatorId());
        if (rows.isEmpty()) {
            jdbcTemplate.update("""
                    INSERT INTO teams (activity_id, title, description, start_time, end_time, creator_id, max_members, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    seed.activityId(), seed.title(), seed.description(),
                    seed.startTime() == null ? null : Timestamp.valueOf(seed.startTime()),
                    seed.endTime() == null ? null : Timestamp.valueOf(seed.endTime()),
                    seed.creatorId(), seed.maxMembers(), seed.status()
            );
        } else {
            jdbcTemplate.update("""
                    UPDATE teams
                    SET activity_id = ?, description = ?, start_time = ?, end_time = ?, max_members = ?, status = ?
                    WHERE id = ?
                    """,
                    seed.activityId(), seed.description(),
                    seed.startTime() == null ? null : Timestamp.valueOf(seed.startTime()),
                    seed.endTime() == null ? null : Timestamp.valueOf(seed.endTime()),
                    seed.maxMembers(), seed.status(),
                    ((Number) rows.get(0).get("id")).longValue()
            );
        }
        return jdbcTemplate.queryForObject("SELECT id FROM teams WHERE title = ? AND creator_id = ? LIMIT 1", Long.class, seed.title(), seed.creatorId());
    }

    private void ensureTeamMember(long teamId, long userId, String role, String status) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM team_member WHERE team_id = ? AND user_id = ?", Long.class, teamId, userId);
        if (count != null && count > 0) {
            jdbcTemplate.update("UPDATE team_member SET role = ?, status = ? WHERE team_id = ? AND user_id = ?", role, status, teamId, userId);
        } else {
            jdbcTemplate.update("INSERT INTO team_member (team_id, user_id, role, status) VALUES (?, ?, ?, ?)", teamId, userId, role, status);
        }
    }

    private void ensureTeamJoinRequest(long teamId, long userId, String message, String status, LocalDateTime createdAt) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM team_join_request WHERE team_id = ? AND user_id = ?", Long.class, teamId, userId);
        if (count != null && count > 0) {
            jdbcTemplate.update("UPDATE team_join_request SET message = ?, status = ?, created_at = ? WHERE team_id = ? AND user_id = ?",
                    message, status, Timestamp.valueOf(createdAt), teamId, userId);
        } else {
            jdbcTemplate.update("INSERT INTO team_join_request (team_id, user_id, message, status, created_at) VALUES (?, ?, ?, ?, ?)",
                    teamId, userId, message, status, Timestamp.valueOf(createdAt));
        }
    }

    private void ensureRegistration(long activityId, long userId, String realName, String phone, String college, String intro, String status, LocalDateTime createdAt) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM registrations WHERE activity_id = ? AND user_id = ?", Long.class, activityId, userId);
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE registrations
                    SET real_name = ?, phone = ?, college = ?, intro = ?, status = ?, created_at = ?
                    WHERE activity_id = ? AND user_id = ?
                    """, realName, phone, college, intro, status, Timestamp.valueOf(createdAt), activityId, userId);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO registrations (activity_id, user_id, real_name, phone, college, intro, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, activityId, userId, realName, phone, college, intro, status, Timestamp.valueOf(createdAt));
        }
    }

    private void ensureFavorite(long userId, long activityId) {
        jdbcTemplate.update("INSERT IGNORE INTO favorites (user_id, activity_id) VALUES (?, ?)", userId, activityId);
    }

    private void ensureFeatured(long activityId, int weight, LocalDateTime start, LocalDateTime end) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ops_featured_activity WHERE activity_id = ?", Long.class, activityId);
        if (count != null && count > 0) {
            jdbcTemplate.update("UPDATE ops_featured_activity SET weight = ?, start_time = ?, end_time = ? WHERE activity_id = ?",
                    weight, start == null ? null : Timestamp.valueOf(start), end == null ? null : Timestamp.valueOf(end), activityId);
        } else {
            jdbcTemplate.update("INSERT INTO ops_featured_activity (activity_id, weight, start_time, end_time) VALUES (?, ?, ?, ?)",
                    activityId, weight, start == null ? null : Timestamp.valueOf(start), end == null ? null : Timestamp.valueOf(end));
        }
    }

    private void ensureTeamMessage(long teamId, long senderId, String content, LocalDateTime createdAt) {
        ensureMessageByQuery("SELECT COUNT(*) FROM messages WHERE team_id = ? AND sender_id = ? AND content = ?",
                "INSERT INTO messages (team_id, sender_id, content, created_at) VALUES (?, ?, ?, ?)",
                List.of(teamId, senderId, content),
                List.of(teamId, senderId, content, Timestamp.valueOf(createdAt)));
    }

    private void ensureDmMessage(long senderId, long receiverId, String content, LocalDateTime createdAt) {
        ensureMessageByQuery("SELECT COUNT(*) FROM dm_message WHERE sender_id = ? AND receiver_id = ? AND content = ?",
                "INSERT INTO dm_message (sender_id, receiver_id, content, created_at) VALUES (?, ?, ?, ?)",
                List.of(senderId, receiverId, content),
                List.of(senderId, receiverId, content, Timestamp.valueOf(createdAt)));
    }

    private void ensureActivityChatMessage(long activityId, long senderId, String content, LocalDateTime createdAt) {
        ensureMessageByQuery("SELECT COUNT(*) FROM activity_chat_message WHERE activity_id = ? AND sender_id = ? AND content = ?",
                "INSERT INTO activity_chat_message (activity_id, sender_id, content, created_at) VALUES (?, ?, ?, ?)",
                List.of(activityId, senderId, content),
                List.of(activityId, senderId, content, Timestamp.valueOf(createdAt)));
    }

    private void ensureNotification(long userId, String type, String title, String content) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = ? AND title = ? AND content = ?",
                Long.class, userId, type, title, content);
        if (count == null || count == 0) {
            jdbcTemplate.update("INSERT INTO notifications (user_id, type, title, content) VALUES (?, ?, ?, ?)", userId, type, title, content);
        }
    }

    private void ensureEvent(long userId, long activityId, String eventType, String extraJson, LocalDateTime eventTime) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM event_log
                WHERE user_id = ? AND activity_id = ? AND event_type = ? AND event_time = ?
                """, Long.class, userId, activityId, eventType, Timestamp.valueOf(eventTime));
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                    INSERT INTO event_log (user_id, activity_id, event_type, event_time, extra_json)
                    VALUES (?, ?, ?, ?, ?)
                    """, userId, activityId, eventType, Timestamp.valueOf(eventTime), extraJson);
        }
    }

    private void ensureAdditionalDemoUsers(Map<String, Long> users) {
        List<SeedUser> seeds = List.of(
                new SeedUser("wangyi", "20223021", "王一", "wangyi@campuspulse.local", true, true, "USER", 1, "计算机科学与技术学院", "独墅湖校区", "数据科学与大数据技术", "2022", "UNDERGRAD", "关注数据分析、机器学习和项目协作。", "13900010001"),
                new SeedUser("liyun", "20233022", "李云", "liyun@campuspulse.local", true, true, "USER", 1, "外国语学院", "天赐庄校区", "英语", "2023", "UNDERGRAD", "喜欢国际交流、志愿服务和语言角。", "13900010002"),
                new SeedUser("zhaoran", "20214023", "赵然", "zhaoran@campuspulse.local", true, true, "USER", 1, "体育学院", "阳澄湖校区", "运动训练", "2021", "UNDERGRAD", "经常参加跑步、球类和户外活动。", "13900010003"),
                new SeedUser("sunhan", "20224024", "孙涵", "sunhan@campuspulse.local", true, true, "USER", 1, "艺术学院", "天赐庄校区", "视觉传达设计", "2022", "UNDERGRAD", "偏好展览、音乐会和活动视觉设计。", "13900010004"),
                new SeedUser("zhoumo", "20234025", "周墨", "zhoumo@campuspulse.local", true, true, "USER", 1, "商学院", "未来校区", "市场营销", "2023", "UNDERGRAD", "关注创业、实习、路演和社群运营。", "13900010005"),
                new SeedUser("wuyang", "20224026", "吴漾", "wuyang@campuspulse.local", true, true, "USER", 1, "材料与化学化工学部", "独墅湖校区", "材料科学", "2022", "MASTER", "喜欢学术讲座和科研经验分享。", "13900010006"),
                new SeedUser("zhengqiu", "20244027", "郑秋", "zhengqiu@campuspulse.local", true, true, "USER", 1, "教育学院", "天赐庄校区", "教育技术学", "2024", "UNDERGRAD", "希望找到学习搭子和课程项目队友。", "13900010007"),
                new SeedUser("gaoyi", "20214028", "高屹", "gaoyi@campuspulse.local", true, true, "USER", 1, "电子信息学院", "独墅湖校区", "通信工程", "2021", "UNDERGRAD", "关注嵌入式、物联网和竞赛组队。", "13900010008"),
                new SeedUser("fenglu", "20234029", "冯鹿", "fenglu@campuspulse.local", true, true, "USER", 1, "社会学院", "独墅湖校区", "社会学", "2023", "UNDERGRAD", "对公益、调研和校园社交活动感兴趣。", "13900010009"),
                new SeedUser("yexiao", "20224030", "叶晓", "yexiao@campuspulse.local", true, true, "USER", 1, "传媒学院", "天赐庄校区", "新闻传播学", "2022", "UNDERGRAD", "喜欢摄影、主持、活动记录和内容运营。", "13900010010"),
                new SeedUser("maolin", "20234031", "毛琳", "maolin@campuspulse.local", true, true, "USER", 1, "数学科学学院", "独墅湖校区", "统计学", "2023", "UNDERGRAD", "正在找数学建模和数据分析队友。", "13900010011"),
                new SeedUser("xieyu", "20214032", "谢予", "xieyu@campuspulse.local", true, true, "USER", 1, "法学院", "天赐庄校区", "法学", "2021", "UNDERGRAD", "关注模拟法庭、讲座和志愿服务。", "13900010012"),
                new SeedUser("caixuan", "20234033", "蔡萱", "caixuan@campuspulse.local", true, true, "USER", 1, "音乐学院", "天赐庄校区", "音乐表演", "2023", "UNDERGRAD", "常参加演出、排练和文艺志愿活动。", "13900010013"),
                new SeedUser("dongchen", "20224034", "董辰", "dongchen@campuspulse.local", true, true, "USER", 1, "计算机科学与技术学院", "独墅湖校区", "网络空间安全", "2022", "MASTER", "偏好安全竞赛、后端工程和技术分享。", "13900010014"),
                new SeedUser("hanxu", "20244035", "韩旭", "hanxu@campuspulse.local", true, true, "USER", 1, "医学部", "独墅湖校区", "临床医学", "2024", "UNDERGRAD", "关注健康科普、志愿服务和运动打卡。", "13900010015"),
                new SeedUser("qinyue", "20234036", "秦月", "qinyue@campuspulse.local", true, true, "USER", 1, "心理学系", "天赐庄校区", "应用心理学", "2023", "UNDERGRAD", "喜欢心理成长、学习陪伴和小组工作坊。", "13900010016")
        );
        for (SeedUser seed : seeds) {
            users.put(seed.username(), ensureSeedUser(seed));
        }
        ensureInterests(users.get("wangyi"), List.of("学术讲座", "竞赛组队", "兼职实习"));
        ensureInterests(users.get("liyun"), List.of("兴趣社交", "志愿服务", "校园生活"));
        ensureInterests(users.get("zhaoran"), List.of("运动健身", "校园生活"));
        ensureInterests(users.get("sunhan"), List.of("文艺演出", "兴趣社交"));
        ensureInterests(users.get("zhoumo"), List.of("兼职实习", "竞赛组队", "校园生活"));
        ensureInterests(users.get("wuyang"), List.of("学术讲座", "竞赛组队"));
        ensureInterests(users.get("zhengqiu"), List.of("考研搭子", "校园生活"));
        ensureInterests(users.get("gaoyi"), List.of("竞赛组队", "学术讲座"));
        ensureInterests(users.get("fenglu"), List.of("志愿服务", "兴趣社交"));
        ensureInterests(users.get("yexiao"), List.of("文艺演出", "校园生活"));
        ensureInterests(users.get("maolin"), List.of("竞赛组队", "学术讲座"));
        ensureInterests(users.get("xieyu"), List.of("学术讲座", "志愿服务"));
        ensureInterests(users.get("caixuan"), List.of("文艺演出", "兴趣社交"));
        ensureInterests(users.get("dongchen"), List.of("竞赛组队", "学术讲座", "兼职实习"));
        ensureInterests(users.get("hanxu"), List.of("运动健身", "志愿服务"));
        ensureInterests(users.get("qinyue"), List.of("考研搭子", "兴趣社交", "校园生活"));
    }

    private void ensureAdditionalDemoActivities(Map<String, Long> users, Map<String, Long> activities) {
        List<ActivitySeedEntry> entries = List.of(
                new ActivitySeedEntry("agent_workshop", "智能体应用工作坊", "从校园活动客服、推荐和自动化三个场景切入，现场拆解智能体应用设计方法。", "独墅湖校区创新港 205", 5, 18, 30, users.get("wangyi"), "/assets/images/campuspulse.svg", 45, List.of("学术讲座", "竞赛组队")),
                new ActivitySeedEntry("deepseek_prompt", "DeepSeek 提示词实战课", "围绕课程汇报、论文阅读、代码辅助和知识库问答，演示如何写稳定提示词。", "天赐庄校区博远楼 301", 11, 19, 30, users.get("dongchen"), "/assets/images/campuspulse.svg", 80, List.of("学术讲座", "校园生活")),
                new ActivitySeedEntry("data_story", "校园数据分析可视化分享", "用真实校园活动数据讲解数据清洗、看板设计和推荐指标解读。", "独墅湖校区理工楼 308", 13, 15, 0, users.get("maolin"), "/assets/images/campuspulse.svg", 60, List.of("学术讲座", "兼职实习")),
                new ActivitySeedEntry("java_backend", "Java 后端接口联调夜校", "面向综合实践项目，集中解决登录、权限、分页、通知和接口联调问题。", "独墅湖校区公共教学楼 B204", 4, 18, 30, users.get("dongchen"), "/assets/images/campuspulse.svg", 36, List.of("学术讲座", "竞赛组队")),
                new ActivitySeedEntry("frontend_portfolio", "前端作品集打磨营", "从页面截图、交互讲解、答辩动线和代码亮点四个方面打磨项目展示。", "天赐庄校区传媒实验室", 17, 14, 0, users.get("yexiao"), "/assets/images/campuspulse.svg", 42, List.of("兼职实习", "校园生活")),
                new ActivitySeedEntry("mock_trial", "模拟法庭公开赛", "面向法学和公共管理同学开放，支持旁听、报名参赛和志愿协助。", "天赐庄校区法学院模拟法庭", 20, 18, 0, users.get("xieyu"), "/assets/images/campuspulse.svg", 70, List.of("学术讲座", "志愿服务")),
                new ActivitySeedEntry("language_corner", "周五英语口语角", "轻松主题交流，适合想练口语、结识同伴和了解交换项目的同学。", "天赐庄校区留学生之家", 2, 19, 0, users.get("liyun"), "/assets/images/campuspulse.svg", 30, List.of("兴趣社交", "校园生活")),
                new ActivitySeedEntry("public_photo", "校园摄影扫街计划", "沿天赐庄老校区路线拍摄建筑、人像和活动纪实，结束后一起选片。", "天赐庄校区东门集合", 9, 16, 0, users.get("yexiao"), "/assets/images/campuspulse.svg", 25, List.of("文艺演出", "兴趣社交")),
                new ActivitySeedEntry("choir_night", "草坪合唱排练夜", "开放式合唱排练，适合喜欢音乐但不想参加正式比赛的同学。", "天赐庄校区钟楼草坪", 6, 19, 30, users.get("caixuan"), "/assets/images/campuspulse.svg", 55, List.of("文艺演出", "兴趣社交")),
                new ActivitySeedEntry("city_volunteer", "古城社区志愿导览", "面向游客提供路线引导和文化介绍，活动前会做统一培训。", "平江路志愿服务点", 15, 9, 0, users.get("fenglu"), "/assets/images/campuspulse.svg", 35, List.of("志愿服务", "校园生活")),
                new ActivitySeedEntry("blood_drive", "医学部健康科普志愿日", "医学部同学带队进行健康科普、问卷收集和基础咨询引导。", "独墅湖校区医学楼广场", 18, 10, 0, users.get("hanxu"), "/assets/images/campuspulse.svg", 50, List.of("志愿服务", "学术讲座")),
                new ActivitySeedEntry("trail_walk", "阳澄湖环湖徒步", "轻量徒步路线，适合想运动但不想高强度训练的同学。", "阳澄湖校区北门", 7, 8, 30, users.get("zhaoran"), "/assets/images/campuspulse.svg", 40, List.of("运动健身", "兴趣社交")),
                new ActivitySeedEntry("badminton_open", "羽毛球新手友好局", "不拼强度，主要练发球和双打轮转，欢迎零基础同学。", "阳澄湖校区体育馆 2 号场", 3, 20, 0, users.get("zhaoran"), "/assets/images/campuspulse.svg", 16, List.of("运动健身", "校园生活")),
                new ActivitySeedEntry("career_panel", "互联网实习校友圆桌", "邀请校友分享投递节奏、简历表达、面试准备和实习复盘。", "未来校区商学院报告厅", 10, 19, 0, users.get("zhoumo"), "/assets/images/campuspulse.svg", 120, List.of("兼职实习", "校园生活")),
                new ActivitySeedEntry("startup_pitch", "三分钟创业路演夜", "每组 3 分钟展示创意，现场同学和老师给出反馈。", "独墅湖创新港路演厅", 24, 18, 30, users.get("zhoumo"), "/assets/images/campuspulse.svg", 90, List.of("竞赛组队", "兼职实习")),
                new ActivitySeedEntry("paper_reading", "论文精读小组开放课", "以推荐系统和智能客服论文为样例，讲解如何读摘要、方法和实验。", "独墅湖校区图书馆研讨室 4", 12, 19, 0, users.get("wuyang"), "/assets/images/campuspulse.svg", 20, List.of("学术讲座", "考研搭子")),
                new ActivitySeedEntry("exam_room", "期末自习冲刺打卡", "连续两周晚间自习，按学院和考试科目自由分桌。", "独墅湖校区图书馆三楼", 1, 18, 0, users.get("zhengqiu"), "/assets/images/campuspulse.svg", 100, List.of("考研搭子", "校园生活")),
                new ActivitySeedEntry("mindful_walk", "正念散步与情绪记录", "心理学同学带领的低压力活动，适合期末前调整节奏。", "天赐庄校区本部花园", 8, 17, 0, users.get("qinyue"), "/assets/images/campuspulse.svg", 24, List.of("兴趣社交", "校园生活")),
                new ActivitySeedEntry("security_ctf", "网络安全 CTF 体验赛", "新手友好的 CTF 入门场，现场讲解题型并自由组队。", "独墅湖校区计算机楼 501", 16, 18, 30, users.get("dongchen"), "/assets/images/campuspulse.svg", 64, List.of("竞赛组队", "学术讲座")),
                new ActivitySeedEntry("iot_lab", "物联网创客开放夜", "体验传感器、开发板和校园场景原型设计，适合想做硬件项目的同学。", "独墅湖校区电子楼开放实验室", 19, 18, 0, users.get("gaoyi"), "/assets/images/campuspulse.svg", 32, List.of("竞赛组队", "学术讲座")),
                new ActivitySeedEntry("campus_market", "跳蚤市集与手作交换", "旧书、手作、文创和闲置交换，适合轻松社交和校园生活记录。", "天赐庄校区东吴桥广场", 22, 14, 0, users.get("sunhan"), "/assets/images/campuspulse.svg", 150, List.of("兴趣社交", "校园生活")),
                new ActivitySeedEntry("design_review", "海报设计互评会", "带上你的活动海报或 PPT 页面，现场互评构图、层级和配色。", "天赐庄校区艺术学院 109", 14, 18, 30, users.get("sunhan"), "/assets/images/campuspulse.svg", 28, List.of("文艺演出", "兼职实习")),
                new ActivitySeedEntry("survey_sprint", "校园调研冲刺营", "从问卷设计到访谈提纲，帮助社科类项目快速完成前期调研。", "独墅湖校区社会学院 203", 26, 15, 0, users.get("fenglu"), "/assets/images/campuspulse.svg", 36, List.of("学术讲座", "志愿服务")),
                new ActivitySeedEntry("graduate_chat", "考研复试经验茶话会", "围绕复试准备、导师沟通、英语口语和心态调整做经验交流。", "天赐庄校区咖啡角", 28, 19, 0, users.get("qinyue"), "/assets/images/campuspulse.svg", 45, List.of("考研搭子", "兴趣社交")),
                new ActivitySeedEntry("robotics_day", "校园机器人开放日", "展示机器人循迹、机械臂抓取和智能交互原型，现场可自由体验并报名项目小组。", "独墅湖校区工程训练中心", 30, 14, 0, users.get("gaoyi"), "/assets/images/campuspulse.svg", 88, List.of("竞赛组队", "学术讲座"))
        );
        for (ActivitySeedEntry entry : entries) {
            activities.put(entry.key(), ensureActivity(new SeedActivity(
                    entry.title(), entry.description(), entry.location(),
                    LocalDateTime.now().plusDays(entry.plusDays()).withHour(entry.hour()).withMinute(entry.minute()),
                    LocalDateTime.now().plusDays(entry.plusDays()).withHour(Math.min(entry.hour() + 2, 23)).withMinute(entry.minute()),
                    entry.organizerId(), entry.coverUrl(), entry.maxParticipants(),
                    "PUBLISHED", "APPROVED", entry.plusDays() % 3 == 0, true, entry.tags()
            )));
        }
    }

    private void ensureAdditionalDemoTeams(Map<String, Long> users, Map<String, Long> activities, Map<String, Long> teams) {
        List<TeamSeedEntry> entries = List.of(
                new TeamSeedEntry("agent_team", activities.get("agent_workshop"), "学术讲座｜智能体客服展示小组", "准备一个 LangGraph 知识库客服演示，缺负责流程图和测试问题整理的同学。", users.get("wangyi"), 5, 5, 16),
                new TeamSeedEntry("prompt_team", activities.get("deepseek_prompt"), "学术讲座｜提示词案例整理队", "一起整理注册、报名、组队、通知四类客服问答案例。", users.get("dongchen"), 4, 9, 18),
                new TeamSeedEntry("data_team", activities.get("data_story"), "竞赛组队｜推荐数据分析队", "用平台行为日志做一个推荐解释看板，适合会 SQL 或可视化的同学。", users.get("maolin"), 5, 12, 19),
                new TeamSeedEntry("java_team", activities.get("java_backend"), "竞赛组队｜后端联调互助组", "集中处理接口、数据库、登录态和分页问题，适合项目冲刺阶段加入。", users.get("dongchen"), 6, 4, 18),
                new TeamSeedEntry("portfolio_team", activities.get("frontend_portfolio"), "兼职实习｜作品集互评队", "互相看项目截图、PPT 和讲解稿，重点提升答辩展示效果。", users.get("yexiao"), 6, 15, 20),
                new TeamSeedEntry("language_team", activities.get("language_corner"), "兴趣社交｜英语口语搭子", "每周固定口语角，先从自我介绍和校园话题开始练。", users.get("liyun"), 4, 2, 19),
                new TeamSeedEntry("photo_team", activities.get("public_photo"), "文艺演出｜校园摄影互拍组", "拍建筑、人像和活动纪实，欢迎负责后期选片的同学。", users.get("yexiao"), 5, 8, 15),
                new TeamSeedEntry("choir_team", activities.get("choir_night"), "文艺演出｜草坪合唱声部组", "临时排练两首歌，缺低声部和现场拍摄同学。", users.get("caixuan"), 8, 6, 18),
                new TeamSeedEntry("volunteer_guide_team", activities.get("city_volunteer"), "志愿服务｜古城导览志愿队", "活动前一起背路线和讲解词，现场两人一组。", users.get("fenglu"), 6, 14, 9),
                new TeamSeedEntry("trail_team", activities.get("trail_walk"), "运动健身｜环湖徒步轻量队", "不追求速度，重点是完成路线和互相照应。", users.get("zhaoran"), 10, 7, 8),
                new TeamSeedEntry("badminton_team_2", activities.get("badminton_open"), "运动健身｜羽毛球双打练习组", "新手友好，主要练双打站位和接发球。", users.get("hanxu"), 4, 3, 20),
                new TeamSeedEntry("career_team", activities.get("career_panel"), "兼职实习｜校友圆桌提问组", "提前准备投递、面试和实习复盘问题，活动后整理笔记。", users.get("zhoumo"), 5, 10, 18),
                new TeamSeedEntry("pitch_team", activities.get("startup_pitch"), "竞赛组队｜三分钟路演打磨队", "已有想法但缺展示结构，想找同学一起练路演。", users.get("zhoumo"), 4, 21, 19),
                new TeamSeedEntry("paper_team", activities.get("paper_reading"), "考研搭子｜论文精读互助组", "每人负责一段论文，最后合并成答辩可用的讲解。", users.get("wuyang"), 5, 11, 18),
                new TeamSeedEntry("exam_team", activities.get("exam_room"), "考研搭子｜期末自习打卡队", "晚间固定打卡，适合需要稳定节奏的同学。", users.get("zhengqiu"), 6, 1, 18),
                new TeamSeedEntry("security_team", activities.get("security_ctf"), "竞赛组队｜CTF 新手练习队", "从 Web 和 Crypto 入门题开始，欢迎零基础但愿意查资料的同学。", users.get("gaoyi"), 4, 16, 19),
                new TeamSeedEntry("iot_team", activities.get("iot_lab"), "竞赛组队｜物联网原型小队", "想做校园打卡或活动签到硬件原型，缺前端展示和文档同学。", users.get("gaoyi"), 5, 18, 18),
                new TeamSeedEntry("market_team", activities.get("campus_market"), "兴趣社交｜市集摊主互助组", "一起准备摊位物料、价签和宣传图，适合手作或旧书交换。", users.get("sunhan"), 8, 22, 13),
                new TeamSeedEntry("robotics_team", activities.get("robotics_day"), "竞赛组队｜机器人开放日讲解队", "为开放日准备机器人演示脚本，缺负责讲解、拍摄和现场引导的同学。", users.get("gaoyi"), 6, 29, 15),
                new TeamSeedEntry("qa_team", null, "学术讲座｜智能客服测试小组", "围绕 LangGraph 客服准备常见问题、兜底问题和转人工测试用例。", users.get("wangyi"), 5, 6, 19),
                new TeamSeedEntry("slides_team", null, "兼职实习｜项目汇报 PPT 打磨队", "一起优化综合实践汇报 PPT，重点看逻辑、截图和演示话术。", users.get("sunhan"), 4, 4, 20),
                new TeamSeedEntry("ml_team", null, "竞赛组队｜推荐算法实验队", "用兴趣标签、点击、收藏、报名和组队行为做推荐排序实验。", users.get("maolin"), 5, 13, 18),
                new TeamSeedEntry("reading_team", null, "考研搭子｜清晨背书监督组", "每天早上固定线上打卡，适合需要稳定节奏和互相提醒的同学。", users.get("qinyue"), 8, 2, 7),
                new TeamSeedEntry("media_team", null, "文艺演出｜活动摄影剪辑队", "给校园活动拍摄短视频和照片，缺负责剪辑、字幕和封面设计的同学。", users.get("yexiao"), 6, 12, 16)
        );
        for (TeamSeedEntry entry : entries) {
            long teamId = ensureTeam(new SeedTeam(
                    entry.activityId(), entry.title(), entry.description(), entry.creatorId(), entry.maxMembers(), "OPEN",
                    LocalDateTime.now().plusDays(entry.plusDays()).withHour(entry.hour()).withMinute(0),
                    LocalDateTime.now().plusDays(entry.plusDays()).withHour(Math.min(entry.hour() + 3, 23)).withMinute(0)
            ));
            teams.put(entry.key(), teamId);
            ensureTeamMember(teamId, entry.creatorId(), "CREATOR", "ACTIVE");
        }
    }

    private void ensureRecommendationDemoData(Map<String, Long> users, Map<String, Long> activities, Map<String, Long> teams) {
        ensureModelVersion("campuspulse-ranker", "synthetic-demo-v1", """
                {"synthetic":true,"evaluation":"not measured","features":["interest_match","history_register","favorite","click","team_interaction","featured_weight"]}
                """);

        ensureFeaturedItem("ACTIVITY", activities.get("ai"), 86, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7));
        ensureFeaturedItem("ACTIVITY", activities.get("math_full"), 92, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(2));
        ensureFeaturedItem("ACTIVITY", activities.get("intl_lunch"), 45, LocalDateTime.now(), LocalDateTime.now().plusDays(30));
        ensureFeaturedItem("TEAM", teams.get("resume_team"), 82, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(10));
        ensureFeaturedItem("TEAM", teams.get("cv_team"), 72, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(5));
        ensureFeaturedItem("TEAM", teams.get("intl_team"), 54, LocalDateTime.now(), LocalDateTime.now().plusDays(30));

        long lin = users.get("linzhixia");
        long gu = users.get("gumingzhe");
        long chen = users.get("chenze");
        long lu = users.get("luqinghe");
        long bai = users.get("baikexin");
        long tang = users.get("tangshuning");

        ensureBehavior(lin, "ACTIVITY", activities.get("ai"), "IMPRESSION", "home_recommend", null, "demo-lin-act", 1, "{\"source\":\"recommendation\"}");
        ensureBehavior(lin, "ACTIVITY", activities.get("ai"), "CLICK", "home_recommend", null, "demo-lin-act", 1, "{\"dwellSeconds\":46}");
        ensureBehavior(lin, "ACTIVITY", activities.get("resume"), "DETAIL_VIEW", "activity_list", null, "demo-lin-act", 2, "{\"dwellSeconds\":88}");
        ensureBehavior(lin, "ACTIVITY", activities.get("resume"), "REGISTER", "activity_detail", null, "demo-lin-act", 2, "{\"conversion\":true}");
        ensureBehavior(lin, "TEAM", teams.get("resume_team"), "IMPRESSION", "home_team_recommend", null, "demo-lin-team", 1, null);
        ensureBehavior(lin, "TEAM", teams.get("resume_team"), "CLICK", "home_team_recommend", null, "demo-lin-team", 1, "{\"dwellSeconds\":32}");

        ensureBehavior(gu, "ACTIVITY", activities.get("math_full"), "IMPRESSION", "home_recommend", null, "demo-gu-act", 1, null);
        ensureBehavior(gu, "ACTIVITY", activities.get("math_full"), "CLICK", "home_recommend", null, "demo-gu-act", 1, "{\"dwellSeconds\":103}");
        ensureBehavior(gu, "ACTIVITY", activities.get("cv_teaming_closed"), "DETAIL_VIEW", "search_result", "计算机视觉", "demo-gu-search", 1, "{\"semanticMatch\":true}");
        ensureBehavior(gu, "TEAM", teams.get("cv_team"), "TEAM_JOIN", "team_detail", null, "demo-gu-team", 1, "{\"conversion\":true}");
        ensureBehavior(gu, "SEARCH", null, "SEARCH", "global_search", "计算机视觉 竞赛", "demo-gu-search", null, null);

        ensureBehavior(chen, "ACTIVITY", activities.get("running"), "CLICK", "home_recommend", null, "demo-chen-act", 1, "{\"dwellSeconds\":54}");
        ensureBehavior(chen, "TEAM", teams.get("running_team"), "TEAM_JOIN", "team_detail", null, "demo-chen-team", 1, "{\"conversion\":true}");
        ensureBehavior(chen, "ACTIVITY", activities.get("basketball"), "DETAIL_VIEW", "activity_history", null, "demo-chen-act", 2, null);

        ensureBehavior(lu, "ACTIVITY", activities.get("resume"), "CLICK", "home_recommend", null, "demo-lu-act", 1, null);
        ensureBehavior(lu, "ACTIVITY", activities.get("intl_lunch"), "FAVORITE", "activity_detail", null, "demo-lu-act", 2, "{\"conversion\":true}");
        ensureBehavior(lu, "TEAM", teams.get("intl_team"), "IMPRESSION", "home_team_recommend", null, "demo-lu-team", 1, null);

        ensureBehavior(bai, "ACTIVITY", activities.get("volunteer"), "IMPRESSION", "home_recommend", null, "demo-bai-act", 1, null);
        ensureBehavior(bai, "ACTIVITY", activities.get("volunteer"), "CLICK", "home_recommend", null, "demo-bai-act", 1, "{\"dwellSeconds\":25}");
        ensureBehavior(bai, "TEAM", teams.get("intl_team"), "CLICK", "home_team_recommend", null, "demo-bai-team", 1, null);

        ensureBehavior(tang, "ACTIVITY", activities.get("old_chat_closed"), "DETAIL_VIEW", "notification", null, "demo-tang-act", 1, null);
        ensureBehavior(tang, "TEAM", teams.get("study"), "TEAM_APPLY", "team_detail", null, "demo-tang-team", 1, "{\"conversion\":true}");

        ensureActivityScore(lin, activities.get("resume"), 0.96, "兴趣匹配兼职实习，且近期收藏并报名过同类活动");
        ensureActivityScore(lin, activities.get("ai"), 0.91, "学术讲座兴趣强匹配，历史点击停留较长");
        ensureActivityScore(lin, activities.get("long_title"), 0.74, "校园生活与学术讲座标签匹配，用于展示长标题兼容");
        ensureActivityScore(lin, activities.get("math_full"), 0.52, "运营推荐但容量紧张，排序保留但降低");
        ensureActivityScore(lin, activities.get("music"), 0.35, "热度高但兴趣弱匹配，低于强匹配内容");

        ensureActivityScore(gu, activities.get("math_full"), 0.98, "竞赛组队和学术讲座强匹配，近期高频点击建模内容");
        ensureActivityScore(gu, activities.get("cv_teaming_closed"), 0.93, "历史参与计算机视觉相关队伍，语义相似度高");
        ensureActivityScore(gu, activities.get("ai"), 0.86, "AI 与技术讲座兴趣匹配");
        ensureActivityScore(gu, activities.get("resume"), 0.61, "技术实习场景相关，但不如竞赛内容强");

        ensureActivityScore(chen, activities.get("running"), 0.97, "运动健身兴趣强匹配，近期参与夜跑队伍");
        ensureActivityScore(chen, activities.get("basketball"), 0.92, "历史参加篮球赛，运动偏好明显");
        ensureActivityScore(chen, activities.get("music"), 0.48, "热度高但兴趣弱匹配");

        ensureActivityScore(lu, activities.get("resume"), 0.95, "兼职实习兴趣强匹配，近期收藏求职相关活动");
        ensureActivityScore(lu, activities.get("intl_lunch"), 0.84, "兴趣社交匹配，近期收藏国际交流活动");
        ensureActivityScore(bai, activities.get("volunteer"), 0.93, "志愿服务兴趣强匹配，近期点击同类内容");
        ensureActivityScore(tang, activities.get("old_chat_closed"), 0.82, "兴趣社交与校园生活匹配，且有历史活动群聊行为");

        ensureTeamScore(lin, teams.get("resume_team"), 0.97, "简历门诊活动相关队伍，匹配兼职实习兴趣和报名记录");
        ensureTeamScore(lin, teams.get("ai_team"), 0.88, "已报名 AI 讲座，队伍与活动强关联");
        ensureTeamScore(lin, teams.get("cv_team"), 0.57, "竞赛组队弱匹配，用于展示候选召回");

        ensureTeamScore(gu, teams.get("cv_team"), 0.98, "计算机视觉竞赛队伍与历史行为强匹配");
        ensureTeamScore(gu, teams.get("math_team"), 0.94, "数学建模密训和竞赛组队兴趣强匹配");
        ensureTeamScore(gu, teams.get("ai_team"), 0.77, "AI 讲座相关志愿队伍，技术兴趣匹配");

        ensureTeamScore(chen, teams.get("running_team"), 0.98, "运动健身兴趣与夜跑行为强匹配");
        ensureTeamScore(chen, teams.get("badminton"), 0.82, "运动类自由组队匹配");
        ensureTeamScore(lu, teams.get("intl_team"), 0.88, "国际交流与兴趣社交匹配");
        ensureTeamScore(bai, teams.get("intl_team"), 0.91, "兴趣社交匹配且当前队伍仍有名额");
        ensureTeamScore(tang, teams.get("study"), 0.9, "考研搭子兴趣匹配，近期申请学习队伍");
    }

    private void ensureModelVersion(String modelName, String version, String metricsJson) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM recommend_model_version
                WHERE model_name = ? AND version = ?
                """, Long.class, modelName, version);
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE recommend_model_version
                    SET metrics_json = ?, active_flag = 1
                    WHERE model_name = ? AND version = ?
                    """, metricsJson, modelName, version);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO recommend_model_version (model_name, version, metrics_json, active_flag)
                    VALUES (?, ?, ?, 1)
                    """, modelName, version, metricsJson);
        }
    }

    private void ensureFeaturedItem(String targetType, long targetId, int weight, LocalDateTime start, LocalDateTime end) {
        jdbcTemplate.update("""
                INSERT INTO ops_featured_item (target_type, target_id, weight, start_time, end_time, status)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                ON DUPLICATE KEY UPDATE
                    weight = VALUES(weight),
                    start_time = VALUES(start_time),
                    end_time = VALUES(end_time),
                    status = 'ACTIVE'
                """, targetType, targetId, weight,
                start == null ? null : Timestamp.valueOf(start),
                end == null ? null : Timestamp.valueOf(end));
    }

    private void ensureBehavior(long userId, String targetType, Long targetId, String eventType, String scene,
                                String queryText, String requestId, Integer rankPosition, String extraJson) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_behavior_log
                WHERE user_id = ? AND target_type = ? AND IFNULL(target_id, -1) = IFNULL(?, -1)
                  AND event_type = ? AND IFNULL(scene, '') = IFNULL(?, '')
                  AND IFNULL(query_text, '') = IFNULL(?, '') AND IFNULL(request_id, '') = IFNULL(?, '')
                  AND IFNULL(rank_position, -1) = IFNULL(?, -1)
                """, Long.class, userId, targetType, targetId, eventType, scene, queryText, requestId, rankPosition);
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                    INSERT INTO user_behavior_log
                        (user_id, target_type, target_id, event_type, scene, query_text, request_id, rank_position, extra_json, source)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SYNTHETIC')
                    """, userId, targetType, targetId, eventType, scene, queryText, requestId, rankPosition, extraJson);
        }
    }

    private void ensureActivityScore(long userId, long activityId, double score, String reason) {
        jdbcTemplate.update("""
                INSERT INTO recommend_activity_score (user_id, activity_id, score, reason, model_version)
                VALUES (?, ?, ?, ?, 'synthetic-demo-v1')
                ON DUPLICATE KEY UPDATE
                    score = VALUES(score),
                    reason = VALUES(reason),
                    model_version = VALUES(model_version)
                """, userId, activityId, score, reason);
    }

    private void ensureTeamScore(long userId, long teamId, double score, String reason) {
        jdbcTemplate.update("""
                INSERT INTO recommend_team_score (user_id, team_id, score, reason, model_version)
                VALUES (?, ?, ?, ?, 'synthetic-demo-v1')
                ON DUPLICATE KEY UPDATE
                    score = VALUES(score),
                    reason = VALUES(reason),
                    model_version = VALUES(model_version)
                """, userId, teamId, score, reason);
    }

    private void ensureMessageByQuery(String countSql, String insertSql, List<Object> countArgs, List<Object> insertArgs) {
        Long count = jdbcTemplate.queryForObject(countSql, Long.class, countArgs.toArray());
        if (count == null || count == 0) {
            jdbcTemplate.update(insertSql, insertArgs.toArray());
        }
    }

    public record SeedUser(String username, String studentNo, String nickname, String email, boolean emailVerified, boolean phoneVerified, String role, int status,
                           String college, String campus, String major, String grade, String educationLevel, String bio, String phone) {
    }

    public record SeedActivity(String title, String description, String location, LocalDateTime startTime, LocalDateTime endTime,
                               long organizerId, String coverUrl, int maxParticipants, String status, String auditStatus,
                               boolean chatEnabled, boolean teamingEnabled, List<String> tags) {
    }

    public record SeedTeam(Long activityId, String title, String description, long creatorId, int maxMembers, String status,
                           LocalDateTime startTime, LocalDateTime endTime) {
    }

    private record ActivitySeedEntry(String key, String title, String description, String location,
                                     int plusDays, int hour, int minute, long organizerId,
                                     String coverUrl, int maxParticipants, List<String> tags) {
    }

    private record TeamSeedEntry(String key, Long activityId, String title, String description,
                                 long creatorId, int maxMembers, int plusDays, int hour) {
    }
}
