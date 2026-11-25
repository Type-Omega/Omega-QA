package com.typeomega.qa.wand;

import com.typeomega.lib.lang.Tr;

/**
 * Trilingual texts for the Omega QA Wand, visual editor, and {@code /omegaqa} command suite.
 */
public final class QaWandTexts {

    public static final Tr WAND_NAME = Tr.of("omegaqa.item.omega_wand",
            "Omega QA Wand", "Varinha Omega QA", "Omega QA 魔杖");
    public static final Tr WAND_TOOLTIP_BOUNDS = Tr.of("omegaqa.wand.tooltip.bounds",
            "Selection: %s to %s (%s)", "Seleção: %s até %s (%s)", "选区：%s 至 %s（%s）");
    public static final Tr WAND_TOOLTIP_EMPTY = Tr.of("omegaqa.wand.tooltip.empty",
            "Left-click: pos1 | Right-click: pos2 | Right-click air: Editor",
            "Clique esq.: pos1 | Clique dir.: pos2 | Clique dir. no ar: Editor",
            "左键：设点1 | 右键：设点2 | 对空右键：打开编辑器");
    public static final Tr WAND_TOOLTIP_LABELS = Tr.of("omegaqa.wand.tooltip.labels",
            "Labels: %s | Export: %s", "Labels: %s | Exportação: %s", "标记：%s | 导出：%s");
    public static final Tr WAND_POS1_SET = Tr.of("omegaqa.wand.pos1_set",
            "Omega Wand pos1 set to %s", "Omega Wand pos1 definido em %s", "Omega 魔杖点1已设为 %s");
    public static final Tr WAND_POS2_SET = Tr.of("omegaqa.wand.pos2_set",
            "Omega Wand pos2 set to %s", "Omega Wand pos2 definido em %s", "Omega 魔杖点2已设为 %s");

    public static final Tr CMD_WAND_GIVEN = Tr.of("omegaqa.cmd.wand_given",
            "Given Omega QA Wand.", "Varinha Omega QA entregue.", "已给予 Omega QA 魔杖。");
    public static final Tr CMD_NO_WAND = Tr.of("omegaqa.cmd.no_wand",
            "Hold the Omega QA Wand in your main hand.",
            "Segure a Varinha Omega QA na mão principal.",
            "请将 Omega QA 魔杖握在主手中。");
    public static final Tr CMD_NO_BOUNDS = Tr.of("omegaqa.cmd.no_bounds",
            "Select both pos1 and pos2 with the Omega QA Wand first.",
            "Selecione pos1 e pos2 com a Varinha Omega QA primeiro.",
            "请先使用 Omega QA 魔杖选择点1与点2。");
    public static final Tr CMD_EXPORTED = Tr.of("omegaqa.cmd.exported",
            "Exported %s (%s blocks, %s labels) to %s",
            "Exportado %s (%s blocos, %s labels) em %s",
            "已导出 %s（%s 个方块，%s 个标记）至 %s");
    public static final Tr CMD_LOADED = Tr.of("omegaqa.cmd.loaded",
            "Loaded %s (%s) at %s with %s labels.",
            "Carregado %s (%s) em %s com %s labels.",
            "已在 %s 加载 %s（%s），包含 %s 个标记。");
    public static final Tr CMD_RUNNING_TESTS = Tr.of("omegaqa.cmd.running_tests",
            "Started %s Omega-QA GameTest(s).",
            "Iniciado(s) %s GameTest(s) Omega-QA.",
            "已启动 %s 个 Omega-QA GameTest。");
    public static final Tr CMD_NO_TESTS_MATCH = Tr.of("omegaqa.cmd.no_tests",
            "No GameTests matched selector '%s'.",
            "Nenhum GameTest corresponde ao seletor '%s'.",
            "没有匹配选择器 '%s' 的 GameTest。");
    public static final Tr CMD_NO_REPORT = Tr.of("omegaqa.cmd.no_report",
            "No Omega-QA report snapshot available yet.",
            "Nenhum relatório Omega-QA disponível ainda.",
            "尚无可用的 Omega-QA 报告快照。");
    public static final Tr CMD_REPORT_SUMMARY = Tr.of("omegaqa.cmd.report_summary",
            "Omega-QA Report [%s]: %s total (%s passed, %s failed, %s errors, %s skipped)",
            "Relatório Omega-QA [%s]: %s total (%s passou, %s falhou, %s erros, %s ignorados)",
            "Omega-QA 报告 [%s]：共 %s 项（通过 %s，失败 %s，错误 %s，跳过 %s）");

