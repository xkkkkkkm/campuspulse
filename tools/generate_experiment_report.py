#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_ROW_HEIGHT_RULE, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Inches, Pt, RGBColor
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
ASSET_DIR = DOCS / "report_assets"
OUT_DOCX = DOCS / "综合项目实践_校园活动智能推荐与组队平台实验报告.docx"

FONT_CN = "PingFang SC"
FONT_LATIN = "Calibri"
BLUE = RGBColor(46, 116, 181)
DARK_BLUE = RGBColor(31, 77, 120)
INK = RGBColor(31, 41, 55)
MUTED = RGBColor(89, 99, 110)
LIGHT_BLUE = "E8EEF5"
LIGHT_GRAY = "F2F4F7"
BORDER = "C9D3DF"
WHITE = "FFFFFF"
CONTENT_WIDTH_DXA = 9360


def rgb(hex_value: str) -> RGBColor:
    return RGBColor.from_string(hex_value.replace("#", ""))


def set_run_font(run, size: float | None = None, color: RGBColor | None = None, bold: bool | None = None,
                 italic: bool | None = None, name: str = FONT_CN) -> None:
    run.font.name = name
    run._element.get_or_add_rPr().rFonts.set(qn("w:ascii"), FONT_LATIN)
    run._element.get_or_add_rPr().rFonts.set(qn("w:hAnsi"), FONT_LATIN)
    run._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), name)
    if size is not None:
        run.font.size = Pt(size)
    if color is not None:
        run.font.color.rgb = color
    if bold is not None:
        run.bold = bold
    if italic is not None:
        run.italic = italic


def set_style(style, size: float, color: RGBColor = INK, bold: bool = False, before: float = 0,
              after: float = 6, line: float = 1.10) -> None:
    style.font.name = FONT_CN
    style._element.rPr.rFonts.set(qn("w:ascii"), FONT_LATIN)
    style._element.rPr.rFonts.set(qn("w:hAnsi"), FONT_LATIN)
    style._element.rPr.rFonts.set(qn("w:eastAsia"), FONT_CN)
    style.font.size = Pt(size)
    style.font.color.rgb = color
    style.font.bold = bold
    style.paragraph_format.space_before = Pt(before)
    style.paragraph_format.space_after = Pt(after)
    style.paragraph_format.line_spacing = line


def paragraph_border_bottom(paragraph, color: str = "2E74B5", size: str = "10", space: str = "6") -> None:
    p_pr = paragraph._p.get_or_add_pPr()
    borders = p_pr.find(qn("w:pBdr"))
    if borders is None:
        borders = OxmlElement("w:pBdr")
        p_pr.append(borders)
    bottom = borders.find(qn("w:bottom"))
    if bottom is None:
        bottom = OxmlElement("w:bottom")
        borders.append(bottom)
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), size)
    bottom.set(qn("w:space"), space)
    bottom.set(qn("w:color"), color)


def shade_cell(cell, fill: str) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)


def shade_paragraph(paragraph, fill: str, border: str | None = None) -> None:
    p_pr = paragraph._p.get_or_add_pPr()
    shd = p_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        p_pr.append(shd)
    shd.set(qn("w:fill"), fill)
    if border:
        borders = p_pr.find(qn("w:pBdr"))
        if borders is None:
            borders = OxmlElement("w:pBdr")
            p_pr.append(borders)
        for edge in ("top", "left", "bottom", "right"):
            node = borders.find(qn(f"w:{edge}"))
            if node is None:
                node = OxmlElement(f"w:{edge}")
                borders.append(node)
            node.set(qn("w:val"), "single")
            node.set(qn("w:sz"), "4")
            node.set(qn("w:space"), "2")
            node.set(qn("w:color"), border)


def set_cell_margins(cell, top: int = 80, start: int = 120, bottom: int = 80, end: int = 120) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    mar = tc_pr.find(qn("w:tcMar"))
    if mar is None:
        mar = OxmlElement("w:tcMar")
        tc_pr.append(mar)
    for side, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = mar.find(qn(f"w:{side}"))
        if node is None:
            node = OxmlElement(f"w:{side}")
            mar.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def set_cell_width(cell, width_dxa: int) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_w = tc_pr.find(qn("w:tcW"))
    if tc_w is None:
        tc_w = OxmlElement("w:tcW")
        tc_pr.append(tc_w)
    tc_w.set(qn("w:w"), str(width_dxa))
    tc_w.set(qn("w:type"), "dxa")


def set_table_geometry(table, widths_dxa: list[int], indent_dxa: int = 120) -> None:
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    table.autofit = False
    tbl = table._tbl
    tbl_pr = tbl.tblPr
    tbl_w = tbl_pr.find(qn("w:tblW"))
    if tbl_w is None:
        tbl_w = OxmlElement("w:tblW")
        tbl_pr.append(tbl_w)
    tbl_w.set(qn("w:w"), str(sum(widths_dxa)))
    tbl_w.set(qn("w:type"), "dxa")
    tbl_ind = tbl_pr.find(qn("w:tblInd"))
    if tbl_ind is None:
        tbl_ind = OxmlElement("w:tblInd")
        tbl_pr.append(tbl_ind)
    tbl_ind.set(qn("w:w"), str(indent_dxa))
    tbl_ind.set(qn("w:type"), "dxa")
    grid = tbl.tblGrid
    for child in list(grid):
        grid.remove(child)
    for width in widths_dxa:
        col = OxmlElement("w:gridCol")
        col.set(qn("w:w"), str(width))
        grid.append(col)
    for row in table.rows:
        row.height_rule = WD_ROW_HEIGHT_RULE.AT_LEAST
        for i, cell in enumerate(row.cells):
            set_cell_width(cell, widths_dxa[i])
            set_cell_margins(cell)
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER


