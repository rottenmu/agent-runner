package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zimo.module.ai.mapper.AiMcpConfigMapper;
import org.springframework.stereotype.Service;

/**
 * MCP 配置服务实现。
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@Service
public class AiMcpConfigServiceImpl extends ServiceImpl<AiMcpConfigMapper, AiMcpConfig>
        implements AiMcpConfigService {
}
