const http = require('http');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');

const prototypeRoot = path.resolve(__dirname, '..', 'frontend');
const port = Number(process.env.PORT || '8124');

function jsonClone(value) {
  return JSON.parse(JSON.stringify(value));
}

function svgDataUri({ width = 1200, height = 720, title = '', subtitle = '', from = '#1f6feb', to = '#74c0fc', accent = '#ffffff' }) {
  const safeTitle = String(title || '');
  const safeSubtitle = String(subtitle || '');
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
      <defs>
        <linearGradient id="g" x1="0" x2="1" y1="0" y2="1">
          <stop offset="0%" stop-color="${from}"/>
          <stop offset="100%" stop-color="${to}"/>
        </linearGradient>
      </defs>
      <rect width="${width}" height="${height}" rx="40" fill="url(#g)"/>
      <circle cx="${width - 160}" cy="130" r="110" fill="rgba(255,255,255,0.12)"/>
      <circle cx="${160}" cy="${height - 140}" r="130" fill="rgba(255,255,255,0.12)"/>
      <text x="72" y="${Math.round(height * 0.48)}" fill="${accent}" font-size="56" font-weight="700" font-family="PingFang SC, SF Pro Display, Helvetica Neue, Arial, sans-serif">${safeTitle}</text>
      <text x="72" y="${Math.round(height * 0.60)}" fill="rgba(255,255,255,0.86)" font-size="28" font-weight="500" font-family="PingFang SC, SF Pro Text, Helvetica Neue, Arial, sans-serif">${safeSubtitle}</text>
    </svg>
  `;
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

function avatarDataUri(label, from, to) {
  return svgDataUri({
    width: 240,
    height: 240,
    title: label,
    subtitle: '',
    from,
    to,
    accent: '#ffffff',
  });
}

const avatars = {
  me: avatarDataUri('林', '#ff7a59', '#ffb38a'),
  organizer: avatarDataUri('周', '#1463ff', '#6ea8ff'),
  creator: avatarDataUri('陈', '#0a8754', '#71d49c'),
  friend: avatarDataUri('许', '#8f5cff', '#c0a7ff'),
  volunteer: avatarDataUri('沈', '#f59f00', '#ffd166'),
  admin: avatarDataUri('管', '#2b2d42', '#8d99ae'),
};

const coverImages = {
  ai: svgDataUri({ title: '人工智能与未来社会', subtitle: '图书馆报告厅 · 主题讲座', from: '#1d4ed8', to: '#38bdf8' }),
  basket: svgDataUri({ title: '校园 3v3 篮球巅峰赛', subtitle: '东区体育馆 · 热门运动', from: '#f97316', to: '#fb7185' }),
  volunteer: svgDataUri({ title: '春季志愿者招募行动', subtitle: '志愿服务 · 校园公益', from: '#16a34a', to: '#6ee7b7' }),
  music: svgDataUri({ title: '校园音乐夜', subtitle: '文艺演出 · 中庭草坪', from: '#7c3aed', to: '#c084fc' }),
  startup: svgDataUri({ title: '创新创业训练营', subtitle: '竞赛组队 · 路演工作坊', from: '#0f766e', to: '#5eead4' }),
  career: svgDataUri({ title: '简历门诊与实习分享', subtitle: '就业交流 · 创新港', from: '#334155', to: '#94a3b8' }),
};

const tagNames = [
  '运动健身',
  '学术讲座',
  '文艺演出',
  '志愿服务',
  '竞赛组队',
  '兼职实习',
  '兴趣社交',
  '考研搭子',
  '校园生活',
  '其他',
];

const tags = tagNames.map((name, index) => ({
  id: index + 1,
  name,
  type: name === '其他' ? 'OTHER' : 'HOME',
}));

const users = [
  {
    id: 1,
    username: '20231001',
    studentNo: '20231001',
    nickname: '林知夏',
    avatarUrl: avatars.me,
    college: '计算机科学与技术学院',
    campus: '独墅湖校区',
    major: '软件工程',
    grade: '2023',
    educationLevel: 'UNDERGRAD',
    bio: '偏好讲座、竞赛和校园产品设计，正在准备课程大作业答辩。',
    email: 'lin.zhixia@campuspulse.local',
    emailVerified: true,
    phone: '13812345678',
    phoneVerified: true,
    role: 'USER',
    status: 1,
    createdAt: '2026-03-01T10:30:00',
    interests: ['学术讲座', '竞赛组队', '校园生活'],
  },
  {
    id: 2,
    username: 'zhoulan',
    studentNo: '20222011',
    nickname: '周岚',
    avatarUrl: avatars.organizer,
    college: '计算机科学与技术学院',
    campus: '独墅湖校区',
    major: '人工智能',
    grade: '2022',
    educationLevel: 'MASTER',
    bio: '喜欢做技术分享和校园活动运营。',
    email: 'zhoulan@campuspulse.local',
    emailVerified: true,
    phone: '13900001234',
    phoneVerified: true,
    role: 'ORG',
    status: 1,
    createdAt: '2026-02-19T14:20:00',
    interests: ['学术讲座', '竞赛组队'],
  },
  {
    id: 3,
    username: 'chenze',
    studentNo: '20212032',
    nickname: '陈泽',
    avatarUrl: avatars.creator,
    college: '体育学院',
    campus: '阳澄湖校区',
    major: '社会体育指导',
    grade: '2021',
    educationLevel: 'UNDERGRAD',
    bio: '篮球和羽毛球爱好者，也参与社团活动策划。',
    email: 'chenze@campuspulse.local',
    emailVerified: true,
    phone: '13722223333',
    phoneVerified: true,
    role: 'USER',
    status: 1,
    createdAt: '2026-02-08T09:10:00',
    interests: ['运动健身', '校园生活'],
  },
  {
    id: 4,
    username: 'xuan',
    studentNo: '20231127',
    nickname: '许安',
    avatarUrl: avatars.friend,
    college: '文学院',
    campus: '天赐庄校区',
    major: '汉语言文学',
    grade: '2023',
    educationLevel: 'UNDERGRAD',
    bio: '偏好演出、志愿和校园社交活动。',
    email: 'xuan@campuspulse.local',
    emailVerified: true,
    phone: '13688886666',
    phoneVerified: true,
    role: 'USER',
    status: 1,
    createdAt: '2026-03-10T18:15:00',
    interests: ['文艺演出', '兴趣社交'],
  },
  {
    id: 5,
    username: 'shenyi',
    studentNo: '20241008',
    nickname: '沈奕',
    avatarUrl: avatars.volunteer,
    college: '管理学院',
    campus: '未来校区',
    major: '信息管理与信息系统',
    grade: '2024',
    educationLevel: 'UNDERGRAD',
    bio: '喜欢志愿、活动运营和数据整理。',
    email: 'shenyi@campuspulse.local',
    emailVerified: false,
    phone: '13566667777',
    phoneVerified: false,
    role: 'USER',
    status: 1,
    createdAt: '2026-03-22T11:40:00',
    interests: ['志愿服务', '校园生活'],
  },
  {
    id: 6,
    username: 'gumingzhe',
    studentNo: '20221017',
    nickname: '顾明哲',
    avatarUrl: avatarDataUri('顾', '#0f766e', '#5eead4'),
    college: '电子信息学院',
    campus: '独墅湖校区',
    major: '电子信息工程',
    grade: '2022',
    educationLevel: 'UNDERGRAD',
    bio: '常年混迹讲座、竞赛和技术社群，也经常主动私聊联系活动发起人。',
    email: 'gumingzhe@campuspulse.local',
    emailVerified: true,
    phone: '13511112222',
    phoneVerified: true,
    role: 'USER',
    status: 1,
    createdAt: '2026-03-05T09:50:00',
    interests: ['学术讲座', '竞赛组队', '兼职实习'],
  },
  {
    id: 7,
    username: 'tangshuning',
    studentNo: '20232015',
    nickname: '唐舒宁',
    avatarUrl: avatarDataUri('唐', '#a855f7', '#f0abfc'),
    college: '教育学院',
    campus: '天赐庄校区',
    major: '应用心理学',
    grade: '2023',
    educationLevel: 'UNDERGRAD',
    bio: '对心理成长、学习搭子和线下工作坊都很感兴趣。',
    email: 'tangshuning@campuspulse.local',
    emailVerified: true,
    phone: '13511113333',
    phoneVerified: true,
    role: 'USER',
    status: 1,
    createdAt: '2026-03-12T14:10:00',
    interests: ['考研搭子', '兴趣社交', '校园生活'],
  },
  {
    id: 8,
    username: 'hezhiyuan',
    studentNo: '20221088',
    nickname: '贺知远',
    avatarUrl: avatarDataUri('贺', '#dc2626', '#fca5a5'),
    college: '计算机科学与技术学院',
    campus: '独墅湖校区',
    major: '人工智能',
    grade: '2022',
    educationLevel: 'MASTER',
    bio: '喜欢算法竞赛与项目路演，但报名信息经常写得很简略。',
    email: 'hezhiyuan@campuspulse.local',
    emailVerified: true,
    phone: '13511114444',
    phoneVerified: true,
    role: 'USER',
    status: 1,
    createdAt: '2026-03-15T18:25:00',
    interests: ['学术讲座', '竞赛组队'],
  },
  {
    id: 9,
    username: 'admin',
    studentNo: 'A0001',
    nickname: '平台管理员',
    avatarUrl: avatars.admin,
    college: '信息化建设办公室',
    campus: '天赐庄校区',
    major: '平台运营',
    grade: '',
    educationLevel: 'OTHER',
    bio: '负责审核活动内容、管理推荐位和维护平台标签。',
    email: 'admin@campuspulse.local',
    emailVerified: true,
    phone: '13999990000',
    phoneVerified: true,
    role: 'ADMIN',
    status: 1,
    createdAt: '2026-01-15T08:00:00',
    interests: ['校园生活'],
  },
  {
    id: 10,
    username: 'songyutong',
    studentNo: '20201118',
    nickname: '宋雨桐',
    avatarUrl: avatarDataUri('宋', '#0284c7', '#7dd3fc'),
    college: '学生发展中心',
    campus: '天赐庄校区',
    major: '活动运营',
    grade: '2020',
    educationLevel: 'OTHER',
    bio: '高频发布活动，负责多场跨校区线下交流。',
    email: 'songyutong@campuspulse.local',
    emailVerified: true,
    phone: '13511116666',
    phoneVerified: true,
    role: 'ORG',
    status: 1,
    createdAt: '2026-02-01T10:00:00',
    interests: ['校园生活', '学术讲座'],
  },
  {
    id: 11,
    username: 'luqinghe',
    studentNo: '20212106',
    nickname: '陆青禾',
    avatarUrl: avatarDataUri('陆', '#0f766e', '#99f6e4'),
    college: '商学院',
    campus: '未来校区',
    major: '金融学',
    grade: '2021',
    educationLevel: 'UNDERGRAD',
    bio: '偏好实习分享、校友交流和高质量私聊沟通。',
    email: 'luqinghe@campuspulse.local',
    emailVerified: true,
    phone: '13511115555',
    phoneVerified: true,
    role: 'USER',
    status: 1,
    createdAt: '2026-03-18T09:30:00',
    interests: ['兼职实习', '兴趣社交'],
  },
  {
    id: 12,
    username: 'baikexin',
    studentNo: '20241032',
    nickname: '白可心',
    avatarUrl: avatarDataUri('白', '#f97316', '#fdba74'),
    college: '社会学院',
    campus: '独墅湖校区',
    major: '社会工作',
    grade: '2024',
    educationLevel: 'UNDERGRAD',
    bio: '资料完整但手机号未验证，喜欢先收藏后再决定是否报名。',
    email: 'baikexin@campuspulse.local',
    emailVerified: true,
    phone: '13511117777',
    phoneVerified: false,
    role: 'USER',
    status: 1,
    createdAt: '2026-03-28T13:20:00',
    interests: ['志愿服务', '兴趣社交'],
  },
  {
    id: 13,
    username: 'gubeichuan',
    studentNo: '20222109',
    nickname: '顾北川',
    avatarUrl: avatarDataUri('北', '#475569', '#cbd5e1'),
    college: '计算机科学与技术学院',
    campus: '独墅湖校区',
    major: '',
    grade: '2022',
    educationLevel: 'MASTER',
    bio: '',
    email: 'gubeichuan@campuspulse.local',
    emailVerified: true,
    phone: '',
    phoneVerified: false,
    role: 'USER',
    status: 1,
    createdAt: '2026-03-30T20:40:00',
    interests: ['竞赛组队', '学术讲座'],
  },
  {
    id: 14,
    username: 'qiaoyiming',
    studentNo: '20211111',
    nickname: '乔一鸣',
    avatarUrl: avatarDataUri('乔', '#334155', '#94a3b8'),
    college: '法学院',
    campus: '天赐庄校区',
    major: '法学',
    grade: '2021',
    educationLevel: 'UNDERGRAD',
    bio: '已被停用的测试账号，用于后台状态和历史数据观察。',
    email: 'qiaoyiming@campuspulse.local',
    emailVerified: true,
    phone: '13511118888',
    phoneVerified: true,
    role: 'USER',
    status: 0,
    createdAt: '2026-02-27T16:05:00',
    interests: ['校园生活'],
  },
];

const activities = [
  {
    id: 1,
    title: '人工智能与未来社会',
    description: '围绕生成式 AI、校园场景应用与未来职业方向展开分享，现场设有提问互动与交流环节。',
    location: '图书馆报告厅',
    startTime: '2026-04-18T19:00:00',
    endTime: '2026-04-18T21:00:00',
    organizerId: 2,
    coverUrl: coverImages.ai,
    maxParticipants: 120,
    auditStatus: 'APPROVED',
    status: 'PUBLISHED',
    chatEnabled: true,
    teamingEnabled: true,
    tags: ['学术讲座', '校园生活'],
    createdAt: '2026-04-03T10:00:00',
  },
  {
    id: 2,
    title: '校园 3v3 篮球巅峰赛',
    description: '面向全校开放的 3v3 篮球赛，欢迎班级或自由组队报名参加，现场有啦啦队与摄影记录。',
    location: '东区体育馆',
    startTime: '2026-04-15T14:00:00',
    endTime: '2026-04-15T18:00:00',
    organizerId: 3,
    coverUrl: coverImages.basket,
    maxParticipants: 40,
    auditStatus: 'APPROVED',
    status: 'PUBLISHED',
    chatEnabled: false,
    teamingEnabled: true,
    tags: ['运动健身', '校园生活'],
    createdAt: '2026-04-01T16:30:00',
  },
  {
    id: 3,
    title: '春季志愿者招募行动',
    description: '面向校内外社区服务点招募志愿者，主要负责秩序维护、引导和信息登记。',
    location: '学生事务中心一楼',
    startTime: '2026-04-20T13:30:00',
    endTime: '2026-04-20T17:30:00',
    organizerId: 2,
    coverUrl: coverImages.volunteer,
    maxParticipants: 60,
    auditStatus: 'APPROVED',
    status: 'PUBLISHED',
    chatEnabled: true,
    teamingEnabled: true,
    tags: ['志愿服务', '校园生活'],
    createdAt: '2026-04-05T09:15:00',
  },
  {
    id: 4,
    title: '校园音乐夜',
    description: '草坪开放式音乐演出，欢迎同学们带上朋友一起参加，也欢迎报名成为现场志愿者。',
    location: '钟楼前草坪',
    startTime: '2026-04-22T18:30:00',
    endTime: '2026-04-22T21:00:00',
    organizerId: 4,
    coverUrl: coverImages.music,
    maxParticipants: 200,
    auditStatus: 'APPROVED',
    status: 'PUBLISHED',
    chatEnabled: false,
    teamingEnabled: true,
    tags: ['文艺演出', '兴趣社交'],
    createdAt: '2026-04-04T20:10:00',
  },
  {
    id: 5,
    title: '创新创业训练营',
    description: '两天一夜的项目路演工作坊，包含选题、答辩、BP 打磨和团队协作训练。',
    location: '独墅湖创新港 302',
    startTime: '2026-04-25T09:00:00',
    endTime: '2026-04-26T17:00:00',
    organizerId: 2,
    coverUrl: coverImages.startup,
    maxParticipants: 80,
    auditStatus: 'PENDING',
    status: 'PUBLISHED',
    chatEnabled: false,
    teamingEnabled: true,
    tags: ['竞赛组队', '学术讲座'],
    createdAt: '2026-04-07T11:05:00',
  },
  {
    id: 6,
    title: '简历门诊与实习分享',
    description: '面向求职与实习准备同学的线下交流，包含简历一对一建议和岗位投递经验分享。',
    location: '天赐庄校区就业中心',
    startTime: '2026-04-28T15:00:00',
    endTime: '2026-04-28T17:00:00',
    organizerId: 1,
    coverUrl: coverImages.career,
    maxParticipants: 50,
    auditStatus: 'APPROVED',
    status: 'PUBLISHED',
    chatEnabled: true,
    teamingEnabled: true,
    tags: ['兼职实习', '校园生活'],
    createdAt: '2026-04-06T12:40:00',
  },
  {
    id: 7,
    title: '苏大夜跑打卡计划',
    description: '今晚开始的夜跑打卡活动，欢迎想规律运动、互相监督的同学加入。',
    location: '未来校区操场北门',
    startTime: '2026-04-16T21:00:00',
    endTime: '2026-04-17T00:00:00',
    organizerId: 3,
    coverUrl: svgDataUri({ title: '苏大夜跑打卡计划', subtitle: '未来校区操场北门 · 运动健身', from: '#0f766e', to: '#22c55e' }),
    maxParticipants: 30,
    auditStatus: 'APPROVED',
    status: 'PUBLISHED',
    chatEnabled: false,
    teamingEnabled: true,
    tags: ['运动健身', '兴趣社交'],
    createdAt: '2026-04-10T08:20:00',
  },
  {
    id: 8,
    title: '保研经验面对面',
    description: '邀请往届学长姐分享保研材料准备、夏令营与面试经验。',
    location: '独墅湖校区学生活动中心 208',
    startTime: '2026-04-27T19:30:00',
    endTime: '2026-04-27T21:00:00',
    organizerId: 10,
    coverUrl: svgDataUri({ title: '保研经验面对面', subtitle: '学生活动中心 208 · 学术讲座', from: '#1d4ed8', to: '#818cf8' }),
    maxParticipants: 90,
    auditStatus: 'REJECTED',
    status: 'PUBLISHED',
    chatEnabled: false,
    teamingEnabled: true,
    tags: ['学术讲座', '校园生活'],
    createdAt: '2026-04-09T19:05:00',
  },
  {
    id: 9,
    title: '心理减压工作坊',
    description: '面向期中周的减压工作坊，包含正念、呼吸练习和小组陪伴交流。',
    location: '天赐庄校区心理中心 302',
    startTime: '2026-04-21T14:00:00',
    endTime: '2026-04-21T16:00:00',
    organizerId: 10,
    coverUrl: svgDataUri({ title: '心理减压工作坊', subtitle: '心理中心 302 · 校园生活', from: '#a855f7', to: '#f9a8d4' }),
    maxParticipants: 40,
    auditStatus: 'APPROVED',
    status: 'CANCELLED',
    chatEnabled: false,
    teamingEnabled: true,
    tags: ['校园生活', '兴趣社交'],
    createdAt: '2026-04-08T13:10:00',
  },
  {
    id: 10,
    title: '这是一场标题特别长特别长用于观察活动卡片和详情排版是否会出现换行错位与按钮挤压的跨校区交流工作坊',
    description: '这条活动数据专门用于观察超长标题、超长描述、跨校区地点与无封面图时，首页卡片、活动详情、管理页和通知里是否还保持清晰排版。',
    location: '天赐庄校区主会场 + 独墅湖校区分会场线上联动',
    startTime: '2026-04-30T13:30:00',
    endTime: '2026-04-30T17:30:00',
    organizerId: 10,
    coverUrl: null,
    maxParticipants: 35,
    auditStatus: 'APPROVED',
    status: 'PUBLISHED',
    chatEnabled: false,
    teamingEnabled: true,
    tags: ['校园生活', '学术讲座', '兴趣社交'],
    createdAt: '2026-04-10T16:00:00',
  },
  {
    id: 11,
    title: '计算机视觉竞赛冲刺分享会',
    description: '围绕计算机视觉竞赛赛题复现、答辩准备和时间安排做最后冲刺分享。',
    location: '独墅湖校区创新港 501',
    startTime: '2026-04-19T18:30:00',
    endTime: '2026-04-19T21:00:00',
    organizerId: 2,
    coverUrl: svgDataUri({ title: '计算机视觉竞赛冲刺分享会', subtitle: '创新港 501 · 竞赛组队', from: '#0f766e', to: '#5eead4' }),
    maxParticipants: 24,
    auditStatus: 'APPROVED',
    status: 'PUBLISHED',
    chatEnabled: false,
    teamingEnabled: false,
    tags: ['竞赛组队', '学术讲座'],
    createdAt: '2026-04-11T10:40:00',
  },
  {
    id: 12,
    title: '旧群聊回看测试活动',
    description: '这个活动用于观察活动群聊关闭后，历史消息是否被保留，以及入口是否按预期消失。',
    location: '独墅湖校区公共教学楼 C102',
    startTime: '2026-04-23T18:00:00',
    endTime: '2026-04-23T20:00:00',
    organizerId: 1,
    coverUrl: svgDataUri({ title: '旧群聊回看测试活动', subtitle: '公共教学楼 C102 · 校园生活', from: '#334155', to: '#94a3b8' }),
    maxParticipants: 18,
    auditStatus: 'APPROVED',
    status: 'PUBLISHED',
    chatEnabled: false,
    teamingEnabled: true,
    tags: ['校园生活', '兴趣社交'],
    createdAt: '2026-04-11T19:20:00',
  },
  {
    id: 13,
    title: '数学建模赛前密训',
    description: '比赛前的集中训练，包含题型拆解、LaTeX 写作和展示答辩。',
    location: '独墅湖校区理工楼 406',
    startTime: '2026-04-17T19:00:00',
    endTime: '2026-04-17T22:00:00',
    organizerId: 10,
    coverUrl: svgDataUri({ title: '数学建模赛前密训', subtitle: '理工楼 406 · 竞赛组队', from: '#1e40af', to: '#60a5fa' }),
    maxParticipants: 3,
    auditStatus: 'APPROVED',
    status: 'PUBLISHED',
    chatEnabled: false,
    teamingEnabled: true,
    tags: ['竞赛组队', '学术讲座'],
    createdAt: '2026-04-15T09:00:00',
  },
  {
    id: 14,
    title: '国际交流分享午餐会',
    description: '轻松的小型午餐交流，适合想练口语、结识交换生或了解留学申请的同学。',
    location: '天赐庄校区留学生之家',
    startTime: '2026-05-07T12:00:00',
    endTime: '2026-05-07T13:30:00',
    organizerId: 10,
    coverUrl: svgDataUri({ title: '国际交流分享午餐会', subtitle: '留学生之家 · 兴趣社交', from: '#ec4899', to: '#f9a8d4' }),
    maxParticipants: 20,
    auditStatus: 'APPROVED',
    status: 'PUBLISHED',
    chatEnabled: false,
    teamingEnabled: true,
    tags: ['兴趣社交', '校园生活'],
    createdAt: '2026-04-12T12:20:00',
  },
  {
    id: 15,
    title: '24 小时公益捐书接力',
    description: '跨校区连续 24 小时捐书接力，适合验证正在进行中的活动展示和提醒逻辑。',
    location: '未来校区图书馆门口',
    startTime: '2026-04-16T09:00:00',
    endTime: '2026-04-17T06:00:00',
    organizerId: 2,
    coverUrl: svgDataUri({ title: '24 小时公益捐书接力', subtitle: '图书馆门口 · 志愿服务', from: '#16a34a', to: '#86efac' }),
    maxParticipants: 80,
    auditStatus: 'APPROVED',
    status: 'PUBLISHED',
    chatEnabled: true,
    teamingEnabled: true,
    tags: ['志愿服务', '校园生活'],
    createdAt: '2026-04-15T08:30:00',
  },
];

const teams = [
  {
    id: 1,
    activityId: 1,
    title: '学术讲座｜AI 讲座志愿接待小组',
    description: '想一起负责签到、引导和提问整理的同学可以加入，适合沟通耐心好、时间稳定的成员。',
    creatorId: 2,
    maxMembers: 5,
    status: 'OPEN',
    memberIds: [2, 1, 4],
    pendingUserIds: [],
    createdAt: '2026-04-08T10:00:00',
  },
  {
    id: 2,
    activityId: 5,
    title: '竞赛组队｜服务外包赛缺前端',
    description: '已有产品和算法同学，想再找 1 名能做展示型页面的前端同学一起冲刺答辩。',
    creatorId: 3,
    maxMembers: 5,
    status: 'OPEN',
    memberIds: [3, 5],
    pendingUserIds: [],
    createdAt: '2026-04-08T13:30:00',
  },
  {
    id: 3,
    activityId: null,
    title: '运动健身｜周三羽毛球搭子',
    description: '每周三晚上打两个小时，水平不限，主要是规律运动、互相拉练。',
    creatorId: 4,
    maxMembers: 4,
    status: 'OPEN',
    memberIds: [4],
    pendingUserIds: [],
    createdAt: '2026-04-02T19:20:00',
  },
  {
    id: 4,
    activityId: null,
    title: '考研搭子｜图书馆晚间互相监督',
    description: '工作日晚间固定打卡，适合想找稳定学习节奏和互相监督的同学。',
    creatorId: 5,
    maxMembers: 3,
    status: 'OPEN',
    memberIds: [5],
    pendingUserIds: [1],
    createdAt: '2026-04-03T21:10:00',
  },
  {
    id: 5,
    activityId: 6,
    title: '兼职实习｜简历互改答辩组',
    description: '围绕简历门诊活动提前准备展示稿和自我介绍，顺带互看简历和项目经历。',
    creatorId: 1,
    maxMembers: 6,
    status: 'OPEN',
    memberIds: [1, 3],
    pendingUserIds: [],
    createdAt: '2026-04-07T15:10:00',
  },
];

const registrations = [
  {
    id: 1,
    activityId: 1,
    userId: 1,
    realName: '林知夏',
    phone: '13812345678',
    college: '计算机科学与技术学院',
    intro: '想了解 AI 校园应用，也愿意协助现场提问整理。',
    status: 'APPROVED',
    createdAt: '2026-04-09T10:20:00',
  },
  {
    id: 2,
    activityId: 1,
    userId: 4,
    realName: '许安',
    phone: '13688886666',
    college: '文学院',
    intro: '对讲座主题感兴趣，也想认识更多做产品设计的同学。',
    status: 'APPLIED',
    createdAt: '2026-04-09T11:15:00',
  },
  {
    id: 3,
    activityId: 2,
    userId: 1,
    realName: '林知夏',
    phone: '13812345678',
    college: '计算机科学与技术学院',
    intro: '偏好后勤记录和比分统计，也想拍摄活动素材。',
    status: 'APPROVED',
    createdAt: '2026-04-08T15:40:00',
  },
  {
    id: 4,
    activityId: 6,
    userId: 5,
    realName: '沈奕',
    phone: '13566667777',
    college: '管理学院',
    intro: '主要想看看简历结构怎么优化，顺便听学长姐分享。',
    status: 'APPLIED',
    createdAt: '2026-04-08T17:00:00',
  },
];

let teamJoinRequests = [
  {
    id: 1,
    teamId: 1,
    userId: 1,
    message: '可以负责签到和现场提问整理，希望一起把讲座流程做顺。',
    status: 'APPROVED',
    createdAt: '2026-04-08T11:10:00',
  },
  {
    id: 2,
    teamId: 1,
    userId: 4,
    message: '我愿意协助串场、整理问题和现场秩序维护。',
    status: 'APPROVED',
    createdAt: '2026-04-08T11:35:00',
  },
  {
    id: 3,
    teamId: 4,
    userId: 1,
    message: '想找晚间固定自习搭子，互相监督效率和进度。',
    status: 'PENDING',
    createdAt: '2026-04-04T09:30:00',
  },
  {
    id: 4,
    teamId: 5,
    userId: 5,
    message: '想一起准备简历展示，顺带请教项目经历怎么讲。',
    status: 'REJECTED',
    createdAt: '2026-04-08T16:20:00',
  },
];

let favoriteIds = new Set([1, 4, 6]);

const dmMessages = {
  2: [
    { id: 1, senderId: 2, content: '你好，看到你报名了讲座，后续有空可以一起准备提问。', createdAt: '2026-04-09T13:20:00' },
    { id: 2, senderId: 1, content: '好呀，我也想提前了解一下讲座流程。', createdAt: '2026-04-09T13:24:00' },
    { id: 3, senderId: 2, content: '今晚我会把流程发在群里，你也可以先看看活动详情页。', createdAt: '2026-04-09T13:26:00' },
  ],
  4: [
    { id: 1, senderId: 4, content: '音乐夜你去吗？要不要一起提前占位置。', createdAt: '2026-04-09T12:10:00' },
    { id: 2, senderId: 1, content: '可以，我大概六点半到。', createdAt: '2026-04-09T12:13:00' },
  ],
};

const teamMessages = {
  1: [
    { id: 1, senderId: 2, content: '欢迎大家进组，周五晚一起确认分工。', createdAt: '2026-04-09T09:00:00' },
    { id: 2, senderId: 1, content: '我可以负责签到和简单拍照。', createdAt: '2026-04-09T09:06:00' },
    { id: 3, senderId: 4, content: '那我来整理现场问题和主持串场。', createdAt: '2026-04-09T09:12:00' },
  ],
  5: [
    { id: 1, senderId: 1, content: '这周先把每个人的简历版本传一下，我统一整理。', createdAt: '2026-04-09T14:10:00' },
    { id: 2, senderId: 3, content: '收到，我今晚把项目经历那部分再润色一下。', createdAt: '2026-04-09T14:18:00' },
  ],
};

const activityChatMessages = {
  1: [
    { id: 1, senderId: 2, content: '欢迎进入活动群聊，有问题可以直接在这里问。', createdAt: '2026-04-09T08:50:00' },
    { id: 2, senderId: 1, content: '想问一下讲座结束后是否有线下交流环节？', createdAt: '2026-04-09T08:55:00' },
    { id: 3, senderId: 2, content: '有的，最后会留 20 分钟交流。', createdAt: '2026-04-09T08:58:00' },
  ],
  6: [
    { id: 1, senderId: 1, content: '简历门诊活动当天请大家带纸质版简历。', createdAt: '2026-04-09T09:40:00' },
  ],
};

let notifications = [
  {
    id: 1,
    userId: 1,
    type: 'TEAM_JOIN',
    title: '你的队伍有新动态',
    content: '“AI 讲座志愿接待小组” 已有 3 名成员加入，可以进入队伍聊天继续沟通。',
    read: false,
    createdAt: '2026-04-09T10:40:00',
  },
  {
    id: 2,
    userId: 1,
    type: 'SYSTEM',
    title: '活动提醒',
    content: '你报名的“人工智能与未来社会”将于 4 月 18 日 19:00 开始。',
    read: true,
    createdAt: '2026-04-09T09:15:00',
  },
  {
    id: 3,
    userId: 1,
    type: 'AUDIT',
    title: '发布成功',
    content: '你发布的“简历门诊与实习分享”已经审核通过并上线展示。',
    read: false,
    createdAt: '2026-04-08T21:05:00',
  },
];

let featured = [
  { activityId: 1, weight: 90, startTime: '2026-04-09T00:00:00', endTime: '2026-04-19T00:00:00' },
  { activityId: 4, weight: 78, startTime: '2026-04-10T00:00:00', endTime: '2026-04-23T00:00:00' },
];

function getUser(id) {
  return users.find((item) => String(item.id) === String(id)) || null;
}

function parseMockUserId(req) {
  const auth = String(req.headers.authorization || '');
  const match = auth.match(/^Bearer\s+mock-token:(\d+)$/);
  return match ? Number(match[1]) : null;
}

function currentUser(req) {
  const tokenUserId = parseMockUserId(req);
  if (tokenUserId) {
    const tokenUser = getUser(tokenUserId);
    if (tokenUser) return tokenUser;
  }
  const referer = String(req.headers.referer || '');
  if (referer.includes('/admin.html')) return getUser(9);
  return getUser(1);
}

function issueMockToken(userId) {
  return `mock-token:${userId}`;
}

function nextUserId() {
  return users.reduce((max, item) => Math.max(max, Number(item.id) || 0), 0) + 1;
}

function nextId(list) {
  return list.reduce((max, item) => Math.max(max, Number(item.id) || 0), 0) + 1;
}

function pushNotification(userId, type, title, content) {
  notifications.unshift({
    id: nextId(notifications),
    userId: Number(userId),
    type,
    title,
    content,
    read: false,
    createdAt: nowIso(),
  });
}

function nowIso() {
  return new Date().toISOString().slice(0, 19);
}

function countParticipants(activityId) {
  return registrations.filter((item) => Number(item.activityId) === Number(activityId) && String(item.status || '').toUpperCase() === 'APPROVED').length;
}

function countPendingRegistrations(activityId) {
  return registrations.filter((item) => Number(item.activityId) === Number(activityId) && String(item.status || '').toUpperCase() === 'APPLIED').length;
}

function countTeamMembers(team) {
  return Array.isArray(team.memberIds) ? team.memberIds.length : 0;
}

function teamRoleForUser(team, userId) {
  if (Number(team.creatorId) === Number(userId)) return 'CREATOR';
  return team.memberIds.includes(userId) ? 'MEMBER' : null;
}

function teamJoinRequestExists(teamId, userId, statuses = []) {
  return teamJoinRequests.find((item) => {
    if (Number(item.teamId) !== Number(teamId) || Number(item.userId) !== Number(userId)) return false;
    if (statuses.length === 0) return true;
    return statuses.includes(String(item.status || '').toUpperCase());
  }) || null;
}

function syncPendingUserIds(team) {
  team.pendingUserIds = teamJoinRequests
    .filter((item) => Number(item.teamId) === Number(team.id) && String(item.status || '').toUpperCase() === 'PENDING')
    .map((item) => Number(item.userId));
}

function teamRequestItem(item) {
  const user = getUser(item.userId);
  return {
    id: item.id,
    userId: item.userId,
    nickname: user ? user.nickname : `用户${item.userId}`,
    avatarUrl: user ? user.avatarUrl : avatars.friend,
    studentNo: user ? user.studentNo : '',
    college: user ? user.college : '',
    major: user ? user.major : '',
    educationLevel: user ? user.educationLevel : '',
    message: item.message || '',
    status: item.status,
    createdAt: item.createdAt,
  };
}

function canManageTeam(team, user) {
  return !!(team && user && (Number(team.creatorId) === Number(user.id) || user.role === 'ADMIN'));
}

function canManageActivity(activity, user) {
  return !!(activity && user && (Number(activity.organizerId) === Number(user.id) || user.role === 'ADMIN'));
}

function teamForViewer(team, viewerId) {
  const creator = getUser(team.creatorId);
  const activity = team.activityId ? activities.find((item) => Number(item.id) === Number(team.activityId)) : null;
  const role = teamRoleForUser(team, viewerId);
  return {
    id: team.id,
    activityId: team.activityId,
    title: team.title,
    description: team.description,
    creatorId: team.creatorId,
    creatorName: creator ? creator.nickname : '未知用户',
    activityTitle: activity ? activity.title : null,
    maxMembers: team.maxMembers,
    members: countTeamMembers(team),
    status: team.status,
    startTime: team.startTime || null,
    endTime: team.endTime || null,
    role,
    joined: !!role,
    pending: !role && !!teamJoinRequestExists(team.id, viewerId, ['PENDING']),
    createdAt: team.createdAt,
  };
}

function publicUserProfile(user) {
  if (!user) return null;
  return {
    userId: user.id,
    nickname: user.nickname,
    avatarUrl: user.avatarUrl,
    college: user.college || '',
    campus: user.campus || '',
    major: user.major || '',
    educationLevel: user.educationLevel || '',
    grade: user.grade || '',
    bio: user.bio || '',
  };
}

function teamDetail(team, viewerId) {
  const base = teamForViewer(team, viewerId);
  const creator = getUser(team.creatorId);
  return {
    ...base,
    members: team.memberIds.map((id) => {
      const user = getUser(id);
      return {
        id,
        nickname: user ? user.nickname : `用户${id}`,
        role: id === team.creatorId ? 'CREATOR' : 'MEMBER',
      };
    }),
    creatorProfile: publicUserProfile(creator),
  };
}

function activityForList(activity) {
  return {
    id: activity.id,
    title: activity.title,
    location: activity.location,
    startTime: activity.startTime,
    endTime: activity.endTime,
    coverUrl: activity.coverUrl,
    maxParticipants: activity.maxParticipants,
    participants: countParticipants(activity.id),
    tags: activity.tags,
  };
}

function activityRegistrationItem(item) {
  const user = getUser(item.userId);
  return {
    id: item.id,
    userId: item.userId,
    nickname: user ? user.nickname : `用户${item.userId}`,
    avatarUrl: user ? user.avatarUrl : avatars.friend,
    studentNo: user ? user.studentNo : '',
    major: user ? user.major : '',
    educationLevel: user ? user.educationLevel : '',
    campus: user ? user.campus : '',
    bio: user ? user.bio : '',
    realName: item.realName,
    phone: item.phone,
    college: item.college,
    intro: item.intro,
    status: item.status,
    createdAt: item.createdAt,
  };
}

function canAccessActivityChat(activityId, user) {
  const activity = activities.find((item) => Number(item.id) === Number(activityId));
  if (!activity) return { activity: null, allowed: false, message: '活动不存在' };
  if (!activity.chatEnabled) return { activity, allowed: false, message: '群聊未开启' };
  if (Number(activity.organizerId) === Number(user.id) || user.role === 'ADMIN') {
    return { activity, allowed: true };
  }
  const approved = registrations.some((item) => Number(item.activityId) === Number(activityId)
    && Number(item.userId) === Number(user.id)
    && String(item.status || '').toUpperCase() === 'APPROVED');
  if (!approved) {
    return { activity, allowed: false, message: '报名通过后才可进入活动群聊' };
  }
  return { activity, allowed: true };
}

function activityDetail(activity, viewerId) {
  const organizer = getUser(activity.organizerId);
  return {
    id: activity.id,
    title: activity.title,
    description: activity.description,
    location: activity.location,
    startTime: activity.startTime,
    endTime: activity.endTime,
    coverUrl: activity.coverUrl,
    organizerId: activity.organizerId,
    organizerName: organizer ? organizer.nickname : '未知发布人',
    maxParticipants: activity.maxParticipants,
    participants: countParticipants(activity.id),
    pendingRegistrations: countPendingRegistrations(activity.id),
    tags: activity.tags,
    favorited: favoriteIds.has(activity.id) && Number(viewerId) === 1,
    auditStatus: activity.auditStatus,
    status: activity.status,
    chatEnabled: !!activity.chatEnabled,
    teamingEnabled: activity.teamingEnabled !== false,
  };
}

function activityForAdmin(activity) {
  const organizer = getUser(activity.organizerId);
  const relatedTeams = teams.filter((item) => Number(item.activityId) === Number(activity.id));
  const favCount = favoriteIds.has(activity.id) ? 1 : 0;
  const feat = featured.find((item) => Number(item.activityId) === Number(activity.id));
  return {
    id: activity.id,
    title: activity.title,
    organizerId: activity.organizerId,
    organizerName: organizer ? organizer.nickname : '未知发布人',
    location: activity.location,
    startTime: activity.startTime,
    createdAt: activity.createdAt,
    status: activity.status,
    auditStatus: activity.auditStatus,
    registeredCount: countParticipants(activity.id),
    favoriteCount: favCount,
    teamCount: relatedTeams.length,
    tags: activity.tags,
    featuredWeight: feat ? feat.weight : null,
  };
}

function registrationsForUser(userId) {
  return registrations
    .filter((item) => Number(item.userId) === Number(userId))
    .filter((item) => ['APPLIED', 'APPROVED'].includes(String(item.status || '').toUpperCase()))
    .map((item) => {
      const activity = activities.find((entry) => Number(entry.id) === Number(item.activityId));
      const organizer = activity ? getUser(activity.organizerId) : null;
      return {
        id: item.id,
        status: item.status,
        createdAt: item.createdAt,
        activity: activity ? {
          id: activity.id,
          title: activity.title,
          organizerId: activity.organizerId,
          organizerName: organizer ? organizer.nickname : '未知发布人',
        } : null,
      };
    });
}

function teamsForUser(userId) {
  return teams
    .filter((team) => team.memberIds.includes(userId))
    .map((team) => ({
      id: team.id,
      title: team.title,
      status: team.status,
      members: countTeamMembers(team),
      maxMembers: team.maxMembers,
      role: teamRoleForUser(team, userId),
      participationStatus: 'JOINED',
    }));
}

function joinedTeamsForUser(userId) {
  const joined = teamsForUser(userId).filter((team) => String(team.role) !== 'CREATOR');
  const pending = teamJoinRequests
    .filter((item) => Number(item.userId) === Number(userId) && String(item.status || '').toUpperCase() === 'PENDING')
    .filter((item) => !joined.some((team) => Number(team.id) === Number(item.teamId)))
    .map((item) => {
      const team = teams.find((entry) => Number(entry.id) === Number(item.teamId));
      if (!team) return null;
      return {
        id: team.id,
        title: team.title,
        status: team.status,
        members: countTeamMembers(team),
        maxMembers: team.maxMembers,
        role: 'APPLICANT',
        participationStatus: 'PENDING',
      };
    })
    .filter(Boolean);
  return [...pending, ...joined];
}

function createdTeamsForUser(userId) {
  return teams
    .filter((team) => Number(team.creatorId) === Number(userId))
    .map((team) => ({
      id: team.id,
      title: team.title,
      status: team.status,
      members: countTeamMembers(team),
      maxMembers: team.maxMembers,
      role: 'CREATOR',
      participationStatus: 'CREATOR',
    }));
}

function teamChatsForUser(userId) {
  return teamsForUser(userId);
}

function purgeExpiredNotifications() {
  const cutoff = new Date();
  cutoff.setMonth(cutoff.getMonth() - 6);
  const cutoffTime = cutoff.getTime();
  notifications = notifications.filter((item) => {
    const ts = new Date(item.createdAt || '').getTime();
    if (Number.isNaN(ts)) return true;
    return ts >= cutoffTime;
  });
}

function notificationsForUser(userId, status = 'all', page = 1, size = 20) {
  purgeExpiredNotifications();
  const normalizedStatus = String(status || 'all').toLowerCase();
  const filtered = notifications
    .filter((item) => Number(item.userId) === Number(userId))
    .filter((item) => {
      if (normalizedStatus === 'read') return !!item.read;
      if (normalizedStatus === 'unread') return !item.read;
      return true;
    })
    .sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')));
  const safePage = Math.max(1, Number(page) || 1);
  const safeSize = Math.max(1, Math.min(100, Number(size) || 20));
  const start = (safePage - 1) * safeSize;
  return {
    items: jsonClone(filtered.slice(start, start + safeSize)),
    total: filtered.length,
    page: safePage,
    size: safeSize,
  };
}

function favoritesForUser(userId) {
  if (Number(userId) !== 1) return [];
  return activities.filter((activity) => favoriteIds.has(activity.id)).map((activity) => ({
    id: activity.id,
    title: activity.title,
  }));
}

function publishedForUser(userId) {
  return activities
    .filter((activity) => Number(activity.organizerId) === Number(userId))
    .map((activity) => ({ id: activity.id, title: activity.title }));
}

function dmThreadsForUser(userId) {
  return Object.entries(dmMessages).map(([peerId, list]) => {
    const peer = getUser(Number(peerId));
    const last = list[list.length - 1];
    const unreadCount = Number(peerId) === 2 ? 1 : 0;
    return {
      peerId: Number(peerId),
      peerNickname: peer ? peer.nickname : `用户${peerId}`,
      peerAvatarUrl: peer ? peer.avatarUrl : avatars.friend,
      lastMessage: last ? last.content : '暂无消息',
      lastCreatedAt: last ? last.createdAt : nowIso(),
      unreadCount,
    };
  }).sort((a, b) => String(b.lastCreatedAt).localeCompare(String(a.lastCreatedAt)));
}

function adminUserItem(user) {
  return {
    id: user.id,
    username: user.username,
    studentNo: user.studentNo,
    nickname: user.nickname,
    avatarUrl: user.avatarUrl,
    college: user.college,
    role: user.role,
    enabled: user.status === 1,
    createdAt: user.createdAt,
    publishedCount: activities.filter((item) => Number(item.organizerId) === Number(user.id)).length,
    registrationCount: registrations.filter((item) => Number(item.userId) === Number(user.id)).length,
    teamCount: teams.filter((team) => team.memberIds.includes(user.id)).length,
  };
}

function adminUserDetail(user) {
  return {
    ...adminUserItem(user),
    email: user.email,
    phone: user.phone,
    bio: user.bio,
    publishedActivities: activities.filter((item) => Number(item.organizerId) === Number(user.id)).map((item) => ({
      ...activityForAdmin(item),
    })),
    teams: teams.filter((team) => team.memberIds.includes(user.id)).map((team) => {
      const relatedActivity = activities.find((item) => Number(item.id) === Number(team.activityId));
      return {
        id: team.id,
        title: team.title,
        activityTitle: relatedActivity ? relatedActivity.title : '',
        memberRole: team.creatorId === user.id ? 'CREATOR' : 'MEMBER',
        memberCount: countTeamMembers(team),
        maxMembers: team.maxMembers,
        status: team.status,
      };
    }),
    registrations: registrations.filter((item) => Number(item.userId) === Number(user.id)).map((item) => {
      const activity = activities.find((entry) => Number(entry.id) === Number(item.activityId));
      return {
        id: item.id,
        activityTitle: activity ? activity.title : '未知活动',
        status: item.status,
        createdAt: item.createdAt,
      };
    }),
  };
}

function stats() {
  const messageCount = Object.values(teamMessages).reduce((sum, list) => sum + list.length, 0)
    + Object.values(dmMessages).reduce((sum, list) => sum + list.length, 0)
    + Object.values(activityChatMessages).reduce((sum, list) => sum + list.length, 0);
  return {
    userCount: users.length,
    activityCount: activities.length,
    publishedActivities: activities.filter((item) => item.status === 'PUBLISHED').length,
    disabledUsers: users.filter((item) => item.status !== 1).length,
    teamCount: teams.length,
    registrationCount: registrations.length,
    messageCount,
    featuredActivities: featured.length,
  };
}

function ok(data) {
  return { success: true, data };
}

function readJson(req) {
  return new Promise((resolve) => {
    let raw = '';
    req.on('data', (chunk) => {
      raw += chunk.toString();
    });
    req.on('end', () => {
      if (!raw) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(raw));
      } catch (err) {
        resolve({});
      }
    });
  });
}

function send(res, statusCode, body, contentType = 'application/json; charset=utf-8') {
  res.writeHead(statusCode, {
    'Content-Type': contentType,
    'Cache-Control': 'no-store',
  });
  res.end(body);
}

function sendJson(res, data, statusCode = 200) {
  send(res, statusCode, JSON.stringify(data, null, 2));
}

function sendText(res, text, contentType = 'text/plain; charset=utf-8') {
  send(res, 200, text, contentType);
}

function notFound(res) {
  sendJson(res, { success: false, message: 'Not Found', data: null }, 404);
}

function isAdminRequest(req) {
  const user = currentUser(req);
  return user && user.role === 'ADMIN';
}

function injectHtml(html) {
  let result = html.replace('</head>', '    <link rel="stylesheet" href="/__mock__/fa-fallback.css">\n</head>');
  result = result.replace(
    /<script src="assets\/js\/script\.js[^"]*"><\/script>/,
    '<script src="/__mock__/bootstrap.js"></script>\n    $&'
  );
  return result;
}

const faFallbackCss = `
i.fa-solid,
i.fa-regular {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1em;
  min-width: 1em;
  font-style: normal;
  line-height: 1;
}
i.fa-solid::before,
i.fa-regular::before {
  display: inline-block;
  width: 1em;
  text-align: center;
  font-family: "SF Pro Text","PingFang SC","Helvetica Neue",Arial,sans-serif;
  font-weight: 700;
  font-size: 0.96em;
  color: currentColor;
}
.fa-house::before { content: "⌂"; }
.fa-calendar-days::before { content: "◷"; }
.fa-user-group::before { content: "群"; }
.fa-comment-dots::before { content: "聊"; }
.fa-user::before { content: "人"; }
.fa-plus::before { content: "+"; }
.fa-chevron-left::before { content: "‹"; }
.fa-chevron-right::before { content: "›"; }
.fa-bell::before { content: "铃"; }
.fa-pen-to-square::before { content: "写"; }
.fa-arrow-right::before { content: "→"; }
.fa-arrow-down::before { content: "↓"; }
.fa-arrow-up::before { content: "↑"; }
.fa-image::before { content: "图"; }
.fa-calendar-plus::before { content: "加"; }
.fa-user-plus::before { content: "＋"; }
.fa-magnifying-glass::before { content: "搜"; }
.fa-compass::before { content: "探"; }
.fa-star::before { content: "★"; }
.fa-eye::before { content: "◉"; }
.fa-eye-slash::before { content: "◎"; }
.fa-circle-question::before { content: "?"; }
.fa-camera::before { content: "拍"; }
.fa-gear::before { content: "设"; }
.fa-cloud-arrow-up::before { content: "传"; }
.fa-location-dot::before { content: "地"; }
.fa-users::before { content: "众"; }
.fa-hashtag::before { content: "#"; }
.fa-xmark::before { content: "×"; }
.fa-bullhorn::before { content: "播"; }
.fa-calendar-check::before { content: "报"; }
.fa-user-tie::before { content: "联"; }
.fa-bookmark::before { content: "藏"; }
.fa-share-nodes::before { content: "分"; }
`;

const bootstrapJs = `
(() => {
  window.__AXURE_MOCK__ = true;
  const authPages = /(?:^|\\/)(index|login|register)\\.html$/;
  try {
    if (authPages.test(location.pathname)) {
      localStorage.removeItem('campus_pulse_token');
    } else {
      if (!localStorage.getItem('campus_pulse_token')) {
        localStorage.setItem('campus_pulse_token', 'mock-token:1');
      }
    }
  } catch (e) {}
})();
`;

function mimeType(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  if (ext === '.html') return 'text/html; charset=utf-8';
  if (ext === '.css') return 'text/css; charset=utf-8';
  if (ext === '.js') return 'application/javascript; charset=utf-8';
  if (ext === '.json') return 'application/json; charset=utf-8';
  if (ext === '.png') return 'image/png';
  if (ext === '.jpg' || ext === '.jpeg') return 'image/jpeg';
  if (ext === '.svg') return 'image/svg+xml';
  return 'application/octet-stream';
}

function filterActivitiesByKeyword(list, keyword) {
  const kw = String(keyword || '').trim().toLowerCase();
  if (!kw) return list;
  return list.filter((item) => {
    return [item.title, item.location, item.description, ...(item.tags || [])]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(kw));
  });
}

async function handleApi(req, res, urlObj) {
  const { pathname, searchParams } = urlObj;
  const me = currentUser(req);

  if (pathname === '/api/auth/me' && req.method === 'GET') {
    sendJson(res, ok(jsonClone(me)));
    return;
  }

  if (pathname === '/api/tags' && req.method === 'GET') {
    sendJson(res, ok(jsonClone(tags)));
    return;
  }

  if (pathname === '/api/teams' && req.method === 'GET') {
    const list = teams.map((team) => teamForViewer(team, me.id));
    sendJson(res, ok(list));
    return;
  }

  if (pathname === '/api/teams' && req.method === 'POST') {
    const body = await readJson(req);
    const teamId = nextId(teams);
    const team = {
      id: teamId,
      activityId: body.activityId || null,
      title: body.title || '新建队伍',
      description: body.description || '队伍简介待补充',
      creatorId: me.id,
      maxMembers: Math.min(Math.max(Number(body.maxMembers || 5), 2), 50),
      status: 'OPEN',
      memberIds: [me.id],
      pendingUserIds: [],
      startTime: body.startTime || null,
      endTime: body.endTime || null,
      createdAt: nowIso(),
    };
    teams.unshift(team);
    sendJson(res, ok({ id: team.id }));
    return;
  }

  const teamDetailMatch = pathname.match(/^\/api\/teams\/(\d+)$/);
  if (teamDetailMatch && req.method === 'GET') {
    const team = teams.find((item) => Number(item.id) === Number(teamDetailMatch[1]));
    if (!team) return notFound(res);
    syncPendingUserIds(team);
    sendJson(res, ok(teamDetail(team, me.id)));
    return;
  }

  if (teamDetailMatch && req.method === 'PUT') {
    const team = teams.find((item) => Number(item.id) === Number(teamDetailMatch[1]));
    if (!team) return notFound(res);
    if (!canManageTeam(team, me)) return sendJson(res, { success: false, message: '无权限', data: null }, 403);
    const body = await readJson(req);
    const maxMembers = Number(body.maxMembers || '0') || 0;
    if (maxMembers > 0 && maxMembers < countTeamMembers(team)) {
      return sendJson(res, { success: false, message: '队伍人数上限不能低于当前成员数', data: null }, 400);
    }
    if (!String(body.title || '').trim()) {
      return sendJson(res, { success: false, message: '队伍名称不能为空', data: null }, 400);
    }
    team.title = String(body.title || '').trim();
    team.description = String(body.description || '').trim();
    team.startTime = body.startTime || null;
    team.endTime = body.endTime || null;
    team.maxMembers = maxMembers;
    sendJson(res, ok(null));
    return;
  }

  if (teamDetailMatch && req.method === 'DELETE') {
    const teamIndex = teams.findIndex((item) => Number(item.id) === Number(teamDetailMatch[1]));
    if (teamIndex === -1) return notFound(res);
    const team = teams[teamIndex];
    if (!canManageTeam(team, me)) return sendJson(res, { success: false, message: '无权限', data: null }, 403);
    teams.splice(teamIndex, 1);
    teamJoinRequests = teamJoinRequests.filter((item) => Number(item.teamId) !== Number(team.id));
    delete teamMessages[team.id];
    sendJson(res, ok(null));
    return;
  }

  const teamJoinMatch = pathname.match(/^\/api\/teams\/(\d+)\/join$/);
  if (teamJoinMatch && req.method === 'POST') {
    const team = teams.find((item) => Number(item.id) === Number(teamJoinMatch[1]));
    if (!team) return notFound(res);
    const body = await readJson(req);
    if (!team.memberIds.includes(me.id)) {
      const existingPending = teamJoinRequestExists(team.id, me.id, ['PENDING']);
      if (!existingPending) {
        const reusable = teamJoinRequestExists(team.id, me.id, ['REJECTED', 'CANCELLED']);
        if (reusable) {
          reusable.status = 'PENDING';
          reusable.message = String(body.message || reusable.message || '').trim();
          reusable.createdAt = nowIso();
        } else {
          teamJoinRequests.push({
            id: nextId(teamJoinRequests),
            teamId: team.id,
            userId: me.id,
            message: String(body.message || '').trim(),
            status: 'PENDING',
            createdAt: nowIso(),
          });
        }
      }
      syncPendingUserIds(team);
    }
    sendJson(res, ok({ status: 'PENDING' }));
    return;
  }

  const teamCancelRequestMatch = pathname.match(/^\/api\/teams\/(\d+)\/cancel-request$/);
  if (teamCancelRequestMatch && req.method === 'POST') {
    const team = teams.find((item) => Number(item.id) === Number(teamCancelRequestMatch[1]));
    if (!team) return notFound(res);
    const request = teamJoinRequests.find((item) => Number(item.teamId) === Number(team.id)
      && Number(item.userId) === Number(me.id)
      && String(item.status || '').toUpperCase() === 'PENDING');
    if (!request) {
      return sendJson(res, { success: false, message: '未找到可取消的入队申请', data: null }, 404);
    }
    request.status = 'CANCELLED';
    syncPendingUserIds(team);
    pushNotification(team.creatorId, 'TEAM_JOIN_CANCELLED', '入队申请已取消', `${me.nickname} 取消了加入队伍《${team.title}》的申请。`);
    sendJson(res, ok(null));
    return;
  }

  const teamRequestsMatch = pathname.match(/^\/api\/teams\/(\d+)\/requests$/);
  if (teamRequestsMatch && req.method === 'GET') {
    const team = teams.find((item) => Number(item.id) === Number(teamRequestsMatch[1]));
    if (!team) return notFound(res);
    if (!canManageTeam(team, me)) return sendJson(res, { success: false, message: '无权限查看申请列表', data: null }, 403);
    const list = teamJoinRequests
      .filter((item) => Number(item.teamId) === Number(team.id))
      .sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)))
      .map(teamRequestItem);
    sendJson(res, ok(list));
    return;
  }

  const teamRequestActionMatch = pathname.match(/^\/api\/teams\/(\d+)\/requests\/(\d+)\/(approve|reject)$/);
  if (teamRequestActionMatch && req.method === 'POST') {
    const team = teams.find((item) => Number(item.id) === Number(teamRequestActionMatch[1]));
    if (!team) return notFound(res);
    if (!canManageTeam(team, me)) return sendJson(res, { success: false, message: '无权限', data: null }, 403);
    const request = teamJoinRequests.find((item) => Number(item.id) === Number(teamRequestActionMatch[2]) && Number(item.teamId) === Number(team.id));
    if (!request) return notFound(res);
    const action = teamRequestActionMatch[3];
    const currentStatus = String(request.status || '').toUpperCase();
    if (action === 'approve') {
      if (!['PENDING', 'REJECTED', 'APPROVED'].includes(currentStatus)) {
        return sendJson(res, { success: false, message: '当前申请状态不可改为通过', data: null }, 409);
      }
      if (currentStatus !== 'APPROVED') {
        if (team.maxMembers > 0 && countTeamMembers(team) >= team.maxMembers) {
          return sendJson(res, { success: false, message: '队伍已满员', data: null }, 409);
        }
        request.status = 'APPROVED';
        if (!team.memberIds.includes(request.userId)) team.memberIds.push(request.userId);
      }
    } else {
      if (!['PENDING', 'APPROVED', 'REJECTED'].includes(currentStatus)) {
        return sendJson(res, { success: false, message: '当前申请状态不可改为拒绝', data: null }, 409);
      }
      request.status = 'REJECTED';
      team.memberIds = team.memberIds.filter((userId) => Number(userId) !== Number(request.userId));
    }
    syncPendingUserIds(team);
    sendJson(res, ok(null));
    return;
  }

  const teamMessagesMatch = pathname.match(/^\/api\/teams\/(\d+)\/messages$/);
  if (teamMessagesMatch && req.method === 'GET') {
    const teamId = Number(teamMessagesMatch[1]);
    const afterId = Number(searchParams.get('afterId') || '0');
    const list = (teamMessages[teamId] || []).filter((item) => item.id > afterId);
    sendJson(res, ok(jsonClone(list)));
    return;
  }
  if (teamMessagesMatch && req.method === 'POST') {
    const teamId = Number(teamMessagesMatch[1]);
    const body = await readJson(req);
    const list = teamMessages[teamId] || (teamMessages[teamId] = []);
    const nextId = list.length > 0 ? list[list.length - 1].id + 1 : 1;
    list.push({ id: nextId, senderId: me.id, content: body.content || '新消息', createdAt: nowIso() });
    sendJson(res, ok({ id: nextId }));
    return;
  }

  if (pathname === '/api/activities' && req.method === 'GET') {
    const page = Number(searchParams.get('page') || '1');
    const size = Number(searchParams.get('size') || '10');
    const keyword = searchParams.get('keyword') || '';
    const filtered = filterActivitiesByKeyword(activities, keyword);
    const start = (page - 1) * size;
    const items = filtered.slice(start, start + size).map(activityForList);
    sendJson(res, ok({ items, total: filtered.length, page, size }));
    return;
  }

  if (pathname === '/api/activities' && req.method === 'POST') {
    const body = await readJson(req);
    const activityId = nextId(activities);
    const item = {
      id: activityId,
      title: body.title || '新建活动',
      description: body.description || '活动简介待补充',
      location: body.location || '待定地点',
      startTime: body.startTime || '2026-04-30T19:00:00',
      endTime: body.endTime || null,
      organizerId: me.id,
      coverUrl: body.coverUrl || coverImages.career,
      maxParticipants: Number(body.maxParticipants || 50),
      auditStatus: 'APPROVED',
      status: 'PUBLISHED',
      chatEnabled: true,
      teamingEnabled: body.teamingEnabled !== false,
      tags: Array.isArray(body.tags) ? body.tags : ['校园生活'],
      createdAt: nowIso(),
    };
    activities.unshift(item);
    sendJson(res, ok({ id: item.id }));
    return;
  }

  if ((pathname === '/api/recommendations/feed' || pathname === '/api/recommendations/activities') && req.method === 'GET') {
    const size = Number(searchParams.get('size') || '6');
    const picks = activities
      .filter((item) => item.tags.some((tag) => me.interests.includes(tag)))
      .concat(activities.filter((item) => !item.tags.some((tag) => me.interests.includes(tag))))
      .slice(0, size)
      .map((item) => ({ ...activityForList(item), promoted: false, reason: item.tags.some((tag) => me.interests.includes(tag)) ? '兴趣匹配' : '近期热门' }));
    sendJson(res, ok(picks));
    return;
  }

  if (pathname === '/api/recommendations/teams' && req.method === 'GET') {
    const size = Number(searchParams.get('size') || '8');
    const picks = teams
      .map((team) => {
        const item = teamForViewer(team, me.id);
        const text = `${team.title || ''} ${team.description || ''}`;
        const interestHit = (me.interests || []).some((tag) => text.includes(tag));
        const vacancy = Math.max((team.maxMembers || 0) - (team.memberIds || []).length, 0);
        return {
          score: (interestHit ? 100 : 0) + Math.min(vacancy, 5) * 5 + (team.activityId ? 3 : 0),
          item: { ...item, tags: (me.interests || []).filter((tag) => text.includes(tag)), promoted: false, reason: interestHit ? '兴趣匹配' : '近期活跃' },
        };
      })
      .sort((a, b) => b.score - a.score)
      .slice(0, size)
      .map((entry) => entry.item);
    sendJson(res, ok(picks));
    return;
  }

  if (pathname === '/api/search' && req.method === 'GET') {
    const keyword = String(searchParams.get('keyword') || '').trim().toLowerCase();
    const type = String(searchParams.get('type') || 'all').trim().toLowerCase();
    const page = Math.max(Number(searchParams.get('page') || '1'), 1);
    const size = Math.min(Math.max(Number(searchParams.get('size') || '10'), 1), 30);
    const items = [];
    if (keyword && (type === 'all' || type === 'activity')) {
      activities.forEach((activity) => {
        const text = [activity.title, activity.description, activity.location, ...(activity.tags || [])].join(' ').toLowerCase();
        if (text.includes(keyword)) {
          items.push({
            targetType: 'ACTIVITY',
            ...activityForList(activity),
            description: activity.description,
            promoted: false,
            reason: '关键词与语义匹配',
            score: activity.title.toLowerCase().includes(keyword) ? 50 : 10,
          });
        }
      });
    }
    if (keyword && (type === 'all' || type === 'team')) {
      teams.forEach((team) => {
        const creator = getUser(team.creatorId);
        const text = [team.title, team.description, creator ? creator.nickname : ''].join(' ').toLowerCase();
        if (text.includes(keyword)) {
          const item = teamForViewer(team, me.id);
          items.push({
            targetType: 'TEAM',
            id: item.id,
            title: item.title,
            description: item.description,
            participants: item.members,
            maxParticipants: item.maxMembers,
            activityId: item.activityId,
            activityTitle: item.activityTitle,
            creatorId: item.creatorId,
            creatorName: item.creatorName,
            startTime: item.startTime,
            endTime: item.endTime,
            joined: item.joined,
            pending: item.pending,
            tags: [],
            promoted: false,
            reason: '关键词与语义匹配',
            score: item.title.toLowerCase().includes(keyword) ? 50 : 10,
          });
        }
      });
    }
    items.sort((a, b) => b.score - a.score);
    const start = (page - 1) * size;
    sendJson(res, ok({ items: items.slice(start, start + size), total: items.length, page, size }));
    return;
  }

  if (pathname === '/api/activities/highlights' && req.method === 'GET') {
    const size = Number(searchParams.get('size') || '6');
    const picks = activities.slice(0, size).map(activityForList);
    sendJson(res, ok(picks));
    return;
  }

  const activityDetailMatch = pathname.match(/^\/api\/activities\/(\d+)$/);
  if (activityDetailMatch && req.method === 'GET') {
    const activity = activities.find((item) => Number(item.id) === Number(activityDetailMatch[1]));
    if (!activity) return notFound(res);
    sendJson(res, ok(activityDetail(activity, me.id)));
    return;
  }

  if (activityDetailMatch && req.method === 'PUT') {
    const activity = activities.find((item) => Number(item.id) === Number(activityDetailMatch[1]));
    if (!activity) return notFound(res);
    if (!canManageActivity(activity, me)) return sendJson(res, { success: false, message: '无权限', data: null }, 403);
    const body = await readJson(req);
    const maxParticipants = Number(body.maxParticipants || '0') || 0;
    if (maxParticipants > 0 && maxParticipants < countParticipants(activity.id)) {
      return sendJson(res, { success: false, message: '人数上限不能低于当前已通过人数', data: null }, 400);
    }
    if (!String(body.title || '').trim()) {
      return sendJson(res, { success: false, message: '活动名称不能为空', data: null }, 400);
    }
    activity.title = String(body.title || '').trim();
    activity.description = String(body.description || '').trim();
    activity.location = String(body.location || '').trim();
    activity.startTime = body.startTime || activity.startTime;
    activity.endTime = body.endTime || null;
    activity.coverUrl = body.coverUrl || null;
    activity.maxParticipants = maxParticipants;
    activity.tags = Array.isArray(body.tags) ? body.tags.filter(Boolean) : [];
    activity.teamingEnabled = body.teamingEnabled !== false;
    activity.auditStatus = 'APPROVED';
    sendJson(res, ok(null));
    return;
  }

  if (activityDetailMatch && req.method === 'DELETE') {
    const activityIndex = activities.findIndex((item) => Number(item.id) === Number(activityDetailMatch[1]));
    if (activityIndex === -1) return notFound(res);
    const activity = activities[activityIndex];
    if (!canManageActivity(activity, me)) return sendJson(res, { success: false, message: '无权限', data: null }, 403);
    activities.splice(activityIndex, 1);
    for (let i = teams.length - 1; i >= 0; i -= 1) {
      if (Number(teams[i].activityId) === Number(activity.id)) {
        delete teamMessages[teams[i].id];
        teamJoinRequests = teamJoinRequests.filter((item) => Number(item.teamId) !== Number(teams[i].id));
        teams.splice(i, 1);
      }
    }
    for (let i = registrations.length - 1; i >= 0; i -= 1) {
      if (Number(registrations[i].activityId) === Number(activity.id)) registrations.splice(i, 1);
    }
    delete activityChatMessages[activity.id];
    favoriteIds.delete(activity.id);
    featured = featured.filter((item) => Number(item.activityId) !== Number(activity.id));
    sendJson(res, ok(null));
    return;
  }

  const activityRegsMatch = pathname.match(/^\/api\/activities\/(\d+)\/registrations$/);
  if (activityRegsMatch && req.method === 'GET') {
    const activityId = Number(activityRegsMatch[1]);
    const list = registrations
      .filter((item) => Number(item.activityId) === activityId)
      .map((item) => activityRegistrationItem(item));
    sendJson(res, ok(list));
    return;
  }

  const activityRegActionMatch = pathname.match(/^\/api\/activities\/(\d+)\/registrations\/(\d+)\/(approve|reject)$/);
  if (activityRegActionMatch && req.method === 'POST') {
    const activity = activities.find((item) => Number(item.id) === Number(activityRegActionMatch[1]));
    if (!activity) return notFound(res);
    if (!canManageActivity(activity, me)) return sendJson(res, { success: false, message: '无权限', data: null }, 403);
    const registration = registrations.find((item) => Number(item.id) === Number(activityRegActionMatch[2]) && Number(item.activityId) === Number(activity.id));
    if (!registration) return notFound(res);
    const action = activityRegActionMatch[3];
    const currentStatus = String(registration.status || '').toUpperCase();
    if (action === 'approve') {
      if (!['APPLIED', 'REJECTED', 'APPROVED'].includes(currentStatus)) {
        return sendJson(res, { success: false, message: '当前报名状态不可改为通过', data: null }, 409);
      }
      if (currentStatus !== 'APPROVED') {
        if (activity.maxParticipants > 0 && countParticipants(activity.id) >= activity.maxParticipants) {
          return sendJson(res, { success: false, message: '活动名额已满', data: null }, 409);
        }
        registration.status = 'APPROVED';
        pushNotification(registration.userId, 'ACTIVITY_REGISTER_APPROVED', '活动报名已通过', `你报名的《${activity.title}》已通过，快去查看活动详情。`);
      }
    } else {
      if (!['APPLIED', 'APPROVED', 'REJECTED'].includes(currentStatus)) {
        return sendJson(res, { success: false, message: '当前报名状态不可改为拒绝', data: null }, 409);
      }
      registration.status = 'REJECTED';
      pushNotification(
        registration.userId,
        'ACTIVITY_REGISTER_REJECTED',
        '活动报名未通过',
        currentStatus === 'APPROVED'
          ? `你已被移出《${activity.title}》，报名资格已撤销，活动群聊资格也会同步失效。`
          : `你报名的《${activity.title}》暂未通过，可以调整信息后重新申请。`,
      );
    }
    sendJson(res, ok(null));
    return;
  }

  const activityMyRegMatch = pathname.match(/^\/api\/activities\/(\d+)\/my-registration$/);
  if (activityMyRegMatch && req.method === 'GET') {
    const activityId = Number(activityMyRegMatch[1]);
    const registration = registrations.find((item) => Number(item.activityId) === activityId && Number(item.userId) === Number(me.id));
    sendJson(res, ok(registration
      ? { registered: true, status: registration.status, createdAt: registration.createdAt, registrationId: registration.id }
      : { registered: false, status: null, createdAt: null, registrationId: null }));
    return;
  }

  const organizerContactMatch = pathname.match(/^\/api\/activities\/(\d+)\/organizer-contact$/);
  if (organizerContactMatch && req.method === 'GET') {
    const activity = activities.find((item) => Number(item.id) === Number(organizerContactMatch[1]));
    if (!activity) return notFound(res);
    const organizer = getUser(activity.organizerId);
    sendJson(res, ok({
      organizerId: activity.organizerId,
      phone: organizer ? organizer.phone : '',
      phoneVerified: organizer ? !!organizer.phoneVerified : false,
    }));
    return;
  }

  const activityFavoriteMatch = pathname.match(/^\/api\/activities\/(\d+)\/favorite$/);
  if (activityFavoriteMatch && req.method === 'POST') {
    const activityId = Number(activityFavoriteMatch[1]);
    if (favoriteIds.has(activityId)) favoriteIds.delete(activityId);
    else favoriteIds.add(activityId);
    sendJson(res, ok({ favorited: favoriteIds.has(activityId) }));
    return;
  }

  const activityTeamsMatch = pathname.match(/^\/api\/activities\/(\d+)\/teams$/);
  if (activityTeamsMatch && req.method === 'GET') {
    const activityId = Number(activityTeamsMatch[1]);
    const activity = activities.find((item) => Number(item.id) === activityId);
    if (!activity) return notFound(res);
    const list = activity.teamingEnabled === false
      ? []
      : teams.filter((item) => Number(item.activityId) === activityId).map((item) => teamForViewer(item, me.id));
    sendJson(res, ok(list));
    return;
  }

  const activityChatEnabledMatch = pathname.match(/^\/api\/activities\/(\d+)\/chat\/enabled$/);
  if (activityChatEnabledMatch && req.method === 'GET') {
    const activity = activities.find((item) => Number(item.id) === Number(activityChatEnabledMatch[1]));
    if (!activity) return notFound(res);
    sendJson(res, ok({ enabled: !!activity.chatEnabled }));
    return;
  }

  const activityChatEnableMatch = pathname.match(/^\/api\/activities\/(\d+)\/chat\/enable$/);
  if (activityChatEnableMatch && req.method === 'POST') {
    const activity = activities.find((item) => Number(item.id) === Number(activityChatEnableMatch[1]));
    if (!activity) return notFound(res);
    activity.chatEnabled = String(searchParams.get('enable') || 'true') !== 'false';
    sendJson(res, ok({ enabled: !!activity.chatEnabled }));
    return;
  }

  const activityRegisterMatch = pathname.match(/^\/api\/activities\/(\d+)\/register$/);
  if (activityRegisterMatch && req.method === 'POST') {
    const activityId = Number(activityRegisterMatch[1]);
    const body = await readJson(req);
    const activity = activities.find((item) => Number(item.id) === Number(activityId));
    if (!activity) return notFound(res);
    if (activity.maxParticipants > 0 && countParticipants(activityId) >= activity.maxParticipants) {
      return sendJson(res, { success: false, message: '名额已满', data: null }, 409);
    }
    const existing = registrations.find((item) => Number(item.activityId) === Number(activityId) && Number(item.userId) === Number(me.id));
    if (existing) {
      if (['APPLIED', 'APPROVED'].includes(String(existing.status || '').toUpperCase())) {
        return sendJson(res, { success: false, message: '您已报名该活动', data: null }, 409);
      }
      existing.realName = body.realName || me.nickname;
      existing.phone = body.phone || me.phone || '13800000000';
      existing.college = body.college || me.college || '未填写学院';
      existing.intro = body.intro || '';
      existing.status = 'APPLIED';
      existing.createdAt = nowIso();
    } else {
      registrations.push({
        id: nextId(registrations),
        activityId,
        userId: me.id,
        realName: body.realName || me.nickname,
        phone: body.phone || me.phone || '13800000000',
        college: body.college || me.college || '未填写学院',
        intro: body.intro || '',
        status: 'APPLIED',
        createdAt: nowIso(),
      });
    }
    pushNotification(activity.organizerId, 'ACTIVITY_REGISTER', '有新报名申请', `${me.nickname} 申请报名《${activity.title}》，请及时审批。`);
    sendJson(res, ok({ registered: true }));
    return;
  }

  const activityCancelMatch = pathname.match(/^\/api\/activities\/(\d+)\/cancel-registration$/);
  if (activityCancelMatch && req.method === 'POST') {
    const activityId = Number(activityCancelMatch[1]);
    const activity = activities.find((item) => Number(item.id) === activityId);
    if (!activity) return notFound(res);
    const registration = registrations.find((item) => Number(item.activityId) === activityId
      && Number(item.userId) === Number(me.id)
      && ['APPLIED', 'APPROVED'].includes(String(item.status || '').toUpperCase()));
    if (!registration) {
      return sendJson(res, { success: false, message: '未找到可取消的报名记录', data: null }, 404);
    }
    registration.status = 'CANCELLED';
    pushNotification(activity.organizerId, 'ACTIVITY_REGISTER_CANCELLED', '报名已取消', `${me.nickname} 取消了《${activity.title}》的报名。`);
    sendJson(res, ok(null));
    return;
  }

  const activityChatMessagesMatch = pathname.match(/^\/api\/activities\/(\d+)\/chat\/messages$/);
  if (activityChatMessagesMatch && req.method === 'GET') {
    const activityId = Number(activityChatMessagesMatch[1]);
    const access = canAccessActivityChat(activityId, me);
    if (!access.activity) return notFound(res);
    if (!access.allowed) {
      return sendJson(res, { success: false, message: access.message || '无权限进入活动群聊', data: null }, access.message === '群聊未开启' ? 400 : 403);
    }
    const afterId = Number(searchParams.get('afterId') || '0');
    const list = (activityChatMessages[activityId] || []).filter((item) => item.id > afterId);
    sendJson(res, ok(jsonClone(list)));
    return;
  }
  if (activityChatMessagesMatch && req.method === 'POST') {
    const activityId = Number(activityChatMessagesMatch[1]);
    const access = canAccessActivityChat(activityId, me);
    if (!access.activity) return notFound(res);
    if (!access.allowed) {
      return sendJson(res, { success: false, message: access.message || '无权限进入活动群聊', data: null }, access.message === '群聊未开启' ? 400 : 403);
    }
    const body = await readJson(req);
    const list = activityChatMessages[activityId] || (activityChatMessages[activityId] = []);
    const nextId = list.length > 0 ? list[list.length - 1].id + 1 : 1;
    list.push({ id: nextId, senderId: me.id, content: body.content || '新消息', createdAt: nowIso() });
    sendJson(res, ok({ id: nextId }));
    return;
  }

  if (pathname === '/api/users/search' && req.method === 'GET') {
    const q = String(searchParams.get('q') || '').trim().toLowerCase();
    const list = users
      .filter((item) => Number(item.id) !== Number(me.id))
      .filter((item) => !q || [item.nickname, item.username, item.studentNo].some((value) => String(value || '').toLowerCase().includes(q)))
      .slice(0, 12)
      .map((item) => ({ id: item.id, nickname: item.nickname, avatarUrl: item.avatarUrl, college: item.college }));
    sendJson(res, ok(list));
    return;
  }

  const userBriefMatch = pathname.match(/^\/api\/users\/(\d+)\/brief$/);
  if (userBriefMatch && req.method === 'GET') {
    const user = getUser(Number(userBriefMatch[1]));
    if (!user) return notFound(res);
    sendJson(res, ok({ id: user.id, nickname: user.nickname, avatarUrl: user.avatarUrl }));
    return;
  }

  if (pathname === '/api/dm/threads' && req.method === 'GET') {
    sendJson(res, ok(dmThreadsForUser(me.id)));
    return;
  }

  const dmMessagesMatch = pathname.match(/^\/api\/dm\/(\d+)\/messages$/);
  if (dmMessagesMatch && req.method === 'GET') {
    const peerId = Number(dmMessagesMatch[1]);
    const afterId = Number(searchParams.get('afterId') || '0');
    const list = (dmMessages[peerId] || []).filter((item) => item.id > afterId);
    sendJson(res, ok(jsonClone(list)));
    return;
  }
  if (dmMessagesMatch && req.method === 'POST') {
    const peerId = Number(dmMessagesMatch[1]);
    const body = await readJson(req);
    const list = dmMessages[peerId] || (dmMessages[peerId] = []);
    const nextId = list.length > 0 ? list[list.length - 1].id + 1 : 1;
    list.push({ id: nextId, senderId: me.id, content: body.content || '新消息', createdAt: nowIso() });
    sendJson(res, ok({ id: nextId }));
    return;
  }

  const dmReadMatch = pathname.match(/^\/api\/dm\/(\d+)\/read$/);
  if (dmReadMatch && req.method === 'POST') {
    sendJson(res, ok({ read: true }));
    return;
  }

  if (pathname === '/api/notifications' && req.method === 'GET') {
    sendJson(res, ok(notificationsForUser(
      me.id,
      searchParams.get('status') || 'all',
      searchParams.get('page') || '1',
      searchParams.get('size') || '20',
    )));
    return;
  }

  const notificationReadMatch = pathname.match(/^\/api\/notifications\/(\d+)\/read$/);
  if (notificationReadMatch && req.method === 'POST') {
    notifications = notifications.map((item) => {
      if (Number(item.id) === Number(notificationReadMatch[1])) return { ...item, read: true };
      return item;
    });
    sendJson(res, ok({ read: true }));
    return;
  }

  if (pathname === '/api/notifications/read-all' && req.method === 'POST') {
    notifications = notifications.map((item) => {
      if (Number(item.userId) === Number(me.id)) return { ...item, read: true };
      return item;
    });
    sendJson(res, ok({ read: true }));
    return;
  }

  const notificationDeleteMatch = pathname.match(/^\/api\/notifications\/(\d+)$/);
  if (notificationDeleteMatch && req.method === 'DELETE') {
    notifications = notifications.filter((item) => !(Number(item.id) === Number(notificationDeleteMatch[1]) && Number(item.userId) === Number(me.id)));
    sendJson(res, ok({ deleted: true }));
    return;
  }

  if (pathname === '/api/profile/teams' && req.method === 'GET') {
    sendJson(res, ok(teamsForUser(me.id)));
    return;
  }

  if (pathname === '/api/profile/teams-joined' && req.method === 'GET') {
    sendJson(res, ok(joinedTeamsForUser(me.id)));
    return;
  }

  if (pathname === '/api/profile/teams-created' && req.method === 'GET') {
    sendJson(res, ok(createdTeamsForUser(me.id)));
    return;
  }

  if (pathname === '/api/profile/team-chats' && req.method === 'GET') {
    sendJson(res, ok(teamChatsForUser(me.id)));
    return;
  }

  if (pathname === '/api/profile/activity-chats' && req.method === 'GET') {
    const list = activities
      .filter((item) => item.chatEnabled)
      .filter((item) => Number(item.organizerId) === Number(me.id)
        || registrations.some((entry) => Number(entry.activityId) === Number(item.id)
          && Number(entry.userId) === Number(me.id)
          && String(entry.status || '').toUpperCase() === 'APPROVED'))
      .map((item) => {
        const organizer = getUser(item.organizerId);
        return {
          id: item.id,
          title: item.title,
          location: item.location,
          startTime: item.startTime,
          coverUrl: item.coverUrl,
          organizerId: item.organizerId,
          organizerName: organizer ? organizer.nickname : '未知发布人',
        };
      });
    sendJson(res, ok(list));
    return;
  }

  if (pathname === '/api/profile/registrations' && req.method === 'GET') {
    sendJson(res, ok(registrationsForUser(me.id)));
    return;
  }

  if (pathname === '/api/profile/favorites' && req.method === 'GET') {
    sendJson(res, ok(favoritesForUser(me.id)));
    return;
  }

  if (pathname === '/api/profile/published' && req.method === 'GET') {
    sendJson(res, ok(publishedForUser(me.id)));
    return;
  }

  if (pathname === '/api/profile/default-avatars' && req.method === 'GET') {
    sendJson(res, ok([avatars.me, avatars.organizer, avatars.creator, avatars.friend]));
    return;
  }

  if (pathname === '/api/profile/avatar' && req.method === 'PUT') {
    const body = await readJson(req);
    const user = getUser(me.id);
    if (user && body.avatarUrl) user.avatarUrl = body.avatarUrl;
    sendJson(res, ok({ updated: true }));
    return;
  }

  if (pathname === '/api/profile/me' && req.method === 'PUT') {
    const body = await readJson(req);
    const user = getUser(me.id);
    if (user) {
      if (typeof body.college === 'string') user.college = body.college;
      if (typeof body.campus === 'string') user.campus = body.campus;
      if (typeof body.educationLevel === 'string') user.educationLevel = body.educationLevel;
      if (typeof body.major === 'string') user.major = body.major;
      if (typeof body.bio === 'string') user.bio = body.bio;
    }
    sendJson(res, ok({ updated: true }));
    return;
  }

  if (pathname === '/api/auth/me/interests' && req.method === 'PUT') {
    const body = await readJson(req);
    const user = getUser(me.id);
    if (user && Array.isArray(body.tags)) user.interests = body.tags.filter(Boolean);
    sendJson(res, ok({ updated: true }));
    return;
  }

  if (pathname === '/api/auth/email/send' && req.method === 'POST') {
    sendJson(res, ok({ devCode: '123456' }));
    return;
  }

  if (pathname === '/api/auth/login' && req.method === 'POST') {
    const body = await readJson(req);
    const account = String(body.usernameOrStudentNo || '').trim();
    const user = users.find((item) => item.username === account || item.studentNo === account) || getUser(1);
    sendJson(res, ok({ token: issueMockToken(user.id) }));
    return;
  }

  if (pathname === '/api/auth/login-email' && req.method === 'POST') {
    const body = await readJson(req);
    const email = String(body.email || '').trim().toLowerCase();
    const user = users.find((item) => String(item.email || '').toLowerCase() === email) || getUser(1);
    sendJson(res, ok({ token: issueMockToken(user.id) }));
    return;
  }

  if (pathname === '/api/auth/register-email' && req.method === 'POST') {
    const body = await readJson(req);
    const created = {
      id: nextUserId(),
      username: String(body.username || `user${Date.now()}`),
      studentNo: String(body.studentNo || body.username || ''),
      nickname: String(body.nickname || '新用户'),
      avatarUrl: body.avatarUrl || avatars.me,
      college: String(body.college || ''),
      campus: '',
      major: '',
      grade: '',
      educationLevel: 'UNDERGRAD',
      bio: '',
      email: String(body.email || '').trim().toLowerCase(),
      emailVerified: true,
      phone: '',
      phoneVerified: false,
      role: 'USER',
      status: 1,
      createdAt: nowIso(),
      interests: [],
    };
    users.push(created);
    sendJson(res, ok({ token: issueMockToken(created.id) }));
    return;
  }

  if (pathname === '/api/auth/password/reset' && req.method === 'POST') {
    sendJson(res, ok({ reset: true }));
    return;
  }

  if (pathname === '/api/auth/me/email/change/send-old' && req.method === 'POST') {
    sendJson(res, ok({ devCode: '123456' }));
    return;
  }

  if (pathname === '/api/auth/me/email/change/send-new' && req.method === 'POST') {
    sendJson(res, ok({ devCode: '123456' }));
    return;
  }

  if (pathname === '/api/auth/me/email/change' && req.method === 'PUT') {
    const body = await readJson(req);
    const user = getUser(me.id);
    if (user && typeof body.newEmail === 'string' && body.newEmail.trim()) {
      user.email = body.newEmail.trim();
      user.emailVerified = true;
    }
    sendJson(res, ok({ updated: true }));
    return;
  }

  if (pathname === '/api/auth/me/email/bind' && req.method === 'PUT') {
    const body = await readJson(req);
    const user = getUser(me.id);
    if (user && typeof body.email === 'string' && body.email.trim()) {
      user.email = body.email.trim();
      user.emailVerified = true;
    }
    sendJson(res, ok({ updated: true }));
    return;
  }

  if (pathname === '/api/auth/me/password/email/send' && req.method === 'POST') {
    sendJson(res, ok({ devCode: '123456' }));
    return;
  }

  if (pathname === '/api/auth/me/password/change-email' && req.method === 'POST') {
    sendJson(res, ok({ updated: true }));
    return;
  }

  if (pathname === '/api/upload/avatar' && req.method === 'POST') {
    sendJson(res, ok({ url: avatars.me }));
    return;
  }

  if (pathname === '/api/upload/cover' && req.method === 'POST') {
    sendJson(res, ok({ url: coverImages.career }));
    return;
  }

  if (pathname === '/api/events' && req.method === 'POST') {
    sendJson(res, ok({ logged: true }));
    return;
  }

  if (pathname === '/api/behavior' && req.method === 'POST') {
    sendJson(res, ok({ logged: true }));
    return;
  }

  if (pathname === '/api/support/chat' && req.method === 'POST') {
    const body = await readJson(req);
    const q = String(body.message || '').trim();
    let answer = '你可以在首页、活动大厅、组队大厅、消息页、通知中心和个人中心完成主要操作。';
    if (/验证码|邮箱|注册|登录/.test(q)) {
      answer = '注册、登录、找回密码、绑定或换绑邮箱都使用邮箱验证码。请确认邮箱格式正确，检查垃圾邮件，并避免短时间内重复发送。';
    } else if (/报名|活动|审批/.test(q)) {
      answer = '进入活动详情页可以报名活动；报名后等待发布者审批。发布者在活动管理页可以查看申请人、通过或拒绝报名，满员后仍可先调整名单再继续审批。';
    } else if (/组队|队伍|入队/.test(q)) {
      answer = '组队大厅可以查看队伍详情、联系组队发起人并申请加入。队长在队伍管理页审批申请，队伍聊天在消息页的群聊区。';
    } else if (/通知|未读|删除/.test(q)) {
      answer = '首页铃铛显示未读通知数量，点击进入通知中心。通知中心支持全部、未读、已读筛选，支持单条删除和一键全部已读。';
    } else if (/推荐|搜索/.test(q)) {
      answer = '推荐活动和推荐组队会结合兴趣标签、历史参加记录、收藏、点击、报名和组队互动综合排序。搜索会同时检索活动和组队。';
    }
    sendJson(res, ok({ answer, conversationId: body.conversationId || null, source: 'LOCAL_KNOWLEDGE' }));
    return;
  }

  if (pathname === '/api/admin/stats' && req.method === 'GET') {
    if (!isAdminRequest(req)) return sendJson(res, { success: false, message: 'Forbidden', data: null }, 403);
    sendJson(res, ok(stats()));
    return;
  }

  if (pathname === '/api/admin/activities' && req.method === 'GET') {
    if (!isAdminRequest(req)) return sendJson(res, { success: false, message: 'Forbidden', data: null }, 403);
    const keyword = String(searchParams.get('keyword') || '').trim().toLowerCase();
    const auditStatus = String(searchParams.get('auditStatus') || '').trim();
    const status = String(searchParams.get('status') || '').trim();
    const list = activities
      .filter((item) => !auditStatus || item.auditStatus === auditStatus)
      .filter((item) => !status || item.status === status)
      .filter((item) => {
        if (!keyword) return true;
        const organizer = getUser(item.organizerId);
        return [item.title, item.location, organizer ? organizer.nickname : '', ...(item.tags || [])]
          .some((value) => String(value || '').toLowerCase().includes(keyword));
      })
      .map(activityForAdmin);
    sendJson(res, ok(list));
    return;
  }

  const adminActivityStatusMatch = pathname.match(/^\/api\/admin\/activities\/(\d+)\/status$/);
  if (adminActivityStatusMatch && req.method === 'POST') {
    const body = await readJson(req);
    const activity = activities.find((item) => Number(item.id) === Number(adminActivityStatusMatch[1]));
    if (!activity) return notFound(res);
    activity.status = body.status || activity.status;
    sendJson(res, ok({ updated: true }));
    return;
  }

  const adminActivityAuditMatch = pathname.match(/^\/api\/admin\/activities\/(\d+)\/audit$/);
  if (adminActivityAuditMatch && req.method === 'POST') {
    const body = await readJson(req);
    const activity = activities.find((item) => Number(item.id) === Number(adminActivityAuditMatch[1]));
    if (!activity) return notFound(res);
    activity.auditStatus = body.auditStatus || activity.auditStatus;
    sendJson(res, ok({ updated: true }));
    return;
  }

  if (pathname === '/api/admin/users' && req.method === 'GET') {
    if (!isAdminRequest(req)) return sendJson(res, { success: false, message: 'Forbidden', data: null }, 403);
    const keyword = String(searchParams.get('keyword') || '').trim().toLowerCase();
    const role = String(searchParams.get('role') || '').trim();
    const status = String(searchParams.get('status') || '').trim();
    const list = users
      .filter((item) => !role || item.role === role)
      .filter((item) => !status || String(item.status) === status)
      .filter((item) => {
        if (!keyword) return true;
        return [item.nickname, item.username, item.studentNo].some((value) => String(value || '').toLowerCase().includes(keyword));
      })
      .map(adminUserItem);
    sendJson(res, ok(list));
    return;
  }

  const adminUserDetailMatch = pathname.match(/^\/api\/admin\/users\/(\d+)\/detail$/);
  if (adminUserDetailMatch && req.method === 'GET') {
    if (!isAdminRequest(req)) return sendJson(res, { success: false, message: 'Forbidden', data: null }, 403);
    const user = getUser(Number(adminUserDetailMatch[1]));
    if (!user) return notFound(res);
    sendJson(res, ok(adminUserDetail(user)));
    return;
  }

  const adminUserStatusMatch = pathname.match(/^\/api\/admin\/users\/(\d+)\/status$/);
  if (adminUserStatusMatch && req.method === 'POST') {
    const body = await readJson(req);
    const user = getUser(Number(adminUserStatusMatch[1]));
    if (!user) return notFound(res);
    user.status = body.enabled ? 1 : 0;
    sendJson(res, ok({ updated: true }));
    return;
  }

  if (pathname === '/api/admin/featured' && req.method === 'GET') {
    if (!isAdminRequest(req)) return sendJson(res, { success: false, message: 'Forbidden', data: null }, 403);
    const list = featured.map((item) => {
      const activity = activities.find((entry) => Number(entry.id) === Number(item.activityId));
      return {
        activityId: item.activityId,
        activityTitle: activity ? activity.title : `活动 ${item.activityId}`,
        weight: item.weight,
        startTime: item.startTime,
        endTime: item.endTime,
      };
    });
    sendJson(res, ok(list));
    return;
  }

  if (pathname === '/api/admin/featured' && req.method === 'POST') {
    const body = await readJson(req);
    const activityId = Number(body.activityId || '0');
    const weight = Number(body.weight || '0');
    const existing = featured.find((item) => Number(item.activityId) === activityId);
    if (existing) existing.weight = weight;
    else featured.push({ activityId, weight, startTime: nowIso(), endTime: null });
    sendJson(res, ok({ updated: true }));
    return;
  }

  if (pathname === '/api/admin/tags' && req.method === 'GET') {
    if (!isAdminRequest(req)) return sendJson(res, { success: false, message: 'Forbidden', data: null }, 403);
    sendJson(res, ok(jsonClone(tags)));
    return;
  }

  if (pathname === '/api/admin/tags' && req.method === 'POST') {
    const body = await readJson(req);
    const nextId = Math.max(...tags.map((item) => item.id)) + 1;
    tags.push({ id: nextId, name: body.name || `标签${nextId}`, type: body.type || 'CATEGORY' });
    sendJson(res, ok({ id: nextId }));
    return;
  }

  if (pathname === '/api/realtime/chat/stream' && req.method === 'GET') {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-store',
      Connection: 'keep-alive',
    });
    res.write('event: CONNECTED\n');
    res.write(`data: ${JSON.stringify({ type: 'CONNECTED' })}\n\n`);
    setTimeout(() => res.end(), 600);
    return;
  }

  notFound(res);
}

function serveStatic(req, res, urlObj) {
  if (urlObj.pathname === '/favicon.ico') {
    res.writeHead(204);
    res.end();
    return;
  }

  if (urlObj.pathname === '/__mock__/bootstrap.js') {
    sendText(res, bootstrapJs, 'application/javascript; charset=utf-8');
    return;
  }

  if (urlObj.pathname === '/__mock__/fa-fallback.css') {
    sendText(res, faFallbackCss, 'text/css; charset=utf-8');
    return;
  }

  let pathnameValue = decodeURIComponent(urlObj.pathname);
  if (pathnameValue === '/') pathnameValue = '/index.html';
  const filePath = path.normalize(path.join(prototypeRoot, pathnameValue));
  if (!filePath.startsWith(prototypeRoot)) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }

  fs.readFile(filePath, (err, buffer) => {
    if (err) {
      res.writeHead(404);
      res.end('Not Found');
      return;
    }
    if (path.extname(filePath).toLowerCase() === '.html') {
      const html = injectHtml(buffer.toString('utf8'));
      sendText(res, html, mimeType(filePath));
      return;
    }
    send(res, 200, buffer, mimeType(filePath));
  });
}

const server = http.createServer(async (req, res) => {
  const urlObj = new URL(req.url, `http://${req.headers.host || `127.0.0.1:${port}`}`);
  if (urlObj.pathname.startsWith('/api/')) {
    await handleApi(req, res, urlObj);
    return;
  }
  serveStatic(req, res, urlObj);
});

server.listen(port, '127.0.0.1', () => {
  console.log(`Axure mock server running at http://127.0.0.1:${port}`);
});
