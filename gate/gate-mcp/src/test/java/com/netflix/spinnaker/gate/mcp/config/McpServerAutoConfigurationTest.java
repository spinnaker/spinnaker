/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.gate.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.mcp.prompts.SpinnakerPrompts;
import com.netflix.spinnaker.gate.mcp.resources.SpinnakerResources;
import com.netflix.spinnaker.gate.mcp.tools.ApplicationTools;
import com.netflix.spinnaker.gate.mcp.tools.DeploymentTools;
import com.netflix.spinnaker.gate.mcp.tools.ExecutionTools;
import com.netflix.spinnaker.gate.mcp.tools.KayentaTools;
import com.netflix.spinnaker.gate.mcp.tools.KeelTools;
import com.netflix.spinnaker.gate.mcp.tools.ManualJudgmentTools;
import com.netflix.spinnaker.gate.mcp.tools.PipelineConfigTools;
import com.netflix.spinnaker.gate.mcp.tools.PipelineTools;
import com.netflix.spinnaker.gate.mcp.tools.SearchTools;
import com.netflix.spinnaker.gate.mcp.tools.TaskTools;
import com.netflix.spinnaker.gate.services.TaskService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.gate.services.internal.KayentaService;
import com.netflix.spinnaker.gate.services.internal.KeelService;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies the MCP server's tool/resource/prompt beans only exist when {@code
 * mcp.server.enabled=true} - this is the mechanism that keeps the whole feature off by default (see
 * {@link McpServerAutoConfiguration}) - and that the Kayenta/Keel-backed tools additionally require
 * {@code services.kayenta.enabled}/{@code services.keel.enabled} (i.e. those services must
 * themselves be enabled).
 */
class McpServerAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(FakeGateCollaborators.class, McpServerAutoConfiguration.class);

  @Test
  void toolBeansAbsentByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(ApplicationTools.class);
          assertThat(context).doesNotHaveBean(DeploymentTools.class);
          assertThat(context).doesNotHaveBean(PipelineTools.class);
          assertThat(context).doesNotHaveBean(PipelineConfigTools.class);
          assertThat(context).doesNotHaveBean(ExecutionTools.class);
          assertThat(context).doesNotHaveBean(SearchTools.class);
          assertThat(context).doesNotHaveBean(ManualJudgmentTools.class);
          assertThat(context).doesNotHaveBean(TaskTools.class);
          assertThat(context).doesNotHaveBean(KayentaTools.class);
          assertThat(context).doesNotHaveBean(KeelTools.class);
          assertThat(context).doesNotHaveBean(SpinnakerResources.class);
          assertThat(context).doesNotHaveBean(SpinnakerPrompts.class);
        });
  }

  @Test
  void toolBeansPresentWhenEnabled() {
    contextRunner
        .withPropertyValues("mcp.server.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ApplicationTools.class);
              assertThat(context).hasSingleBean(DeploymentTools.class);
              assertThat(context).hasSingleBean(PipelineTools.class);
              assertThat(context).hasSingleBean(PipelineConfigTools.class);
              assertThat(context).hasSingleBean(ExecutionTools.class);
              assertThat(context).hasSingleBean(SearchTools.class);
              assertThat(context).hasSingleBean(ManualJudgmentTools.class);
              assertThat(context).hasSingleBean(TaskTools.class);
              assertThat(context).hasSingleBean(SpinnakerResources.class);
              assertThat(context).hasSingleBean(SpinnakerPrompts.class);
              assertThat(context.getBean(McpServerProperties.class).isReadOnly())
                  .as("read-only defaults to true even once enabled")
                  .isTrue();
            });
  }

  @Test
  void kayentaToolsAbsentWhenKayentaNotConfigured() {
    contextRunner
        .withPropertyValues("mcp.server.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(KayentaTools.class));
  }

  @Test
  void kayentaToolsPresentWhenKayentaEnabled() {
    contextRunner
        .withUserConfiguration(FakeKayentaService.class)
        .withPropertyValues("mcp.server.enabled=true", "services.kayenta.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(KayentaTools.class));
  }

  @Test
  void kayentaToolsAbsentWhenKayentaServiceBeanExistsButPropertyIsFalse() {
    contextRunner
        .withUserConfiguration(FakeKayentaService.class)
        .withPropertyValues("mcp.server.enabled=true", "services.kayenta.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(KayentaTools.class));
  }

  @Test
  void keelToolsAbsentWhenKeelNotConfigured() {
    contextRunner
        .withPropertyValues("mcp.server.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(KeelTools.class));
  }

  @Test
  void keelToolsPresentWhenKeelEnabled() {
    contextRunner
        .withUserConfiguration(FakeKeelService.class)
        .withPropertyValues("mcp.server.enabled=true", "services.keel.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(KeelTools.class));
  }

  @Test
  void keelToolsAbsentWhenKeelServiceBeanExistsButPropertyIsFalse() {
    contextRunner
        .withUserConfiguration(FakeKeelService.class)
        .withPropertyValues("mcp.server.enabled=true", "services.keel.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(KeelTools.class));
  }

  @Test
  void readOnlyCanBeDisabledExplicitly() {
    contextRunner
        .withPropertyValues("mcp.server.enabled=true", "mcp.server.read-only=false")
        .run(
            context ->
                assertThat(context.getBean(McpServerProperties.class).isReadOnly()).isFalse());
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
  }

  @Configuration(proxyBeanMethods = false)
  static class FakeKayentaService {
    @Bean
    KayentaService kayentaService() {
      return mock(KayentaService.class);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class FakeKeelService {
    @Bean
    KeelService keelService() {
      return mock(KeelService.class);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
