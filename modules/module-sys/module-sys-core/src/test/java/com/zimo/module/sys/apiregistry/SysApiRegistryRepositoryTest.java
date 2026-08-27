package com.zimo.module.sys.apiregistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

@SuppressWarnings({"unchecked", "rawtypes"})
class SysApiRegistryRepositoryTest {

    @Test
    void queriesAllActiveAndInactiveRowsWithOptionalFilters() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        SysApiRegistryRepository repository = new SysApiRegistryRepository(jdbc, new ObjectMapper());

        repository.find(new SysApiRegistryQuery("wms", "get", 0, "warehouse"));

        String sql = capturedSql(jdbc);
        assertThat(sql).contains("FROM api_registry", "is_deleted = 0");
        assertThat(sql).doesNotContain("controller_name", "controller_path");
        assertThat(sql).doesNotContain("WHERE is_deleted = 0 AND status = 1");
        assertThat(sql).contains("module_code = ?", "method = ?", "status = ?");
        assertThat(sql).contains("ORDER BY module_code ASC, sort ASC, path ASC, method ASC");
        assertThat(capturedArgs(jdbc)).containsExactly(
                "wms", "GET", 0,
                "%warehouse%", "%warehouse%", "%warehouse%");
    }

    @Test
    void omitsStatusFilterWhenStatusIsNull() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        SysApiRegistryRepository repository = new SysApiRegistryRepository(jdbc, new ObjectMapper());

        repository.find(new SysApiRegistryQuery("wms", "get", null, null));

        assertThat(capturedSql(jdbc)).doesNotContain("AND status = ?");
        assertThat(capturedArgs(jdbc)).containsExactly("wms", "GET");
    }

    @Test
    void updatesStatusOnlyForExistingNonDeletedRow() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.update(anyString(), eq(0), eq(7L))).thenReturn(1);
        SysApiRegistryRepository repository = new SysApiRegistryRepository(jdbc, new ObjectMapper());

        int affected = repository.updateStatus(7L, 0);

        assertThat(affected).isEqualTo(1);
        verify(jdbc).update(
                "UPDATE api_registry SET status = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ? AND is_deleted = 0",
                0, 7L);
    }

    @Test
    void mapsJsonColumnsAndBooleanFlags() throws Exception {
        JdbcOperations jdbc = jdbcReturning(validRow());
        SysApiRegistryRepository repository = new SysApiRegistryRepository(jdbc, new ObjectMapper());

        List<SysApiRegistryItem> items = repository.find(null);

        assertThat(items).hasSize(1);
        SysApiRegistryItem item = items.get(0);
        assertThat(item.id()).isEqualTo(7L);
        assertThat(item.moduleCode()).isEqualTo("sys");
        assertThat(item.method()).isEqualTo("POST");
        assertThat(item.tags()).containsExactly("user", "create");
        assertThat(item.requestParams()).hasSize(1);
        assertThat(item.responseExample()).isInstanceOf(Map.class);
        assertThat((Map<String, Object>) item.responseExample()).containsEntry("code", 200);
        assertThat(item.authRequired()).isTrue();
        assertThat(item.deprecated()).isFalse();
        assertThat(item.status()).isEqualTo(0);
        assertThat(item.sort()).isEqualTo(10);
        List<String> componentNames = java.util.Arrays.stream(SysApiRegistryItem.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
        assertThat(componentNames).doesNotContain("controllerName", "controllerPath");
    }

    @Test
    void fallsBackWhenJsonColumnsAreInvalid() throws Exception {
        ResultSet row = validRow();
        when(row.getString("tags")).thenReturn("invalid-json");
        when(row.getString("request_params")).thenReturn("{");
        when(row.getString("response_example")).thenReturn("[");
        SysApiRegistryRepository repository = new SysApiRegistryRepository(jdbcReturning(row), new ObjectMapper());

        SysApiRegistryItem item = repository.find(null).get(0);

        assertThat(item.tags()).isEmpty();
        assertThat(item.requestParams()).isEmpty();
        assertThat(item.responseExample()).isNull();
    }

    @Test
    void fallsBackWhenJsonListColumnsContainNullLiteral() throws Exception {
        ResultSet row = validRow();
        when(row.getString("tags")).thenReturn("null");
        when(row.getString("request_params")).thenReturn("null");
        SysApiRegistryRepository repository = new SysApiRegistryRepository(jdbcReturning(row), new ObjectMapper());

        SysApiRegistryItem item = repository.find(null).get(0);

        assertThat(item.tags()).isEmpty();
        assertThat(item.requestParams()).isEmpty();
    }

    private static JdbcOperations jdbcReturning(ResultSet resultSet) {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            RowMapper<SysApiRegistryItem> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        });
        return jdbc;
    }

    private static String capturedSql(JdbcOperations jdbc) {
        return org.mockito.Mockito.mockingDetails(jdbc)
                .getInvocations()
                .iterator()
                .next()
                .getArgument(0);
    }

    private static Object[] capturedArgs(JdbcOperations jdbc) {
        Object[] arguments = org.mockito.Mockito.mockingDetails(jdbc)
                .getInvocations()
                .iterator()
                .next()
                .getArguments();
        return java.util.Arrays.copyOfRange(arguments, 2, arguments.length);
    }

    private static ResultSet validRow() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(7L);
        when(row.getString("module_code")).thenReturn("sys");
        when(row.getString("module_name")).thenReturn("系统管理");
        when(row.getString("module_base_path")).thenReturn("/api/biz/sys");
        when(row.getString("module_desc")).thenReturn("系统模块");
        when(row.getString("controller_desc")).thenReturn("用户接口");
        when(row.getString("method")).thenReturn("POST");
        when(row.getString("path")).thenReturn("/api/biz/sys/users");
        when(row.getString("api_name")).thenReturn("createUser");
        when(row.getString("summary")).thenReturn("创建用户");
        when(row.getString("description")).thenReturn("创建系统用户");
        when(row.getString("tags")).thenReturn("[\"user\",\"create\"]");
        when(row.getString("request_params")).thenReturn("[{\"name\":\"username\"}]");
        when(row.getString("response_example")).thenReturn("{\"code\":200}");
        when(row.getInt("auth_required")).thenReturn(1);
        when(row.getInt("deprecated")).thenReturn(0);
        when(row.getString("version")).thenReturn("1.0.0");
        when(row.getInt("status")).thenReturn(0);
        when(row.getInt("sort")).thenReturn(10);
        return row;
    }
}
