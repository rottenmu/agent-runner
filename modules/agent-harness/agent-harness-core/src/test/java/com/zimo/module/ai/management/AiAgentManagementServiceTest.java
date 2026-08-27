package com.zimo.module.ai.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zimo.starter.ai.skill.AiApiSkillConfig;
import com.zimo.starter.ai.skill.AiSkillRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiAgentManagementServiceTest {
    @Test
    void findsEnabledAgentByNormalizedId() {
        InMemoryAgentRepository agentRepository = new InMemoryAgentRepository();
        agentRepository.save(agent("enabled-agent", "启用智能体", List.of()));
        AiAgentManagementService service = serviceWithAgentRepository(agentRepository);

        assertThat(service.findEnabledAgentById(" enabled-agent "))
                .hasValueSatisfying(agent -> assertThat(agent.id()).isEqualTo("enabled-agent"));
    }

    @Test
    void returnsEmptyWhenAgentIdIsBlankMissingOrDisabled() {
        InMemoryAgentRepository agentRepository = new InMemoryAgentRepository();
        agentRepository.save(new AiManagedAgent(
                "disabled-agent",
                "停用智能体",
                "停用智能体描述",
                "你是停用智能体",
                "qwen-plus",
                null,
                List.of(),
                false,
                "admin",
                "管理员",
                List.of()));
        AiAgentManagementService service = serviceWithAgentRepository(agentRepository);

        assertThat(service.findEnabledAgentById(null)).isEmpty();
        assertThat(service.findEnabledAgentById(" ")).isEmpty();
        assertThat(service.findEnabledAgentById("missing-agent")).isEmpty();
        assertThat(service.findEnabledAgentById("disabled-agent")).isEmpty();
    }
    @Test
    void loadsManagedAgentsFromRepositoryAndPersistsCreatedAgents() {
        AiSkillRegistry registry = new AiSkillRegistry(List.of());
        InMemoryAgentRepository agentRepository = new InMemoryAgentRepository();
        agentRepository.save(new AiManagedAgent(
                "persisted-agent",
                "已持久化智能体",
                "从数据库加载的智能体",
                "你是已持久化智能体",
                "qwen-plus",
                9L,
                List.of("view_projects"),
                true,
                "admin",
                "管理员",
                List.of("feishu")));
        AiAgentManagementService service = new AiAgentManagementService(
                registry,
                List.of(),
                new EmptySkillConfigRepository(),
                agentRepository);

        assertThat(service.listAgents()).extracting(AiManagedAgent::id).contains("persisted-agent");
        assertThat(service.findDefaultAgentForChannel("feishu"))
                .hasValueSatisfying(agent -> assertThat(agent.name()).isEqualTo("已持久化智能体"));

        AiManagedAgentRequest request = new AiManagedAgentRequest();
        request.setName("新建智能体");
        request.setDesc("需要落库的新建智能体");
        request.setPersona("你是新建智能体");
        request.setModel("qwen-max");
        request.setPromptTemplateId(10L);
        request.setSkillIds(List.of("generate_project_code"));
        request.setEnabled(true);
        request.setUserId("admin");
        request.setUserName("管理员");
        request.setDefaultChannels(List.of("web"));

        AiManagedAgent created = service.create(request);

        assertThat(agentRepository.findActiveById(created.id()))
                .hasValueSatisfying(agent -> {
                    assertThat(agent.name()).isEqualTo("新建智能体");
                    assertThat(agent.skillIds()).containsExactly("generate_project_code");
                    assertThat(agent.defaultChannels()).containsExactly("web");
                });
    }

    @Test
    void findsChannelDefaultOnlyWithinRequestedTenant() {
        InMemoryAgentRepository agentRepository = new InMemoryAgentRepository();
        agentRepository.save(agent("tenant-a-default", "租户 A 默认智能体", "tenant-a", List.of("web")));
        agentRepository.save(agent("tenant-b-default", "租户 B 默认智能体", "tenant-b", List.of("web")));
        AiAgentManagementService service = serviceWithAgentRepository(agentRepository);

        assertThat(service.findDefaultAgentForChannel("web", "tenant-b"))
                .hasValueSatisfying(agent -> assertThat(agent.id()).isEqualTo("tenant-b-default"));
        assertThat(service.findDefaultAgentForChannel("web", "tenant-c")).isEmpty();
    }

    @Test
    void seedsProjectManagementAgentForFeishuWhenRepositoryHasNoAgents() {
        AiSkillRegistry registry = new AiSkillRegistry(List.of());
        InMemoryAgentRepository agentRepository = new InMemoryAgentRepository();
        AiAgentManagementService service = new AiAgentManagementService(
                registry,
                List.of(),
                new EmptySkillConfigRepository(),
                agentRepository);

        assertThat(service.findDefaultAgentForChannel("feishu"))
                .hasValueSatisfying(agent -> {
                    assertThat(agent.name()).isEqualTo("项目管理智能体");
                    assertThat(agent.skillIds()).containsExactly(
                            "parse_excel_projects",
                            "generate_project_code",
                            "view_project_code",
                            "view_all_projects");
                });
        assertThat(agentRepository.listActive()).extracting(AiManagedAgent::name)
                .contains("项目管理智能体");
    }
    @Test
    void doesNotReviveSoftDeletedSeedAgentsWhenRepositoryHasHistory() {
        AiSkillRegistry registry = new AiSkillRegistry(List.of());
        InMemoryAgentRepository agentRepository = new InMemoryAgentRepository();
        agentRepository.save(agent("project-management-agent", "项目管理智能体", List.of("feishu")));
        assertThat(agentRepository.softDelete("project-management-agent")).isTrue();

        AiAgentManagementService service = new AiAgentManagementService(
                registry,
                List.of(),
                new EmptySkillConfigRepository(),
                agentRepository);

        assertThat(service.listAgents()).isEmpty();
        assertThat(agentRepository.findActiveById("project-management-agent")).isEmpty();
    }

    @Test
    void keepsContributedAgentsVisibleWhenRepositoryAlreadyHasAgents() {
        AiSkillRegistry registry = new AiSkillRegistry(List.of());
        InMemoryAgentRepository agentRepository = new InMemoryAgentRepository();
        agentRepository.save(agent("stored-agent", "已持久化普通智能体", List.of("web")));
        AiManagedAgent contributed = agent("contributed-feishu", "外部贡献飞书智能体", List.of("feishu"));

        AiAgentManagementService service = new AiAgentManagementService(
                registry,
                List.of(() -> List.of(contributed)),
                new EmptySkillConfigRepository(),
                agentRepository);

        assertThat(service.listAgents()).extracting(AiManagedAgent::id)
                .contains("stored-agent", "contributed-feishu");
        assertThat(service.findDefaultAgentForChannel("feishu"))
                .hasValueSatisfying(agent -> assertThat(agent.id()).isEqualTo("contributed-feishu"));
    }


    @Test
    void doesNotRegisterRuntimeSkillWhenRepositorySaveFails() {
        AiSkillRegistry registry = new AiSkillRegistry(List.of());
        AiAgentManagementService service = new AiAgentManagementService(
                registry,
                List.of(),
                new FailingSkillConfigRepository(),
                null);
        AiManagedSkillRequest request = new AiManagedSkillRequest();
        request.setName("remote_failure_skill");
        request.setDescription("remote failure skill");
        request.setReadOnly(true);
        AiSkillApiConfigRequest apiConfig = new AiSkillApiConfigRequest();
        apiConfig.setEnabled(true);
        apiConfig.setBaseUrl("https://api.example.com");
        apiConfig.setPath("/failure");
        apiConfig.setMethod("POST");
        apiConfig.setTimeoutMillis(3000);
        request.setApiConfig(apiConfig);

        assertThatThrownBy(() -> service.createApiSkill(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");

        assertThat(registry.apiSkillConfig("remote_failure_skill")).isEmpty();
    }

    @Test
    void persistsApiRegistryIdWhenCreatingAndUpdatingApiSkill() {
        AiSkillRegistry registry = new AiSkillRegistry(List.of());
        CapturingSkillConfigRepository repository = new CapturingSkillConfigRepository();
        AiAgentManagementService service = new AiAgentManagementService(
                registry,
                List.of(),
                repository,
                null);

        AiManagedSkillRequest request = apiSkillRequest("remote_quality_check", 1001L);
        AiManagedSkill created = service.createApiSkill(request);

        assertThat(created.apiConfig().apiRegistryId()).isEqualTo(1001L);
        assertThat(repository.findActiveByName("remote_quality_check"))
                .hasValueSatisfying(config -> assertThat(config.getApiRegistryId()).isEqualTo(1001L));

        AiManagedSkillRequest updateRequest = apiSkillRequest("remote_quality_check", null);
        AiManagedSkill updated = service.updateApiSkill("remote_quality_check", updateRequest);

        assertThat(updated.apiConfig().apiRegistryId()).isNull();
        assertThat(repository.findActiveByName("remote_quality_check"))
                .hasValueSatisfying(config -> assertThat(config.getApiRegistryId()).isNull());
    }

    @Test
    void updatesOnlyApiConfigCanPersistApiRegistryId() {
        AiSkillRegistry registry = new AiSkillRegistry(List.of());
        CapturingSkillConfigRepository repository = new CapturingSkillConfigRepository();
        AiAgentManagementService service = new AiAgentManagementService(
                registry,
                List.of(),
                repository,
                null);
        service.createApiSkill(apiSkillRequest("remote_stock_query", null));

        AiSkillApiConfigRequest apiConfig = apiConfigRequest(2002L);
        apiConfig.setPath("/stock/current");
        AiManagedSkill updated = service.updateApiSkillConfig("remote_stock_query", apiConfig);

        assertThat(updated.apiConfig().apiRegistryId()).isEqualTo(2002L);
        assertThat(repository.findActiveByName("remote_stock_query"))
                .hasValueSatisfying(config -> assertThat(config.getApiRegistryId()).isEqualTo(2002L));
    }
    @Test
    void defaultsCreatedAgentTenantIdToUserIdWhenRequestTenantIdIsBlank() {
        AiAgentManagementService service = new AiAgentManagementService(new AiSkillRegistry(List.of()), List.of(), null, null);
        AiManagedAgentRequest request = new AiManagedAgentRequest();
        request.setName("tenant default agent"); request.setDesc("tenant default agent description");
        request.setUserId("tenant-user"); request.setTenantId(" ");
        assertThat(service.create(request).tenantId()).isEqualTo("tenant-user");
    }
    @Test
    void trimsExplicitTenantIdWhenCreatingAgent() {
        AiAgentManagementService service = new AiAgentManagementService(
                new AiSkillRegistry(List.of()),
                List.of(),
                null,
                null);
        AiManagedAgentRequest request = new AiManagedAgentRequest();
        request.setName("tenant normalized agent");
        request.setDesc("tenant normalized agent description");
        request.setUserId("owner-user");
        request.setTenantId(" tenant-special ");

        assertThat(service.create(request).tenantId()).isEqualTo("tenant-special");
    }

    @Test
    void preservesExplicitTenantWhenDeletingBoundApiSkill() {
        AiSkillRegistry registry = new AiSkillRegistry(List.of());
        registry.registerApiSkill(new AiApiSkillConfig(
                "remote_skill",
                "remote skill",
                true,
                true,
                "https://api.example.com",
                "/invoke",
                "POST",
                Map.of(),
                3000));
        InMemoryAgentRepository repository = new InMemoryAgentRepository();
        repository.save(new AiManagedAgent(
                "agent-a",
                "Agent A",
                "Agent A description",
                "system",
                "qwen-plus",
                null,
                List.of("remote_skill"),
                "conversation",
                null,
                true,
                "user-a",
                "tenant-special",
                "User A",
                List.of(),
                null));
        AiAgentManagementService service =
                new AiAgentManagementService(registry, List.of(), null, repository);

        assertThat(service.deleteApiSkill("remote_skill")).isTrue();

        assertThat(repository.findActiveById("agent-a"))
                .hasValueSatisfying(agent -> {
                    assertThat(agent.tenantId()).isEqualTo("tenant-special");
                    assertThat(agent.skillIds()).isEmpty();
                });
    }
    @Test
    void invalidatesBoundRuntimeAgentAfterApiSkillUpdate() {
        AiSkillRegistry registry = new AiSkillRegistry(List.of());
        InMemoryAgentRepository agentRepository = new InMemoryAgentRepository();
        agentRepository.save(new AiManagedAgent(
                "agent-a",
                "Agent A",
                "Agent A description",
                "system",
                "qwen-plus",
                null,
                List.of("remote_quality_check"),
                "conversation",
                null,
                true,
                "admin",
                "tenant-special",
                "管理员",
                List.of(),
                null));
        List<String> invalidated = new ArrayList<>();
        AiAgentManagementService service = new AiAgentManagementService(
                registry,
                List.of(),
                new CapturingSkillConfigRepository(),
                agentRepository,
                (tenantId, agentId) -> invalidated.add(tenantId + ":" + agentId));
        service.createApiSkill(apiSkillRequest("remote_quality_check", null));
        invalidated.clear();

        service.updateApiSkill(
                "remote_quality_check",
                apiSkillRequest("remote_quality_check", 1001L));

        assertThat(invalidated).containsExactly("tenant-special:agent-a");
    }

    @Test
    void invalidatesRuntimeAgentsAfterUpdateAndDelete() {
        InMemoryAgentRepository repository = new InMemoryAgentRepository();
        repository.save(agent("agent-a", "Agent A", "tenant-special", List.of("web")));
        List<String> invalidated = new ArrayList<>();
        AiAgentManagementService service = new AiAgentManagementService(
                new AiSkillRegistry(List.of()),
                List.of(),
                null,
                repository,
                (tenantId, agentId) -> invalidated.add(tenantId + ":" + agentId));
        AiManagedAgentRequest request = new AiManagedAgentRequest();
        request.setName("Agent A updated");
        request.setDesc("Agent A updated description");
        request.setPersona("updated persona");
        request.setModel("qwen-max");
        request.setUserId("admin");
        request.setTenantId("tenant-special");
        request.setDefaultChannels(List.of("web"));

        assertThat(service.update("agent-a", request)).isNotNull();
        assertThat(service.delete("agent-a")).isTrue();

        assertThat(invalidated).containsExactly(
                "tenant-special:agent-a",
                "tenant-special:agent-a");
    }

    private static AiManagedAgent agent(String id, String name, List<String> defaultChannels) {
        return agent(id, name, "admin", defaultChannels);
    }

    private static AiManagedAgent agent(
            String id,
            String name,
            String tenantId,
            List<String> defaultChannels) {
        return new AiManagedAgent(
                id,
                name,
                name + "描述",
                "你是" + name,
                "qwen-plus",
                null,
                List.of("view_all_projects"),
                "conversation",
                null,
                true,
                "admin",
                tenantId,
                "管理员",
                defaultChannels,
                null);
    }
    private static AiAgentManagementService serviceWithAgentRepository(
            InMemoryAgentRepository agentRepository) {
        return new AiAgentManagementService(
                new AiSkillRegistry(List.of()),
                List.of(),
                new EmptySkillConfigRepository(),
                agentRepository);
    }
    private static AiManagedSkillRequest apiSkillRequest(String name, Long apiRegistryId) {
        AiManagedSkillRequest request = new AiManagedSkillRequest();
        request.setName(name);
        request.setDescription("remote api skill");
        request.setReadOnly(true);
        request.setApiConfig(apiConfigRequest(apiRegistryId));
        return request;
    }

    private static AiSkillApiConfigRequest apiConfigRequest(Long apiRegistryId) {
        AiSkillApiConfigRequest apiConfig = new AiSkillApiConfigRequest();
        apiConfig.setApiRegistryId(apiRegistryId);
        apiConfig.setEnabled(true);
        apiConfig.setBaseUrl("https://api.example.com");
        apiConfig.setPath("/quality");
        apiConfig.setMethod("POST");
        apiConfig.setHeaders(Map.of());
        apiConfig.setTimeoutMillis(3000);
        return apiConfig;
    }
    private static class EmptySkillConfigRepository implements AiManagedSkillConfigRepository {
        @Override
        public List<AiManagedSkillConfig> listActive() {
            return List.of();
        }

        @Override
        public Optional<AiManagedSkillConfig> findActiveByName(String skillName) {
            return Optional.empty();
        }

        @Override
        public AiManagedSkillConfig save(AiManagedSkillConfig config) {
            return config;
        }

        @Override
        public boolean softDelete(String skillName) {
            return false;
        }
    }

    private static final class FailingSkillConfigRepository extends EmptySkillConfigRepository {
        @Override
        public AiManagedSkillConfig save(AiManagedSkillConfig config) {
            throw new IllegalStateException("save failed");
        }
    }

    private static final class CapturingSkillConfigRepository extends EmptySkillConfigRepository {
        private final Map<String, AiManagedSkillConfig> saved = new LinkedHashMap<>();

        @Override
        public List<AiManagedSkillConfig> listActive() {
            return new ArrayList<>(saved.values());
        }

        @Override
        public Optional<AiManagedSkillConfig> findActiveByName(String skillName) {
            return Optional.ofNullable(saved.get(skillName));
        }

        @Override
        public AiManagedSkillConfig save(AiManagedSkillConfig config) {
            if (config.getId() == null) {
                config.setId((long) saved.size() + 1);
            }
            AiManagedSkillConfig copy = copyOf(config);
            saved.put(copy.getSkillName(), copy);
            return copy;
        }

        private AiManagedSkillConfig copyOf(AiManagedSkillConfig source) {
            AiManagedSkillConfig copy = new AiManagedSkillConfig();
            copy.setId(source.getId());
            copy.setAgentId(source.getAgentId());
            copy.setSkillName(source.getSkillName());
            copy.setSkillDescription(source.getSkillDescription());
            copy.setSkillType(source.getSkillType());
            copy.setReadOnly(source.isReadOnly());
            copy.setEnabled(source.isEnabled());
            copy.setBaseUrl(source.getBaseUrl());
            copy.setApiPath(source.getApiPath());
            copy.setHttpMethod(source.getHttpMethod());
            copy.setRequestHeaders(source.getRequestHeaders());
            copy.setTimeoutMillis(source.getTimeoutMillis());
            copy.setApiRegistryId(source.getApiRegistryId());
            copy.setPromptTemplateId(source.getPromptTemplateId());
            copy.setDeleted(source.isDeleted());
            return copy;
        }
    }
    private static final class InMemoryAgentRepository implements AiManagedAgentRepository {
        private final Map<String, AiManagedAgent> agents = new LinkedHashMap<>();
        private final List<String> deletedIds = new ArrayList<>();

        @Override
        public List<AiManagedAgent> listActive() {
            return new ArrayList<>(agents.values());
        }

        @Override
        public Optional<AiManagedAgent> findActiveById(String id) {
            return Optional.ofNullable(agents.get(id));
        }

        @Override
        public AiManagedAgent save(AiManagedAgent agent) {
            deletedIds.remove(agent.id());
            agents.put(agent.id(), agent);
            return agent;
        }

        @Override
        public boolean hasAny() {
            return !agents.isEmpty() || !deletedIds.isEmpty();
        }

        @Override
        public boolean softDelete(String id) {
            AiManagedAgent removed = agents.remove(id);
            if (removed != null) {
                deletedIds.add(id);
                return true;
            }
            return false;
        }
    }
}
