/*
 * Copyright 2026 Wise, PLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.mcp.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.mcp.config.McpServerAutoConfiguration;
import com.netflix.spinnaker.gate.services.TaskService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerSpecificationFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;

/**
 * Runs the real Spring AI annotation scanner and specification factory over gate-mcp's tool beans,
 * then inspects the tool specification lists that Spring AI's MCP server auto-configuration
 * registers - so this proves what an MCP client would actually see in {@code tools/list}.
 */
class McpToolAllowlistTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  McpServerAnnotationScannerAutoConfiguration.class,
                  McpServerSpecificationFactoryAutoConfiguration.class))
          .withUserConfiguration(FakeGateCollaborators.class, McpServerAutoConfiguration.class)
          .withPropertyValues("mcp.server.enabled=true");

  @Test
  void exposesEveryToolWhenNoAllowlistIsConfigured() {
    contextRunner.run(
        context ->
            assertThat(registeredToolNames(context))
                .contains("get_application", "trigger_pipeline", "cancel_zombie_pipeline")
                .hasSizeGreaterThan(3));
  }

  @Test
  void exposesOnlyAllowlistedTools() {
    contextRunner
        .withPropertyValues(
            "mcp.server.allowed-tools[0]=get_application",
            "mcp.server.allowed-tools[1]=trigger_pipeline")
        .run(
            context ->
                assertThat(registeredToolNames(context))
                    .containsExactlyInAnyOrder("get_application", "trigger_pipeline"));
  }

  @Test
  void bindsCommaSeparatedAllowlist() {
    contextRunner
        .withPropertyValues("mcp.server.allowed-tools=get_application,get_task")
        .run(
            context ->
                assertThat(registeredToolNames(context))
                    .containsExactlyInAnyOrder("get_application", "get_task"));
  }

  @Test
  void ignoresUnknownToolNames() {
    contextRunner
        .withPropertyValues("mcp.server.allowed-tools=get_application,no_such_tool")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(registeredToolNames(context)).containsExactly("get_application");
            });
  }

  @Test
  void leavesPromptsAndResourcesAlone() {
    contextRunner
        .withPropertyValues("mcp.server.allowed-tools=get_application")
        .run(
            context ->
                assertThat(specifications(context, SyncPromptSpecification.class))
                    .extracting(prompt -> prompt.prompt().name())
                    .contains("triage-failed-pipeline", "review-manual-judgment"));
  }

  private static List<String> registeredToolNames(ApplicationContext context) {
    return specifications(context, SyncToolSpecification.class).stream()
        .map(tool -> tool.tool().name())
        .toList();
  }

  private static <T> List<T> specifications(ApplicationContext context, Class<T> type) {
    return context
        .<List<T>>getBeanProvider(ResolvableType.forClassWithGenerics(List.class, type))
        .stream()
        .flatMap(List::stream)
        .toList();
  }

  @Configuration(proxyBeanMethods = false)
  static class FakeGateCollaborators {
    @Bean
    Front50Service front50Service() {
      return mock(Front50Service.class);
    }

    @Bean
    OrcaServiceSelector orcaServiceSelector() {
      return mock(OrcaServiceSelector.class);
    }

    @Bean
    ClouddriverServiceSelector clouddriverServiceSelector() {
      return mock(ClouddriverServiceSelector.class);
    }

    @Bean
    TaskService taskService() {
      return mock(TaskService.class);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
