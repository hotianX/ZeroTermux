package com.termux.zerocore.ai.provider;

import com.termux.zerocore.ai.model.ProviderStreamEvent;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ProviderParserTest {

    @Test
    public void openAiCompatibleResponseExtractsContentReasoningAndToolCalls() throws Exception {
        String body = "{\"choices\":[{\"message\":{\"content\":\"hello\","
            + "\"reasoning_content\":\"thought\",\"tool_calls\":[{\"id\":\"call_1\","
            + "\"function\":{\"name\":\"lookup\",\"arguments\":\"{\\\"q\\\":\\\"x\\\"}\"}}]},"
            + "\"finish_reason\":\"tool_calls\"}]}";

        List<ProviderStreamEvent> events = new OpenAIProvider().parseResponseEvents(body);

        Assert.assertTrue(hasEvent(events, ProviderStreamEvent.TYPE_CONTENT_DELTA, "hello"));
        Assert.assertTrue(hasEvent(events, ProviderStreamEvent.TYPE_REASONING_DELTA, "thought"));
        Assert.assertTrue(events.stream().anyMatch(e -> ProviderStreamEvent.TYPE_TOOL_CALL_DELTA.equals(e.getType())
            && "call_1".equals(e.getToolCallId()) && "lookup".equals(e.getToolName())
            && "{\"q\":\"x\"}".equals(e.getToolArgumentsDelta())));
        Assert.assertTrue(events.stream().anyMatch(e -> ProviderStreamEvent.TYPE_DONE.equals(e.getType())
            && "tool_calls".equals(e.getFinishReason())));
    }

    @Test
    public void claudeResponseExtractsTextThinkingAndToolUse() throws Exception {
        String body = "{\"content\":[{\"type\":\"thinking\",\"thinking\":\"chain\"},"
            + "{\"type\":\"text\",\"text\":\"answer\"},"
            + "{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"search\",\"input\":{\"q\":\"x\"}}],"
            + "\"stop_reason\":\"end_turn\"}";

        List<ProviderStreamEvent> events = new ClaudeProvider().parseResponseEvents(body);

        Assert.assertTrue(hasEvent(events, ProviderStreamEvent.TYPE_REASONING_DELTA, "chain"));
        Assert.assertTrue(hasEvent(events, ProviderStreamEvent.TYPE_CONTENT_DELTA, "answer"));
        Assert.assertTrue(events.stream().anyMatch(e -> ProviderStreamEvent.TYPE_TOOL_CALL_DELTA.equals(e.getType())
            && "toolu_1".equals(e.getToolCallId()) && "search".equals(e.getToolName())
            && "{\"q\":\"x\"}".equals(e.getToolArgumentsDelta())));
    }

    @Test
    public void geminiResponseExtractsTextAndFunctionCall() throws Exception {
        String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"answer\"},"
            + "{\"functionCall\":{\"name\":\"lookup\",\"args\":{\"q\":\"x\"}}}]},"
            + "\"finishReason\":\"STOP\"}]}";

        List<ProviderStreamEvent> events = new GeminiProvider().parseResponseEvents(body);

        Assert.assertTrue(hasEvent(events, ProviderStreamEvent.TYPE_CONTENT_DELTA, "answer"));
        Assert.assertTrue(events.stream().anyMatch(e -> ProviderStreamEvent.TYPE_TOOL_CALL_DELTA.equals(e.getType())
            && "lookup".equals(e.getToolName()) && "{\"q\":\"x\"}".equals(e.getToolArgumentsDelta())));
        Assert.assertTrue(events.stream().anyMatch(e -> ProviderStreamEvent.TYPE_DONE.equals(e.getType())
            && "STOP".equals(e.getFinishReason())));
    }

    @Test
    public void toolNoticeIsExactDisplayOnlyChineseText() {
        Assert.assertEquals("\u4ec5\u5c55\u793a\uff0c\u4e0d\u4f1a\u81ea\u52a8\u6267\u884c", ProviderStreamEvent.TOOL_DISPLAY_ONLY_NOTICE);
    }

    private boolean hasEvent(List<ProviderStreamEvent> events, String type, String text) {
        return events.stream().anyMatch(e -> type.equals(e.getType())
            && (text.equals(e.getTextDelta()) || text.equals(e.getReasoningDelta())));
    }
}
