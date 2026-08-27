package com.zimo.module.sys.apiregistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zimo.framework.common.BizException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SysApiRegistryServiceTest {

    @Test
    void returnsRepositoryItemsWithoutChangingTheirOrder() {
        SysApiRegistryRepository repository = mock(SysApiRegistryRepository.class);
        SysApiRegistryQuery query = new SysApiRegistryQuery("sys", "GET", 1, "用户");
        List<SysApiRegistryItem> items = List.of(item(1L, "sys"), item(2L, "sys"));
        when(repository.find(query)).thenReturn(items);
        SysApiRegistryService service = new SysApiRegistryService(repository);

        assertThat(service.list(query)).containsExactlyElementsOf(items);
    }

    @Test
    void groupsItemsByModuleInRepositoryOrder() {
        SysApiRegistryRepository repository = mock(SysApiRegistryRepository.class);
        when(repository.find(any())).thenReturn(List.of(
                item(1L, "sys"), item(2L, "sys"), item(3L, "wms")));
        SysApiRegistryService service = new SysApiRegistryService(repository);

        List<SysApiRegistryModuleGroup> groups = service.grouped(
                new SysApiRegistryQuery(null, null, null, null));

        assertThat(groups).extracting(SysApiRegistryModuleGroup::moduleCode)
                .containsExactly("sys", "wms");
        assertThat(groups.get(0).apis()).extracting(SysApiRegistryItem::id)
                .containsExactly(1L, 2L);
    }

    @Test
    void rejectsInvalidIdBeforeDatabaseUpdate() {
        SysApiRegistryRepository repository = mock(SysApiRegistryRepository.class);
        SysApiRegistryService service = new SysApiRegistryService(repository);

        assertThatThrownBy(() -> service.updateStatus(0L, 1))
                .isInstanceOf(BizException.class)
                .hasMessage("API 注册信息 ID 必须大于 0")
                .extracting("code").isEqualTo(400);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsUnsupportedStatusBeforeDatabaseUpdate() {
        SysApiRegistryRepository repository = mock(SysApiRegistryRepository.class);
        SysApiRegistryService service = new SysApiRegistryService(repository);

        assertThatThrownBy(() -> service.updateStatus(1L, 2))
                .isInstanceOf(BizException.class)
                .hasMessage("API 状态只允许为 0 或 1")
                .extracting("code").isEqualTo(400);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsNullStatusBeforeDatabaseUpdate() {
        SysApiRegistryRepository repository = mock(SysApiRegistryRepository.class);
        SysApiRegistryService service = new SysApiRegistryService(repository);

        assertThatThrownBy(() -> service.updateStatus(1L, null))
                .isInstanceOf(BizException.class)
                .hasMessage("API 状态只允许为 0 或 1");
        verifyNoInteractions(repository);
    }

    @Test
    void reportsMissingRegistryRow() {
        SysApiRegistryRepository repository = mock(SysApiRegistryRepository.class);
        when(repository.updateStatus(99L, 0)).thenReturn(0);
        SysApiRegistryService service = new SysApiRegistryService(repository);

        assertThatThrownBy(() -> service.updateStatus(99L, 0))
                .isInstanceOf(BizException.class)
                .hasMessage("API 注册信息不存在")
                .extracting("code").isEqualTo(404);
    }

    @Test
    void updatesExistingRegistryStatus() {
        SysApiRegistryRepository repository = mock(SysApiRegistryRepository.class);
        when(repository.updateStatus(7L, 0)).thenReturn(1);
        SysApiRegistryService service = new SysApiRegistryService(repository);

        service.updateStatus(7L, 0);

        verify(repository).updateStatus(7L, 0);
    }

    private static SysApiRegistryItem item(long id, String moduleCode) {
        return new SysApiRegistryItem(id, moduleCode, moduleCode, "/api/" + moduleCode, "",
                "",
                "GET", "/api/" + moduleCode + "/demo", "demo", "示例接口", "",
                List.of(), List.of(), null, true, false, "1.0.0", 1, 0);
    }
}