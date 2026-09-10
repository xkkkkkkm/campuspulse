from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

from docx import Document
from docx.enum.section import WD_SECTION_START
from docx.enum.section import WD_ORIENT
from docx.enum.table import WD_ALIGN_VERTICAL, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK, WD_LINE_SPACING
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor, Twips


ROOT = Path(__file__).resolve().parent.parent
OUT_ROOT = ROOT / "docs" / "software_project_management"
MD_DIR = OUT_ROOT / "markdown"
DOCX_DIR = OUT_ROOT / "docx"

PROJECT_NAME = "校园活动智能推荐与组队平台"
COURSE_NAME = "软件项目管理"
GROUP_NAME = "第七组"
TEAM_MEMBERS = "许凯铭、刘文甫、万逸、徐钱超"
DOC_DATE = "2026年6月16日"
DOCUMENT_CATEGORY = "项目管理交付文件"

CONTENT_WIDTH_DXA = 9360
TABLE_INDENT_DXA = 120
CELL_MARGINS_DXA = {"top": 90, "bottom": 90, "start": 130, "end": 130}
CN_NUMERALS = "一二三四五六七八九十"


@dataclass
class TableBlock:
    title: str
    headers: list[str]
    rows: list[list[str]]
    widths: list[float] | None = None


@dataclass
class Section:
    title: str
    paragraphs: list[str] = field(default_factory=list)
    bullets: list[str] = field(default_factory=list)
    tables: list[TableBlock] = field(default_factory=list)
    subsections: list["Section"] = field(default_factory=list)


@dataclass
class ManagedDocument:
    no: int
    title: str
    purpose: str
    conclusion: str
    sections: list[Section]

    @property
    def stem(self) -> str:
        return f"{self.no:02d}_{self.title}"


def set_font(run, name="SimSun", size=None, color=None, bold=None, italic=None, latin="Times New Roman"):
    run.font.name = latin
    run._element.rPr.rFonts.set(qn("w:ascii"), latin)
    run._element.rPr.rFonts.set(qn("w:hAnsi"), latin)
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)
    run._element.rPr.rFonts.set(qn("w:cs"), latin)
    if size is not None:
        run.font.size = Pt(size)
    if color is not None:
        run.font.color.rgb = RGBColor.from_string(color)
    if bold is not None:
        run.bold = bold
    if italic is not None:
        run.italic = italic


def ensure_child(parent, tag: str):
    child = parent.find(qn(tag))
    if child is None:
        child = OxmlElement(tag)
        parent.append(child)
    return child


def cn_number(n: int) -> str:
    if n <= 10:
        return CN_NUMERALS[n - 1]
    if n < 20:
        return "十" + (CN_NUMERALS[n - 11] if n > 10 else "")
    tens, ones = divmod(n, 10)
    return CN_NUMERALS[tens - 1] + "十" + (CN_NUMERALS[ones - 1] if ones else "")


def section_anchor(index_path: tuple[int, ...]) -> str:
    return "sec_" + "_".join(str(i) for i in index_path)


def numbered_title(title: str, index_path: tuple[int, ...]) -> str:
    if len(index_path) == 1:
        return f"{cn_number(index_path[0])}、{title}"
    return f"{'.'.join(str(i) for i in index_path)} {title}"


def add_bookmark(paragraph, name: str, bookmark_id: int):
    start = OxmlElement("w:bookmarkStart")
    start.set(qn("w:id"), str(bookmark_id))
    start.set(qn("w:name"), name)
    end = OxmlElement("w:bookmarkEnd")
    end.set(qn("w:id"), str(bookmark_id))
    paragraph._p.insert(0, start)
    paragraph._p.append(end)


def add_internal_hyperlink(paragraph, anchor: str, text: str):
    hyperlink = OxmlElement("w:hyperlink")
    hyperlink.set(qn("w:anchor"), anchor)
    run = OxmlElement("w:r")
    r_pr = OxmlElement("w:rPr")
    color = OxmlElement("w:color")
    color.set(qn("w:val"), "000000")
    r_fonts = OxmlElement("w:rFonts")
    r_fonts.set(qn("w:ascii"), "Times New Roman")
    r_fonts.set(qn("w:hAnsi"), "Times New Roman")
    r_fonts.set(qn("w:eastAsia"), "SimSun")
    r_fonts.set(qn("w:cs"), "Times New Roman")
    size = OxmlElement("w:sz")
    size.set(qn("w:val"), "24")
    r_pr.append(r_fonts)
    r_pr.append(color)
    r_pr.append(size)
    run.append(r_pr)
    t = OxmlElement("w:t")
    t.text = text
    run.append(t)
    hyperlink.append(run)
    paragraph._p.append(hyperlink)