def set_table_borders(table, color: str = BORDER, size: str = "6") -> None:
    tbl_pr = table._tbl.tblPr
    borders = tbl_pr.find(qn("w:tblBorders"))
    if borders is None:
        borders = OxmlElement("w:tblBorders")
        tbl_pr.append(borders)
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        node = borders.find(qn(f"w:{edge}"))
        if node is None:
            node = OxmlElement(f"w:{edge}")
            borders.append(node)
        node.set(qn("w:val"), "single")
        node.set(qn("w:sz"), size)
        node.set(qn("w:space"), "0")
        node.set(qn("w:color"), color)


def keep_row_together(row) -> None:
    tr_pr = row._tr.get_or_add_trPr()
    if tr_pr.find(qn("w:cantSplit")) is None:
        tr_pr.append(OxmlElement("w:cantSplit"))


def repeat_table_header(row) -> None:
    tr_pr = row._tr.get_or_add_trPr()
    repeat = tr_pr.find(qn("w:tblHeader"))
    if repeat is None:
        repeat = OxmlElement("w:tblHeader")
        tr_pr.append(repeat)
    repeat.set(qn("w:val"), "true")


def add_field(paragraph, field: str) -> None:
    run = paragraph.add_run()
    fld_char_1 = OxmlElement("w:fldChar")
    fld_char_1.set(qn("w:fldCharType"), "begin")
    instr_text = OxmlElement("w:instrText")
    instr_text.set(qn("xml:space"), "preserve")
    instr_text.text = field
    fld_char_2 = OxmlElement("w:fldChar")
    fld_char_2.set(qn("w:fldCharType"), "end")
    run._r.append(fld_char_1)
    run._r.append(instr_text)
    run._r.append(fld_char_2)


def add_header_footer(section) -> None:
    header = section.header
    p = header.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(0)
    run = p.add_run("综合项目实践实验报告 | 校园活动智能推荐与组队平台")
    set_run_font(run, size=9, color=MUTED)
    paragraph_border_bottom(p, color="D9E2EC", size="4", space="3")

    footer = section.footer
    p = footer.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(0)
    set_run_font(p.add_run("第 "), size=9, color=MUTED)
    add_field(p, "PAGE")
    set_run_font(p.add_run(" 页"), size=9, color=MUTED)


def add_para(doc, text: str = "", style: str | None = None, bold_prefix: str | None = None,
             align: WD_ALIGN_PARAGRAPH | None = None, after: float | None = None) -> None:
    p = doc.add_paragraph(style=style)
    if align is not None:
        p.alignment = align
    if after is not None:
        p.paragraph_format.space_after = Pt(after)
    if bold_prefix and text.startswith(bold_prefix):
        run = p.add_run(bold_prefix)
        set_run_font(run, bold=True)
        run = p.add_run(text[len(bold_prefix):])
        set_run_font(run)
    else:
        run = p.add_run(text)
        set_run_font(run)


def add_bullets(doc, items: list[str]) -> None:
    for item in items:
        p = doc.add_paragraph(style="List Bullet")
        p.paragraph_format.left_indent = Inches(0.5)
        p.paragraph_format.first_line_indent = Inches(-0.25)
        p.paragraph_format.space_after = Pt(8)
        p.paragraph_format.line_spacing = 1.167
        set_run_font(p.add_run(item))


def add_numbered(doc, items: list[str]) -> None:
    for item in items:
        p = doc.add_paragraph(style="List Number")
        p.paragraph_format.left_indent = Inches(0.5)
        p.paragraph_format.first_line_indent = Inches(-0.25)
        p.paragraph_format.space_after = Pt(8)
        p.paragraph_format.line_spacing = 1.167
        set_run_font(p.add_run(item))


def add_callout(doc, title: str, body: str, fill: str = "F4F7FB") -> None:
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(8)
    p.paragraph_format.left_indent = Inches(0.06)
    p.paragraph_format.right_indent = Inches(0.06)
    shade_paragraph(p, fill, border="D7E3F0")
    r = p.add_run(title + "：")
    set_run_font(r, size=10.5, color=DARK_BLUE, bold=True)
    r = p.add_run(body)
    set_run_font(r, size=10.5, color=INK)


def add_table(doc, headers: list[str], rows: list[list[str]], widths_dxa: list[int]) -> None:
    table = doc.add_table(rows=1, cols=len(headers))
    set_table_geometry(table, widths_dxa)
    set_table_borders(table)
    repeat_table_header(table.rows[0])
    keep_row_together(table.rows[0])
    hdr = table.rows[0].cells
    for i, head in enumerate(headers):
        shade_cell(hdr[i], LIGHT_BLUE)
        p = hdr[i].paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_after = Pt(0)
        run = p.add_run(head)
        set_run_font(run, size=10, color=DARK_BLUE, bold=True)
    for row in rows:
        new_row = table.add_row()
        keep_row_together(new_row)
        cells = new_row.cells
        for i, value in enumerate(row):
            p = cells[i].paragraphs[0]
            p.paragraph_format.space_after = Pt(0)
            p.paragraph_format.line_spacing = 1.10
            if len(value) <= 10 and "\n" not in value:
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            run = p.add_run(value)
            set_run_font(run, size=9.6, color=INK)
    for row in table.rows:
        for cell in row.cells:
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
    doc.add_paragraph().paragraph_format.space_after = Pt(3)


