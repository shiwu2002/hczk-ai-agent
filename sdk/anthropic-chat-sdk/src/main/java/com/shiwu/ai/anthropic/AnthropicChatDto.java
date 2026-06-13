package com.shiwu.ai.anthropic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

public class AnthropicChatDto {

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        private String model;
        @JsonProperty("max_tokens")
        private Integer maxTokens;
        private List<Message> messages;
        private String system;
        @JsonProperty("temperature")
        private Double temperature;
        private Boolean stream;
        private List<Tool> tools;
        @JsonProperty("tool_choice")
        private Object toolChoice;
        private Map<String, Object> thinking;

        public void setStream(boolean stream) {
            this.stream = stream;
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tool {
        private String name;
        private String description;
        @JsonProperty("input_schema")
        private Map<String, Object> inputSchema;

        public Tool() {}

        public Tool(String name, String description, Map<String, Object> inputSchema) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String role;
        private Object content;

        public Message() {}

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public Message(String role, Object content) {
            this.role = role;
            this.content = content;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {
        private String id;
        private String type;
        private String role;
        private List<ContentBlock> content;
        private String model;
        @JsonProperty("stop_reason")
        private String stopReason;
        @JsonProperty("stop_sequence")
        private String stopSequence;
        private Usage usage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentBlock {
        private String type;
        private String text;
        private String id;
        private String name;
        private Object input;
        private ImageSource source;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageSource {
        private String type;
        @JsonProperty("media_type")
        private String mediaType;
        private String data;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolResultContent {
        private String type;
        @JsonProperty("tool_use_id")
        private String toolUseId;
        private Object content;

        public ToolResultContent() {}

        public ToolResultContent(String toolUseId, Object content) {
            this.type = "tool_result";
            this.toolUseId = toolUseId;
            this.content = content;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("input_tokens")
        private Integer inputTokens;
        @JsonProperty("output_tokens")
        private Integer outputTokens;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StreamEvent {
        private String type;
        private StreamMessageStart message;
        @JsonProperty("content_block")
        private StreamContentBlockStart contentBlock;
        private StreamContentBlockDelta delta;
        @JsonProperty("message_delta")
        private StreamMessageDelta messageDelta;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StreamMessageStart {
        private String id;
        private String type;
        private String role;
        private List<ContentBlock> content;
        private String model;
        private Usage usage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StreamContentBlockStart {
        private int index;
        private String type;
        private String text;
        private String id;
        private String name;
        private Object input;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StreamContentBlockDelta {
        private String type;
        private String text;
        @JsonProperty("partial_json")
        private String partialJson;
        private String thinking;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StreamMessageDelta {
        @JsonProperty("stop_reason")
        private String stopReason;
        @JsonProperty("stop_sequence")
        private String stopSequence;
        private Usage usage;
    }
}
