package com.hczk.hczkaiagentserver.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.RetrievalLog;
import com.hczk.hczkaiagentserver.mapper.RetrievalLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 知识库检索日志 Controller
 * 提供 RAG 检索记录查询、命中率统计、趋势分析接口
 */
@Slf4j
@RestController
@RequestMapping("/retrieval-logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RetrievalLogController {

    private final RetrievalLogMapper retrievalLogMapper;

    /**
     * 分页查询检索记录（支持按智能体、策略、命中状态筛选）
     */
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String strategy,
            @RequestParam(required = false) Boolean hitSuccess,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        LambdaQueryWrapper<RetrievalLog> wrapper = new LambdaQueryWrapper<RetrievalLog>()
                .orderByDesc(RetrievalLog::getCreatedAt);
        if (agentId != null && !agentId.isBlank()) wrapper.eq(RetrievalLog::getAgentId, agentId);
        if (strategy != null && !strategy.isBlank()) wrapper.eq(RetrievalLog::getStrategy, strategy);
        if (hitSuccess != null) wrapper.eq(RetrievalLog::getHitSuccess, hitSuccess);

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

    /**
     * 检索统计概览（总检索次数、命中次数、命中率、平均耗时、策略分布）
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {

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
                .mapToLong(RetrievalLog::getDurationMs)
                .average().orElse(0);
        double avgConfidence = allLogs.stream()
                .filter(l -> l.getConfidence() != null)
                .mapToDouble(RetrievalLog::getConfidence)
                .average().orElse(0);

        // 策略分布
        Map<String, Long> strategyDist = new LinkedHashMap<>();
        for (RetrievalLog l : allLogs) {
            String s = l.getStrategy() != null ? l.getStrategy() : "unknown";
            strategyDist.merge(s, 1L, Long::sum);
        }

        // 降级次数
        long fallbackCount = allLogs.stream().filter(l -> Boolean.TRUE.equals(l.getFallbackUsed())).count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", total);
        response.put("hitCount", hitCount);
        response.put("missCount", total - hitCount);
        response.put("hitRate", Math.round(hitRate * 10000) / 100.0); // 百分比保留2位
        response.put("fallbackCount", fallbackCount);
        response.put("avgDurationMs", Math.round(avgDuration));
        response.put("avgConfidence", Math.round(avgConfidence * 100) / 100.0);
        response.put("strategyDistribution", strategyDist);
        return Result.success(response);
    }

    /**
     * 命中率趋势（按天聚合，最近 N 天）
     */
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

        // 按天聚合
        Map<String, long[]> daily = new TreeMap<>(); // date -> [total, hit]
        for (RetrievalLog l : logs) {
            if (l.getCreatedAt() == null) continue;
            String day = l.getCreatedAt().toLocalDate().toString();
            long[] arr = daily.computeIfAbsent(day, k -> new long[2]);
            arr[0]++;
            if (Boolean.TRUE.equals(l.getHitSuccess())) arr[1]++;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : daily.entrySet()) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", entry.getKey());
            point.put("total", entry.getValue()[0]);
            point.put("hit", entry.getValue()[1]);
            point.put("hitRate", entry.getValue()[0] > 0
                    ? Math.round((double) entry.getValue()[1] / entry.getValue()[0] * 10000) / 100.0
                    : 0);
            result.add(point);
        }
        return Result.success(result);
    }

    /**
     * 查询单条检索记录详情
     */
    @GetMapping("/{id}")
    public Result<RetrievalLog> detail(@PathVariable Long id) {
        return Result.success(retrievalLogMapper.selectById(id));
    }
}
