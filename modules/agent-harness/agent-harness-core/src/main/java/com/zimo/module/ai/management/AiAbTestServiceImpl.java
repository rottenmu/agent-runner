package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zimo.module.ai.mapper.AiAbTestMapper;
import org.springframework.stereotype.Service;

/**
 * 提示词 AB 测试服务实现。
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@Service
public class AiAbTestServiceImpl extends ServiceImpl<AiAbTestMapper, AiAbTest>
        implements AiAbTestService {
}
