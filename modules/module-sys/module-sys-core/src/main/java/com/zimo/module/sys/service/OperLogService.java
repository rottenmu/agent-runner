package com.zimo.module.sys.service;

import com.zimo.module.sys.entity.SysOperLog;
import com.zimo.module.sys.mapper.SysOperLogMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Operation log service that persists sys_oper_log audit entities.
 */
public class OperLogService {

    private final Repository repository;
    private final boolean enabled;

    public OperLogService() {
        this(new InMemoryRepository(), true);
    }

    public OperLogService(Repository repository) {
        this(repository, true);
    }

    public OperLogService(Repository repository, boolean enabled) {
        this.repository = Objects.requireNonNullElseGet(repository, InMemoryRepository::new);
        this.enabled = enabled;
    }

    public void save(SysOperLog operLog) {
        if (!enabled || operLog == null) {
            return;
        }
        if (operLog.getCreateTime() == null) {
            operLog.setCreateTime(LocalDateTime.now());
        }
        repository.save(operLog);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public interface Repository {

        void save(SysOperLog operLog);
    }

    public static class InMemoryRepository implements Repository {

        private final List<SysOperLog> logs = new ArrayList<>();

        @Override
        public void save(SysOperLog operLog) {
            logs.add(operLog);
        }

        public List<SysOperLog> findAll() {
            return Collections.unmodifiableList(logs);
        }

        public void clear() {
            logs.clear();
        }
    }

    public static class MybatisRepository implements Repository {

        private final SysOperLogMapper mapper;

        public MybatisRepository(SysOperLogMapper mapper) {
            this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        }

        @Override
        public void save(SysOperLog operLog) {
            mapper.insert(operLog);
        }
    }
}