    public static final Tr EDITOR_TITLE = Tr.of("omegaqa.editor.title",
            "Omega QA Wand Editor", "Editor da Varinha Omega QA", "Omega QA 魔杖编辑器");
    public static final Tr EDITOR_TOOL_MOVE = Tr.of("omegaqa.editor.tool.move",
            "Move", "Mover", "移动");
    public static final Tr EDITOR_TOOL_RESIZE = Tr.of("omegaqa.editor.tool.resize",
            "Resize", "Ajustar", "调整尺寸");
    public static final Tr EDITOR_TOOL_MOVE_LABEL = Tr.of("omegaqa.editor.tool.move_label",
            "Label", "Label", "移动标记");
    public static final Tr EDITOR_ADD_LABEL = Tr.of("omegaqa.editor.add_label",
            "+ Label", "+ Label", "+ 标记");
    public static final Tr EDITOR_RENAME = Tr.of("omegaqa.editor.rename",
            "Rename", "Renomear", "重命名");
    public static final Tr EDITOR_DELETE = Tr.of("omegaqa.editor.delete",
            "Delete", "Remover", "删除");
    public static final Tr EDITOR_ROLE = Tr.of("omegaqa.editor.role",
            "Role", "Papel", "角色");
    public static final Tr EDITOR_EXPORT = Tr.of("omegaqa.editor.export",
            "Export", "Exportar", "导出");
    public static final Tr EDITOR_FREECAM = Tr.of("omegaqa.editor.freecam",
            "Freecam", "Câmera Livre", "自由视角");

    public static final Tr ROLE_CONTROLLER = Tr.of("omegaqa.role.controller",
            "Controller", "Controlador", "控制器");
    public static final Tr ROLE_ENERGY_HATCH = Tr.of("omegaqa.role.energy_hatch",
            "Energy Hatch", "Hatch de Energia", "能源仓");
    public static final Tr ROLE_DYNAMO_HATCH = Tr.of("omegaqa.role.dynamo_hatch",
            "Dynamo Hatch", "Hatch Dínamo", "动力仓");
    public static final Tr ROLE_INPUT_BUS = Tr.of("omegaqa.role.input_bus",
            "Input Bus", "Barramento de Entrada", "输入总线");
    public static final Tr ROLE_OUTPUT_BUS = Tr.of("omegaqa.role.output_bus",
            "Output Bus", "Barramento de Saída", "输出总线");
    public static final Tr ROLE_INPUT_HATCH = Tr.of("omegaqa.role.input_hatch",
            "Input Hatch", "Hatch de Entrada", "输入仓");
    public static final Tr ROLE_OUTPUT_HATCH = Tr.of("omegaqa.role.output_hatch",
            "Output Hatch", "Hatch de Saída", "输出仓");
    public static final Tr ROLE_MAINTENANCE_HATCH = Tr.of("omegaqa.role.maintenance_hatch",
            "Maintenance Hatch", "Hatch de Manutenção", "维护仓");
    public static final Tr ROLE_MUFFLER_HATCH = Tr.of("omegaqa.role.muffler_hatch",
            "Muffler Hatch", "Hatch Silenciador", "消声仓");
    public static final Tr ROLE_ME_BUS = Tr.of("omegaqa.role.me_bus",
            "ME Bus", "Barramento ME", "ME 总线");
    public static final Tr ROLE_CUSTOM = Tr.of("omegaqa.role.custom",
            "Custom", "Personalizado", "自定义");

    public static final Tr REPORT_GROUP_FAILED = Tr.of("omegaqa.report.failed",
            "Failed / Errors", "Falhas / Erros", "失败 / 错误");
    public static final Tr REPORT_GROUP_SKIPPED = Tr.of("omegaqa.report.skipped",
            "Skipped", "Ignorados", "已跳过");
    public static final Tr REPORT_GROUP_PASSED = Tr.of("omegaqa.report.passed",
            "Passed", "Aprovados", "已通过");
    public static final Tr REPORT_GROUP_BENCH = Tr.of("omegaqa.report.bench",
            "Benchmarks", "Benchmarks", "性能基准");
    public static final Tr REPORT_TITLE = Tr.of("omegaqa.report.title",
            "Omega-QA Report", "Relatório Omega-QA", "Omega-QA 报告");

    private QaWandTexts() {}

    public static void init() {}
}
