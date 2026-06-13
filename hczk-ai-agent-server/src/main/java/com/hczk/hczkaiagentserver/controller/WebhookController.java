package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.enums.PlatformType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/webhook")
@CrossOrigin(origins = "*")
public class WebhookController {

    @PostMapping("/meituan")
    public Result<String> handleMeituanWebhook(@RequestBody Map<String, Object> payload) {
        log.info("收到美团Webhook: {}", payload);
        return Result.success("received");
    }

    @PostMapping("/douyin")
    public Result<String> handleDouyinWebhook(@RequestBody Map<String, Object> payload) {
        log.info("收到抖音Webhook: {}", payload);
        return Result.success("received");
    }
}
