package com.hczk.hczkaiagentserver.config;

import com.hczk.hczkaiagentserver.entity.AiModel;
import com.hczk.hczkaiagentserver.entity.User;
import com.hczk.hczkaiagentserver.enums.ModelStatus;
import com.hczk.hczkaiagentserver.enums.UserRole;
import com.hczk.hczkaiagentserver.repository.AiModelRepository;
import com.hczk.hczkaiagentserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AiModelRepository aiModelRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        initUsers();
        initModels();
    }

    private void initUsers() {
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setEmail("admin@hczk.com");
            admin.setRole(UserRole.ADMIN);
            admin.setBalance(new BigDecimal("10000.00"));
            userRepository.save(admin);
        }

        if (!userRepository.existsByUsername("user")) {
            User user = new User();
            user.setUsername("user");
            user.setPassword(passwordEncoder.encode("user123"));
            user.setEmail("user@hczk.com");
            user.setRole(UserRole.USER);
            user.setBalance(new BigDecimal("1250.00"));
            userRepository.save(user);
        }
    }

    private void initModels() {
        if (aiModelRepository.count() == 0) {
            AiModel deepseek = new AiModel();
            deepseek.setName("DeepSeek-V3");
            deepseek.setProvider("DeepSeek");
            deepseek.setModelId("deepseek-chat");
            deepseek.setStatus(ModelStatus.ACTIVE);
            deepseek.setApiBase("https://api.deepseek.com/v1");
            deepseek.setInputPrice(new BigDecimal("0.001"));
            deepseek.setOutputPrice(new BigDecimal("0.002"));
            deepseek.setMaxTokens(8192);
            aiModelRepository.save(deepseek);

            AiModel ernie = new AiModel();
            ernie.setName("ERNIE-4.0-Turbo");
            ernie.setProvider("百度智能云");
            ernie.setModelId("ernie-4.0-turbo-8k");
            ernie.setStatus(ModelStatus.ACTIVE);
            ernie.setApiBase("https://qianfan.baidubce.com/v2");
            ernie.setInputPrice(new BigDecimal("0.008"));
            ernie.setOutputPrice(new BigDecimal("0.008"));
            ernie.setMaxTokens(8192);
            aiModelRepository.save(ernie);

            AiModel qwen = new AiModel();
            qwen.setName("qwen-max");
            qwen.setProvider("阿里云");
            qwen.setModelId("qwen-max");
            qwen.setStatus(ModelStatus.ACTIVE);
            qwen.setApiBase("https://dashscope.aliyuncs.com/compatible-mode/v1");
            qwen.setInputPrice(new BigDecimal("0.02"));
            qwen.setOutputPrice(new BigDecimal("0.02"));
            qwen.setMaxTokens(32768);
            aiModelRepository.save(qwen);

            AiModel glm = new AiModel();
            glm.setName("GLM-4-Plus");
            glm.setProvider("智谱AI");
            glm.setModelId("glm-4-plus");
            glm.setStatus(ModelStatus.INACTIVE);
            glm.setApiBase("https://open.bigmodel.cn/api/paas/v4");
            glm.setInputPrice(new BigDecimal("0.01"));
            glm.setOutputPrice(new BigDecimal("0.01"));
            glm.setMaxTokens(128000);
            aiModelRepository.save(glm);
        }
    }
}