def add_image(doc, image_path: Path, caption: str, width: float = 6.2, description: str | None = None) -> None:
    if not image_path.exists():
        return
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.keep_with_next = True
    run = p.add_run()
    run.add_picture(str(image_path), width=Inches(width))
    cap = doc.add_paragraph()
    cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
    cap.paragraph_format.space_before = Pt(1)
    cap.paragraph_format.space_after = Pt(3 if description else 10)
    r = cap.add_run(caption)
    set_run_font(r, size=9.2, color=MUTED, italic=True)
    if description:
        desc = doc.add_paragraph()
        desc.alignment = WD_ALIGN_PARAGRAPH.LEFT
        desc.paragraph_format.left_indent = Inches(0.18)
        desc.paragraph_format.right_indent = Inches(0.18)
        desc.paragraph_format.space_before = Pt(0)
        desc.paragraph_format.space_after = Pt(10)
        desc.paragraph_format.line_spacing = 1.08
        r = desc.add_run(description)
        set_run_font(r, size=9.6, color=RGBColor(82, 91, 103))


def add_section_heading(doc, title: str, subtitle: str | None = None) -> None:
    p = doc.add_heading(title, level=1)
    p.paragraph_format.keep_with_next = True
    if subtitle:
        p = doc.add_paragraph()
        p.paragraph_format.space_after = Pt(8)
        r = p.add_run(subtitle)
        set_run_font(r, size=10.5, color=MUTED)


def find_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = [
        "/System/Library/Fonts/PingFang.ttc",
        "/System/Library/Fonts/STHeiti Light.ttc",
        "/Library/Fonts/Arial Unicode.ttf",
    ]
    for candidate in candidates:
        if Path(candidate).exists():
            try:
                return ImageFont.truetype(candidate, size=size, index=1 if bold else 0)
            except Exception:
                continue
    return ImageFont.load_default()


def configure_document(doc: Document) -> None:
    section = doc.sections[0]
    section.page_width = Inches(8.5)
    section.page_height = Inches(11)
    section.top_margin = Inches(1)
    section.bottom_margin = Inches(1)
    section.left_margin = Inches(1)
    section.right_margin = Inches(1)
    section.header_distance = Inches(0.492)
    section.footer_distance = Inches(0.492)
    add_header_footer(section)

    styles = doc.styles
    set_style(styles["Normal"], size=11, color=INK, before=0, after=6, line=1.10)
    set_style(styles["Heading 1"], size=16, color=BLUE, bold=True, before=16, after=8, line=1.10)
    set_style(styles["Heading 2"], size=13, color=BLUE, bold=True, before=12, after=6, line=1.10)
    set_style(styles["Heading 3"], size=12, color=DARK_BLUE, bold=True, before=8, after=4, line=1.10)
    set_style(styles["List Bullet"], size=11, color=INK, before=0, after=8, line=1.167)
    set_style(styles["List Number"], size=11, color=INK, before=0, after=8, line=1.167)


def add_cover(doc: Document) -> None:
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(22)
    p.paragraph_format.space_after = Pt(10)
    r = p.add_run("综合项目实践")
    set_run_font(r, size=13, color=MUTED, bold=True)

    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(8)
    p.paragraph_format.space_after = Pt(6)
    r = p.add_run("校园活动智能推荐与组队平台")
    set_run_font(r, size=27, color=rgb("#0B2545"), bold=True)

    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(12)
    r = p.add_run("实验报告")
    set_run_font(r, size=24, color=BLUE, bold=True)
    paragraph_border_bottom(p, color="2E74B5", size="14", space="8")

    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(6)
    p.paragraph_format.space_after = Pt(16)
    r = p.add_run("以“活动发现 + 智能推荐 + 组队协作 + 消息通知 + 智能客服 + 后台治理”为主线，完成校园活动平台的需求分析、系统设计、编码实现、模型接入、联调测试与结果展示。")
    set_run_font(r, size=11.5, color=INK)

    table = doc.add_table(rows=6, cols=2)
    set_table_geometry(table, [1900, 7100])
    set_table_borders(table, color="D7E0EA")
    rows = [
        ("实验题目", "校园活动智能推荐与组队平台的设计与实现"),
        ("项目小组", "第25组"),
        ("项目成员", "许凯铭、刘文甫、万逸、徐钱超"),
        ("开发形态", "Web 用户平台 + Web 管理后台 + Spring Boot 后端 + MySQL + 推荐模型脚本"),
        ("核心能力", "个性化活动推荐、活动报名审批、自由/活动关联组队、私聊与群聊、通知中心、智能客服、后台管理"),
        ("报告日期", "2026年6月26日"),
    ]
    for i, (label, value) in enumerate(rows):
        cells = table.rows[i].cells
        shade_cell(cells[0], LIGHT_GRAY)
        for cell in cells:
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
        lp = cells[0].paragraphs[0]
        lp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        lp.paragraph_format.space_after = Pt(0)
        set_run_font(lp.add_run(label), size=10.2, color=DARK_BLUE, bold=True)
        vp = cells[1].paragraphs[0]
        vp.paragraph_format.space_after = Pt(0)
        set_run_font(vp.add_run(value), size=10.4, color=INK)

    add_callout(
        doc,
        "报告摘要",
        "本实验围绕高校活动信息分散、兴趣匹配不足与团队活动组队困难等问题，构建一套可运行、可演示、可扩展的校园活动智能推荐与组队平台。系统采用模块化架构，前端负责多角色交互，后端负责权限、业务流程与数据服务，推荐模块采用离线模型分表优先、规则排序回退的策略，保证推荐效果与系统可用性兼顾。",
        fill="EEF6FF",
    )
    doc.add_page_break()


