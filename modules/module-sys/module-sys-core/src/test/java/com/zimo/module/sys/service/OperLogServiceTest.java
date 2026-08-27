package com.zimo.module.sys.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.module.sys.entity.SysOperLog;
import com.zimo.module.sys.enums.PermOperTypeEnum;
import org.junit.jupiter.api.Test;

class OperLogServiceTest {

    @Test
    void savesSysOperLogEntityThroughRepository() {
        OperLogService.InMemoryRepository repository = new OperLogService.InMemoryRepository();
        OperLogService service = new OperLogService(repository, true);
        SysOperLog log = new SysOperLog();
        log.setOperType(PermOperTypeEnum.ADD.getCode());
        log.setRequestUri("/api/biz/sys/role");
        log.setOperAccount("zhangsan");
        log.setResultStatus("SUCCESS");

        service.save(log);

        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findAll().get(0).getTableName()).isEqualTo("sys_oper_log");
        assertThat(repository.findAll().get(0).getOperType()).isEqualTo(PermOperTypeEnum.ADD.getCode());
    }

    @Test
    void ignoresSaveWhenOperationLogSwitchDisabled() {
        OperLogService.InMemoryRepository repository = new OperLogService.InMemoryRepository();
        OperLogService service = new OperLogService(repository, false);
        SysOperLog log = new SysOperLog();

        service.save(log);

        assertThat(repository.findAll()).isEmpty();
    }
}
