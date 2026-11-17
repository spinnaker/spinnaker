/*
 * Copyright 2026 Spinnaker.io
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
import com.netflix.spinnaker.gate.banner.GlobalBannerProperties;
import com.netflix.spinnaker.gate.banner.GlobalBannerService;
import com.netflix.spinnaker.gate.mcp.config.McpServerAutoConfiguration;
import com.netflix.spinnaker.gate.services.TaskService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.gate.services.internal.KayentaService;
import com.netflix.spinnaker.gate.services.internal.KeelService;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.util.List;
import java.util.function.Predicate;
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
 * Runs the real Spring AI annotation scanner over every gate-mcp tool group (Kayenta, Keel and the
 * global banner included), then checks the {@code annotations} that each tool advertises in {@code
 * tools/list}. Read-only tools must advertise themselves as safe to auto-approve, and mutating
 * tools must never advertise readOnlyHint=true.
 */
class McpToolAnnotationsTest {

  private static final List<String> READ_ONLY_TOOLS =
      List.of(
          "list_applications",
          "get_application",
          "get_clusters",
          "get_server_groups",
          "get_load_balancers",
          "search_executions",
          "get_latest_executions",
          "get_failed_stages",
          "list_global_banners",
          "list_canary_configs",
          "get_canary_config",
          "list_canary_judges",
          "list_canary_metrics_metadata",
          "test_canary_metric_query",
          "get_canary_result",
          "list_canary_results_by_application",
          "get_canary_metric_set_pair_list",
          "get_delivery_config",
          "get_delivery_config_for_application",
          "get_delivery_config_artifacts",
          "get_delivery_config_schema",
          "validate_delivery_config",
          "diff_delivery_config",
          "get_managed_application",
          "get_managed_environments",
          "get_environment_constraints",
          "get_managed_resource",
          "get_managed_resource_status",
          "list_pending_manual_judgments",
          "get_manual_judgment",
          "list_pipeline_configs",
          "get_pipeline_config",
          "get_pipeline_execution",
          "evaluate_pipeline_expression",
          "search_infrastructure",
          "search_all_types",
          "search_application_tasks",
          "get_task");

  private static final List<String> MUTATING_TOOLS_SAMPLE =
      List.of(
          "trigger_pipeline",
          "delete_application",
          "cancel_zombie_pipeline",
          "set_global_banner",
          "save_delivery_config",
          "initiate_canary");

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  McpServerAnnotationScannerAutoConfiguration.class,
                  McpServerSpecificationFactoryAutoConfiguration.class))
          .withUserConfiguration(FakeGateCollaborators.class, McpServerAutoConfiguration.class)
          .withPropertyValues(
              "mcp.server.enabled=true",
              "services.kayenta.enabled=true",
              "services.keel.enabled=true",
              "global-banner.enabled=true");

  @Test
  void readOnlyToolsAdvertiseExactlyTheReadOnlySet() {
    contextRunner.run(
        context ->
            assertThat(toolsWhere(context, McpToolAnnotationsTest::isReadOnlyHint))
                .containsExactlyInAnyOrder(READ_ONLY_TOOLS.toArray(String[]::new)));
  }

  @Test
  void readOnlyToolsAreNonDestructiveAndIdempotent() {
    contextRunner.run(
        context -> {
          List<Tool> readOnly =
              tools(context).stream()
                  .filter(tool -> READ_ONLY_TOOLS.contains(tool.name()))
                  .toList();
          assertThat(readOnly).hasSize(READ_ONLY_TOOLS.size());
          for (Tool tool : readOnly) {
            ToolAnnotations annotations = tool.annotations();
            assertThat(annotations).as(tool.name()).isNotNull();
            assertThat(annotations.destructiveHint()).as(tool.name()).isEqualTo(Boolean.FALSE);
            assertThat(annotations.idempotentHint()).as(tool.name()).isEqualTo(Boolean.TRUE);
          }
        });
  }

  @Test
  void mutatingToolsNeverAdvertiseReadOnly() {
    contextRunner.run(
        context -> {
          List<Tool> mutating =
              tools(context).stream()
                  .filter(tool -> !READ_ONLY_TOOLS.contains(tool.name()))
                  .toList();
          assertThat(mutating).isNotEmpty();
          for (Tool tool : mutating) {
            ToolAnnotations annotations = tool.annotations();
            assertThat(annotations).as(tool.name()).isNotNull();
            assertThat(annotations.readOnlyHint()).as(tool.name()).isEqualTo(Boolean.FALSE);
            assertThat(annotations.destructiveHint()).as(tool.name()).isEqualTo(Boolean.TRUE);
          }
        });
  }

  @Test
  void sanityCheckToolCountAndKnownMutatingTools() {
    contextRunner.run(
        context -> {
          assertThat(tools(context)).hasSizeGreaterThan(READ_ONLY_TOOLS.size());
          assertThat(toolNames(context)).containsAll(MUTATING_TOOLS_SAMPLE);
          assertThat(toolsWhere(context, McpToolAnnotationsTest::isReadOnlyHint))
              .doesNotContainAnyElementsOf(MUTATING_TOOLS_SAMPLE);
        });
  }

  private static boolean isReadOnlyHint(Tool tool) {
    ToolAnnotations annotations = tool.annotations();
    return annotations != null && Boolean.TRUE.equals(annotations.readOnlyHint());
  }

  private static List<String> toolsWhere(ApplicationContext context, Predicate<Tool> predicate) {
    return tools(context).stream().filter(predicate).map(Tool::name).toList();
  }

  private static List<String> toolNames(ApplicationContext context) {
    return tools(context).stream().map(Tool::name).toList();
  }

  private static List<Tool> tools(ApplicationContext context) {
    return specifications(context, SyncToolSpecification.class).stream()
        .map(SyncToolSpecification::tool)
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

    @Bean
    KayentaService kayentaService() {
      return mock(KayentaService.class);
    }

    @Bean
    KeelService keelService() {
      return mock(KeelService.class);
    }

    @Bean
    GlobalBannerService globalBannerService() {
      return mock(GlobalBannerService.class);
    }

    @Bean
    GlobalBannerProperties globalBannerProperties() {
      return new GlobalBannerProperties();
    }
  }
}
