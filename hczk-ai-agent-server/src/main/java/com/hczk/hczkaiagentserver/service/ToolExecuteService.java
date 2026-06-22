package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.knowledge.controller.KnowledgeController;
import com.hczk.hczkaiagentserver.knowledge.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 工具执行服务
 * 根据工具名称和参数执行内置工具（知识库操作等）或 CLI-Anything 工具
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolExecuteService {

    private final KnowledgeController knowledgeController;
    private final CliAnythingService cliAnythingService;

    /**
     * 执行内置工具
     * @param toolName 工具名称
     * @param arguments 工具参数
     * @param authenticatedUserId 认证上下文中的 userId（用于数据隔离，禁止从 arguments 回退）
     * @return 执行结果
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(String toolName, Map<String, Object> arguments, String authenticatedUserId) {
        log.info("执行工具: name={}, userId={}, arguments={}", toolName, authenticatedUserId, arguments);

        try {
            // CLI-Anything 工具：名称以 "cli-anything-" 开头
            if (toolName.startsWith("cli-anything-")) {
                return executeCliAnythingTool(toolName, arguments);
            }

            return switch (toolName) {
                case "knowledge_search" -> executeKnowledgeSearch(arguments, authenticatedUserId);
                case "knowledge_ingest" -> executeKnowledgeIngest(arguments, authenticatedUserId);
                case "knowledge_ingest_file" -> executeKnowledgeIngestFile(arguments, authenticatedUserId);
                case "knowledge_list_collections" -> executeListCollections(arguments, authenticatedUserId);
                case "knowledge_get_chunks" -> executeGetChunks(arguments, authenticatedUserId);
                // CLI 工具市场 Skill 的内置工具
                case "cli_tools_list" -> executeCliToolsList();
                case "cli_tools_install" -> executeCliToolsInstall(arguments);
                default -> Map.of("error", "未知工具: " + toolName);
            };
        } catch (Exception e) {
            log.error("工具执行失败: name={}, error={}", toolName, e.getMessage());
            return Map.of("error", toolName + " 执行失败: " + e.getMessage());
        }
    }

    /**
     * 获取 agentId（雪花ID字符串）
     * 安全修复：仅使用认证上下文传入的 authenticatedUserId，禁止从 args 回退，防止越权访问他人知识库
     */
    private String getAgentId(Map<String, Object> args, String authenticatedUserId) {
        if (authenticatedUserId != null && !authenticatedUserId.isBlank()) {
            return authenticatedUserId;
        }
        return null;
    }

    /**
     * 处理 CLI-Anything 工具调用
     * 平台不执行 CLI 命令，返回元数据让智能体自行安装执行
     */
    private Map<String, Object> executeCliAnythingTool(String toolName, Map<String, Object> arguments) {
        String stripped = toolName.substring("cli-anything-".length());
        int underscoreIdx = stripped.indexOf('_');
        String cliName;
        String command = null;

        if (underscoreIdx > 0) {
            cliName = stripped.substring(0, underscoreIdx);
            String remainder = stripped.substring(underscoreIdx + 1);
            if (!"execute".equals(remainder)) {
                command = remainder.replace("_", " ");
            }
        } else {
            cliName = stripped;
        }

        if (command == null && arguments != null && arguments.containsKey("command")) {
            command = (String) arguments.get("command");
        }

        // 平台不执行 CLI，返回元数据指引智能体自行安装执行
        Map<String, Object> metadata = cliAnythingService.getCliMetadataForAgent(cliName);
        if (metadata == null) {
            return Map.of("success", false, "error",
                    "CLI 工具未在平台注册或未启用: " + cliName + "。请联系管理员在 CLI 工具市场中启用该工具。");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", "CLI 工具需要在智能体本地执行，平台无法代为执行");
        result.put("cli_name", cliName);
        result.put("command", command);
        result.put("entry_point", metadata.get("entry_point"));
        result.put("install_cmd", metadata.get("install_cmd"));
        result.put("install_guide", metadata.get("install_guide"));
        result.put("message", String.format(
                "请先在本地安装 CLI 工具: %s，然后执行: %s %s",
                metadata.get("install_cmd"), metadata.get("entry_point"), command != null ? command : ""));
        return result;
    }

    /**
     * CLI 工具市场 — 列出可用 CLI 工具（供智能体发现）
     * 仅返回管理员已启用且同步成功的工具
     */
    private Map<String, Object> executeCliToolsList() {
        List<Map<String, Object>> tools = cliAnythingService.getAvailableClisForAgent();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("total", tools.size());
        result.put("tools", tools);
        return result;
    }

    /**
     * CLI 工具市场 — 安装指定 CLI 工具（供智能体按需安装）
     * 创建平台 Skill + ToolDefinition，智能体随后可调用该工具的命令
     */
    private Map<String, Object> executeCliToolsInstall(Map<String, Object> arguments) {
        String cliName = (String) arguments.get("cli_name");
        if (cliName == null || cliName.isBlank()) {
            return Map.of("success", false, "error", "缺少必填参数: cli_name");
        }
        try {
            Map<String, Object> installResult = cliAnythingService.installCliForAgent(cliName);
            installResult.put("success", true);
            return installResult;
        } catch (Exception e) {
            log.error("CLI 工具安装失败: name={}, error={}", cliName, e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    private Map<String, Object> executeKnowledgeSearch(Map<String, Object> args, String authenticatedUserId) {
        String agentId = getAgentId(args, authenticatedUserId);
        if (agentId == null) {
            log.warn("knowledge_search 调用缺少认证 userId，无法定位知识库集合");
            return Map.of("success", false, "error", "未认证，无法识别用户身份");
        }

        RetrieveRequest req = new RetrieveRequest();
        req.setAgentId(agentId);
        req.setQuery((String) args.get("query"));
        req.setCollection((String) args.getOrDefault("collection_name", "default"));
        if (args.containsKey("top_k")) {
            req.setTopK(((Number) args.get("top_k")).intValue());
        }

        Result<RetrieveResponse> result = knowledgeController.retrieve(req);
        if (result.getCode() == 200 && result.getData() != null) {
            RetrieveResponse data = result.getData();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("chunks", data.getResults());
            response.put("total", data.getResults() != null ? data.getResults().size() : 0);
            response.put("strategy", data.getStrategy());
            return response;
        }
        return Map.of("success", false, "error", result.getMessage());
    }

    private Map<String, Object> executeKnowledgeIngest(Map<String, Object> args, String authenticatedUserId) {
        String agentId = getAgentId(args, authenticatedUserId);
        if (agentId == null) {
            return Map.of("success", false, "error", "未认证，无法识别用户身份");
        }

        IngestRequest req = new IngestRequest();
        req.setAgentId(agentId);
        req.setText((String) args.get("text"));
        req.setCollection((String) args.getOrDefault("collection_name", "default"));
        req.setTitle(args.containsKey("title") ? (String) args.get("title") : null);
        req.setSource("tool_call");

        Result<IngestResponse> result = knowledgeController.ingest(req);
        if (result.getCode() == 200 && result.getData() != null) {
            IngestResponse data = result.getData();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("chunks_created", data.getIngestedChunks());
            response.put("total_chunks", data.getTotalChunks());
            response.put("skipped", data.getSkippedChunks());
            response.put("message", "文本已成功摄入到知识库");
            return response;
        }
        return Map.of("success", false, "error", result.getMessage());
    }

    private Map<String, Object> executeKnowledgeIngestFile(Map<String, Object> args, String authenticatedUserId) {
        return Map.of(
            "success", false,
            "message", "文件上传需要直接调用 POST /knowledge/ingest/file 接口（multipart/form-data）",
            "instructions", "请告知用户通过管理界面上传文件，或使用 multipart/form-data 请求 {collection_name, agent_id, doc_type, file}"
        );
    }

    private Map<String, Object> executeListCollections(Map<String, Object> args, String authenticatedUserId) {
        String agentId = getAgentId(args, authenticatedUserId);
        Result<List<CollectionInfo>> result = knowledgeController.listCollections(
                agentId != null && !agentId.isEmpty() ? agentId : null);
        if (result.getCode() == 200 && result.getData() != null) {
            List<Map<String, Object>> collections = new ArrayList<>();
            for (CollectionInfo info : result.getData()) {
                collections.add(Map.of(
                    "name", info.getCollectionName(),
                    "rows", info.getRowCount(),
                    "agent_id", info.getAgentId()
                ));
            }
            return Map.of("success", true, "collections", collections, "total", collections.size());
        }
        return Map.of("success", false, "error", result.getMessage());
    }

    private Map<String, Object> executeGetChunks(Map<String, Object> args, String authenticatedUserId) {
        String collectionName = (String) args.get("collection_name");
        String agentId = getAgentId(args, authenticatedUserId);
        int limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 20;

        Result<List<ChunkItem>> result = knowledgeController.browseChunks(collectionName, agentId, null, null, limit);
        if (result.getCode() == 200 && result.getData() != null) {
            List<Map<String, Object>> chunks = new ArrayList<>();
            for (ChunkItem item : result.getData()) {
                chunks.add(Map.of("id", item.getId() != null ? item.getId() : "",
                        "content", item.getContent() != null ? item.getContent() : "",
                        "source", item.getSource() != null ? item.getSource() : "",
                        "title", item.getTitle() != null ? item.getTitle() : ""));
            }
            return Map.of("success", true, "chunks", chunks, "total", chunks.size());
        }
        return Map.of("success", false, "error", result.getMessage());
    }
}
