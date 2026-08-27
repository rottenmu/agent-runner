package com.zimo.module.trace.genai;

import java.util.List;

/**
 * 默认导出器：结构化日志输出（traceId + span 摘要 + 关键 gen_ai 属性）。
 */
public class LogGenAiSpanExporter implements GenAiSpanExporter {

    @Override
    public void export(List<GenAiSpan> spans) {
        for (GenAiSpan span : spans) {
            System.out.println("[genai-span] trace=" + span.traceId()
                    + " | " + span.summary()
                    + " | model=" + span.attributes().get(GenAiAttributeNames.REQUEST_MODEL)
                    + " | in=" + span.attributes().get(GenAiAttributeNames.USAGE_INPUT_TOKENS)
                    + " | out=" + span.attributes().get(GenAiAttributeNames.USAGE_OUTPUT_TOKENS)
                    + " | session=" + span.attributes().get(GenAiAttributeNames.SESSION_ID)
                    + " | agent=" + span.attributes().get(GenAiAttributeNames.AGENT_NAME));
        }
    }
}
