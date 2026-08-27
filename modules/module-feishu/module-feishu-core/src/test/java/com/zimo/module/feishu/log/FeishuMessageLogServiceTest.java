package com.zimo.module.feishu.log;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zimo.module.feishu.admin.dto.FeishuMessageLogPageRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeishuMessageLogServiceTest {

    @Test
    void recordsLogThroughMapper() {
        FeishuMessageLogMapper mapper = mock(FeishuMessageLogMapper.class);
        FeishuMessageLogService service = new FeishuMessageLogService(mapper, true);
        FeishuMessageLogEntity entity = new FeishuMessageLogEntity();
        entity.setMessageId("msg_1");
        entity.setStage(FeishuMessageLogStage.RECEIVED.name());
        entity.setSuccess(1);

        service.record(entity);

        verify(mapper).insert(entity);
        assertThat(entity.getStage()).isEqualTo(FeishuMessageLogStage.RECEIVED.name());
    }

    @Test
    void skipsWhenDisabled() {
        FeishuMessageLogMapper mapper = mock(FeishuMessageLogMapper.class);
        FeishuMessageLogService service = new FeishuMessageLogService(mapper, false);
        FeishuMessageLogEntity entity = new FeishuMessageLogEntity();

        service.record(entity);

        verify(mapper, never()).insert(entity);
    }

    @Test
    void returnsEmptyPageWhenMapperMissingAndNormalizesBounds() {
        FeishuMessageLogService service = new FeishuMessageLogService(null, true);
        FeishuMessageLogPageRequest request = new FeishuMessageLogPageRequest();
        request.setPageNo(0L);
        request.setPageSize(200L);

        Page<FeishuMessageLogEntity> page = service.page(request);

        assertThat(page.getCurrent()).isEqualTo(1L);
        assertThat(page.getSize()).isEqualTo(100L);
        assertThat(page.getRecords()).isEmpty();
    }

    @Test
    void returnsEmptyPageWhenLogDisabled() {
        FeishuMessageLogMapper mapper = mock(FeishuMessageLogMapper.class);
        FeishuMessageLogService service = new FeishuMessageLogService(mapper, false);
        FeishuMessageLogPageRequest request = new FeishuMessageLogPageRequest();
        request.setPageNo(2L);
        request.setPageSize(5L);

        Page<FeishuMessageLogEntity> page = service.page(request);

        assertThat(page.getCurrent()).isEqualTo(2L);
        assertThat(page.getSize()).isEqualTo(5L);
        assertThat(page.getRecords()).isEmpty();
        verify(mapper, never()).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    void delegatesPageQueryWithFiltersAndDescendingCreateTime() {
        initTableInfo();
        FeishuMessageLogMapper mapper = mock(FeishuMessageLogMapper.class);
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FeishuMessageLogService service = new FeishuMessageLogService(mapper, true);
        FeishuMessageLogPageRequest request = new FeishuMessageLogPageRequest();
        request.setPageNo(null);
        request.setPageSize(0L);
        request.setTenantKey("tenant_a");
        request.setChatId("oc_xxx");
        request.setSenderUserId("ou_xxx");
        request.setStage("DISPATCH");
        request.setSuccess(1);
        request.setCommandText("项目");

        Page<FeishuMessageLogEntity> page = service.page(request);

        assertThat(page.getCurrent()).isEqualTo(1L);
        assertThat(page.getSize()).isEqualTo(10L);
        ArgumentCaptor<Wrapper<FeishuMessageLogEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectPage(any(Page.class), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(sqlSegment)
                .contains("tenant_key")
                .contains("chat_id")
                .contains("sender_user_id")
                .contains("stage")
                .contains("success")
                .contains("command_text")
                .contains("ORDER BY")
                .contains("create_time")
                .contains("DESC");
    }

    private static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                FeishuMessageLogEntity.class);
    }
}