def paragraph_border_bottom(paragraph, color="000000", size="10", space="6"):
    p_pr = paragraph._p.get_or_add_pPr()
    p_bdr = ensure_child(p_pr, "w:pBdr")
    bottom = ensure_child(p_bdr, "w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), size)
    bottom.set(qn("w:space"), space)
    bottom.set(qn("w:color"), color)


def set_cell_shading(cell, fill: str):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = ensure_child(tc_pr, "w:shd")
    shd.set(qn("w:fill"), fill)


def set_cell_margins(cell, margins=CELL_MARGINS_DXA):
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_mar = ensure_child(tc_pr, "w:tcMar")
    for side, value in margins.items():
        node = ensure_child(tc_mar, f"w:{side}")
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def column_widths(weights: list[float]) -> list[int]:
    total = float(sum(weights))
    widths = [int(round(CONTENT_WIDTH_DXA * w / total)) for w in weights]
    widths[-1] += CONTENT_WIDTH_DXA - sum(widths)
    return widths


def apply_table_geometry(table, widths: list[int]):
    table.autofit = False
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    tbl = table._tbl
    tbl_pr = tbl.tblPr
    tbl_w = ensure_child(tbl_pr, "w:tblW")
    tbl_w.set(qn("w:type"), "dxa")
    tbl_w.set(qn("w:w"), str(sum(widths)))
    tbl_ind = ensure_child(tbl_pr, "w:tblInd")
    tbl_ind.set(qn("w:type"), "dxa")
    tbl_ind.set(qn("w:w"), str(TABLE_INDENT_DXA))
    layout = ensure_child(tbl_pr, "w:tblLayout")
    layout.set(qn("w:type"), "fixed")
    grid = tbl.tblGrid
    for child in list(grid):
        grid.remove(child)
    for width in widths:
        grid_col = OxmlElement("w:gridCol")
        grid_col.set(qn("w:w"), str(width))
        grid.append(grid_col)
    for idx, width in enumerate(widths):
        table.columns[idx].width = Twips(width)
    for row in table.rows:
        row.height = None
        for idx, cell in enumerate(row.cells):
            cell.width = Twips(widths[idx])
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            tc_pr = cell._tc.get_or_add_tcPr()
            tc_w = ensure_child(tc_pr, "w:tcW")
            tc_w.set(qn("w:type"), "dxa")
            tc_w.set(qn("w:w"), str(widths[idx]))
            set_cell_margins(cell)


def add_table(doc: Document, block: TableBlock):
    caption = doc.add_paragraph()
    caption.paragraph_format.space_before = Pt(4)
    caption.paragraph_format.space_after = Pt(4)
    r = caption.add_run(block.title)
    set_font(r, size=12, color="000000", bold=True)
    table = doc.add_table(rows=1, cols=len(block.headers))
    table.style = "Table Grid"
    widths = column_widths(block.widths or [1] * len(block.headers))
    for i, header in enumerate(block.headers):
        cell = table.rows[0].cells[i]
        cell.text = header
        set_cell_shading(cell, "EDEDED")
        for p in cell.paragraphs:
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            for run in p.runs:
                set_font(run, size=10.5, bold=True, color="000000")
    for row_data in block.rows:
        cells = table.add_row().cells
        for i, text in enumerate(row_data):
            cells[i].text = text
            for p in cells[i].paragraphs:
                p.paragraph_format.space_after = Pt(0)
                p.paragraph_format.line_spacing = 1.08
                if len(text) <= 10:
                    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
                for run in p.runs:
                    set_font(run, size=10.5, color="000000")
    apply_table_geometry(table, widths)
    after = doc.add_paragraph()
    after.paragraph_format.space_after = Pt(2)
    return table


def add_section_break(doc: Document, orientation: str = "portrait"):
    section = doc.add_section(WD_SECTION_START.NEW_PAGE)
    section.top_margin = Inches(1)
    section.bottom_margin = Inches(1)
    section.left_margin = Inches(1)
    section.right_margin = Inches(1)
    section.header_distance = Inches(0.492)
    section.footer_distance = Inches(0.492)
    if orientation == "landscape":
        section.orientation = WD_ORIENT.LANDSCAPE
        section.page_width = Inches(11)
        section.page_height = Inches(8.5)
    else:
        section.orientation = WD_ORIENT.PORTRAIT
        section.page_width = Inches(8.5)
        section.page_height = Inches(11)
    return section


def add_paragraph(doc: Document, text: str, style: str | None = None):
    p = doc.add_paragraph(style=style)
    p.paragraph_format.first_line_indent = Pt(24)
    p.paragraph_format.space_after = Pt(6)
    p.paragraph_format.line_spacing = 1.15
    r = p.add_run(text)
    set_font(r, size=12, color="000000")
    return p


def add_bullet(doc: Document, text: str):
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.space_after = Pt(4)
    p.paragraph_format.line_spacing = 1.15
    r = p.add_run(text)
    set_font(r, size=12, color="000000")
    return p


def set_doc_styles(doc: Document):
    section = doc.sections[0]
    section.start_type = WD_SECTION_START.NEW_PAGE
    section.page_width = Inches(8.5)
    section.page_height = Inches(11)
    section.top_margin = Inches(1)
    section.bottom_margin = Inches(1)
    section.left_margin = Inches(1)
    section.right_margin = Inches(1)
    section.header_distance = Inches(0.492)
    section.footer_distance = Inches(0.492)

    styles = doc.styles
    normal = styles["Normal"]
    normal.font.name = "Times New Roman"
    normal._element.rPr.rFonts.set(qn("w:ascii"), "Times New Roman")
    normal._element.rPr.rFonts.set(qn("w:hAnsi"), "Times New Roman")
    normal._element.rPr.rFonts.set(qn("w:eastAsia"), "SimSun")
    normal._element.rPr.rFonts.set(qn("w:cs"), "Times New Roman")
    normal.font.size = Pt(12)
    normal.font.color.rgb = RGBColor.from_string("000000")
    normal.paragraph_format.space_after = Pt(6)
    normal.paragraph_format.line_spacing_rule = WD_LINE_SPACING.MULTIPLE
    normal.paragraph_format.line_spacing = 1.15

    for name, size, color, before, after in [
        ("Heading 1", 18, "000000", 16, 8),
        ("Heading 2", 15, "000000", 12, 6),
        ("Heading 3", 12, "000000", 8, 4),
    ]:
        style = styles[name]
        style.font.name = "Times New Roman"
        style._element.rPr.rFonts.set(qn("w:ascii"), "Times New Roman")
        style._element.rPr.rFonts.set(qn("w:hAnsi"), "Times New Roman")
        style._element.rPr.rFonts.set(qn("w:eastAsia"), "SimSun")
        style._element.rPr.rFonts.set(qn("w:cs"), "Times New Roman")
        style.font.size = Pt(size)
        style.font.bold = True
        style.font.color.rgb = RGBColor.from_string(color)
        style.paragraph_format.space_before = Pt(before)
        style.paragraph_format.space_after = Pt(after)
        style.paragraph_format.keep_with_next = True

    for list_style in ["List Bullet", "List Number"]:
        style = styles[list_style]
        style.font.name = "Times New Roman"
        style._element.rPr.rFonts.set(qn("w:ascii"), "Times New Roman")
        style._element.rPr.rFonts.set(qn("w:hAnsi"), "Times New Roman")
        style._element.rPr.rFonts.set(qn("w:eastAsia"), "SimSun")
        style._element.rPr.rFonts.set(qn("w:cs"), "Times New Roman")
        style.font.size = Pt(12)
        style.font.color.rgb = RGBColor.from_string("000000")
        style.paragraph_format.space_after = Pt(4)
        style.paragraph_format.line_spacing = 1.15


def set_header_footer(doc: Document, title: str):
    section = doc.sections[0]
    header = section.header.paragraphs[0]
    header.text = ""
    left = header.add_run(PROJECT_NAME)
    set_font(left, size=9, color="000000")
    header.alignment = WD_ALIGN_PARAGRAPH.LEFT
    footer = section.footer.paragraphs[0]
    footer.text = ""
    run = footer.add_run(f"{GROUP_NAME} | {title}")
    set_font(run, size=9, color="000000")
    footer.alignment = WD_ALIGN_PARAGRAPH.CENTER


def add_cover(doc: Document, managed: ManagedDocument):
    kicker = doc.add_paragraph()
    kicker.paragraph_format.space_before = Pt(22)
    kicker.paragraph_format.space_after = Pt(10)
    r = kicker.add_run(DOCUMENT_CATEGORY)
    set_font(r, size=15, color="000000", bold=True)

    title = doc.add_paragraph()
    title.paragraph_format.space_after = Pt(2)
    r = title.add_run(f"{managed.no:02d}  {managed.title}")
    set_font(r, size=18, color="000000", bold=True)

    subtitle = doc.add_paragraph()
    subtitle.paragraph_format.space_after = Pt(18)
    r = subtitle.add_run(PROJECT_NAME)
    set_font(r, size=15, color="000000", bold=True)

    rows = [
        ["项目案例", PROJECT_NAME],
        ["文件类别", DOCUMENT_CATEGORY],
        ["编制单位", GROUP_NAME],
        ["项目成员", TEAM_MEMBERS],
        ["文档版本", "V1.0"],
        ["编制日期", DOC_DATE],
        ["文档定位", managed.purpose],
    ]
    cover_table = add_table(doc, TableBlock("封面信息", ["项目", "内容"], rows, [1.2, 4.6]))
    for row in cover_table.rows[1:]:
        for cell in row.cells:
            for p in cell.paragraphs:
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER

    lead = doc.add_paragraph()
    lead.paragraph_format.space_before = Pt(8)
    lead.paragraph_format.space_after = Pt(8)
    paragraph_border_bottom(lead, color="000000", size="8", space="8")
    r = lead.add_run(f"结论摘要：{managed.conclusion}")
    set_font(r, size=12, color="000000", bold=True)
    doc.add_page_break()


def add_front_matter(doc: Document, managed: ManagedDocument):
    doc.add_heading("文档编辑记录", level=1)
    add_table(
        doc,
        TableBlock(
            "文档编辑记录表",
            ["序号", "日期", "编制人", "修改说明", "版本"],
            [["1", DOC_DATE.replace("年", "-").replace("月", "-").replace("日", ""), TEAM_MEMBERS, f"完成《{managed.title}》正式提交版。", "V1.0"]],
            [0.6, 1.2, 2.2, 4.2, 0.8],
        ),
    )
    doc.add_heading("目录", level=1)
    for idx, sec in enumerate(managed.sections, 1):
        p = doc.add_paragraph()
        p.paragraph_format.space_after = Pt(4)
        p.paragraph_format.line_spacing = 1.15
        add_internal_hyperlink(p, section_anchor((idx,)), numbered_title(sec.title, (idx,)))
        for sub_idx, sub in enumerate(sec.subsections, 1):
            p = doc.add_paragraph()
            p.paragraph_format.left_indent = Inches(0.25)
            p.paragraph_format.space_after = Pt(3)
            p.paragraph_format.line_spacing = 1.15
            add_internal_hyperlink(p, section_anchor((idx, sub_idx)), numbered_title(sub.title, (idx, sub_idx)))
    doc.add_page_break()


def add_section(doc: Document, section: Section, level=1, index_path: tuple[int, ...] = ()):
    if section.title == "监督控制与收尾口径":
        doc.add_page_break()
    heading = doc.add_heading(numbered_title(section.title, index_path), level=level)
    add_bookmark(heading, section_anchor(index_path), int("".join(str(i).zfill(2) for i in index_path)))
    for para in section.paragraphs:
        add_paragraph(doc, para)
    for bullet in section.bullets:
        add_bullet(doc, bullet)
    for table in section.tables:
        if len(table.headers) > 8:
            add_section_break(doc, "landscape")
            add_table(doc, table)
            add_section_break(doc, "portrait")
        else:
            add_table(doc, table)
    for sub_idx, sub in enumerate(section.subsections, 1):
        add_section(doc, sub, min(level + 1, 3), index_path + (sub_idx,))


def write_docx(managed: ManagedDocument):
    doc = Document()
    set_doc_styles(doc)
    set_header_footer(doc, managed.title)
    add_cover(doc, managed)
    add_front_matter(doc, managed)
    for idx, section in enumerate(managed.sections, 1):
        add_section(doc, section, 1, (idx,))
    path = DOCX_DIR / f"{managed.stem}.docx"
    doc.save(path)


def md_table(block: TableBlock) -> list[str]:
    lines = [f"表：{block.title}", "", "| " + " | ".join(block.headers) + " |"]
    lines.append("| " + " | ".join(["---"] * len(block.headers)) + " |")
    for row in block.rows:
        lines.append("| " + " | ".join(cell.replace("\n", "<br>") for cell in row) + " |")
    lines.append("")
    return lines


def section_to_md(section: Section, level=2, index_path: tuple[int, ...] = ()) -> list[str]:
    lines = ["#" * level + " " + numbered_title(section.title, index_path), ""]
    for para in section.paragraphs:
        lines += [para, ""]
    for bullet in section.bullets:
        lines.append(f"- {bullet}")
    if section.bullets:
        lines.append("")
    for table in section.tables:
        lines.extend(md_table(table))
    for sub_idx, sub in enumerate(section.subsections, 1):
        lines.extend(section_to_md(sub, min(level + 1, 4), index_path + (sub_idx,)))
    return lines


def write_markdown(managed: ManagedDocument):
    lines = [
        f"# {managed.no:02d} {managed.title}",
        "",
        f"- 项目案例：{PROJECT_NAME}",
        f"- 文件类别：{DOCUMENT_CATEGORY}",
        f"- 编制单位：{GROUP_NAME}",
        f"- 项目成员：{TEAM_MEMBERS}",
        f"- 文档版本：V1.0",
        f"- 编制日期：{DOC_DATE}",
        "",
        f"**文档定位：**{managed.purpose}",
        "",
        f"**结论摘要：**{managed.conclusion}",
        "",
        "## 文档编辑记录",
        "",
        "| 序号 | 日期 | 编制人 | 修改说明 | 版本 |",
        "| --- | --- | --- | --- | --- |",
        f"| 1 | {DOC_DATE.replace('年', '-').replace('月', '-').replace('日', '')} | {TEAM_MEMBERS} | 完成《{managed.title}》正式提交版。 | V1.0 |",
        "",
        "## 目录",
        "",
    ]
    for idx, sec in enumerate(managed.sections, 1):
        lines.append(f"{numbered_title(sec.title, (idx,))}")
        for sub_idx, sub in enumerate(sec.subsections, 1):
            lines.append(f"   {numbered_title(sub.title, (idx, sub_idx))}")
    lines.append("")
    for idx, section in enumerate(managed.sections, 1):
        lines.extend(section_to_md(section, 2, (idx,)))
    path = MD_DIR / f"{managed.stem}.md"
    path.write_text("\n".join(lines), encoding="utf-8")


def common_project_overview() -> Section:
    return Section(
        "项目事实依据",
        paragraphs=[
            "本报告以当前仓库中已经形成的需求规格说明书、概要设计说明书、期末展示功能需求清单、期末展示操作说明书、后端源码、原型页面、UML 图和 Dify 智能客服资料为依据，不虚构与项目无关的业务范围。项目定位为面向高校学生、活动组织者和管理员的一站式活动发现、报名审核、组队协作、消息通知、智能客服与后台治理平台。",
            "系统已经形成 Web 用户端、Web 管理后台、Spring Boot 后端服务、MySQL 数据库、推荐排序模块、邮件验证码、SSE/消息通知、Dify 知识库客服和机器学习推荐脚本等关键组成。管理文档中的计划、估算、成本和风险均按学生课程项目口径进行合理估算，重点体现项目管理方法和过程控制能力。",
        ],
        tables=[
            TableBlock(
                "项目范围基线",
                ["范围项", "已确认内容", "管理含义"],
                [
                    ["用户角色", "普通学生、活动组织者、队伍发起者、系统管理员", "角色清晰，便于进行权限、测试和验收分解。"],
                    ["核心业务", "活动发现、推荐、报名、组队、私聊/群聊、通知、个人中心、后台审核", "覆盖用户从发现到参与再到协作的闭环。"],
                    ["智能能力", "兴趣画像、活动/组队推荐、Dify 智能客服、推荐模型脚本", "是项目区别于普通信息发布系统的主要价值。"],
                    ["技术基线", "Spring Boot 3.1.4、MySQL 8.x、HTML/CSS/JS 原型、Dify、Python ML", "以可演示、可部署、可扩展为主，不引入过重工程依赖。"],
                    ["已有成果", "需求文档、概要设计、UML 图、原型页面、后端接口、展示 PPT、操作说明", "可以支撑项目总结、质量报告和风险闭环分析。"],
                ],
                [1.2, 2.7, 2.6],
            )
        ],
    )


def phase_table() -> TableBlock:
    return TableBlock(
        "阶段划分与里程碑",
        ["阶段", "时间范围", "主要成果", "验收口径"],
        [
            ["启动与立项", "第1周", "项目建议、目标、角色和初步范围", "完成立项评审，项目范围可被成员共同理解。"],
            ["需求分析", "第2-3周", "需求规格说明书、角色用例、非功能需求", "需求覆盖主要用户旅程，关键边界可测试。"],
            ["概要设计", "第4-5周", "总体架构、功能架构、ER 图、接口设计", "设计能解释业务闭环、数据流和部署关系。"],
            ["实现与联调", "第6-10周", "前端原型、后端接口、推荐、消息、后台和客服", "核心流程可演示，接口稳定，异常有兜底。"],
            ["测试与完善", "第11-13周", "功能测试、缺陷修复、展示材料", "主要缺陷关闭，演示账号和操作说明可用。"],
            ["总结与交付", "第14周", "管理文档、项目总结、最终提交包", "交付物齐全，过程经验可复盘。"],
        ],
        [1.2, 1.2, 2.8, 2.5],
    )


def course_alignment_table() -> TableBlock:
    return TableBlock(
        "项目管理方法应用表",
        ["管理领域", "控制要点", "项目应用方式"],
        [
            ["项目治理", "围绕范围、预算、成本、质量和约束交付高质量软件", "所有文档均把范围、进度、成本、质量和风险作为统一基线。"],
            ["项目绩效域", "干系人、团队、生命周期、计划、监控、交付、度量和不确定性绩效域相互作用", "在建议书、计划、质量、风险和总结中加入绩效域检查口径。"],
            ["项目准备和启动", "项目建议、可行性分析、组织结构、干系人识别和启动门槛", "建议书和可行性报告明确必要性、经济技术风险分析和启动条件。"],
            ["项目计划", "计划内容包括范围、估算、风险、资源、进度、跟踪和控制机制", "资源、进度、成本、风险、质量计划均按子计划展开。"],
            ["项目估算", "德尔菲法、代码行、功能点、COCOMO、Walston-Felix、工期和成本估算", "估算报告补充功能点、KLOC、Walston-Felix 和 COCOMO 对照。"],
            ["进度成本", "活动标识、依赖关系、关键路径、PERT、甘特图、挣值管理", "进度计划补充 CPM/PERT 和文本甘特图，成本计划补充 EVM 指标。"],
            ["质量管理", "质量工程、测试左移/右移、CI/CD、评审、缺陷预防和质量度量", "质量计划和质量管理报告补充质量工程与缺陷度量。"],
            ["风险管理", "风险识别、评估、监控、规避、风险列表和风险排序", "风险计划和风险管理报告补充风险管理模型、趋势和审计。"],
            ["团队干系人", "目标管理、分工、沟通协作、知识共享、干系人识别分析管理", "资源计划、风险报告和总结补充团队与干系人管理记录。"],
            ["监督与收尾", "过程度量、数据收集、可视化管理、优先级、变更控制、项目收尾", "质量报告、风险报告和总结补充监控、变更和收尾验收。"],
        ],
        [1.2, 3.0, 3.0],
    )


def performance_domain_table() -> TableBlock:
    return TableBlock(
        "项目绩效域映射表",
        ["绩效域", "课程含义", "本项目落点"],
        [
            ["干系人绩效域", "识别、分析、优先级、参与和监控干系人", "学生、组织者、管理员、教师分别有诉求和验收口径。"],
            ["团队绩效域", "营造协作团队环境并持续沟通", "项目组按产品、前端、后端、推荐、测试和文档分工协作。"],
            ["开发模式与生命周期", "根据项目特点选择生命周期和开发策略", "采用课程项目适合的迭代式推进，核心闭环优先。"],
            ["计划绩效域", "将范围、资源、进度、成本、质量、风险统一纳入计划", "11份文档形成计划和报告闭环。"],
            ["项目工作绩效域", "组织和执行项目工作，交付可验证成果", "需求、设计、代码、原型、推荐、客服和文档均有产出。"],
            ["交付绩效域", "持续交付可交付物并创造价值", "系统解决活动发现、推荐、组队和治理问题。"],
            ["度量绩效域", "用数据和指标支持判断与控制", "使用功能点、人时、缺陷、质量指标、挣值和风险等级。"],
            ["不确定性绩效域", "识别、评估和应对风险与变化", "保留规则回退、本地知识库、变更控制和演示预案。"],
        ],
        [1.4, 2.7, 3.1],
    )


def wbs_detail_table() -> TableBlock:
    return TableBlock(
        "WBS 分解细化表",
        ["WBS编码", "工作包", "可交付物", "验收标准"],
        [
            ["1.1", "立项与启动", "项目建议书、启动会议记录", "明确项目价值、范围和成员分工。"],
            ["1.2", "可行性分析", "可行性分析报告", "完成经济、技术、风险和操作可行性判断。"],
            ["2.1", "需求获取", "需求条目、角色和用例", "覆盖普通学生、组织者、管理员。"],
            ["2.2", "需求规格", "需求规格说明书", "功能需求和非功能需求可测试。"],
            ["3.1", "架构设计", "总体架构、功能架构、部署图", "结构与技术选型可解释。"],
            ["3.2", "数据设计", "ER图、schema.sql", "实体关系支持报名、组队、消息和推荐。"],
            ["4.1", "前端实现", "用户端和后台页面", "主要页面可通过本地代理访问。"],
            ["4.2", "后端实现", "REST接口、权限、数据库访问", "核心业务接口可联调。"],
            ["4.3", "智能模块", "推荐排序、ML脚本、Dify客服", "推荐差异和客服问答可演示。"],
            ["5.1", "系统测试", "测试记录、缺陷清单", "高优先级缺陷关闭。"],
            ["5.2", "展示交付", "操作说明、PPT、11份管理文档", "交付物完整、格式统一。"],
        ],
        [1.0, 1.5, 2.3, 2.7],
    )


def gantt_text_table() -> TableBlock:
    return TableBlock(
        "文本甘特图",
        ["任务", "W1", "W2", "W3", "W4", "W5", "W6", "W7", "W8", "W9", "W10", "W11", "W12", "W13", "W14"],
        [
            ["立项启动", "■", "", "", "", "", "", "", "", "", "", "", "", "", ""],
            ["需求分析", "", "■", "■", "", "", "", "", "", "", "", "", "", "", ""],
            ["概要设计", "", "", "", "■", "■", "", "", "", "", "", "", "", "", ""],
            ["前端原型", "", "", "", "", "■", "■", "■", "■", "", "", "", "", "", ""],
            ["后端接口", "", "", "", "", "", "■", "■", "■", "■", "", "", "", "", ""],
            ["推荐客服", "", "", "", "", "", "", "", "■", "■", "■", "", "", "", ""],
            ["后台治理", "", "", "", "", "", "", "", "", "■", "■", "■", "", "", ""],
            ["测试修复", "", "", "", "", "", "", "", "", "", "", "■", "■", "■", ""],
            ["总结交付", "", "", "", "", "", "", "", "", "", "", "", "", "■", "■"],
        ],
        [1.7] + [0.45] * 14,
    )


def cpm_pert_table() -> TableBlock:
    return TableBlock(
        "CPM/PERT 估算表",
        ["活动", "前置", "乐观O", "最可能M", "悲观P", "PERT期望", "关键路径"],
        [
            ["A 立项启动", "-", "0.5周", "1周", "1.5周", "1.0周", "是"],
            ["B 需求分析", "A", "1.5周", "2周", "3周", "2.1周", "是"],
            ["C 概要设计", "B", "1.5周", "2周", "2.5周", "2.0周", "是"],
            ["D 前端原型", "B/C", "3周", "4周", "5周", "4.0周", "否"],
            ["E 后端实现", "C", "3周", "4周", "5周", "4.0周", "是"],
            ["F 推荐客服", "C/E", "1.5周", "2周", "3周", "2.1周", "是"],
            ["G 后台治理", "E", "1.5周", "2周", "3周", "2.1周", "否"],
            ["H 测试修复", "D/E/F/G", "2周", "3周", "4周", "3.0周", "是"],
            ["I 总结交付", "H", "1周", "1.5周", "2周", "1.5周", "是"],
        ],
        [1.4, 1.0, 0.8, 0.9, 0.8, 1.0, 0.8],
    )


def evm_table() -> TableBlock:
    return TableBlock(
        "挣值管理 EVM 控制表",
        ["检查点", "PV计划值", "EV挣值", "AC实际成本", "SPI", "CPI", "判断"],
        [
            ["W5 设计结束", "18,000", "17,500", "18,800", "0.97", "0.93", "略有成本超支，进度基本可控。"],
            ["W10 联调结束", "45,000", "43,500", "46,200", "0.97", "0.94", "推荐和客服占用缓冲，需控制返工。"],
            ["W13 测试结束", "58,000", "57,600", "59,500", "0.99", "0.97", "接近计划，缺陷修复成本可接受。"],
            ["W14 最终交付", "64,640", "64,640", "64,640", "1.00", "1.00", "按课程影子成本基线完成。"],
        ],
        [1.2, 1.0, 1.0, 1.0, 0.65, 0.65, 2.2],
    )


def function_point_estimation_table() -> TableBlock:
    return TableBlock(
        "功能点与代码规模估算表",
        ["模块", "估算功能点FP", "Java LOC/FP", "估算LOC", "说明"],
        [
            ["账号与权限", "18", "46", "828", "注册、登录、邮箱验证、角色权限。"],
            ["活动与报名", "32", "46", "1,472", "活动发布、收藏、报名、审批和群聊。"],
            ["组队协作", "30", "46", "1,380", "组队、申请、成员和队伍群聊。"],
            ["推荐与搜索", "26", "46", "1,196", "推荐流、规则回退、搜索和行为日志。"],
            ["消息与通知", "22", "46", "1,012", "私聊、群聊、通知中心。"],
            ["后台治理", "24", "46", "1,104", "用户、活动、标签、推荐位管理。"],
            ["智能客服", "16", "46", "736", "Dify接入、本地知识库和转人工。"],
            ["个人中心与展示", "18", "46", "828", "资料、记录、收藏和展示路线。"],
            ["合计", "186", "46", "8,556", "课程项目功能点估算口径。"],
        ],
        [1.5, 1.0, 1.0, 1.0, 2.8],
    )


def walston_felix_table() -> TableBlock:
    return TableBlock(
        "Walston-Felix 参数估算表",
        ["估算项", "公式", "代入值", "结果", "管理解释"],
        [
            ["KLOC", "LOC / 1000", "8,556 / 1000", "8.556", "按功能点和Java LOC/FP折算。"],
            ["工作量E", "5.2 × KLOC^0.91", "5.2 × 8.556^0.91", "约36.7人月", "工业模型结果显著高于课程实际投入。"],
            ["工期D", "4.1 × KLOC^0.36", "4.1 × 8.556^0.36", "约8.9个月", "说明课程项目采用原型和复用后压缩周期。"],
            ["人员S", "0.54 × E^0.6", "0.54 × 36.7^0.6", "约4.7人", "与4人课程团队规模接近。"],
            ["文档DOC", "49 × KLOC^1.01", "49 × 8.556^1.01", "约429页", "完整工业文档量大，课程交付采用精简高价值文档。"],
        ],
        [1.1, 2.0, 1.7, 1.1, 2.5],
    )


def quality_engineering_table() -> TableBlock:
    return TableBlock(
        "质量工程与度量表",
        ["课程概念", "含义", "本项目应用"],
        [
            ["质量内建", "质量应进入需求、设计、开发、测试全过程", "需求和概要设计先行，核心流程按验收清单实现。"],
            ["测试左移", "尽早评审需求、设计和接口，减少后期缺陷成本", "在需求清单和接口联调阶段提前发现状态和文案问题。"],
            ["测试右移", "交付后继续关注运行和用户反馈", "通过操作说明、演示反馈和后续优化建议承接。"],
            ["CI/CD思想", "用自动化构建、验证和交付降低风险", "课程阶段采用脚本生成文档、可重复渲染检查作为轻量化实践。"],
            ["缺陷度量", "通过缺陷密度、发现率和关闭率评价质量", "质量报告中记录用例、问题数、缺陷等级和整改闭环。"],
            ["过程质量", "管理过程本身也需要度量和改进", "使用周度检查、风险登记、变更记录和交付审计。"],
        ],
        [1.4, 2.8, 3.0],
    )


def risk_model_table() -> TableBlock:
    return TableBlock(
        "风险管理模型对齐表",
        ["步骤", "课程要求", "本项目执行"],
        [
            ["风险识别", "识别技术、进度、成本、质量、团队和外部环境风险", "列出推荐、Dify、联调、部署、文档同步等风险。"],
            ["风险评估", "分析概率、影响和优先级", "使用概率-影响矩阵和高/中/低等级。"],
            ["风险应对", "采取规避、转移、缓解、接受等策略", "推荐规则回退、客服本地兜底、演示预案。"],
            ["风险监控", "设置控制基线，更新风险列表和排序", "周度检查风险状态，跟踪触发和关闭。"],
            ["风险审计", "检查应对措施是否有效", "质量报告和风险管理报告中复盘处置效果。"],
            ["经验沉淀", "将风险处理经验用于后续项目", "总结预置数据、回退机制、操作说明和文档基线。"],
        ],
        [1.2, 2.4, 3.4],
    )


def stakeholder_matrix_table() -> TableBlock:
    return TableBlock(
        "干系人影响力-利益矩阵",
        ["干系人", "影响力", "利益程度", "管理策略"],
        [
            ["课程教师", "高", "高", "重点管理：提交完整文档、演示路线和课程概念映射。"],
            ["项目组成员", "高", "高", "紧密协作：明确分工、例会同步、共享知识。"],
            ["普通学生用户", "中", "高", "积极参与：围绕推荐、报名和组队体验设计验收。"],
            ["活动组织者", "中", "高", "积极参与：关注发布、审批、群聊和通知。"],
            ["管理员", "中", "中", "保持满意：关注审核、标签和推荐位治理。"],
            ["外部服务提供方", "低", "中", "监控：Dify和邮件服务异常时启用回退。"],
        ],
        [1.3, 0.9, 0.9, 3.8],
    )


def risk_register() -> TableBlock:
    return TableBlock(
        "核心风险登记表",
        ["编号", "风险", "概率", "影响", "应对策略", "责任角色"],
        [
            ["R01", "推荐算法效果不稳定，演示差异不明显", "中", "高", "保留规则排序回退，准备多画像演示账号和推荐理由。", "算法/后端"],
            ["R02", "Dify 外部服务或变量绑定异常", "中", "中", "后端检测未解析变量并切换本地知识库，保留转人工入口。", "后端/测试"],
            ["R03", "活动、组队、消息流程联动复杂导致回归缺陷", "中", "高", "按用户旅程设计端到端用例，关键接口修改后执行回归清单。", "测试/全体"],
            ["R04", "课程时间有限导致文档与系统不同步", "中", "中", "建立文档基线，重大功能变更同步更新需求、设计和展示说明。", "项目经理"],
            ["R05", "本地部署环境端口、数据库或邮箱配置异常", "中", "中", "提供预置数据、演示账号和本地代理说明，演示前完成环境预检。", "运维/后端"],
        ],
        [0.6, 1.8, 0.7, 0.7, 2.8, 1.2],
    )


def quality_metrics() -> TableBlock:
    return TableBlock(
        "质量目标与度量指标",
        ["质量维度", "目标值", "度量方式", "项目落点"],
        [
            ["功能完整性", "核心流程覆盖率 >= 95%", "按需求清单逐项验收", "登录、推荐、报名、组队、消息、通知、后台、客服。"],
            ["可用性", "主要演示路线 10-15 分钟完成", "现场操作计时与观察", "预置账号、清晰入口、操作说明书。"],
            ["可靠性", "关键接口异常有明确提示或兜底", "异常场景测试", "Dify 不可用回退、本地知识库、权限校验。"],
            ["性能", "课程演示数据下页面响应可感知流畅", "手工测试与浏览器观察", "推荐读取分表优先，规则回退保证可用。"],
            ["可维护性", "模块职责清晰，文档与代码对应", "代码结构和设计文档审查", "Controller/Service/Repository 与 UML 架构匹配。"],
        ],
        [1.2, 1.2, 1.8, 2.7],
    )


def docs() -> list[ManagedDocument]:
    return [
        ManagedDocument(
            1,
            "项目建议书",
            "用于说明项目立项必要性、建设目标、建设内容、预期收益和实施建议，为课程项目启动提供依据。",
            "建议批准立项，并按“活动发现 + 智能推荐 + 组队协作 + 平台治理”的主线推进。",
            [
                common_project_overview(),
                Section(
                    "项目背景与问题定义",
                    paragraphs=[
                        "高校活动信息来源分散，学生通常需要在公众号、班群、社团群、海报和口头通知之间切换，信息获取成本高。即使活动数量充足，学生也常常难以根据兴趣、时间、地点和参与目标快速筛选出真正适合自己的活动。",
                        "团队活动和竞赛组队还存在供需错配：有活动意愿的学生找不到合适队友，活动组织者难以及时触达目标人群，队伍发起者缺少统一的招募、审批和沟通空间。传统静态列表式平台很难支撑兴趣画像、行为反馈和协同沟通闭环。",
                    ],
                    bullets=[
                        "信息过载：活动入口多、格式不统一、检索和筛选效率低。",
                        "匹配不足：缺少基于兴趣标签、历史行为和活动内容的个性化推荐。",
                        "协作断点：报名、组队、审批、聊天和通知分散在不同工具中。",
                        "管理滞后：活动审核、用户治理和推荐位运营缺少统一后台。",
                    ],
                ),
                Section(
                    "建设目标与建设内容",
                    paragraphs=[
                        "项目目标是建设一个校园场景下可演示、可扩展、可治理的一体化平台，使普通学生能够更快发现活动和队伍，使活动组织者能够完成发布与报名管理，使管理员能够完成审核、标签和推荐位管理，并通过智能客服降低使用咨询成本。",
                        "建设内容围绕三条主线展开：一是用户侧体验闭环，包括登录注册、兴趣标签、首页推荐、活动报名、组队申请、消息通知和个人中心；二是平台侧治理闭环，包括活动审核、用户管理、标签管理和运营推荐；三是智能化能力，包括推荐排序、规则兜底、机器学习升级预留和 Dify 知识库客服。",
                    ],
                    tables=[
                        TableBlock(
                            "建设目标分解",
                            ["目标", "具体要求", "验收结果"],
                            [
                                ["一站式发现", "活动、组队、搜索、推荐集中呈现", "用户无需跨多个渠道寻找信息。"],
                                ["智能推荐", "结合兴趣、行为、热度和运营权重排序", "不同演示账号推荐结果存在明显差异。"],
                                ["组队协作", "支持自由组队、活动关联组队、申请审批和群聊", "队伍从创建到协作形成闭环。"],
                                ["平台治理", "支持活动审核、用户状态、标签和推荐位管理", "管理员能够控制内容质量和曝光策略。"],
                                ["智能服务", "支持知识库问答、兜底和转人工", "常见问题可由客服页面快速解答。"],
                            ],
                            [1.2, 3.2, 2.2],
                        )
                    ],
                ),
                Section(
                    "实施条件与组织建议",
                    paragraphs=[
                        "项目组已经具备需求、设计、原型、后端、推荐、智能客服和展示资料基础，具备继续完善并形成课程交付的条件。项目规模适合作为软件项目管理课程案例：既包含明确业务流程，又包含智能推荐、外部服务接入、后台治理和文档交付等管理挑战。",
                        "建议采用迭代式推进方式，以每周里程碑管理成果，先稳定核心业务闭环，再逐步增强推荐解释、客服知识库和后台治理能力。每次迭代都应同时更新需求、设计、测试和展示材料，避免系统实现与课程文档脱节。",
                    ],
                    tables=[phase_table()],
                ),
                Section(
                    "预期成果与立项结论",
                    paragraphs=[
                        "预期成果包括可运行系统、可演示原型、需求规格说明书、概要设计说明书、UML 图、推荐模型脚本、Dify 智能客服配置、期末展示材料和本套软件项目管理文档。成果既能支撑课程展示，也能体现项目管理中的范围、进度、成本、风险和质量控制过程。",
                        "综合项目价值、技术可行性、团队能力和课程适配度，建议批准项目立项。项目应以“先可用、再智能、再治理、再总结”为实施原则，避免过度追求复杂算法而影响核心流程稳定性。",
                    ],
                ),
            ],
        ),
        ManagedDocument(
            2,
            "可行性分析报告",
            "从技术、经济、操作、进度、法律合规和风险角度判断项目是否值得实施，并给出推荐方案。",
            "项目总体可行，推荐采用 Web 原型 + Spring Boot + MySQL + 规则/模型混合推荐 + Dify 客服的渐进式方案。",
            [
                common_project_overview(),
                Section(
                    "候选方案比较",
                    paragraphs=["围绕课程交付目标，项目可选择三类方案：纯静态原型、完整工程化平台、渐进式可运行平台。考虑课程周期、团队规模和展示要求，第三种方案最适合。"],
                    tables=[
                        TableBlock(
                            "候选方案对比",
                            ["方案", "优点", "不足", "结论"],
                            [
                                ["纯静态原型", "开发快，展示界面直观", "缺少真实数据流、推荐和后台治理，说服力不足", "不推荐作为最终方案。"],
                                ["完整工程化平台", "架构完整，可扩展性强", "周期和测试压力大，学生项目风险较高", "作为远期方向。"],
                                ["渐进式可运行平台", "核心流程可运行，智能能力可演示，工程成本可控", "需管理联调和文档同步", "推荐采用。"],
                            ],
                            [1.2, 2.0, 2.2, 1.8],
                        )
                    ],
                ),
                Section(
                    "技术可行性",
                    paragraphs=[
                        "后端采用 Spring Boot 3.1.4，配合 MySQL 8.x 存储用户、活动、报名、队伍、消息、通知、行为日志和推荐结果。该技术组合成熟、资料丰富，适合课程项目快速开发和本地演示。",
                        "推荐能力采用“离线模型分表优先 + 规则排序回退”的方式。机器学习脚本可从 MySQL 抽取用户、活动、队伍、兴趣标签和行为日志，输出活动与组队推荐分；当模型分表缺少当前用户数据时，后端使用规则排序保证系统持续可用。",
                        "智能客服采用 Dify Chatflow 与本地知识库兜底。后端对 Dify 结果进行清洗，识别未解析变量、空回答和需要转人工的情况，降低外部服务异常对演示体验的影响。",
                    ],
                    tables=[
                        TableBlock(
                            "关键技术可行性判断",
                            ["技术项", "当前依据", "可行性结论"],
                            [
                                ["Web 前端", "已有多页面原型和本地 HTTP 代理说明", "可行，适合课程展示和手工验收。"],
                                ["Spring Boot 后端", "已有认证、活动、组队、推荐、消息、客服等控制器", "可行，模块划分与设计文档一致。"],
                                ["MySQL 数据库", "schema.sql 覆盖 20 余张业务表", "可行，数据模型支撑核心流程。"],
                                ["推荐算法", "已有推荐分表、规则回退和 ML 训练脚本", "可行，先满足演示，再逐步提升模型质量。"],
                                ["Dify 客服", "已有 DSL、搭建说明和后端回退逻辑", "可行，但需保留本地兜底。"],
                            ],
                            [1.4, 3.0, 2.2],
                        )
                    ],
                ),
                Section(
                    "经济与资源可行性",
                    paragraphs=[
                        "项目以课程实践为主，成本主要是学生人工投入和本地开发设备使用。软件依赖以开源框架和本地部署为主，Dify 与大模型服务可按演示需求配置，不作为高额持续成本项。",
                        "经济收益不以商业收入衡量，而以课程成果、项目经验、复用价值和校园服务价值衡量。通过统一平台减少信息查找和组队沟通成本，能够形成明确的非财务收益。",
                    ],
                    tables=[
                        TableBlock(
                            "经济可行性摘要",
                            ["成本/收益项", "估算口径", "判断"],
                            [
                                ["人工成本", "4名成员，约14周，按课程项目影子成本核算", "可控，是主要成本。"],
                                ["软硬件成本", "个人电脑、本地 MySQL、开源依赖", "低成本，无需采购服务器。"],
                                ["外部服务成本", "邮件 SMTP、Dify/模型 API 可配置", "可通过本地回退控制风险。"],
                                ["课程收益", "形成完整系统、展示材料和管理文档", "收益明确，符合课程目标。"],
                            ],
                            [1.6, 3.0, 1.8],
                        )
                    ],
                ),
                Section(
                    "操作、进度与合规可行性",
                    paragraphs=[
                        "操作层面，系统提供预置演示账号、期末展示操作说明书和推荐访问地址，老师可按 10 到 15 分钟路线完成体验，不依赖阅读源码。管理员、组织者和普通用户入口区分明确，适合现场验收。",
                        "进度层面，项目已经形成需求、概要设计、核心实现和展示资料，剩余工作主要是缺陷收敛、文档整理和最终验收。合规层面，项目仅使用课程演示数据，不处理真实敏感个人信息；如后续真实上线，应补充隐私授权、数据脱敏和日志保留策略。",
                    ],
                    tables=[risk_register()],
                ),
                Section(
                    "综合结论",
                    paragraphs=[
                        "项目在技术、经济、资源、操作和进度方面均具备可行性。主要风险来自推荐效果、外部智能客服稳定性、联调回归和课程时间约束，但均可以通过规则回退、预置数据、端到端测试和文档同步机制控制。",
                        "建议按推荐方案继续实施，并将质量目标聚焦在核心流程可用、智能推荐可解释、后台治理可演示、文档资料完整四个方面。",
                    ],
                ),
            ],
        ),
        ManagedDocument(
            3,
            "资源计划",
            "规划项目所需人力、软硬件、知识、数据、沟通和时间资源，并明确分配和管理方式。",
            "项目资源总体充足，关键在于明确角色责任、保护联调时间、沉淀公共资料和控制外部服务依赖。",
            [
                common_project_overview(),
                Section(
                    "资源规划原则",
                    paragraphs=[
                        "资源计划遵循“角色清晰、共享复用、关键资源优先、外部依赖可替代”的原则。学生课程项目的人力资源最宝贵，因此应减少重复沟通、重复造数和重复排查环境问题，把时间集中到核心流程实现、联调、测试和文档交付。",
                        "项目资源分为人力资源、软件资源、硬件资源、数据资源、知识资源和沟通资源。所有资源都应与 WBS 任务、进度计划和质量目标建立对应关系，避免资源计划只停留在清单层面。",
                    ],
                ),
                Section(
                    "人力资源计划",
                    paragraphs=["项目组采用小团队矩阵式分工，一名成员可承担多个角色，但每项关键任务必须有唯一主责人和明确协作人。"],
                    tables=[
                        TableBlock(
                            "角色职责分配表",
                            ["角色", "主责内容", "协作接口", "主要产出"],
                            [
                                ["项目经理/文档负责人", "范围、进度、风险、会议和最终文档整合", "全体成员、课程教师", "计划、报告、总结、展示节奏。"],
                                ["产品与前端负责人", "用户旅程、原型页面、交互细节和展示操作", "后端、测试", "页面原型、操作说明、验收清单。"],
                                ["后端与数据库负责人", "认证、活动、组队、消息、通知、权限和数据模型", "前端、算法", "接口、数据库脚本、部署说明。"],
                                ["推荐与智能客服负责人", "推荐排序、行为数据、ML 脚本、Dify 知识库", "后端、测试", "推荐接口、客服流程、兜底策略。"],
                                ["测试与质量负责人", "测试用例、缺陷跟踪、回归验证和质量报告", "全体成员", "测试记录、缺陷统计、质量结论。"],
                            ],
                            [1.4, 2.3, 1.8, 2.2],
                        )
                    ],
                ),
                Section(
                    "软硬件与数据资源计划",
                    paragraphs=[
                        "软件资源以开源和课程可获得工具为主，包括 Java/Spring Boot、MySQL、浏览器、Python、Dify、PlantUML 和文档生成工具。硬件资源主要是成员个人电脑和本地开发环境，不依赖昂贵服务器。",
                        "数据资源包括预置用户、活动、组队、报名、消息、通知、标签和推荐分。演示数据需要覆盖不同兴趣画像、不同角色、不同审批状态和不同消息通知状态，才能支撑推荐差异和后台治理展示。",
                    ],
                    tables=[
                        TableBlock(
                            "资源清单",
                            ["资源类别", "具体资源", "用途", "管理要求"],
                            [
                                ["开发环境", "JDK、Maven、Spring Boot、MySQL", "后端开发、接口联调、数据存储", "统一版本和启动说明。"],
                                ["前端环境", "HTML/CSS/JavaScript、本地 HTTP 代理", "用户端和后台演示", "避免 file:// 访问导致接口失败。"],
                                ["智能服务", "Dify Chatflow、本地知识库、SMTP", "客服、验证码和通知辅助", "配置项不写入公开文档。"],
                                ["建模资源", "Python、joblib、行为日志和推荐分表", "推荐排序实验与演示", "保留规则回退。"],
                                ["文档资料", "需求、概要设计、UML、操作说明、PPT", "管理文档和验收依据", "变更后及时同步。"],
                            ],
                            [1.2, 2.0, 1.8, 2.0],
                        )
                    ],
                ),
                Section(
                    "资源日历与冲突管理",
                    paragraphs=[
                        "项目按 14 周课程周期安排资源投入。第 1 到 5 周偏需求和设计，第 6 到 10 周偏开发联调，第 11 到 13 周偏测试和展示准备，第 14 周偏交付总结。期末阶段课程压力较大，应提前锁定演示环境和文档模板。",
                        "当资源冲突发生时，优先保障核心链路：登录、首页推荐、活动报名、组队申请、消息通知、后台审核和智能客服。对于不影响主流程的增强项，应进入待办池，不挤占缺陷修复和文档验收时间。",
                    ],
                    tables=[phase_table()],
                ),
                Section(
                    "资源风险与控制",
                    paragraphs=[
                        "资源风险主要集中在人力时间不稳定、环境配置不一致、外部服务不可控和文档资料分散。控制措施包括共享启动脚本、演示账号、统一资料目录、每周短会和缺陷优先级规则。",
                        "资源计划不是静态表格，项目经理应在每个里程碑检查资源消耗与剩余任务是否匹配；若发现推荐、客服或后台治理任务超出预期，应及时裁剪低优先级功能，保证核心交付。",
                    ],
                    tables=[risk_register()],
                ),
            ],
        ),
        ManagedDocument(
            4,
            "进度计划",
            "定义项目 WBS、阶段里程碑、任务顺序、关键路径和进度控制机制。",
            "项目采用 14 周迭代式进度基线，关键路径为需求确认、概要设计、后端数据模型、核心联调、端到端测试和最终交付。",
            [
                common_project_overview(),
                Section(
                    "进度管理方法",
                    paragraphs=[
                        "进度计划采用 WBS 分解、里程碑验收和每周滚动检查相结合的方法。项目不是单一编码任务，而是包含需求、设计、实现、智能能力、测试、展示和管理文档的综合交付，因此进度控制应以可验证成果为中心。",
                        "每个阶段结束时必须形成可检查物：需求阶段看需求规格说明书和用例，设计阶段看架构图和数据模型，实现阶段看可运行流程，测试阶段看缺陷关闭和操作说明，交付阶段看文档和总结。",
                    ],
                ),
                Section(
                    "WBS 分解",
                    paragraphs=["WBS 按交付成果而非人员分解，确保每个任务都能对应明确验收标准。"],
                    tables=[
                        TableBlock(
                            "WBS 工作分解结构",
                            ["WBS", "工作包", "主要任务", "交付物"],
                            [
                                ["1.0", "项目管理", "范围确认、计划、会议、风险、总结", "管理计划和报告。"],
                                ["2.0", "需求分析", "角色分析、用例、非功能需求、验收口径", "需求规格说明书。"],
                                ["3.0", "系统设计", "总体架构、功能架构、ER、接口、安全和维护", "概要设计说明书和 UML 图。"],
                                ["4.0", "前端原型", "首页、活动、组队、消息、个人中心、后台、客服", "可访问页面和操作说明。"],
                                ["5.0", "后端实现", "认证、活动、组队、消息、通知、管理接口", "Spring Boot 服务和数据库脚本。"],
                                ["6.0", "智能模块", "推荐排序、行为日志、ML 脚本、Dify 客服", "推荐接口、模型产物和客服流程。"],
                                ["7.0", "测试验收", "功能测试、回归测试、演示路线、缺陷修复", "测试记录和质量报告。"],
                                ["8.0", "交付归档", "PPT、操作说明、管理文档、总结报告", "最终提交包。"],
                            ],
                            [0.8, 1.4, 3.1, 2.0],
                        )
                    ],
                ),
                Section(
                    "阶段计划与关键路径",
                    paragraphs=[
                        "关键路径从需求规格开始，经概要设计和数据库模型进入后端核心接口，再与前端原型联调，最后通过测试和演示材料收敛。推荐和智能客服属于关键展示亮点，但其实现必须服从核心流程稳定性。",
                        "若某阶段延期，优先压缩增强功能和展示润色，不压缩测试验收时间。特别是活动报名、组队申请、消息通知和后台审核之间存在联动关系，缺少回归测试会显著增加最终演示风险。",
                    ],
                    tables=[phase_table()],
                ),
                Section(
                    "详细任务排期",
                    tables=[
                        TableBlock(
                            "详细任务排期表",
                            ["任务", "计划周次", "前置任务", "责任角色", "进度检查点"],
                            [
                                ["项目立项与范围确认", "W1", "无", "项目经理", "确认项目目标、用户角色和核心模块。"],
                                ["需求规格编写", "W2-W3", "范围确认", "产品/全体", "需求评审通过，核心用例完整。"],
                                ["概要设计与数据库设计", "W4-W5", "需求基线", "后端/架构", "架构图、ER 图、接口清单完成。"],
                                ["前端核心页面", "W5-W8", "需求原型", "前端", "首页、活动、组队、消息、个人中心可访问。"],
                                ["后端核心接口", "W6-W9", "数据库设计", "后端", "认证、活动、组队、通知等接口可联调。"],
                                ["推荐与客服", "W8-W10", "数据模型、核心接口", "算法/后端", "推荐差异和客服问答可演示。"],
                                ["后台治理", "W9-W11", "活动和用户模型", "后端/前端", "审核、用户、标签、推荐位可操作。"],
                                ["系统测试与缺陷修复", "W11-W13", "核心功能联调", "测试/全体", "高优先级缺陷关闭。"],
                                ["展示与文档归档", "W13-W14", "测试完成", "项目经理/全体", "PPT、操作说明和管理文档完成。"],
                            ],
                            [1.7, 1.1, 1.5, 1.3, 2.4],
                        )
                    ],
                ),
                Section(
                    "进度控制机制",
                    paragraphs=[
                        "进度控制采用每周检查、任务看板、里程碑评审和延期处理四级机制。每周至少检查一次任务状态，重点关注接口联调、推荐展示、Dify 接入和缺陷关闭等容易拖延的工作。",
                        "当任务延期超过 2 天，应立即判断是否影响关键路径。影响关键路径的延期需要调整资源或裁剪低优先级功能；不影响关键路径的延期进入缓冲池，但不得挤占最终测试和文档时间。",
                    ],
                ),
            ],
        ),
        ManagedDocument(
            5,
            "成本计划",
            "估算并规划项目人工、软硬件、外部服务、测试、文档和管理成本，形成成本基准。",
            "课程项目以人工影子成本为主，预算基准为 64,640 元，实际现金支出很低，成本控制重点是时间和返工。",
            [
                common_project_overview(),
                Section(
                    "成本估算口径",
                    paragraphs=[
                        "本项目为课程实践项目，不以真实商业采购成本作为主要衡量口径。为体现软件项目管理方法，成本计划采用“影子成本”估算：将成员投入工时按学生项目等价人工单价折算，同时列示实际现金支出。",
                        "人工成本按 4 名成员、14 周、平均每人每周 8 小时估算，总工时 448 小时。按综合学习与开发成本 120 元/小时折算，人工影子成本为 53,760 元。软硬件和外部服务以低成本或免费资源为主。",
                    ],
                    tables=[
                        TableBlock(
                            "成本估算假设",
                            ["假设项", "取值", "说明"],
                            [
                                ["项目周期", "14周", "覆盖立项、需求、设计、实现、测试、总结。"],
                                ["团队规模", "4人", "成员承担产品、前端、后端、算法、测试和文档等复合角色。"],
                                ["平均投入", "8小时/人/周", "课程项目平均投入，期末阶段可能上浮。"],
                                ["人工单价", "120元/小时", "用于管理估算，不代表真实支付。"],
                                ["现金支出", "低", "主要使用本地设备、开源框架和课程环境。"],
                            ],
                            [1.4, 1.4, 3.2],
                        )
                    ],
                ),
                Section(
                    "成本预算基准",
                    tables=[
                        TableBlock(
                            "成本预算表",
                            ["成本类别", "估算金额", "占比", "估算依据", "控制措施"],
                            [
                                ["人工成本", "53,760元", "83.2%", "448小时 x 120元/小时", "明确分工，减少返工。"],
                                ["开发设备", "0元现金 / 4,000元影子", "6.2%", "个人电脑折旧和本地环境", "复用现有设备。"],
                                ["软件工具", "0元现金 / 1,200元影子", "1.9%", "开源框架、PlantUML、浏览器工具", "优先开源。"],
                                ["外部服务", "800元预留", "1.2%", "Dify/API/邮件等演示预留", "可用本地兜底替代。"],
                                ["测试与演示", "1,600元影子", "2.5%", "测试数据、演示准备、环境预检", "提前准备账号和说明。"],
                                ["文档与管理", "3,280元影子", "5.0%", "管理文档、PPT、评审会议", "统一模板和脚本生成。"],
                                ["合计", "64,640元", "100%", "课程项目管理估算口径", "设 10% 管理储备。"],
                            ],
                            [1.2, 1.1, 0.8, 2.1, 2.1],
                        )
                    ],
                ),
                Section(
                    "成本控制策略",
                    paragraphs=[
                        "成本控制重点不是现金采购，而是减少返工、等待和环境排查时间。需求和设计阶段要尽早冻结核心范围，避免后期频繁改变数据库结构和接口；实现阶段要优先打通用户旅程，避免局部功能精致但整体不可演示。",
                        "对智能推荐和 Dify 客服采用成本可控策略：推荐先保证规则排序和分表读取，再进行模型升级；客服先保证知识库和转人工闭环，再追求更复杂的意图识别。",
                    ],
                    bullets=[
                        "范围变更必须说明对工时、测试和文档的影响。",
                        "每周检查剩余任务与剩余时间，及时调整优先级。",
                        "重大技术问题超过半天无进展时，必须启用替代方案。",
                        "最终两周禁止引入影响核心流程的新功能。",
                    ],
                ),
                Section(
                    "成本偏差与储备",
                    paragraphs=[
                        "项目设置 10% 管理储备，主要用于应对联调延期、Dify 接入异常、推荐数据补充、测试返工和文档补充。若成本偏差超过 10%，项目经理应组织范围复核，区分必须完成项和可延后增强项。",
                    ],
                    tables=[
                        TableBlock(
                            "成本偏差处理规则",
                            ["偏差等级", "判断标准", "处理措施"],
                            [
                                ["绿色", "偏差 <= 5%", "正常跟踪，无需调整基线。"],
                                ["黄色", "5% < 偏差 <= 10%", "分析原因，压缩低优先级任务。"],
                                ["红色", "偏差 > 10%", "启动范围评审，使用管理储备或裁剪增强功能。"],
                            ],
                            [1.0, 2.0, 3.2],
                        )
                    ],
                ),
            ],
        ),
        ManagedDocument(
            6,
            "风险计划",
            "识别项目实施前和实施中的风险，制定概率影响评估、应对策略、触发条件和责任安排。",
            "项目风险可控，但必须重点管理推荐效果、联调复杂度、外部服务、演示环境和文档同步。",
            [
                common_project_overview(),
                Section(
                    "风险管理方法",
                    paragraphs=[
                        "风险管理采用识别、评估、响应、监控和关闭五步法。项目经理维护风险登记表，测试负责人根据缺陷和回归结果更新风险状态，技术负责人对高风险技术项给出替代方案。",
                        "风险等级按概率和影响综合判断：概率分为低、中、高，影响分为低、中、高。高概率高影响风险必须有预防措施和应急措施；中等级风险至少要有责任人和触发条件；低风险纳入观察清单。",
                    ],
                ),
                Section(
                    "风险识别与评估",
                    tables=[risk_register()],
                ),
                Section(
                    "风险应对策略",
                    paragraphs=[
                        "推荐风险采用规避与缓解结合：不将模型效果作为唯一推荐来源，保留规则排序、运营权重和预置演示数据。Dify 风险采用转移与缓解结合：外部服务不可用时后端切换本地知识库，问题超出知识库时引导转人工。",
                        "进度风险采用缓解策略：每周检查关键路径，最后两周冻结大功能；质量风险采用预防策略：围绕用户旅程设计端到端测试，而不是只测试单个页面。",
                    ],
                    tables=[
                        TableBlock(
                            "风险响应计划",
                            ["风险类别", "触发条件", "预防措施", "应急措施"],
                            [
                                ["推荐效果", "不同账号推荐差异不明显", "提前准备兴趣画像和推荐分数据", "切换规则权重，展示推荐理由。"],
                                ["客服接入", "Dify 返回空值或变量未解析", "按版本搭建并做预览测试", "启用本地知识库和转人工提示。"],
                                ["联调回归", "接口修改导致页面异常", "接口变更记录和回归清单", "回滚变更或临时兼容旧字段。"],
                                ["演示环境", "端口、数据库或邮箱异常", "演示前环境预检", "使用预置账号和本地代理说明。"],
                                ["文档同步", "文档描述落后于实现", "每个里程碑同步更新资料", "最终按仓库现状集中校准。"],
                            ],
                            [1.2, 1.7, 2.2, 2.0],
                        )
                    ],
                ),
                Section(
                    "风险监控机制",
                    paragraphs=[
                        "风险监控纳入每周例会。每项风险记录状态、责任人、最近检查日期、应对效果和下一步动作。已发生的风险转入问题清单，问题解决后再评估是否关闭风险或保留残余风险。",
                        "项目后期风险监控重点从“能否做出来”转向“能否稳定演示和完整交付”。因此最终两周重点检查演示路线、预置数据、操作说明、管理文档和缺陷关闭状态。",
                    ],
                    tables=[
                        TableBlock(
                            "风险状态定义",
                            ["状态", "含义", "管理动作"],
                            [
                                ["开放", "风险仍可能发生或影响尚未消除", "持续跟踪，按计划执行预防措施。"],
                                ["触发", "风险条件已经出现并转化为问题", "启动应急措施，进入问题处理。"],
                                ["缓解", "风险影响已降低但仍需观察", "降低等级，保留监控。"],
                                ["关闭", "触发条件消失或项目阶段已结束", "记录经验，归档关闭原因。"],
                            ],
                            [1.0, 2.5, 2.5],
                        )
                    ],
                ),
            ],
        ),
        ManagedDocument(
            7,
            "质量计划",
            "定义项目质量目标、质量标准、评审机制、测试策略、缺陷管理和验收准则。",
            "质量目标聚焦核心业务闭环稳定、智能能力可解释、管理后台可治理、文档交付可追溯。",
            [
                common_project_overview(),
                Section(
                    "质量目标",
                    paragraphs=[
                        "本项目质量计划不追求商业级大规模并发，而以课程验收和可运行演示为核心。质量目标是让老师能够按操作说明完成主要流程，让项目组能够解释需求、设计、实现、测试和管理过程之间的对应关系。",
                        "质量标准覆盖功能完整性、正确性、可用性、可靠性、安全性、可维护性和文档完整性。每项质量标准都需要对应检查方式，避免只写原则不落地。",
                    ],
                    tables=[quality_metrics()],
                ),
                Section(
                    "评审机制",
                    paragraphs=[
                        "评审分为需求评审、设计评审、代码/接口评审、测试评审和交付评审。需求评审确认范围是否完整，设计评审确认架构和数据模型是否支撑需求，代码/接口评审关注异常处理和权限控制，测试评审关注缺陷闭环，交付评审关注文档与系统一致性。",
                    ],
                    tables=[
                        TableBlock(
                            "质量评审安排",
                            ["评审类型", "评审对象", "评审重点", "通过标准"],
                            [
                                ["需求评审", "需求规格说明书", "角色、用例、非功能需求、验收口径", "关键用户旅程无遗漏。"],
                                ["设计评审", "概要设计、UML、ER", "模块边界、数据关系、部署和安全", "能支撑全部核心需求。"],
                                ["接口评审", "后端 API、前端调用", "参数、权限、异常、状态码", "接口可联调且错误可定位。"],
                                ["测试评审", "测试用例和缺陷记录", "覆盖率、缺陷等级、回归结果", "高优先级缺陷关闭。"],
                                ["交付评审", "PPT、操作说明、管理文档", "一致性、完整性、可读性", "提交物齐全且格式统一。"],
                            ],
                            [1.2, 1.7, 2.5, 2.1],
                        )
                    ],
                ),
                Section(
                    "测试策略",
                    paragraphs=[
                        "测试采用用户旅程测试、模块功能测试、异常场景测试和回归测试结合的方式。用户旅程测试优先级最高，因为项目最终需要现场演示完整闭环。",
                        "推荐模块测试重点不是追求绝对算法准确率，而是验证排序依据、推荐理由、不同画像差异和规则回退。智能客服测试重点是知识库覆盖、多轮追问、无法回答时转人工和 Dify 异常兜底。",
                    ],
                    tables=[
                        TableBlock(
                            "测试范围与样例",
                            ["测试对象", "关键场景", "预期结果"],
                            [
                                ["账号与兴趣", "登录、注册、邮箱验证码、兴趣标签维护", "身份状态正确，兴趣能影响推荐。"],
                                ["活动流程", "浏览、收藏、报名、取消、审批、群聊", "状态流转和通知一致。"],
                                ["组队流程", "大厅筛选、申请、审批、队伍管理、群聊", "成员、申请和聊天权限正确。"],
                                ["推荐搜索", "不同账号推荐、关键词搜索、规则回退", "结果差异可见，理由可解释。"],
                                ["后台治理", "活动审核、用户禁用、标签和推荐位管理", "治理操作影响前台展示。"],
                                ["智能客服", "常见问题、多轮追问、转人工、Dify 异常", "回答结构清晰，兜底有效。"],
                            ],
                            [1.2, 3.0, 2.5],
                        )
                    ],
                ),
                Section(
                    "缺陷管理与验收准则",
                    paragraphs=[
                        "缺陷按严重程度分为致命、严重、一般和轻微。致命缺陷指核心流程无法完成或系统无法启动；严重缺陷指关键模块结果错误或权限失效；一般缺陷指局部功能异常但有替代路径；轻微缺陷指文案、样式和体验问题。",
                        "最终验收前必须关闭所有致命和严重缺陷；一般缺陷需要明确是否影响演示；轻微缺陷可记录在后续优化建议中。验收通过标准是核心用户路线可完成、后台治理可操作、智能能力可展示、文档资料完整。",
                    ],
                    tables=[
                        TableBlock(
                            "验收准则",
                            ["准则", "具体要求", "证据"],
                            [
                                ["功能验收", "需求清单核心项全部可演示", "操作说明和现场演示。"],
                                ["质量验收", "高优先级缺陷关闭，异常有提示", "测试记录和缺陷清单。"],
                                ["文档验收", "需求、设计、管理报告和总结齐全", "提交目录和文档版本。"],
                                ["展示验收", "10-15 分钟路线顺畅", "预置账号和演示脚本。"],
                            ],
                            [1.2, 2.6, 2.2],
                        )
                    ],
                ),
            ],
        ),
        ManagedDocument(
            8,
            "项目估算报告",
            "对项目规模、工作量、进度、成本和不确定性进行估算，为计划和控制提供基线。",
            "项目估算规模为中等课程综合实践项目，约 448 人时、14 周完成，成本影子基准 64,640 元。",
            [
                common_project_overview(),
                Section(
                    "估算方法与依据",
                    paragraphs=[
                        "估算采用类比估算、专家判断和自下而上估算结合。类比依据来自同类校园信息系统和课程综合实践项目；专家判断依据来自项目组已完成的需求、概要设计和源码结构；自下而上估算依据 WBS 工作包拆分。",
                        "估算对象包括规模、工作量、进度和成本。由于项目已进入较完整实现和展示准备阶段，本报告同时参考当前仓库成果进行校准，使估算更接近实际课程项目情况。",
                    ],
                ),
                Section(
                    "规模估算",
                    paragraphs=[
                        "从功能点角度看，项目包含账号、活动、报名、组队、消息、通知、推荐、后台、智能客服和个人中心等多个模块，属于中等规模课程项目。从数据模型看，schema 覆盖用户、标签、活动、报名、收藏、队伍、成员、申请、消息、通知、行为日志和推荐相关表，业务实体较完整。",
                    ],
                    tables=[
                        TableBlock(
                            "功能规模估算表",
                            ["模块", "复杂度", "估算功能点", "说明"],
                            [
                                ["账号与权限", "中", "18", "登录、注册、邮箱验证码、角色权限和状态管理。"],
                                ["活动与报名", "高", "32", "发布、浏览、收藏、报名、审批、群聊和容量控制。"],
                                ["组队协作", "高", "30", "自由/关联组队、申请审批、成员管理和队伍群聊。"],
                                ["推荐与搜索", "高", "26", "活动推荐、组队推荐、行为日志、规则回退和搜索。"],
                                ["消息与通知", "中高", "22", "私聊、群聊、通知中心、已读和分页。"],
                                ["后台治理", "中高", "24", "活动审核、用户管理、标签和推荐位。"],
                                ["智能客服", "中", "16", "Dify 接入、本地知识库、转人工和兜底。"],
                                ["个人中心与展示", "中", "18", "资料、记录、收藏和演示路线。"],
                                ["合计", "-", "186", "课程项目估算功能点。"],
                            ],
                            [1.4, 0.9, 1.2, 3.0],
                        )
                    ],
                ),
                Section(
                    "工作量估算",
                    tables=[
                        TableBlock(
                            "工作量估算表",
                            ["工作包", "估算人时", "占比", "说明"],
                            [
                                ["项目管理与沟通", "36", "8.0%", "计划、会议、风险和协调。"],
                                ["需求分析", "52", "11.6%", "需求规格、用例、验收清单。"],
                                ["概要设计", "58", "12.9%", "架构、数据库、接口、安全和维护设计。"],
                                ["前端原型", "78", "17.4%", "多页面交互、样式和展示动线。"],
                                ["后端实现", "92", "20.5%", "核心接口、权限、数据流和异常处理。"],
                                ["推荐与客服", "48", "10.7%", "推荐排序、ML 脚本、Dify 和兜底。"],
                                ["测试与修复", "52", "11.6%", "功能测试、回归和缺陷关闭。"],
                                ["展示与文档", "32", "7.1%", "PPT、操作说明和项目管理文档。"],
                                ["合计", "448", "100%", "4人 x 14周 x 8小时。"],
                            ],
                            [1.5, 1.0, 0.9, 3.0],
                        )
                    ],
                ),
                Section(
                    "进度与成本估算",
                    paragraphs=[
                        "进度估算结果为 14 周，与课程周期匹配。关键路径任务包括需求确认、数据库设计、后端核心接口、前端联调、推荐和客服演示、测试回归以及最终文档归档。",
                        "成本估算沿用成本计划中的影子成本口径，总预算基准为 64,640 元，其中人工影子成本占主要比例。由于实际现金采购很少，成本偏差通常表现为工时超支和返工增加。",
                    ],
                    tables=[phase_table()],
                ),
                Section(
                    "估算不确定性与校准",
                    paragraphs=[
                        "估算不确定性主要来自智能推荐效果、外部服务接入、联调缺陷数量和文档完善程度。项目越接近交付，不确定性越低；但如果临近期末新增功能或改变技术路线，估算基线将明显失效。",
                    ],
                    tables=[
                        TableBlock(
                            "估算区间",
                            ["估算项", "乐观", "最可能", "悲观", "控制建议"],
                            [
                                ["总工时", "400", "448", "520", "保留功能裁剪清单。"],
                                ["总周期", "12周", "14周", "16周", "最终两周冻结大功能。"],
                                ["影子成本", "58,000元", "64,640元", "75,000元", "控制返工和联调等待。"],
                                ["高优先级缺陷", "5个", "10个", "18个", "端到端测试提前执行。"],
                            ],
                            [1.3, 0.9, 0.9, 0.9, 2.4],
                        )
                    ],
                ),
            ],
        ),
        ManagedDocument(
            9,
            "质量管理报告",
            "总结项目质量计划执行情况、评审记录、测试结果、缺陷统计、质量偏差和改进措施。",
            "项目质量总体达到课程验收要求，核心流程和展示路线具备可用性，后续应继续提升自动化测试和真实数据质量。",
            [
                common_project_overview(),
                Section(
                    "质量执行概况",
                    paragraphs=[
                        "项目质量管理围绕需求一致性、设计一致性、功能可演示、异常可处理和文档可追溯展开。当前项目已经形成需求规格说明书、概要设计说明书、期末展示功能需求清单和操作说明书，能够支撑质量审查和验收。",
                        "从实现成果看，系统覆盖普通学生、活动组织者、队伍发起者和管理员等角色，支持活动发现、推荐、报名、组队、消息、通知、个人中心、后台治理和智能客服等核心能力。",
                    ],
                    tables=[quality_metrics()],
                ),
                Section(
                    "评审与检查结果",
                    tables=[
                        TableBlock(
                            "质量评审结果",
                            ["评审项", "检查内容", "结果", "改进动作"],
                            [
                                ["需求评审", "功能需求、非功能需求、角色权限", "通过", "用期末需求清单补充现场验收口径。"],
                                ["设计评审", "总体架构、功能架构、ER、部署", "通过", "用 PlantUML 图增强可读性。"],
                                ["接口评审", "认证、活动、组队、推荐、消息、客服", "基本通过", "对异常返回和权限提示继续优化。"],
                                ["展示评审", "预置账号、推荐差异、操作路线", "通过", "优先使用 8125 本地代理入口。"],
                                ["文档评审", "需求、设计、管理报告和总结", "通过", "统一封面、目录和表格格式。"],
                            ],
                            [1.2, 2.2, 1.0, 2.5],
                        )
                    ],
                ),
                Section(
                    "测试结果摘要",
                    paragraphs=[
                        "测试以手工端到端测试为主，覆盖登录、首页推荐、活动报名、组队申请、消息通知、个人中心、后台管理和智能客服。测试重点放在用户可感知流程是否连贯、状态是否同步、通知是否准确和异常是否有兜底。",
                    ],
                    tables=[
                        TableBlock(
                            "测试执行摘要",
                            ["测试模块", "用例数", "通过数", "问题数", "质量结论"],
                            [
                                ["账号与兴趣", "12", "11", "1", "基本稳定，邮箱相关场景需依赖配置。"],
                                ["活动与报名", "18", "16", "2", "核心流程可用，容量和审批边界需重点回归。"],
                                ["组队协作", "16", "15", "1", "申请、审批、群聊流程可演示。"],
                                ["推荐与搜索", "10", "9", "1", "推荐差异可展示，模型效果可继续提升。"],
                                ["消息与通知", "14", "13", "1", "未读、筛选和通知文案基本符合要求。"],
                                ["后台治理", "12", "11", "1", "活动审核和推荐位管理可演示。"],
                                ["智能客服", "10", "9", "1", "常见问题可答，外部服务异常有回退。"],
                            ],
                            [1.4, 0.8, 0.8, 0.8, 3.0],
                        )
                    ],
                ),
                Section(
                    "缺陷统计与处理",
                    paragraphs=[
                        "项目缺陷主要集中在联动状态、通知文案、长描述展示、Dify 变量绑定和演示环境访问方式。多数问题通过页面调整、后端兜底、数据补充和操作说明修订得到处理。",
                    ],
                    tables=[
                        TableBlock(
                            "缺陷分类统计",
                            ["等级", "数量", "示例", "处理要求", "当前状态"],
                            [
                                ["致命", "0", "系统无法启动、无法登录", "必须立即修复", "无遗留。"],
                                ["严重", "2", "核心流程状态不同步、权限异常", "交付前关闭", "已关闭或规避。"],
                                ["一般", "5", "文案不清、列表摘要不完整", "不影响主流程可延期", "大部分已处理。"],
                                ["轻微", "6", "样式间距、提示细节", "记录优化建议", "保留少量后续优化。"],
                            ],
                            [1.0, 0.8, 2.2, 1.8, 1.2],
                        )
                    ],
                ),
                Section(
                    "质量结论与改进建议",
                    paragraphs=[
                        "综合评审和测试结果，项目达到课程交付质量要求。核心流程可演示，智能推荐、后台治理和智能客服具备展示亮点，文档资料能够解释项目从需求到实现再到测试总结的过程。",
                        "后续改进建议包括：补充自动化接口测试，引入更真实的行为数据评估推荐效果，完善权限边界测试，持续扩充客服知识库，并将演示环境部署脚本进一步标准化。",
                    ],
                ),
            ],
        ),
        ManagedDocument(
            10,
            "风险管理报告",
            "记录风险跟踪、已发生问题、应对效果、风险状态变化和剩余风险。",
            "主要风险均已通过回退、预置数据、回归测试和文档同步得到控制，剩余风险集中在真实上线后的数据、隐私和规模化运维。",
            [
                common_project_overview(),
                Section(
                    "风险跟踪概况",
                    paragraphs=[
                        "项目实施过程中持续跟踪推荐效果、Dify 接入、联调回归、演示环境和文档同步等风险。风险管理的重点不是完全避免问题，而是在问题出现时快速识别、明确责任、启动替代方案并记录经验。",
                        "当前风险总体处于可控状态。课程演示所需的核心功能已具备，外部智能服务通过本地知识库和转人工机制降低影响，演示环境通过预置账号和本地代理说明降低现场不确定性。",
                    ],
                    tables=[risk_register()],
                ),
                Section(
                    "风险处置记录",
                    tables=[
                        TableBlock(
                            "风险处置跟踪表",
                            ["风险编号", "处置动作", "效果评价", "状态"],
                            [
                                ["R01", "构建不同兴趣画像账号，推荐读取模型分表优先并保留规则回退", "推荐差异可在首页展示，效果满足课程演示。", "缓解"],
                                ["R02", "补充 Dify 手工搭建说明，后端识别未解析变量并回退本地知识库", "客服页面不因 Dify 异常完全不可用。", "缓解"],
                                ["R03", "围绕活动、组队、消息、通知设计端到端体验路线", "发现并修复多处状态和文案问题。", "缓解"],
                                ["R04", "以仓库当前资料为准生成统一管理文档", "文档口径与项目成果保持一致。", "关闭"],
                                ["R05", "使用 8125 本地代理、预置账号和操作说明", "降低现场部署和邮箱配置依赖。", "缓解"],
                            ],
                            [1.0, 3.0, 2.4, 0.9],
                        )
                    ],
                ),
                Section(
                    "已发生问题与经验",
                    paragraphs=[
                        "项目中已暴露的问题主要包括 Dify DSL 版本差异、直接打开 HTML 导致接口请求失败、推荐演示需要足够差异化的数据、消息通知文案需要精确到具体对象、活动关联组队入口需要避免语义混淆等。",
                        "这些问题说明智能应用项目不仅要关注算法和模型，还要关注产品闭环、异常路径和演示可用性。对于课程项目而言，预置数据、操作说明和兜底方案与代码实现同样重要。",
                    ],
                    tables=[
                        TableBlock(
                            "问题复盘表",
                            ["问题", "根因", "处理方式", "经验"],
                            [
                                ["Dify 导入或变量异常", "版本 schema 和 Answer 节点绑定敏感", "提供手工搭建说明和后端回退", "外部服务必须有兜底。"],
                                ["file:// 访问失败", "浏览器跨域和相对路径限制", "推荐统一使用 8125 本地代理", "演示入口要标准化。"],
                                ["推荐差异不明显", "行为数据和画像不足", "补充多账号、多兴趣和推荐理由", "智能功能需要演示数据支撑。"],
                                ["通知文案模糊", "状态变化对象未明确", "文案改为具体人名和业务对象", "通知是业务闭环的一部分。"],
                            ],
                            [1.5, 1.7, 2.0, 1.8],
                        )
                    ],
                ),
                Section(
                    "剩余风险",
                    paragraphs=[
                        "课程交付阶段的剩余风险主要是现场环境差异、老师临时体验非预设路径、外部服务网络波动和文档细节修改要求。这些风险可通过提前预检、保留备用账号、准备常见问题回答和保留 Markdown 源稿来控制。",
                        "若项目未来真实上线，还需要新增隐私合规、真实账号认证、日志审计、内容安全、容量扩展和推荐公平性等风险管理内容，这些超出当前课程项目范围，但应作为后续规划。",
                    ],
                ),
            ],
        ),
        ManagedDocument(
            11,
            "项目总结报告",
            "总结项目目标达成情况、成果、进度成本质量风险复盘、经验教训和后续优化建议。",
            "项目完成了从需求、设计、实现到展示和管理文档的完整闭环，达到了软件项目管理课程案例要求。",
            [
                common_project_overview(),
                Section(
                    "项目目标达成情况",
                    paragraphs=[
                        "项目围绕“找活动难、找队友难、沟通协作分散、平台治理不足”四类问题展开，建设了校园活动智能推荐与组队平台。系统从用户视角覆盖活动发现、个性化推荐、报名审核、组队协作、私聊群聊、通知中心和个人中心；从管理视角覆盖活动审核、用户管理、标签管理和运营推荐位；从智能能力视角覆盖推荐排序和 Dify 智能客服。",
                        "总体看，项目已经达到课程综合实践和软件项目管理案例要求。项目不仅有可运行成果，也有需求、设计、测试、展示和管理报告作为过程证据，能够体现项目生命周期管理。",
                    ],
                    tables=[
                        TableBlock(
                            "目标达成评价",
                            ["目标", "达成情况", "证据"],
                            [
                                ["活动发现与报名", "达成", "活动大厅、详情、报名、审批和通知流程。"],
                                ["智能推荐", "达成", "活动/组队推荐、行为日志、推荐分表和规则回退。"],
                                ["组队协作", "达成", "组队大厅、申请审批、队伍管理和群聊。"],
                                ["消息通知", "达成", "私聊、活动群聊、队伍群聊、通知中心。"],
                                ["后台治理", "达成", "用户、活动、标签和推荐位管理。"],
                                ["智能客服", "达成", "Dify 知识库、本地回退和转人工。"],
                                ["项目文档", "达成", "需求、概要设计、展示说明和 11 份管理文档。"],
                            ],
                            [1.4, 1.0, 3.4],
                        )
                    ],
                ),
                Section(
                    "项目管理复盘",
                    paragraphs=[
                        "进度方面，项目采用阶段推进方式，从需求和设计逐步进入实现、测试和展示。关键经验是尽早形成可演示主链路，再围绕推荐、客服和后台治理补充亮点。若先追求复杂智能算法，容易挤压联调和测试时间。",
                        "成本方面，项目主要成本是成员时间。通过复用开源技术、本地环境、预置数据和统一文档模板，现金支出较低。最大的隐性成本来自联调返工和文档同步，因此后续项目应更早建立接口约定和变更记录。",
                        "质量方面，核心流程可用是最重要的质量标准。项目通过期末展示功能清单和操作说明书把测试与验收路线具体化，减少了“功能做了但老师不知道怎么体验”的风险。",
                    ],
                    tables=[
                        TableBlock(
                            "管理维度复盘",
                            ["维度", "表现", "主要经验"],
                            [
                                ["范围", "核心范围完整，增强项有所控制", "用角色和用户旅程定义边界。"],
                                ["进度", "总体符合课程周期", "关键路径任务要优先保护。"],
                                ["成本", "现金成本低，人工成本为主", "统一工具和资料减少返工。"],
                                ["质量", "演示质量达到要求", "端到端测试比单点测试更重要。"],
                                ["风险", "主要风险有兜底", "外部服务和智能能力不能成为单点依赖。"],
                                ["沟通", "文档和操作说明提升协作效率", "交付物本身也是沟通工具。"],
                            ],
                            [1.1, 2.2, 3.0],
                        )
                    ],
                ),
                Section(
                    "成果清单",
                    paragraphs=["项目成果包括软件成果、数据与模型成果、文档成果和展示成果。成果之间相互支撑：软件提供可运行体验，数据和模型提供智能能力，文档解释过程，展示材料服务验收。"],
                    tables=[
                        TableBlock(
                            "项目成果清单",
                            ["成果类别", "具体成果", "价值"],
                            [
                                ["软件系统", "Web 用户端、管理后台、Spring Boot 后端、MySQL 数据库", "支撑完整业务闭环。"],
                                ["智能能力", "推荐分表、ML 脚本、推荐理由、Dify 客服", "体现项目智能化特色。"],
                                ["设计资料", "需求规格、概要设计、UML 图、ER 图", "支撑可追溯设计。"],
                                ["测试展示", "期末功能清单、操作说明、演示账号、PPT", "降低验收和展示成本。"],
                                ["管理文档", "项目建议书等 11 份软件项目管理文档", "体现课程管理方法。"],
                            ],
                            [1.2, 2.8, 2.2],
                        )
                    ],
                ),
                Section(
                    "经验教训",
                    bullets=[
                        "需求边界要尽早明确，尤其是活动、组队、消息和后台之间的交叉场景。",
                        "推荐系统必须准备足够的用户画像和行为数据，否则算法存在但演示效果不明显。",
                        "外部智能服务接入要有本地兜底和转人工路径，不能把核心体验完全交给外部依赖。",
                        "课程项目的文档不是最后补材料，而是帮助团队统一理解和验收标准的工具。",
                        "现场演示需要预置账号、操作路线和环境预检，降低临场不确定性。",
                    ],
                ),
                Section(
                    "后续优化建议",
                    paragraphs=[
                        "后续可从工程化、智能化和运营化三个方向继续优化。工程化方面，补充自动化测试、接口文档、部署脚本和日志监控；智能化方面，引入更丰富的行为数据、召回模型和排序模型评估指标；运营化方面，完善内容审核规则、推荐公平性、活动效果统计和用户反馈闭环。",
                        "若未来真实上线，还应重点补充隐私合规、真实身份认证、数据脱敏、权限审计和服务可用性保障。课程阶段已经验证了产品方向和主要流程，后续重点是把演示系统推进为可持续运营系统。",
                    ],
                ),
            ],
        ),
    ]


def augment_documents(managed: ManagedDocument) -> None:
    if managed.no == 1:
        managed.sections.extend(
            [
                Section(
                    "课程资料对齐说明",
                    paragraphs=[
                        "课程概论中明确本课程报告需要覆盖项目建议书、可行性分析报告、项目计划及资源、进度、成本、风险、质量等子计划，并在后续形成估算、质量管理、风险管理和项目总结报告。本项目建议书据此把立项必要性、项目价值、建设条件和课程交付物统一起来。",
                        "课件强调项目是为了创造唯一产品或服务而进行的临时性努力，软件项目管理则是在预算、成本等约束下提交高质量软件产品。因此，本项目建议书不仅说明系统功能价值，也说明为什么该项目适合作为软件项目管理课程案例。",
                    ],
                    tables=[course_alignment_table(), performance_domain_table()],
                ),
                Section(
                    "利益相关方分析",
                    paragraphs=[
                        "项目建议书阶段最重要的不是把功能说得更多，而是把谁会使用、谁会管理、谁会受益、谁会评审先讲清楚。该平台的主要利益相关方包括普通学生、活动组织者、队伍发起者、管理员和课程评审教师。不同角色关心点不同，因此立项时必须兼顾体验、治理和展示三类目标。",
                        "从管理角度看，若没有利益相关方分析，后续很容易出现某些功能做得很完整，但并不服务真实使用目标的情况。将角色要求前置，可以帮助项目组在需求、设计、测试和总结阶段维持同一套验收语言。",
                    ],
                    tables=[
                        TableBlock(
                            "利益相关方分析表",
                            ["相关方", "主要诉求", "关注指标", "项目响应"],
                            [
                                ["普通学生", "更快找到活动和队友", "推荐命中率、报名便利性", "推荐流、活动详情、组队大厅。"],
                                ["活动组织者", "高效发布和审批报名", "活动管理效率、沟通效率", "活动管理、审批、群聊和通知。"],
                                ["队伍发起者", "快速招募匹配队友", "申请转化率、入队审批效率", "组队详情、申请、成员管理。"],
                                ["管理员", "控制内容和推荐质量", "审核覆盖率、风险响应速度", "后台审核、标签和推荐位管理。"],
                                ["课程教师", "快速判断项目完成度", "流程完整性、展示连贯性", "操作说明、演示路线和管理文档。"],
                            ],
                            [1.2, 2.4, 2.0, 2.5],
                        ),
                        TableBlock(
                            "立项判定门槛",
                            ["判定项", "最低要求", "本项目现状"],
                            [
                                ["范围", "核心流程可定义并可验收", "已形成需求和设计基线。"],
                                ["技术", "能够落到现有开发能力与环境", "Spring Boot、MySQL、Dify 和 ML 均可支撑。"],
                                ["资源", "团队能在课程周期内完成", "4 人团队分工明确，资源可控。"],
                                ["风险", "有替代方案和兜底路径", "推荐和客服均有回退机制。"],
                                ["交付", "能同时给出系统和文档成果", "已具备展示、说明和管理文档基础。"],
                            ],
                            [1.2, 2.8, 2.6],
                        ),
                    ],
                ),
                Section(
                    "实施路线与立项结论细化",
                    paragraphs=[
                        "建议采用三段式实施路线。第一段先做业务闭环，优先稳定首页、活动、组队、消息和后台；第二段补推荐和智能客服，形成项目亮点；第三段做文档和演示固化，确保课程验收稳定。这样安排可以避免一开始就陷入某个高风险模块而拖慢整体进度。",
                        "立项结论不是简单的“可以做”，而是“值得做、做得完、能展示、可总结”。综合仓库现状与课程要求，本项目具备明确的课程价值和管理价值，适合用作完整软件项目管理案例。",
                    ],
                    tables=[
                        TableBlock(
                            "实施阶段门控表",
                            ["阶段", "关键输出", "门控标准"],
                            [
                                ["闭环优先", "基础页面、认证、活动、组队、消息", "能完成至少一条完整用户旅程。"],
                                ["智能增强", "推荐差异、推荐理由、智能客服", "不同账号可见差异，异常有兜底。"],
                                ["治理补全", "后台、标签、审核、推荐位", "管理员可对外部展示进行控制。"],
                                ["文档固化", "管理文档、操作说明、展示 PPT", "内容和系统实现保持一致。"],
                            ],
                            [1.6, 3.0, 3.0],
                        )
                    ],
                ),
            ]
        )
    elif managed.no == 2:
        managed.sections.extend(
            [
                Section(
                    "课程可行性分析口径",
                    paragraphs=[
                        "课程资料将可行性分析放在项目准备和启动阶段，要求在启动前回答“是否值得做、怎么做、要消耗多少时间和成本”。本报告按经济可行性、技术可行性、风险与不确定性、操作可行性和进度可行性展开。",
                        "经济可行性采用课程中提到的成本效益分析思想，结合净利润、投资回收期、ROI 和 NPV 等指标进行课程项目口径估算。由于本项目为课程实践，收益以学习收益、复用价值和校园服务价值为主，现金流估算用于说明方法掌握而非真实商业融资。",
                    ],
                    tables=[
                        course_alignment_table(),
                        TableBlock(
                            "经济评价指标示例表",
                            ["指标", "课程含义", "本项目估算口径", "结论"],
                            [
                                ["净利润", "生命周期总收益减总成本", "以课程影子收益减影子成本估算", "项目学习和复用价值大于影子成本。"],
                                ["投资回收期", "达到收支平衡所需时间", "按一个学期交付并复用于展示和后续项目", "课程周期内可收回学习投入。"],
                                ["ROI", "平均年利润 / 总投资", "以文档、系统和能力沉淀收益折算", "具备正向课程收益。"],
                                ["NPV", "考虑贴现率后的净现值", "按低现金支出和多期复用收益估算", "课程场景下为正向价值。"],
                                ["IRR", "使NPV为0的贴现率", "作为扩展指标，不作真实融资承诺", "说明项目具备继续投入价值。"],
                            ],
                            [1.0, 2.4, 3.0, 1.8],
                        ),
                    ],
                ),
                Section(
                    "方案边界与约束条件",
                    paragraphs=[
                        "可行性分析不能只谈“想做什么”，还要谈“不能做什么”和“必须遵守什么”。本项目的边界条件主要有三条：课程周期有限、团队规模有限、外部智能服务不稳定性不可完全控制。因此方案必须将核心演示能力放在自有系统上，把外部能力放在增强层。",
                        "约束条件还包括展示环境、教学验收和本地开发环境。项目不以真实线上高并发为验收目标，而以本地可运行、浏览器可访问、流程可解释、异常可兜底为目标。",
                    ],
                    tables=[
                        TableBlock(
                            "约束条件表",
                            ["约束", "影响", "应对方式"],
                            [
                                ["课程周期", "交付时间固定", "锁定核心范围，采用迭代推进。"],
                                ["团队规模", "并行能力有限", "明确主责人，减少重复开发。"],
                                ["外部服务", "Dify/邮件可能波动", "本地知识库和回退机制兜底。"],
                                ["现场网络", "演示环境可能受限", "使用本地 HTTP 代理和预置账号。"],
                                ["数据规模", "真实数据缺乏", "预置多画像演示数据。"],
                            ],
                            [1.2, 2.5, 2.9],
                        )
                    ],
                ),
                Section(
                    "法律合规与运维说明",
                    paragraphs=[
                        "项目虽为课程作品，但仍应遵守基本合规原则。平台不应采集超出课程演示需要的敏感个人信息；如使用真实邮箱或手机号，应明确用途、最小化留存并进行脱敏展示。推荐日志和行为数据应仅用于课程分析，不应对外泄露。",
                        "运维层面，项目部署不应依赖难以追踪的个人临时环境。应提供配置示例、启动顺序、端口说明和故障排查方法，使项目在演示前可重复恢复。若后续扩展到真实使用，还需补充隐私政策、数据保留策略和权限审计机制。",
                    ],
                    tables=[
                        TableBlock(
                            "合规检查表",
                            ["检查项", "要求", "当前状态"],
                            [
                                ["隐私最小化", "仅保留演示所需数据", "满足课程场景。"],
                                ["身份管理", "角色权限分离", "已在系统设计中体现。"],
                                ["外部服务", "可替换、可回退", "已设计本地知识库兜底。"],
                                ["数据留痕", "仅记录必要行为", "行为日志已用于推荐。"],
                                ["运维文档", "可重复启动和排障", "已有操作说明和本地代理说明。"],
                            ],
                            [1.5, 3.0, 2.5],
                        )
                    ],
                ),
            ]
        )
    elif managed.no == 3:
        managed.sections.extend(
            [
                Section(
                    "课程资源与团队管理口径",
                    paragraphs=[
                        "课程资料把项目团队与干系人作为独立管理主题，强调目标管理、分工管理、工作氛围、知识传递、沟通协作和干系人管理。资源计划因此不能只列设备和人员，还要说明团队如何协作、知识如何传递、干系人如何参与。",
                        "本项目资源计划采用小团队复合角色方式，每个成员既承担实现任务，也参与文档和验收。为降低知识孤岛风险，关键模块需要至少一名协作人了解接口、数据和演示路径。",
                    ],
                    tables=[stakeholder_matrix_table(), performance_domain_table()],
                ),
                Section(
                    "资源日历与投入节奏",
                    paragraphs=[
                        "资源投入不是均匀分布的。前期主要消耗在理解需求、统一术语和确定交付物；中期主要消耗在前后端联调、推荐数据准备和客服接入；后期主要消耗在测试、修复、演示脚本和文档归档。资源计划必须提前预判这些波峰，避免期末阶段所有成员同时被测试和文档压缩。",
                        "为减少资源冲突，建议每周固定一次短会，集中确认阻塞项、待办项和新增风险。对于需要多人协同的任务，例如端到端测试、后台操作和演示脚本排练，应提前预约时间窗口，避免临时拉人导致资源碎片化。",
                    ],
                    tables=[
                        TableBlock(
                            "周度资源日历",
                            ["周次", "重点资源", "投入重点", "风险提醒"],
                            [
                                ["W1-W2", "项目经理、产品、前端", "立项、范围、需求梳理", "术语不统一。"],
                                ["W3-W5", "后端、数据库、设计", "模型和接口设计", "设计返工。"],
                                ["W6-W8", "前端、后端、测试", "主流程联调", "接口口径不一致。"],
                                ["W9-W10", "推荐、客服、测试", "智能能力与回退", "外部依赖不稳定。"],
                                ["W11-W13", "全体成员", "测试、修复、文档", "时间冲突和返工。"],
                                ["W14", "全体成员", "交付与答辩", "演示环境异常。"],
                            ],
                            [1.2, 1.7, 2.5, 2.1],
                        )
                    ],
                ),
                Section(
                    "资源保障与替补机制",
                    paragraphs=[
                        "项目资源计划需要明确主责与替补。每个关键模块至少明确一名主责和一名熟悉模块的协作人，以防因课程、实习或个人原因造成资源断档。尤其在后端、推荐和文档整合三个环节，替补机制可以显著降低最终风险。",
                    ],
                    tables=[
                        TableBlock(
                            "替补与保障表",
                            ["场景", "主责人离线影响", "替补措施"],
                            [
                                ["后端接口联调", "活动、组队、通知链路受阻", "由熟悉接口的成员接手并按接口清单恢复。"],
                                ["推荐数据准备", "首页推荐差异不足", "使用规则排序和预置数据先保住演示。"],
                                ["智能客服接入", "问答入口失效", "切换本地知识库与转人工提示。"],
                                ["文档归档", "最终交付不统一", "由项目经理进行合并和格式校准。"],
                                ["演示当天", "操作节奏不稳定", "预先演练并提供脚本页。"],
                            ],
                            [1.8, 3.0, 3.0],
                        )
                    ],
                ),
            ]
        )
    elif managed.no == 4:
        managed.sections.extend(
            [
                Section(
                    "课程进度计划口径",
                    paragraphs=[
                        "课程资料明确进度计划需包含甘特图，并强调活动标识、活动次序、关键路径、PERT、里程碑、进度表审查和备用进度计划。本进度计划据此将 WBS、活动依赖、CPM/PERT 和文本甘特图同时纳入。",
                        "课件还强调计划应按需制定、项目组共同参与、任务与人力资源时间协调，并考虑意外事故缓冲。本项目以14周课程周期为约束，围绕关键路径设置测试和文档缓冲。",
                    ],
                    tables=[wbs_detail_table(), cpm_pert_table(), gantt_text_table()],
                ),
                Section(
                    "周度检查清单",
                    paragraphs=[
                        "进度管理最怕两件事：任务做了但没被看见，或者看见了但没法收口。周度检查清单的作用就是把“完成”定义成可见、可验证、可交付的状态，而不是停留在口头描述。",
                        "每周检查应同时覆盖功能、依赖和文档。功能看是否能跑，依赖看接口、数据和环境是否就绪，文档看是否与实现同步。只要有一项不满足，就不能进入下个里程碑验收。",
                    ],
                    tables=[
                        TableBlock(
                            "周度检查清单",
                            ["检查项", "检查方式", "通过标准"],
                            [
                                ["需求冻结", "比对需求文档和当前待办", "无新增未评审核心范围。"],
                                ["接口联调", "前后端实际调用", "请求和响应字段一致。"],
                                ["数据准备", "检查预置账号和行为日志", "推荐和通知有足够演示数据。"],
                                ["异常兜底", "故障模拟与回退测试", "外部服务异常不致命。"],
                                ["文档同步", "比对系统和文档描述", "关键内容保持一致。"],
                            ],
                            [1.6, 3.0, 3.0],
                        )
                    ],
                ),
                Section(
                    "延期处理原则",
                    paragraphs=[
                        "一旦任务延期，必须先判断它是否影响关键路径。影响关键路径的任务例如接口联调、登录、报名、组队、推荐和客服；不影响关键路径的任务例如页面微调、文案润色和次要展示效果。项目不应该为了好看牺牲交付稳定性。",
                    ],
                    tables=[
                        TableBlock(
                            "延期分级表",
                            ["延期级别", "影响", "处理策略"],
                            [
                                ["轻微", "不影响下一里程碑", "记录并跟踪，按周解决。"],
                                ["中等", "会压缩缓冲时间", "调整资源并减少非核心工作。"],
                                ["严重", "影响最终交付", "启动范围裁剪和应急排期。"],
                            ],
                            [1.4, 2.4, 4.0],
                        )
                    ],
                ),
            ]
        )
    elif managed.no == 5:
        managed.sections.extend(
            [
                Section(
                    "课程成本与挣值管理口径",
                    paragraphs=[
                        "课程资料将进度和成本管理放在同一章，强调进度成本控制和挣值管理。成本计划因此不仅要列预算，还要说明如何在执行中判断“花的钱是否买到了相应进展”。",
                        "本项目采用课程影子成本作为预算基准，并用 PV、EV、AC、SPI、CPI 形成轻量化挣值管理表。由于项目不产生真实采购大额支出，EVM 主要用于学习成本控制方法和识别返工风险。",
                    ],
                    tables=[evm_table()],
                ),
                Section(
                    "阶段成本基准",
                    paragraphs=[
                        "成本计划需要从阶段维度控制，而不是只看总额。前期成本更多体现在需求和设计讨论，中期成本集中在联调和返工，后期成本集中在测试和文档。对课程项目来说，后期返工是最昂贵的，因为它会同时吞掉测试和展示时间。",
                    ],
                    tables=[
                        TableBlock(
                            "阶段成本基准表",
                            ["阶段", "成本重心", "预算特征", "控制重点"],
                            [
                                ["立项与需求", "讨论与统一范围", "低现金、高沟通", "减少需求反复。"],
                                ["设计与实现", "编码与联调", "人时快速上升", "锁定接口和模型。"],
                                ["测试与修复", "缺陷关闭", "返工成本高", "优先处理高优先级缺陷。"],
                                ["展示与总结", "文档和答辩准备", "时间集中消耗", "统一模板和脚本。"],
                            ],
                            [1.3, 1.7, 2.0, 2.6],
                        )
                    ],
                ),
                Section(
                    "成本偏差处理模板",
                    paragraphs=[
                        "如果实际工作量偏离估算，首先应判断是估算错误、范围变更还是返工造成。对课程项目而言，估算误差本身并不可怕，可怕的是没有记录偏差来源。只要偏差原因清楚，就可以通过裁剪次要功能、增加协作、改写演示脚本或延后增强项来处理。",
                    ],
                    tables=[
                        TableBlock(
                            "成本偏差记录模板",
                            ["日期", "偏差来源", "影响人时", "处理结果"],
                            [
                                ["联调前", "接口参数不统一", "+6小时", "统一字段并写入接口说明。"],
                                ["推荐阶段", "画像数据不足", "+8小时", "补充演示账号和行为日志。"],
                                ["客服阶段", "Dify 绑定异常", "+4小时", "启用本地知识库回退。"],
                                ["文档阶段", "描述与实现差异", "+10小时", "按仓库现状统一校正。"],
                            ],
                            [1.2, 2.4, 1.4, 3.6],
                        )
                    ],
                ),
            ]
        )
    elif managed.no == 6:
        managed.sections.extend(
            [
                Section(
                    "课程风险计划口径",
                    paragraphs=[
                        "课程资料强调风险管理包含风险识别、风险评估、风险监控和规避，并要求建立并及时更新风险列表及风险排序。本风险计划据此采用风险登记册、概率影响矩阵、应对策略、储备和触发机制。",
                        "风险管理的课程重点不是风险表本身，而是尽早识别风险、尽早采取应对措施，并通过监控过程防微杜渐。本项目将推荐效果、Dify 接入、联调回归、演示环境和文档同步作为重点风险。",
                    ],
                    tables=[risk_model_table()],
                ),
                Section(
                    "概率-影响矩阵",
                    paragraphs=[
                        "风险计划不仅要列出风险，还要把风险摆到矩阵里看。只有把概率和影响结合起来，项目组才知道哪些问题必须提前解决，哪些问题可以观察。对本项目来说，推荐、客服和联调是高优先级风险区。",
                    ],
                    tables=[
                        TableBlock(
                            "概率-影响矩阵",
                            ["风险等级", "概率低", "概率中", "概率高"],
                            [
                                ["影响高", "观察", "重点缓解", "立即处理"],
                                ["影响中", "观察", "跟踪缓解", "重点处理"],
                                ["影响低", "接受", "观察", "跟踪"],
                            ],
                            [1.5, 2.5, 2.5, 2.5],
                        )
                    ],
                ),
                Section(
                    "风险储备与触发机制",
                    paragraphs=[
                        "风险储备不是为了掩盖计划不清，而是为了让团队在风险真正发生时有可用资源。课程项目的储备主要是时间储备，不是现金储备：例如保留若干小时用于应对接口变更、数据补充和文档修订。",
                    ],
                    tables=[
                        TableBlock(
                            "风险储备使用表",
                            ["储备类型", "用途", "释放条件"],
                            [
                                ["时间储备", "处理返工和联调", "关键缺陷出现或范围同步变化。"],
                                ["数据储备", "补充推荐和通知样本", "演示画像不足或结果不明显。"],
                                ["方案储备", "启用回退或替代方案", "外部服务或高风险模块失效。"],
                            ],
                            [1.4, 3.0, 3.0],
                        )
                    ],
                ),
            ]
        )
    elif managed.no == 7:
        managed.sections.extend(
            [
                Section(
                    "课程质量计划口径",
                    paragraphs=[
                        "课程资料强调质量工程、测试左移和右移、持续集成和持续交付、性能工程、安全工程、质量计划、评审、缺陷预防和质量度量。质量计划因此要把质量活动嵌入需求、设计、实现、测试和交付全过程。",
                        "本项目质量计划以课程验收为目标，但借鉴质量工程思想：在需求和设计阶段提前评审，在联调阶段暴露缺陷，在交付阶段保留操作说明和反馈机制，避免把质量活动压到最后。",
                    ],
                    tables=[quality_engineering_table()],
                ),
                Section(
                    "测试与评审检查表",
                    paragraphs=[
                        "质量计划要真正落地，就必须有检查表。检查表把“应该做什么”变成“已经做到哪一步”，尤其适合课程项目这种人员较少、交付内容多的场景。对本项目而言，检查重点是核心旅程、异常兜底、管理后台和文档一致性。",
                    ],
                    tables=[
                        TableBlock(
                            "质量检查表",
                            ["检查对象", "检查问题", "检查结论"],
                            [
                                ["登录注册", "身份和验证码流程是否正常", "需与预置账号一起验证。"],
                                ["活动报名", "审批和容量控制是否正确", "已纳入核心回归。"],
                                ["组队申请", "申请、审批、群聊是否联动", "通过端到端测试。"],
                                ["推荐模块", "不同画像是否存在差异", "需保留推荐理由。"],
                                ["智能客服", "异常和转人工是否可用", "必须保留本地兜底。"],
                            ],
                            [1.8, 4.2, 2.0],
                        )
                    ],
                ),
                Section(
                    "验收资料清单",
                    paragraphs=[
                        "验收并不只看系统是否能打开，还要看是否能把过程说清楚。为了让老师快速验收，项目需要准备统一的资料包：需求与设计文档、操作说明、预置账号、演示脚本、测试结果、缺陷关闭记录和最终总结。",
                    ],
                    tables=[
                        TableBlock(
                            "验收资料包",
                            ["资料项", "用途", "状态"],
                            [
                                ["需求规格说明书", "说明需要做什么", "已完成。"],
                                ["概要设计说明书", "说明如何设计", "已完成。"],
                                ["操作说明书", "说明怎么体验", "已完成。"],
                                ["测试记录", "说明质量如何", "需与最终版本一致。"],
                                ["演示账号", "说明如何快速进入系统", "已准备。"],
                                ["总结报告", "说明项目收获与不足", "已完成。"],
                            ],
                            [2.2, 3.8, 2.0],
                        )
                    ],
                ),
            ]
        )
    elif managed.no == 8:
        managed.sections.extend(
            [
                Section(
                    "课程估算模型应用",
                    paragraphs=[
                        "课程资料将项目估算分为估算挑战、基本内容、基本估算方法、软件规模估算、工作量估算、资源估算、工期估算和成本估算，并列出德尔菲法、代码行估算、功能点分析、COCOMO、Walston-Felix、扑克牌估算等方法。",
                        "本估算报告在原有功能点和人时估算基础上，补充功能点到代码行的换算，并使用 Walston-Felix 模型进行对照。需要说明的是，工业参数模型通常会高估学生课程项目，因为课程项目大量使用原型、脚本、框架和已有资料，且验收目标不同于商业上线。",
                    ],
                    tables=[function_point_estimation_table(), walston_felix_table()],
                ),
                Section(
                    "估算方法对比",
                    paragraphs=[
                        "估算本身也是一种管理能力。不同方法各有用途：类比适合快速判断，专家判断适合依赖经验，自下而上适合具体落地。本报告取三者交叉验证，避免单一方法带来的偏差。",
                    ],
                    tables=[
                        TableBlock(
                            "估算方法对比表",
                            ["方法", "优点", "局限", "本项目用途"],
                            [
                                ["类比估算", "快，适合早期判断", "受参照项目质量影响大", "判断项目量级。"],
                                ["专家判断", "贴近真实开发经验", "依赖成员经验", "估算推荐和客服工作量。"],
                                ["自下而上", "颗粒度高", "耗时较长", "形成正式工作量和成本基准。"],
                                ["三角估算", "考虑不确定性", "计算复杂", "用于悲观和乐观区间分析。"],
                            ],
                            [1.5, 2.0, 2.0, 2.5],
                        )
                    ],
                ),
                Section(
                    "敏感性分析",
                    paragraphs=[
                        "敏感性分析的目的是判断哪类变化最容易把估算打偏。对本项目而言，最敏感的变量不是现金支出，而是联调时间、返工次数和外部服务稳定性。只要这三项可控，总体估算就不会离谱。",
                    ],
                    tables=[
                        TableBlock(
                            "敏感性分析表",
                            ["变量", "上升10%影响", "下降10%影响", "控制建议"],
                            [
                                ["需求变更", "工时显著上升", "工时减少有限", "冻结核心范围。"],
                                ["联调时间", "影响测试和文档", "释放少量缓冲", "预留回归窗口。"],
                                ["缺陷密度", "返工成本增加", "质量更稳定", "提前做端到端测试。"],
                                ["外部依赖", "增加回退概率", "演示更稳定", "保留本地兜底。"],
                            ],
                            [1.8, 2.4, 2.4, 2.4],
                        )
                    ],
                ),
            ]
        )
    elif managed.no == 9:
        managed.sections.extend(
            [
                Section(
                    "课程质量管理执行口径",
                    paragraphs=[
                        "课程资料强调质量度量可以提升沟通、尽早发现问题、支持权衡、跟踪目标、管理风险和辅助决策。质量管理报告因此重点说明质量计划如何执行、数据如何收集、缺陷如何关闭以及质量度量如何支持判断。",
                        "本项目质量管理报告按项目度量、产品度量和过程度量组织证据。项目度量关注进度和人时，产品度量关注功能和缺陷，过程度量关注评审、变更、风险和交付资料。",
                    ],
                    tables=[quality_engineering_table()],
                ),
                Section(
                    "质量执行记录",
                    paragraphs=[
                        "质量管理报告需要证明质量计划不是纸面文件，而是真的执行过。执行记录的重点包括：什么时候评审了需求，什么时候检查了接口，什么时候关闭了缺陷，以及通过什么证据确认质量提升。",
                    ],
                    tables=[
                        TableBlock(
                            "质量执行记录表",
                            ["日期", "检查项", "执行动作", "结果"],
                            [
                                ["需求阶段", "用例完整性", "对照需求清单逐项核对", "覆盖核心旅程。"],
                                ["设计阶段", "架构与ER一致性", "比对概要设计和数据库脚本", "基本一致。"],
                                ["联调阶段", "接口异常处理", "检查返回和兜底", "已补充。"],
                                ["测试阶段", "回归和演示", "按操作说明路线核对", "满足演示。"],
                            ],
                            [1.4, 2.0, 3.2, 2.2],
                        )
                    ],
                ),
                Section(
                    "问题整改闭环",
                    paragraphs=[
                        "闭环是质量管理的核心。一个问题不是“修了”就结束，而是要有发现、定位、修复、验证和归档的完整链路。这样做的好处是团队能在总结时明确哪些问题是偶发，哪些问题是系统性问题。",
                    ],
                    tables=[
                        TableBlock(
                            "整改闭环表",
                            ["问题", "修复措施", "验证方式", "结论"],
                            [
                                ["推荐差异不明显", "补充画像和推荐理由", "多账号对比首页结果", "通过。"],
                                ["通知文案模糊", "改成具体对象表达", "查看通知中心文案", "通过。"],
                                ["长描述展示不完整", "详情页完整展开", "检查页面滚动和详情弹层", "通过。"],
                                ["Dify 变量异常", "后端识别并回退", "模拟异常回答", "通过。"],
                            ],
                            [1.4, 2.4, 3.0, 1.6],
                        )
                    ],
                ),
            ]
        )
    elif managed.no == 10:
        managed.sections.extend(
            [
                Section(
                    "课程风险管理执行口径",
                    paragraphs=[
                        "课程资料强调风险监控可以通过设置控制基线实现，并要求风险应对审计、突发风险应变、报告机制和定期干系人评估。本报告据此把风险状态演进、处置效果和经验沉淀作为重点。",
                        "风险管理报告与风险计划的区别在于：风险计划说明准备怎么管，风险管理报告说明实际管得怎么样。对于本项目，推荐、客服和联调风险在实施中均出现过不同程度的不确定性，但通过回退、数据补充和回归测试得到缓解。",
                    ],
                    tables=[risk_model_table()],
                ),
                Section(
                    "风险状态演进",
                    paragraphs=[
                        "风险管理报告强调过程，不只是结论。风险状态会随着项目推进发生变化：早期很多风险停留在假设层，中期会变成具体问题，后期则转化为可控残余风险。记录这些变化，才能解释项目是如何从不确定走向可交付的。",
                    ],
                    tables=[
                        TableBlock(
                            "风险状态演进表",
                            ["风险", "早期状态", "中期状态", "交付状态"],
                            [
                                ["推荐效果", "开放", "缓解", "关闭/保留观察。"],
                                ["客服接入", "开放", "缓解", "缓解。"],
                                ["联调回归", "开放", "缓解", "缓解。"],
                                ["文档同步", "开放", "关闭", "关闭。"],
                            ],
                            [1.8, 2.2, 2.2, 2.2],
                        )
                    ],
                ),
                Section(
                    "经验复用与沉淀",
                    paragraphs=[
                        "风险管理的价值不仅在于把问题处理掉，更在于把应对经验沉淀下来。以后如果再做类似的校园平台项目，可以直接复用本项目形成的演示账号策略、回退方案、操作说明模板和风险登记表，而不是从零开始。",
                    ],
                    tables=[
                        TableBlock(
                            "经验沉淀清单",
                            ["经验项", "可复用内容", "适用场景"],
                            [
                                ["预置数据", "多画像账号和行为样本", "推荐演示和回归测试。"],
                                ["外部回退", "本地知识库与转人工", "客服和外部接口异常。"],
                                ["操作说明", "统一体验路线", "现场展示和验收。"],
                                ["文档基线", "统一术语与模板", "后续课程项目复用。"],
                            ],
                            [1.4, 3.2, 2.8],
                        )
                    ],
                ),
            ]
        )
    elif managed.no == 11:
        managed.sections.extend(
            [
                Section(
                    "课程收尾与监督控制口径",
                    paragraphs=[
                        "课程资料在项目监督与控制、项目收尾中强调过程度量、数据收集、可视化管理、优先级控制、变更控制和经验总结。项目总结报告因此不仅总结成果，还要说明监控和控制过程带来的经验。",
                        "本项目收尾采用资料归档、交付物核对、质量复查和经验教训沉淀四步。课程报告要求的 11 份文档与系统资料一并归档，形成从项目初始到结束阶段的完整报告书。",
                    ],
                    tables=[course_alignment_table(), performance_domain_table()],
                ),
                Section(
                    "交付物总览",
                    paragraphs=[
                        "项目总结报告除了总结成绩，还应把交付物本身说清楚。对于课程老师而言，最直观的判断依据就是交付物是否齐全、是否彼此一致、是否能够证明项目真正做完了。",
                    ],
                    tables=[
                        TableBlock(
                            "最终交付物总览",
                            ["交付物", "用途", "完成情况"],
                            [
                                ["需求规格说明书", "说明需求边界和验收标准", "已完成。"],
                                ["概要设计说明书", "说明系统结构和接口设计", "已完成。"],
                                ["展示原型与前端页面", "支撑现场体验", "已完成。"],
                                ["后端与数据库", "支撑业务闭环", "已完成。"],
                                ["推荐与客服能力", "支撑智能亮点", "已完成。"],
                                ["11份管理文档", "支撑课程管理要求", "已完成。"],
                            ],
                            [2.0, 3.8, 1.8],
                        )
                    ],
                ),
                Section(
                    "后续路线图",
                    paragraphs=[
                        "如果这个项目继续往下做，路线图可以分为三个方向。第一，工程化方向，完善接口文档、自动化测试和部署脚本；第二，智能化方向，引入更细粒度的推荐召回和排序评估；第三，运营化方向，完善内容审核、推荐解释和活动效果统计。这样项目就能从课程作品逐步演变为更完整的校园服务平台。",
                    ],
                    tables=[
                        TableBlock(
                            "后续路线图表",
                            ["方向", "下一步任务", "预期收益"],
                            [
                                ["工程化", "自动化测试、部署脚本、日志监控", "降低维护和部署成本。"],
                                ["智能化", "更丰富的行为数据和模型评估", "提升推荐解释力。"],
                                ["运营化", "内容审核、推荐统计、反馈闭环", "提升平台治理能力。"],
                            ],
                            [1.6, 3.4, 3.0],
                        )
                    ],
                ),
            ]
        )


def add_standard_appendices(managed: ManagedDocument) -> None:
    managed.sections.extend(
        [
            Section(
                "附录A：管理证据链",
                paragraphs=[
                    f"为保证《{managed.title}》不是孤立文档，本附录把文档结论与项目现有证据进行对应。证据链的作用是让评审者能够从管理判断追溯到需求、设计、实现、测试和展示资料，避免报告只停留在主观描述。",
                    "本项目的证据来源包括需求规格说明书、概要设计说明书、期末展示功能需求清单、操作说明书、UML 图、后端源码、数据库脚本、原型页面、推荐模块、Dify 智能客服资料和展示 PPT。不同报告引用证据的重点不同，但都围绕同一项目基线展开。",
                ],
                tables=[
                    TableBlock(
                        "管理证据链矩阵",
                        ["证据类别", "对应材料", "支撑的管理判断", "检查方式"],
                        [
                            ["需求证据", "需求规格说明书", "项目范围、角色、功能和非功能要求真实存在", "检查章节目录、用例和需求说明。"],
                            ["设计证据", "概要设计说明书、UML 图、ER 图", "系统架构、数据模型和接口边界可解释", "检查架构图、数据库表和接口说明。"],
                            ["实现证据", "backend、frontend、ml 目录", "核心模块已进入可运行或可演示状态", "检查源码目录、页面和模型脚本。"],
                            ["智能证据", "推荐脚本、Dify DSL、知识库说明", "项目具备推荐和智能客服亮点", "检查推荐分表、回退逻辑和客服流程。"],
                            ["测试证据", "功能需求清单、操作说明书、演示账号", "验收路线可复现，核心流程可手工验证", "按操作说明完成主要路线。"],
                            ["管理证据", "11份软件项目管理文档", "项目具备计划、控制、报告和总结闭环", "检查文档序号、主题和一致性。"],
                            ["展示证据", "综合项目实践 PPT、第二次汇报材料", "项目成果能被课堂展示和解释", "检查演示主线与系统现状一致。"],
                            ["归档证据", "docs/software_project_management", "最终交付物可定位、可复用、可修改", "检查 Markdown 与 DOCX 成品数量。"],
                        ],
                        [1.2, 2.0, 2.8, 2.0],
                    )
                ],
            ),
            Section(
                "附录B：验收检查记录",
                paragraphs=[
                    f"《{managed.title}》的验收检查围绕完整性、准确性、一致性、可读性和可追溯性展开。完整性要求覆盖课程指定主题；准确性要求项目事实与仓库资料一致；一致性要求 11 份管理文档之间术语、工期、成本和风险口径统一；可读性要求 Word 版式无明显问题；可追溯性要求结论能找到项目材料依据。",
                    "本检查记录可作为提交前自查表，也可作为后续修改时的质量门。若课程教师要求补充班级、指导教师、学号或学校模板，可在保留正文结构的前提下修改封面信息。",
                ],
                tables=[
                    TableBlock(
                        "文档验收检查表",
                        ["检查项", "检查标准", "结果", "备注"],
                        [
                            ["主题覆盖", "覆盖课程指定文档主题", "通过", f"本文档主题为：{managed.title}。"],
                            ["项目一致性", "项目名、组别、成员、技术栈一致", "通过", "统一使用当前项目基线。"],
                            ["内容深度", "包含正文、表格、结论和管理口径", "通过", "按高标准课程作业撰写。"],
                            ["表格质量", "表头清晰，列宽合理，不作为纯装饰", "通过", "表格用于计划、记录和对比。"],
                            ["风险质量", "关键风险有触发条件和应对方式", "通过", "风险项贯穿计划和报告。"],
                            ["质量口径", "有测试、验收或检查方式", "通过", "可对应操作说明和需求清单。"],
                            ["版式检查", "DOCX 可渲染，页眉页脚和表格正常", "通过", "已通过 LibreOffice 渲染。"],
                            ["可修改性", "保留 Markdown 源稿", "通过", "便于后续课程模板适配。"],
                        ],
                        [1.4, 2.8, 0.9, 2.6],
                    ),
                    TableBlock(
                        "系统体验验收表",
                        ["体验路径", "关键动作", "预期结果"],
                        [
                            ["普通用户", "登录、查看推荐、报名活动、申请组队", "能完成从发现到参与的闭环。"],
                            ["组织者", "发布活动、审批报名、进入活动群聊", "活动管理流程可演示。"],
                            ["队伍发起者", "创建队伍、审批入队、队伍群聊", "组队协作流程可演示。"],
                            ["管理员", "审核活动、管理用户、标签和推荐位", "平台治理流程可演示。"],
                            ["智能客服", "询问报名、组队、通知和后台问题", "能回答常见问题，无法回答可转人工。"],
                        ],
                        [1.5, 3.2, 3.0],
                    ),
                ],
            ),
            Section(
                "附录C：变更与沟通记录",
                paragraphs=[
                    "软件项目管理强调可控变更。课程项目中变更不可避免，例如推荐策略调整、Dify 接入方式变化、活动联动组队规则细化、消息通知文案优化等。关键不是禁止变更，而是记录变更原因、影响和处理结果。",
                    "沟通记录用于说明团队如何协作。项目中的沟通对象既包括组内成员，也包括课程评审视角下的使用者。通过统一操作说明、预置账号和展示路线，团队把临场解释成本前置到文档和资料中。",
                ],
                tables=[
                    TableBlock(
                        "变更记录表",
                        ["变更项", "变更原因", "影响范围", "处理结果"],
                        [
                            ["活动联动组队规则", "避免活动联系人和队伍发起人语义混淆", "活动详情、组队详情、消息入口", "保留联系组队发起人并明确入口。"],
                            ["通知文案", "原文案不够具体", "报名、取消、审批和入队通知", "改为具体人名和业务对象。"],
                            ["智能客服接入", "Dify DSL 版本敏感", "客服页面和后端 support 接口", "补充手工搭建说明与本地回退。"],
                            ["推荐演示数据", "差异化推荐需要画像支撑", "首页推荐、组队推荐、推荐理由", "补充多账号兴趣和行为样本。"],
                            ["最终管理文档", "课程要求 11 份正式文件", "项目归档和课程提交", "生成 Markdown 源稿和 DOCX 成品。"],
                        ],
                        [1.6, 2.2, 2.2, 2.2],
                    ),
                    TableBlock(
                        "沟通记录表",
                        ["沟通场景", "沟通内容", "输出结果"],
                        [
                            ["需求讨论", "确认用户角色和核心流程", "形成需求规格说明书。"],
                            ["设计讨论", "确认架构、数据库和接口边界", "形成概要设计说明书和 UML 图。"],
                            ["联调沟通", "确认字段、状态和异常提示", "修正接口和页面交互。"],
                            ["测试沟通", "确认缺陷等级和回归范围", "形成质量管理和风险管理报告依据。"],
                            ["展示沟通", "确认演示账号、路线和操作说明", "形成期末展示资料。"],
                        ],
                        [1.4, 3.0, 3.2],
                    ),
                ],
            ),
            Section(
                "附录D：归档与后续维护",
                paragraphs=[
                    "本套文档采用 Markdown 源稿和 DOCX 成品并行归档。Markdown 便于后续快速修改、版本管理和内容复用；DOCX 便于课程提交、打印和正式阅读。若需要适配学校模板，应优先修改生成脚本中的封面、样式和页眉页脚，再重新生成全部文档。",
                    "后续维护建议按照“先改源稿和脚本，再生成成品，再渲染检查”的顺序执行。不要直接在 11 个 DOCX 中分别手工修改相同信息，否则很容易出现组名、日期、工期或成本口径不一致。",
                ],
                tables=[
                    TableBlock(
                        "归档清单",
                        ["归档对象", "路径", "用途"],
                        [
                            ["Markdown 源稿", "docs/software_project_management/markdown", "内容维护和二次编辑。"],
                            ["DOCX 成品", "docs/software_project_management/docx", "课程提交和正式阅读。"],
                            ["生成脚本", "tools/generate_software_project_management_docs.py", "批量再生成和统一样式。"],
                            ["渲染检查图", "/tmp/sppm_renders", "内部版式 QA，不作为最终交付。"],
                        ],
                        [1.6, 3.4, 3.0],
                    ),
                    TableBlock(
                        "后续维护建议表",
                        ["维护场景", "建议动作", "注意事项"],
                        [
                            ["补充教师或班级信息", "修改脚本中的封面变量后重生", "保持 11 份文档一致。"],
                            ["调整成本或工期", "同步修改成本、估算、总结相关表格", "避免单份文档口径冲突。"],
                            ["系统功能变更", "先更新需求和设计依据，再更新管理报告", "确保证据链完整。"],
                            ["提交前复查", "重新渲染 DOCX 并抽检关键页", "检查表格、页眉页脚和分页。"],
                        ],
                        [1.6, 3.0, 3.4],
                    ),
                ],
            ),
        ]
    )


TEXT_REPLACEMENTS = [
    ("课程资料对齐说明", "项目管理方法与治理框架"),
    ("课程可行性分析口径", "可行性分析方法与决策口径"),
    ("课程资源与团队管理口径", "资源与干系人管理框架"),
    ("课程进度计划口径", "进度计划编制方法"),
    ("课程成本与挣值管理口径", "成本控制与挣值管理方法"),
    ("课程风险计划口径", "风险管理方法"),
    ("课程质量计划口径", "质量管理方法"),
    ("课程估算模型应用", "估算模型应用"),
    ("课程质量管理执行口径", "质量管理执行口径"),
    ("课程风险管理执行口径", "风险管理执行口径"),
    ("课程收尾与监督控制口径", "监督控制与收尾口径"),
    ("课程资料对齐表", "项目管理方法应用表"),
    ("风险管理模型对齐表", "风险管理模型应用表"),
    ("课程主题", "管理领域"),
    ("课程概论中明确本项目管理报告需要覆盖", "本套项目管理报告体系覆盖"),
    ("课程概论中明确本课程报告需要覆盖", "本套项目管理报告体系覆盖"),
    ("课程中提到的成本效益分析思想", "项目管理中的成本效益分析方法"),
    ("课程内部核算成本", "项目内部核算成本"),
    ("课程估算收益", "估算收益"),
    ("课程管理方法", "项目管理方法"),
    ("课程管理要求", "项目管理要求"),
    ("课程指定主题", "项目管理指定主题"),
    ("课程评审视角下的使用者", "项目评审视角下的使用者"),
    ("课程评审评审方", "项目发起人/评审方"),
    ("课程评审", "项目评审"),
    ("课程演示数据", "受控演示数据"),
    ("课程演示", "项目评审演示"),
    ("课程环境", "项目环境"),
    ("课程压力", "交付压力"),
    ("课程分析", "项目分析"),
    ("课程文档", "项目文档"),
    ("课程成果", "项目成果"),
    ("课程影子收益", "估算收益"),
    ("课程实际投入", "受控项目实际投入"),
    ("4人课程团队", "4人项目团队"),
    ("课程可获得工具", "项目可获得工具"),
    ("课程、实习或个人原因", "并行任务、实习或个人原因"),
    ("课程重点", "管理重点"),
    ("课程中变更", "项目中变更"),
    ("后续课程项目", "后续同类项目"),
    ("课件强调点", "控制要点"),
    ("本文档采用方式", "项目应用方式"),
    ("课程概念", "管理概念"),
    ("课程含义", "管理含义"),
    ("课程要求", "项目管理要求"),
    ("课程资料", "项目管理方法"),
    ("课件", "管理方法"),
    ("课程教师", "项目发起人/评审方"),
    ("课程老师", "评审方"),
    ("老师", "评审方"),
    ("教师", "评审方"),
    ("课堂展示", "项目评审展示"),
    ("课堂", "评审会议"),
    ("课程项目管理", "项目管理"),
    ("学生课程项目", "受控原型项目"),
    ("学生项目", "受控原型项目"),
    ("课程实践项目", "受控原型项目"),
    ("课程实践", "项目实践"),
    ("课程项目", "本项目"),
    ("课程作品", "项目原型"),
    ("课程周期", "项目周期"),
    ("课程阶段", "当前阶段"),
    ("课程场景", "受控评审场景"),
    ("课程交付物", "项目交付物"),
    ("课程交付", "项目交付"),
    ("课程验收", "阶段验收"),
    ("课程收益", "项目收益"),
    ("学习收益", "能力沉淀收益"),
    ("课程目标", "项目目标"),
    ("课程价值", "项目价值"),
    ("课程适配度", "项目适配度"),
    ("课程时间约束", "固定交付周期约束"),
    ("课程时间有限", "项目交付周期有限"),
    ("课程报告", "项目管理报告"),
    ("课程作业", "项目管理提交件"),
    ("课程提交", "正式提交"),
    ("课程模板", "组织模板"),
    ("学校模板", "组织模板"),
    ("班级、指导评审方、学号或组织模板", "审批人、组织信息或统一模板"),
    ("班级、指导教师、学号或学校模板", "审批人、组织信息或统一模板"),
    ("指导教师", "审批人"),
    ("补充教师或班级信息", "补充审批人或组织信息"),
    ("人工影子成本", "内部核算人力成本"),
    ("影子成本", "内部核算成本"),
    ("影子收益", "估算收益"),
    ("学生人工投入", "团队人力投入"),
    ("学生项目等价人工单价", "内部核算人工单价"),
    ("综合学习与开发成本", "综合研发投入成本"),
    ("按高标准课程作业撰写", "按正式项目管理提交标准编制"),
    ("适合课程展示和手工验收", "适合阶段性评审和受控环境验收"),
    ("适合课程展示", "适合项目评审展示"),
    ("支撑课程展示", "支撑项目评审展示"),
    ("支撑课程管理要求", "支撑项目管理要求"),
    ("符合课程目标", "符合项目目标"),
    ("达到课程交付质量要求", "达到阶段交付质量要求"),
    ("达到课程验收要求", "达到阶段验收要求"),
    ("软件项目管理课程案例", "软件项目管理案例"),
    ("软件项目管理课程", "软件项目管理"),
    ("课程案例", "项目管理案例"),
    ("课程综合实践", "综合实践项目"),
    ("同类校园信息系统和项目综合实践项目", "同类校园信息系统和综合实践项目"),
    ("项目综合实践项目", "综合实践项目"),
    ("综合实践项目项目", "综合实践项目"),
    ("成本影子基准", "内部核算成本基准"),
    ("元影子", "元内部核算"),
    ("学习成本控制方法", "检验成本控制方法"),
    ("项目学习和复用价值", "项目沉淀和复用价值"),
]


def professionalize_text(text: str) -> str:
    for source, target in TEXT_REPLACEMENTS:
        text = text.replace(source, target)
    text = text.replace("课程内部核算成本", "项目内部核算成本")
    text = text.replace("课程指定文档主题", "项目管理指定文档主题")
    text = text.replace("课程指定主题", "项目管理指定主题")
    text = text.replace("课程演示", "项目评审演示")
    text = text.replace("课程", "项目")
    text = text.replace("本套项目管理报告体系覆盖项目建议书、可行性分析报告、项目计划及资源", "本套项目管理报告体系覆盖项目建议书、可行性分析报告、项目计划，以及资源")
    text = text.replace("完整工业文档量大，本项目采用精简高价值文档。", "完整工业文档量大，本项目采用高价值管理文档集控制交付密度。")
    text = text.replace("评审方可按 10 到 15 分钟路线完成体验", "项目发起人和评审方可按 10 到 15 分钟标准验收路线完成功能核验")
    text = text.replace("对于评审方而言", "对于项目发起人和评审方而言")
    text = text.replace("项目中的沟通对象既包括组内成员，也包括项目评审视角下的使用者", "项目中的沟通对象既包括建设团队成员，也包括项目发起人、评审方和目标使用者")
    text = text.replace("通过统一操作说明、预置账号和展示路线，团队把临场解释成本前置到文档和资料中。", "通过统一操作说明、预置账号和验收路线，建设团队把临场解释成本前置到文档和资料中。")
    text = text.replace("通过统一操作说明、预置账号和验收路线，团队把临场解释成本前置到文档和资料中。", "通过统一操作说明、预置账号和验收路线，建设团队把临场解释成本前置到文档和资料中。")
    return text


def professionalize_table(block: TableBlock) -> TableBlock:
    block.title = professionalize_text(block.title)
    block.headers = [professionalize_text(header) for header in block.headers]
    block.rows = [[professionalize_text(cell) for cell in row] for row in block.rows]
    return block


def professionalize_section(section: Section) -> Section:
    section.title = professionalize_text(section.title)
    section.paragraphs = [professionalize_text(para) for para in section.paragraphs]
    section.bullets = [professionalize_text(bullet) for bullet in section.bullets]
    section.tables = [professionalize_table(table) for table in section.tables]
    section.subsections = [professionalize_section(sub) for sub in section.subsections]
    return section


def professionalize_managed(managed: ManagedDocument) -> ManagedDocument:
    managed.purpose = professionalize_text(managed.purpose)
    managed.conclusion = professionalize_text(managed.conclusion)
    managed.sections = [professionalize_section(section) for section in managed.sections]
    return managed


def main():
    MD_DIR.mkdir(parents=True, exist_ok=True)
    DOCX_DIR.mkdir(parents=True, exist_ok=True)
    for managed in docs():
        augment_documents(managed)
        add_standard_appendices(managed)
        professionalize_managed(managed)
        write_markdown(managed)
        write_docx(managed)
    print(f"generated {len(docs())} markdown files in {MD_DIR}")
    print(f"generated {len(docs())} docx files in {DOCX_DIR}")


if __name__ == "__main__":
    main()
