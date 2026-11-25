package com.typeomega.qa.spi;

/**
 * Service-loaded module hook discovered by {@link OmegaQaBootstrap}.
 * <p>
 * // EN: Visual/Wand modules plug in via META-INF/services/com.typeomega.qa.spi.OmegaQaModule with zero merge
 * // conflicts against core QA files.
 * // PT: Módulos visuais/Wand conectam-se via META-INF/services/com.typeomega.qa.spi.OmegaQaModule sem conflito
 * // com os arquivos centrais de QA.
 * // ZH: 视觉与手杖模块通过 META-INF/services/com.typeomega.qa.spi.OmegaQaModule 接入，与核心 QA 文件零冲突。
 */
public interface OmegaQaModule {

    default void onInit() {}
}