def add_toc(doc: Document) -> None:
    doc.add_heading("目录", level=1)
    toc_items = [
        "一、实验题目",
        "二、实验目的",
        "三、实验任务（内容）",
        "四、实验过程",
        "五、实验结果（展示）",
        "六、实验总结",
        "附录：关键文件与演示入口",
    ]
    for item in toc_items:
        p = doc.add_paragraph()
        p.paragraph_format.left_indent = Inches(0.15)
        p.paragraph_format.space_after = Pt(5)
        set_run_font(p.add_run(item), size=11.5, color=INK)
    doc.add_page_break()


def build_report() -> Path:
    live = ROOT / "docs/report_assets/live_screens/report_ready"
    doc = Document()
    configure_document(doc)
    add_cover(doc)
    add_toc(doc)

    add_section_heading(doc, "一、实验题目")
    add_para(doc, "校园活动智能推荐与组队平台的设计与实现。")
    add_para(doc, "本实验面向高校校园活动场景，综合运用需求分析、数据库设计、Web 前后端开发、推荐算法、智能客服、接口联调与系统测试等知识，完成一个支持多角色协同使用的校园活动平台。")

    add_section_heading(doc, "二、实验目的")
    add_bullets(doc, [
        "理解真实校园活动场景中的信息过载、兴趣匹配、组队协作和活动治理问题，并将问题转化为可实现的软件需求。",
        "掌握从需求规格说明、概要设计、数据库建模到编码实现、测试验收的完整软件工程实践流程。",
        "熟悉 Spring Boot、MySQL、REST/JSON 接口、Token 鉴权、前端原型页面与后端接口联调的实现方法。",
        "学习将个性化推荐引入业务系统：采集用户行为、构建推荐特征、生成离线推荐分数，并在后端提供稳定的规则回退策略。",
        "训练系统化表达能力，能够用架构图、ER 图、流程说明、界面截图和测试结果展示项目成果。"
    ])

    add_section_heading(doc, "三、实验任务（内容）")
    add_para(doc, "实验任务围绕“学生找活动、找队友，组织者发布与管理活动，管理员完成平台治理”的完整业务闭环展开，主要内容如下。")
    add_table(
        doc,
        ["任务模块", "建设内容", "完成标准"],
        [
            ["账号与画像", "实现注册、登录、邮箱验证码、密码修改、兴趣标签维护与个人资料管理。", "用户可安全登录，兴趣画像能参与推荐排序。"],
            ["活动发现与管理", "实现活动大厅、活动详情、收藏、报名、取消报名、活动发布、报名审批和群聊开关。", "普通学生与活动组织者均能完成完整活动流程。"],
            ["组队协作", "实现自由组队、活动关联组队、队伍详情、申请入队、队长审批和队伍群聊。", "组队流程与活动流程相互衔接，职责清晰。"],
            ["推荐与搜索", "实现推荐活动、推荐组队、关键词搜索、行为日志与离线推荐分表。", "不同兴趣用户可看到差异化推荐，搜索可定位活动和组队。"],
            ["消息与通知", "实现私聊、活动群聊、队伍群聊、通知筛选、已读、删除、分页与过期清理。", "沟通与通知形成用户参与后的反馈闭环。"],
            ["智能客服与后台", "接入知识库客服与人工兜底，建设管理员统计、审核、用户、标签和推荐位管理。", "平台具备运营、审核、服务支持能力。"],
        ],
        [1700, 5100, 2560],
    )

    doc.add_heading("3.1 系统角色与需求边界", level=2)
    add_para(doc, "系统覆盖普通学生、活动组织者、队伍发起者和管理员四类角色。普通学生侧重发现、报名、组队和沟通；组织者负责活动发布、报名审核和活动群聊；队伍发起者负责队伍资料和入队申请；管理员负责活动审核、用户状态、标签体系和运营推荐位。")
    add_callout(doc, "需求边界", "系统重点解决校园活动信息聚合、个性化推荐、组队协作和通知沟通，不追求复杂商业化运营功能；AI 与 Dify 客服作为增强能力，必须提供规则排序、静态知识库和人工处理的兜底机制。")

    doc.add_heading("3.2 技术与数据任务", level=2)
    add_bullets(doc, [
        "后端采用 Spring Boot 分模块组织控制器，统一使用 ApiResponse<T> 返回结构，并通过 ApiAuthConfig 完成 /api/** 鉴权拦截。",
        "数据库采用 MySQL，核心表包括 users、tags、activities、registrations、favorites、teams、team_member、team_join_request、messages、dm_message、notifications、user_behavior_log 与推荐分表。",
        "推荐模块以用户兴趣、活动/队伍标签、报名收藏点击、运营权重、时间衰减、剩余名额等特征综合排序；离线训练脚本可使用 GradientBoostingClassifier 与 TF-IDF 文本向量生成推荐分数。",
        "前端原型覆盖登录注册、兴趣标签、首页推荐、活动大厅、活动详情、组队大厅、消息页、通知中心、个人中心、后台管理与智能客服等页面。"
    ])

    add_section_heading(doc, "四、实验过程", "本节按照软件工程生命周期说明实验实施过程，重点呈现从需求到运行结果的工程闭环。")
    doc.add_heading("4.1 需求分析与业务建模", level=2)
    add_para(doc, "实验首先分析校园活动参与过程中的典型痛点：活动来源分散导致信息过载，学生兴趣与活动匹配不足，团队活动缺少高效找队友渠道，报名审批、沟通通知和后台治理分散。围绕这些问题，项目将平台定位为“活动发现 + 报名审核 + 组队协作 + 消息通知 + 智能客服 + 后台管理”的一体化系统。")
    add_para(doc, "在需求建模阶段，项目输出了需求规格说明书与概要设计说明书，明确功能需求、非功能需求、核心数据实体、业务流程和部署约束。需求中强调推荐接口响应原则上控制在 2 秒以内，普通分页查询在 1 秒级返回；同时要求在 AI 服务、邮件服务或客服服务异常时具备降级能力。")

    doc.add_heading("4.2 总体架构设计", level=2)
    add_para(doc, "系统整体划分为前端展示层、业务服务层、数据持久层和外部服务层。前端通过 REST/JSON 与后端交互，聊天场景通过 SSE 获取实时消息；后端由 Spring Boot 统一承载认证、活动、推荐、组队、消息、通知、客服和后台管理；MySQL 保存核心数据；SMTP、Dify 知识库 Chatflow、AI 外部服务与文件存储提供支撑。")
    add_image(doc, ROOT / "docs/overview_design/assets/overall-architecture.png", "图1 系统总体架构图", width=6.25,
              description="该图展示系统由前端展示层、业务服务层、数据持久层和外部服务层组成。前端负责用户交互，后端统一承载认证、活动、推荐、组队、消息通知和后台管理，MySQL 与外部 AI/邮件/客服服务为平台提供数据和智能能力支撑。")
    add_para(doc, "在功能架构上，系统围绕“发现活动、参与活动、发现组队、参与组队、协同沟通、后台治理”展开，活动模块与组队模块并列设计，同时通过推荐、消息、通知和个人中心形成体验闭环。")
    add_image(doc, ROOT / "docs/overview_design/assets/functional-architecture.png", "图2 系统功能架构图", width=6.25,
              description="该图按业务能力划分系统模块，突出活动发现、活动参与、组队协作、消息沟通、通知中心、个人中心和后台治理之间的关系，体现平台从学生参与到运营管理的完整闭环。")

    doc.add_heading("4.3 数据库与数据结构设计", level=2)
    add_para(doc, "数据库设计以用户、活动、队伍和行为日志为中心。用户通过 user_interest 与标签体系关联；活动通过 activity_tag 与 tags 关联；活动报名记录使用 registrations 保存状态；队伍通过 team_member 与 team_join_request 支持成员管理和审批；消息体系分为队伍群聊、活动群聊和私聊；推荐体系通过 user_behavior_log、item_embedding、recommend_activity_score、recommend_team_score 和 recommend_model_version 支持模型训练和线上读取。")
    add_image(doc, ROOT / "docs/overview_design/assets/data-architecture.png", "图3 数据架构与核心实体关系", width=6.25,
              description="该图展示用户、活动、队伍、标签、报名记录、队伍成员、消息通知和推荐行为日志等核心实体之间的关联。数据模型支撑了兴趣画像、活动报名审批、队伍申请审批和推荐排序等关键功能。")
    add_table(
        doc,
        ["数据域", "主要表", "设计说明"],
        [
            ["用户画像", "users、tags、user_interest", "保存账号、角色、状态、学院专业和显式兴趣标签。"],
            ["活动参与", "activities、activity_tag、registrations、favorites", "保存活动基础信息、标签、收藏和报名审批状态。"],
            ["组队协作", "teams、team_member、team_join_request", "支持自由组队与活动关联组队，区分队长、成员和申请状态。"],
            ["沟通通知", "messages、activity_chat_message、dm_message、dm_read_state、notifications", "覆盖队伍群聊、活动群聊、私聊、已读状态和通知中心。"],
            ["推荐训练", "user_behavior_log、item_embedding、recommend_activity_score、recommend_team_score、recommend_model_version", "记录曝光、点击、收藏、报名、申请入队等行为，并保存离线排序结果。"],
        ],
        [1550, 3300, 4510],
    )

    doc.add_heading("4.4 后端功能实现", level=2)
    add_para(doc, "后端以 com.campuspulse 包分模块实现，控制器覆盖 AuthController、ActivityController、TeamController、RecommendController、SearchController、NotificationController、ChatController、DirectMessageController、ActivityChatController、SupportController、AdminController 等。统一响应结构由 Api.ApiResponse<T> 提供，异常由全局异常处理器转换为稳定 JSON 响应。")
    add_bullets(doc, [
        "认证与安全：TokenService 负责令牌生成和解析，PasswordHasher 负责密码摘要；ApiAuthConfig 对 /api/** 做统一鉴权，公开 GET 查询和客服接口允许携带可选身份。",
        "活动流程：ActivityController 支持活动列表、详情、发布、编辑、删除、收藏、报名、取消报名、组织者查看报名、通过/拒绝报名和活动关联组队查询。",
        "组队流程：TeamController 支持队伍列表、详情、创建、编辑、删除、申请加入、取消申请、队长查看申请、通过/拒绝申请。",
        "消息通知：ChatController、ActivityChatController 和 DirectMessageController 分别处理队伍群聊、活动群聊和私聊；NotificationController 支持筛选、已读、全部已读、删除和分页。",
        "后台运营：AdminController 支持标签管理、推荐位配置、统计总览、用户状态、活动审核和活动状态管理。"
    ])

    doc.add_heading("4.5 推荐算法与模型接入", level=2)
    add_para(doc, "推荐模块采用“模型分表优先 + 规则排序回退”的工程策略。线上 RecommendController 先读取 recommend_activity_score 或 recommend_team_score；当用户暂无模型分数时，使用运营权重、热度、时间、兴趣标签匹配、队伍剩余名额和用户是否已加入/待审批等规则计算得分，确保冷启动和模型缺失时系统仍可运行。")
    add_image(doc, ROOT / "docs/architecture/uml/out/推荐逻辑示意图/推荐逻辑示意图.png", "图4 推荐逻辑示意图", width=5.95,
              description="该图说明推荐模块的输入、计算与输出过程。系统综合用户兴趣标签、历史参加记录、队伍参与信息、行为日志、活动标签和运营推荐位，生成活动推荐结果与组队推荐结果，并保留 AI 语义增强入口。")
    add_table(
        doc,
        ["推荐特征", "来源", "作用"],
        [
            ["兴趣标签匹配", "user_interest、activity_tag、队伍文本匹配", "提升与用户显式兴趣相关的活动或队伍。"],
            ["行为强度", "点击、收藏、报名、申请入队、加入队伍", "区分弱正样本与强正样本，作为训练和排序依据。"],
            ["热度指标", "报名数、收藏数、点击数、待审批数", "兼顾群体偏好，避免推荐过冷内容。"],
            ["时间因素", "活动开始时间、队伍开始时间", "优先推荐近期可参加但未过期的内容。"],
            ["运营权重", "ops_featured_item、ops_featured_activity", "支持管理员配置推荐位和重点内容。"],
            ["模型分数", "recommend_activity_score、recommend_team_score", "离线训练生成用户-内容排序分，线上直接读取。"],
        ],
        [1750, 3100, 4510],
    )
    add_para(doc, "离线训练脚本 ml/train_recommendation.py 从 MySQL 抽取用户、活动、队伍、标签和行为日志，使用 TF-IDF 文本向量与 GradientBoostingClassifier 训练活动与组队两个排序代理模型，并将推荐分数写回推荐分表。模型产物保存在 ml/models 目录中，便于后续替换为 LightGBM、XGBoost Ranker、LambdaMART 或双塔模型。")

    doc.add_heading("4.6 前端原型与交互实现", level=2)
    add_para(doc, "前端原型位于 frontend 目录，覆盖 index、login、register、interest-tags、home、activity-lobby、activity-detail、team-lobby、messages、notifications、profile、activity-manage、admin、customer-service 等页面。本次报告中的界面图来自本机运行后的浏览器截图：后端连接 MySQL 演示库运行在 8080 端口，前端代理运行在 8125 端口。界面以校园场景为核心，首页同时呈现推荐活动、推荐组队、活动速览和通知入口；活动详情支持收藏、报名、联系发布者和关联组队；组队大厅支持队伍筛选、详情查看与入队申请；消息页统一呈现私聊、群聊和通知预览。")

    doc.add_heading("4.7 智能客服与知识库兜底", level=2)
    add_para(doc, "智能客服模块由 SupportController 对外提供 /api/support/chat、/api/support/escalate 和 /api/support/knowledge 接口，设计上优先使用 Dify Chatflow 与知识库回答平台使用问题；当外部服务不可用或知识库无法覆盖时，返回结构化兜底答案并提示转人工。知识库覆盖注册、登录、报名、组队、消息、通知、推荐和后台等常见问题，适合期末展示现场直接提问体验。")

    doc.add_heading("4.8 部署联调与测试验证", level=2)
    add_para(doc, "项目本地部署建议为：后端服务运行在 8080 端口，前端通过本地 HTTP 代理运行在 8125 端口，数据库使用 MySQL 的 campus_pulse 库。前端统一从 8125 入口访问，避免直接 file:// 打开页面造成接口请求失败。")
    add_image(doc, ROOT / "docs/overview_design/assets/deployment-architecture.png", "图6 系统部署架构图", width=6.2,
              description="该图展示本地演示与部署结构：浏览器通过前端静态页面访问系统，前端请求由本地代理转发至 Spring Boot 后端，后端连接 MySQL 数据库，并可对接邮件、Dify 知识库和 AI 外部服务。")
    add_table(
        doc,
        ["测试项", "操作路径", "验证结果"],
        [
            ["登录与画像", "使用预置学生账号登录，维护兴趣标签。", "能进入首页，推荐内容与账号画像存在差异。"],
            ["活动报名", "活动详情 -> 报名活动 -> 查看通知与个人中心记录。", "报名记录、通知和个人参与记录同步更新。"],
            ["报名审批", "组织者账号 -> 我发布的活动 -> 审批报名。", "通过/拒绝后报名状态变化，容量约束生效。"],
            ["组队申请", "组队大厅 -> 查看详情 -> 申请加入。", "队长可在申请列表审批，审批后进入队伍关系。"],
            ["沟通通知", "消息页 -> 私聊/群聊/通知中心。", "私聊、活动群聊、队伍群聊和通知筛选均可体验。"],
            ["后台治理", "管理员账号 -> 后台管理。", "能查看统计、审核活动、管理用户、标签和推荐位。"],
            ["智能客服", "客服页面输入报名、组队、通知等问题。", "能返回结构化答案，无法覆盖时提示人工兜底。"],
        ],
        [1450, 4300, 3610],
    )

    add_section_heading(doc, "五、实验结果（展示）", "本节从功能完成度、系统运行效果、推荐效果和工程产物四个角度总结实验结果。")
    doc.add_heading("5.1 功能完成情况", level=2)
    add_table(
        doc,
        ["功能域", "完成内容", "展示结论"],
        [
            ["学生端", "登录、兴趣标签、首页推荐、活动浏览、活动报名、组队申请、消息、通知、个人中心。", "普通学生可以完成“发现 -> 参与 -> 沟通 -> 查看记录”的闭环。"],
            ["组织者端", "活动发布、活动管理、报名审批、活动群聊、联动组队开关。", "组织者可以完成活动从发布到协作管理的流程。"],
            ["队长端", "队伍创建、资料维护、入队申请审批、队伍群聊。", "队长可以管理队伍成员与协同沟通。"],
            ["管理员端", "统计总览、活动审核、用户状态、标签管理、推荐位管理。", "平台具备基础运营治理能力。"],
            ["智能能力", "推荐活动、推荐组队、行为日志、离线模型、规则回退、智能客服。", "智能化能力已接入业务流程，并具备可扩展空间。"],
        ],
        [1450, 5200, 2710],
    )

    doc.add_heading("5.2 推荐与搜索结果", level=2)
    add_para(doc, "系统推荐结果不是单纯按照发布时间排序，而是综合兴趣标签、历史行为、运营权重、热度与时间因素生成。演示账号具有不同兴趣画像，例如技术/竞赛类、学术/实习类、运动类、国际交流与社交类账号，登录后首页推荐活动和推荐组队会呈现可见差异。搜索模块提供活动与组队统一搜索入口，可通过关键词定位相关内容。")

    doc.add_heading("5.3 界面展示结果", level=2)
    add_para(doc, "以下截图均来自本机实际运行页面。截图采集时，MySQL 8 容器提供数据库服务，Spring Boot 后端运行在 8080 端口，前端静态页面通过 8125 端口代理访问后端接口；学生端使用 linzhixia / demo12345 登录，组织者端使用 org / org123 登录，后台使用 admin / admin123 登录。")
    add_image(doc, live / "01-login-page.png", "图7 登录页面：账号密码登录入口", width=5.9,
              description="该页面验证了系统的基础身份入口。用户可通过用户名或学号配合密码登录，登录成功后后端返回 Token，前端将 Token 用于后续活动、组队、通知和个人中心等接口访问。")
    add_image(doc, live / "02-home-recommendations-top.png", "图8 学生端首页：推荐活动、推荐组队与通知入口", width=6.15,
              description="该页面展示学生端登录后的首页效果。顶部提供搜索和通知入口，中部展示平台核心价值主张，下面根据用户画像展示推荐活动卡片，体现“活动发现”和“个性化推荐”的主要体验。")
    add_image(doc, live / "03-activity-lobby-top.png", "图9 活动大厅：活动搜索、分类筛选与活动卡片", width=6.15,
              description="该页面用于集中浏览校园活动。用户可以通过关键词搜索、分类入口和活动卡片查看活动列表，卡片中展示封面、时间、地点、标签和报名状态，为进一步进入详情和报名提供入口。")
    add_image(doc, live / "04-activity-detail.png", "图10 活动详情：活动信息、报名入口与关联操作", width=6.15,
              description="该页面展示单个活动的完整信息，包括活动封面、时间地点、人数、标签、简介和报名操作。页面同时提供收藏、联系发布者和关联组队等入口，验证活动参与流程的核心闭环。")
    add_image(doc, live / "05-team-lobby-top.png", "图11 组队大厅：队伍搜索、筛选与申请入口", width=6.15,
              description="该页面展示组队模块的主要入口。用户可以按类别浏览队伍，通过搜索定位队伍，并在队伍卡片中查看人数、时间窗口和申请按钮，体现自由组队与活动关联组队的统一呈现。")
    add_image(doc, live / "06-messages.png", "图12 消息页：私聊、群聊与通知预览", width=6.15,
              description="该页面将平台沟通能力集中展示，分为私聊、群聊和通知预览。用户报名、申请入队、联系组织者或进入队伍后，都可以在该页面继续沟通，形成参与后的协作反馈链路。")
    add_image(doc, live / "07-notifications.png", "图13 通知中心：已读筛选、删除与分页浏览", width=6.15,
              description="该页面展示通知中心的运行效果。通知列表支持全部、未读、已读筛选，用户可以查看通知内容、标记已读、删除通知并分页浏览，验证系统事件反馈和消息留痕能力。")
    add_image(doc, live / "08-profile.png", "图14 个人中心：资料、参与记录与管理入口", width=6.15,
              description="该页面展示学生个人中心，包含头像、基本资料、统计信息、活动记录、组队记录、收藏和资料编辑入口。该页面用于承载用户画像维护和个人参与记录查询。")
    add_image(doc, live / "09-customer-service.png", "图15 智能客服：知识库问答与人工兜底入口", width=6.15,
              description="该页面展示智能客服交互界面。用户可以围绕报名、组队、通知、推荐和后台等问题发起咨询；系统优先根据知识库回答，无法覆盖时再提示转人工处理。")
    add_image(doc, live / "11-publish-activity-form.png", "图16 组织者端：活动发布表单", width=5.95,
              description="该页面展示活动组织者发布活动的表单流程，包括活动名称、时间地点、人数上限、标签、封面和活动说明等字段。该功能将组织者侧的活动创建行为转化为后端 activities 与 activity_tag 数据。")
    add_image(doc, live / "12-activity-manage-top.png", "图17 组织者端：活动管理概览与报名审批", width=6.15,
              description="该页面展示组织者对已发布活动的管理能力。页面上方显示活动状态、名额和时间信息，下方提供报名审批、申请人联系方式、通过/拒绝报名和编辑活动信息等操作。")
    add_image(doc, live / "10-admin-dashboard-top.png", "图18 管理员后台：统计总览与审核入口", width=6.15,
              description="该页面展示管理员进入后台后的运营总览。系统汇总用户数、活动数、队伍数、报名记录、消息总量和精选活动等指标，并提供活动治理和用户治理入口。")
    add_image(doc, live / "10-admin-activity-audit.png", "图19 管理员后台：活动审核与状态管理", width=6.15,
              description="该页面展示管理员对活动进行审核和状态管理的界面。管理员可以查看活动发布人、时间、标签和统计信息，并执行设为精选、下架活动、标记拒绝等运营操作。")
    add_image(doc, live / "10-admin-user-manage.png", "图20 管理员后台：用户状态与标签运营", width=6.15,
              description="该页面展示后台用户治理能力。管理员可查看用户身份、发布活动、报名和队伍数量，并进行联系、停用账号等操作，为平台安全和运营维护提供支撑。")

    doc.add_heading("5.4 工程产物结果", level=2)
    add_bullets(doc, [
        "需求与设计产物：需求规格说明书、概要设计说明书、UML 架构图、ER 图、部署图和推荐逻辑示意图。",
        "前端产物：frontend 下的完整 HTML/CSS/JS 原型与截图资源。",
        "后端产物：backend 下的 Spring Boot 项目、数据库 schema.sql、初始化数据 data.sql 和业务控制器代码。",
        "智能推荐产物：ml/train_recommendation.py、ml/models 下的模型文件、推荐分表与模型版本表设计。",
        "展示验收产物：期末展示功能需求清单、操作说明书和本实验报告。"
    ])

    add_section_heading(doc, "六、实验总结")
    doc.add_heading("6.1 收获", level=2)
    add_para(doc, "通过本次综合项目实践，项目组完整经历了从校园场景问题识别到系统落地展示的全过程。实验不仅训练了 Web 前后端开发能力，也强化了需求拆解、架构设计、数据建模、接口设计、推荐算法接入和测试验收的系统思维。尤其是在推荐模块中，项目没有停留在页面展示层，而是设计了行为日志、模型训练、离线分表和规则回退，使智能推荐具备工程可运行性。")
    doc.add_heading("6.2 不足", level=2)
    add_bullets(doc, [
        "推荐模型目前仍以排序代理模型和规则融合为主，真实大规模用户行为数据不足，模型效果需要在长期运行中持续评估。",
        "前端原型已覆盖主要流程，但移动端适配、无障碍体验、异常提示和加载状态仍有进一步打磨空间。",
        "智能客服已具备知识库问答与人工兜底设计，但知识库覆盖深度、问题召回质量和多轮对话上下文仍可增强。",
        "文件上传、邮箱验证码和外部 AI 服务涉及生产安全配置，正式部署时需要替换开发密钥、完善审计和限流策略。"
    ])
    doc.add_heading("6.3 改进方向", level=2)
    add_bullets(doc, [
        "扩大行为样本量，补充曝光、点击、报名、收藏、搜索和停留时长等特征，使用 LightGBM、XGBoost Ranker 或双塔模型提升推荐质量。",
        "完善活动签到、活动评价、队伍任务协作和活动后复盘，让平台从“报名组队”延伸到“过程协作与效果沉淀”。",
        "增加接口自动化测试、前端端到端测试和压力测试，建立可重复的回归验证流程。",
        "强化管理员风控能力，例如敏感词审核、异常账号检测、活动容量预警和通知模板管理。",
        "将前端升级为组件化框架或小程序形态，进一步提升可维护性和真实校园使用体验。"
    ])
    add_callout(doc, "总体结论", "本实验完成了一个具备完整业务链路、清晰架构设计、可运行后端、可展示前端、推荐算法工程入口和智能客服能力的校园活动智能推荐与组队平台。系统能够支撑课程验收、现场演示和后续迭代，是一次覆盖软件工程、数据库、Web 开发与智能应用的综合实践成果。", fill="EEF6FF")

    add_section_heading(doc, "附录：关键文件与演示入口")
    add_table(
        doc,
        ["类别", "路径或入口", "说明"],
        [
            ["前端入口", "http://127.0.0.1:8125/", "期末展示推荐访问入口。"],
            ["后端服务", "http://127.0.0.1:8080/", "Spring Boot 服务默认端口。"],
            ["前端原型", "frontend/", "学生端、组织者端、后台与客服页面。"],
            ["后端项目", "backend/", "Spring Boot 业务代码与数据库脚本。"],
            ["推荐工程", "ml/", "离线推荐训练脚本、模型文件与说明。"],
        ["设计图", "docs/overview_design/assets/、docs/architecture/uml/out/", "总体架构、功能架构、ER、部署和推荐逻辑图。"],
            ["展示文档", "docs/期末展示功能需求清单.md、docs/期末展示操作说明书.md", "现场验收与操作说明。"],
        ],
        [1400, 4200, 3760],
    )

    doc.save(OUT_DOCX)
    return OUT_DOCX


if __name__ == "__main__":
    path = build_report()
    print(path)
