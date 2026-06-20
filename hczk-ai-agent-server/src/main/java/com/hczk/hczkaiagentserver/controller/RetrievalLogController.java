package com.hczk.hczkaiagentserver.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.IngestLog;
import com.hczk.hczkaiagentserver.entity.RetrievalLog;
import com.hczk.hczkaiagentserver.entity.ServiceHealthMetric;
import com.hczk.hczkaiagentserver.mapper.IngestLogMapper;
import com.hczk.hczkaiagentserver.mapper.RetrievalLogMapper;
import com.hczk.hczkaiagentserver.mapper.ServiceHealthMetricMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/retrieval-logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RetrievalLogController {

    private final RetrievalLogMapper retrievalLogMapper;
    private final IngestLogMapper ingestLogMapper;
    private final ServiceHealthMetricMapper serviceHealthMetricMapper;

    // ===== 检索日志 =====

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String strategy,
            @RequestParam(required = false) Boolean hitSuccess,
            @RequestParam(required = false) Boolean adopted,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        LambdaQueryWrapper<RetrievalLog> wrapper = new LambdaQueryWrapper<RetrievalLog>()
                .orderByDesc(RetrievalLog::getCreatedAt);
        if (agentId != null && !agentId.isBlank()) wrapper.eq(RetrievalLog::getAgentId, agentId);
        if (strategy != null && !strategy.isBlank()) wrapper.eq(RetrievalLog::getStrategy, strategy);
        if (hitSuccess != null) wrapper.eq(RetrievalLog::getHitSuccess, hitSuccess);
        if (adopted != null) wrapper.eq(RetrievalLog::getAdopted, adopted);

        IPage<RetrievalLog> pageResult = retrievalLogMapper.selectPage(
                new Page<>(page, size), wrapper);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("records", pageResult.getRecords());
        response.put("total", pageResult.getTotal());
        response.put("page", page);
        response.put("size", size);
        response.put("pages", pageResult.getPages());
        return Result.success(response);
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        LambdaQueryWrapper<RetrievalLog> wrapper = new LambdaQueryWrapper<>();
        if (agentId != null && !agentId.isBlank()) wrapper.eq(RetrievalLog::getAgentId, agentId);
        if (startDate != null) wrapper.ge(RetrievalLog::getCreatedAt, startDate.atStartOfDay());
        if (endDate != null) wrapper.le(RetrievalLog::getCreatedAt, endDate.plusDays(1).atStartOfDay());

        List<RetrievalLog> allLogs = retrievalLogMapper.selectList(wrapper);

        long total = allLogs.size();
        long hitCount = allLogs.stream().filter(l -> Boolean.TRUE.equals(l.getHitSuccess())).count();
        double hitRate = total > 0 ? (double) hitCount / total : 0;
        double avgDuration = allLogs.stream()
                .filter(l -> l.getDurationMs() != null)
                .mapToLong(RetrievalLog::getDurationMs).average().orElse(0);
        double avgConfidence = allLogs.stream()
                .filter(l -> l.getConfidence() != null)
                .mapToDouble(RetrievalLog::getConfidence).average().orElse(0);
        double avgScore = allLogs.stream()
                .filter(l -> l.getAvgScore() != null && l.getAvgScore() > 0)
                .mapToDouble(RetrievalLog::getAvgScore).average().orElse(0);
        double avgDistance = allLogs.stream()
                .filter(l -> l.getAvgDistance() != null)
                .mapToDouble(RetrievalLog::getAvgDistance).average().orElse(0);

        // 策略分布
        Map<String, Long> strategyDist = new LinkedHashMap<>();
        for (RetrievalLog l : allLogs) {
            String s = l.getStrategy() != null ? l.getStrategy() : "unknown";
            strategyDist.merge(s, 1L, Long::sum);
        }

        // 块类型分布（汇总所有记录的 chunkTypeDist）
        Map<String, Long> chunkTypeDist = new LinkedHashMap<>();
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            for (RetrievalLog l : allLogs) {
                if (l.getChunkTypeDist() != null && !l.getChunkTypeDist().isBlank()) {
                    Map<String, Integer> dist = om.readValue(l.getChunkTypeDist(), Map.class);
                    dist.forEach((k, v) -> chunkTypeDist.merge(k, v.longValue(), Long::sum));
                }
            }
        } catch (Exception ignored) {}

        // 采纳统计
        long adoptedCount = allLogs.stream().filter(l -> Boolean.TRUE.equals(l.getAdopted())).count();
        long rejectedCount = allLogs.stream().filter(l -> Boolean.FALSE.equals(l.getAdopted())).count();
        long noFeedbackCount = allLogs.stream().filter(l -> l.getAdopted() == null).count();

        long fallbackCount = allLogs.stream().filter(l -> Boolean.TRUE.equals(l.getFallbackUsed())).count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", total);
        response.put("hitCount", hitCount);
        response.put("missCount", total - hitCount);
        response.put("hitRate", Math.round(hitRate * 10000) / 100.0);
        response.put("fallbackCount", fallbackCount);
        response.put("avgDurationMs", Math.round(avgDuration));
        response.put("avgConfidence", Math.round(avgConfidence * 100) / 100.0);
        response.put("avgScore", Math.round(avgScore * 10000) / 10000.0);
        response.put("avgDistance", Math.round(avgDistance * 10000) / 10000.0);
        response.put("strategyDistribution", strategyDist);
        response.put("chunkTypeDistribution", chunkTypeDist);
        response.put("adoptedCount", adoptedCount);
        response.put("rejectedCount", rejectedCount);
        response.put("noFeedbackCount", noFeedbackCount);
        return Result.success(response);
    }

    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(
            @RequestParam(required = false) String agentId,
            @RequestParam(defaultValue = "30") int days) {

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<RetrievalLog> wrapper = new LambdaQueryWrapper<RetrievalLog>()
                .ge(RetrievalLog::getCreatedAt, since)
                .orderByAsc(RetrievalLog::getCreatedAt);
        if (agentId != null && !agentId.isBlank()) wrapper.eq(RetrievalLog::getAgentId, agentId);

        List<RetrievalLog> logs = retrievalLogMapper.selectList(wrapper);

        Map<String, long[]> daily = new TreeMap<>();
        Map<String, List<Double>> dailyScores = new TreeMap<>();
        for (RetrievalLog l : logs) {
            if (l.getCreatedAt() == null) continue;
            String day = l.getCreatedAt().toLocalDate().toString();
            long[] arr = daily.computeIfAbsent(day, k -> new long[2]);
            arr[0]++;
            if (Boolean.TRUE.equals(l.getHitSuccess())) arr[1]++;
            if (l.getAvgScore() != null && l.getAvgScore() > 0) {
                dailyScores.computeIfAbsent(day, k -> new ArrayList<>()).add(l.getAvgScore());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : daily.entrySet()) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", entry.getKey());
            point.put("total", entry.getValue()[0]);
            point.put("hit", entry.getValue()[1]);
            point.put("hitRate", entry.getValue()[0] > 0
                    ? Math.round((double) entry.getValue()[1] / entry.getValue()[0] * 10000) / 100.0 : 0);
            List<Double> scores = dailyScores.getOrDefault(entry.getKey(), List.of());
            point.put("avgScore", scores.isEmpty() ? 0
                    : Math.round(scores.stream().mapToDouble(d -> d).average().orElse(0) * 10000) / 10000.0);
            result.add(point);
        }
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<RetrievalLog> detail(@PathVariable Long id) {
        return Result.success(retrievalLogMapper.selectById(id));
    }

    /**
     * 用户采纳反馈（采纳/拒绝检索结果）
     */
    @PostMapping("/{id}/adopt")
    public Result<String> adoptFeedback(@PathVariable Long id, @RequestParam boolean adopted) {
        RetrievalLog logEntry = retrievalLogMapper.selectById(id);
        if (logEntry == null) return Result.error("记录不存在");
        logEntry.setAdopted(adopted);
        retrievalLogMapper.updateById(logEntry);
        return Result.success(adopted ? "已标记为采纳" : "已标记为拒绝");
    }

    // ===== 智能体维度排行 =====

    @GetMapping("/agent-ranking")
    public Result<List<Map<String, Object>>> agentRanking(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "20") int limit) {

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<RetrievalLog> wrapper = new LambdaQueryWrapper<RetrievalLog>()
                .ge(RetrievalLog::getCreatedAt, since);
        List<RetrievalLog> logs = retrievalLogMapper.selectList(wrapper);

        // 按 agentId 聚合
        Map<String, List<RetrievalLog>> byAgent = logs.stream()
                .collect(Collectors.groupingBy(l -> l.getAgentId() != null ? l.getAgentId() : "unknown"));

        List<Map<String, Object>> ranking = new ArrayList<>();
        for (Map.Entry<String, List<RetrievalLog>> entry : byAgent.entrySet()) {
            List<RetrievalLog> agentLogs = entry.getValue();
            long total = agentLogs.size();
            long hit = agentLogs.stream().filter(l -> Boolean.TRUE.equals(l.getHitSuccess())).count();
            double avgDur = agentLogs.stream().mapToLong(l -> l.getDurationMs() != null ? l.getDurationMs() : 0).average().orElse(0);
            double avgScore = agentLogs.stream()
                    .filter(l -> l.getAvgScore() != null && l.getAvgScore() > 0)
                    .mapToDouble(RetrievalLog::getAvgScore).average().orElse(0);
            long fallback = agentLogs.stream().filter(l -> Boolean.TRUE.equals(l.getFallbackUsed())).count();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("agentId", entry.getKey());
            item.put("total", total);
            item.put("hitCount", hit);
            item.put("hitRate", total > 0 ? Math.round((double) hit / total * 10000) / 100.0 : 0);
            item.put("avgDurationMs", Math.round(avgDur));
            item.put("avgScore", Math.round(avgScore * 10000) / 10000.0);
            item.put("fallbackCount", fallback);
            ranking.add(item);
        }

        // 按检索次数降序
        ranking.sort((a, b) -> Long.compare((long) b.get("total"), (long) a.get("total")));
        return Result.success(ranking.stream().limit(limit).toList());
    }

    // ===== 异常告警 =====

    @GetMapping("/alerts")
    public Result<Map<String, Object>> alerts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        // 最近 24 小时的异常检索记录
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        LambdaQueryWrapper<RetrievalLog> wrapper = new LambdaQueryWrapper<RetrievalLog>()
                .ge(RetrievalLog::getCreatedAt, since)
                .and(w -> w.eq(RetrievalLog::getHitSuccess, false)
                        .or().eq(RetrievalLog::getFallbackUsed, true))
                .orderByDesc(RetrievalLog::getCreatedAt);

        IPage<RetrievalLog> pageResult = retrievalLogMapper.selectPage(new Page<>(page, size), wrapper);

        List<Map<String, Object>> alerts = pageResult.getRecords().stream().map(l -> {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("id", l.getId());
            alert.put("agentId", l.getAgentId());
            alert.put("query", l.getQuery());
            alert.put("strategy", l.getStrategy());
            alert.put("hitSuccess", l.getHitSuccess());
            alert.put("fallbackUsed", l.getFallbackUsed());
            alert.put("durationMs", l.getDurationMs());
            alert.put("createdAt", l.getCreatedAt());
            // 告警类型
            String alertType = "";
            if (Boolean.TRUE.equals(l.getFallbackUsed())) alertType = "降级到兜底回复";
            else if (!Boolean.TRUE.equals(l.getHitSuccess())) alertType = "检索未命中";
            alert.put("alertType", alertType);
            return alert;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("records", alerts);
        response.put("total", pageResult.getTotal());
        response.put("page", page);
        response.put("size", size);
        return Result.success(response);
    }

    // ===== 入库操作日志 =====

    @GetMapping("/ingest-logs")
    public Result<Map<String, Object>> ingestLogs(
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        LambdaQueryWrapper<IngestLog> wrapper = new LambdaQueryWrapper<IngestLog>()
                .orderByDesc(IngestLog::getCreatedAt);
        if (agentId != null && !agentId.isBlank()) wrapper.eq(IngestLog::getAgentId, agentId);
        if (status != null && !status.isBlank()) wrapper.eq(IngestLog::getStatus, status);

        IPage<IngestLog> pageResult = ingestLogMapper.selectPage(new Page<>(page, size), wrapper);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("records", pageResult.getRecords());
        response.put("total", pageResult.getTotal());
        response.put("page", page);
        response.put("size", size);
        return Result.success(response);
    }

    // ===== 服务健康指标 =====

    @GetMapping("/service-health")
    public Result<Map<String, Object>> serviceHealth(
            @RequestParam(required = false) String serviceName,
            @RequestParam(defaultValue = "24") int hours) {

        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        LambdaQueryWrapper<ServiceHealthMetric> wrapper = new LambdaQueryWrapper<ServiceHealthMetric>()
                .ge(ServiceHealthMetric::getCreatedAt, since);
        if (serviceName != null && !serviceName.isBlank()) wrapper.eq(ServiceHealthMetric::getServiceName, serviceName);

        List<ServiceHealthMetric> metrics = serviceHealthMetricMapper.selectList(wrapper);

        // 按服务名聚合
        Map<String, Map<String, Object>> byService = new LinkedHashMap<>();
        for (ServiceHealthMetric m : metrics) {
            String key = m.getServiceName();
            Map<String, Object> stat = byService.computeIfAbsent(key, k -> {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("serviceName", key);
                s.put("totalCalls", 0L);
                s.put("successCalls", 0L);
                s.put("failCalls", 0L);
                s.put("totalDurationMs", 0L);
                return s;
            });
            stat.put("totalCalls", (long) stat.get("totalCalls") + 1);
            if (Boolean.TRUE.equals(m.getSuccess())) {
                stat.put("successCalls", (long) stat.get("successCalls") + 1);
            } else {
                stat.put("failCalls", (long) stat.get("failCalls") + 1);
            }
            stat.put("totalDurationMs", (long) stat.get("totalDurationMs") + (m.getDurationMs() != null ? m.getDurationMs() : 0));
        }

        List<Map<String, Object>> services = new ArrayList<>(byService.values());
        for (Map<String, Object> s : services) {
            long total = (long) s.get("totalCalls");
            long success = (long) s.get("successCalls");
            long totalDur = (long) s.get("totalDurationMs");
            s.put("successRate", total > 0 ? Math.round((double) success / total * 10000) / 100.0 : 0);
            s.put("avgDurationMs", total > 0 ? Math.round((double) totalDur / total) : 0);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("services", services);
        response.put("hours", hours);
        return Result.success(response);
    }
}
