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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.mcp.prompts.SpinnakerPrompts;
import com.netflix.spinnaker.gate.mcp.resources.SpinnakerResources;
import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.mcp.support.OrchestrationJobs;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Gate's MCP server: a curated set of tools/resources/prompts, each backed by the same
 * gate-core Retrofit client beans (Orca/Clouddriver/Front50/Kayenta/Keel) that Gate's own REST
 * controllers use, so authentication and identity propagation already applied to those beans is
 * inherited automatically. (gate-mcp is a dependency of gate-web, so it talks to gate-core's
 * clients directly rather than gate-web's controllers/services, which it cannot depend on.)
 *
 * <p>Disabled by default. Set {@code mcp.server.enabled: true} to expose the MCP endpoint
 * (registered by {@code spring-ai-starter-mcp-server-webmvc}), and {@code mcp.server.read-only:
 * false} to allow mutating tools to actually execute rather than reject with {@link
 * com.netflix.spinnaker.gate.mcp.support.McpReadOnlyModeException}.
 *
 * <p>Tool/resource/prompt beans are deliberately registered here via {@code @Bean} methods rather
 * than as {@code @Component}-scanned classes, so that when this configuration is off, none of them
 * exist in the application context at all.
 */
@Configuration
@ConditionalOnProperty(prefix = "mcp.server", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(McpServerProperties.class)
public class McpServerAutoConfiguration {

  @Bean
  public McpAccessGuard mcpAccessGuard(McpServerProperties properties) {
    return new McpAccessGuard(properties);
  }

  @Bean
  public OrchestrationJobs orchestrationJobs(TaskService taskService, McpAccessGuard accessGuard) {
    return new OrchestrationJobs(taskService, accessGuard);
  }

  @Bean
  public ApplicationTools applicationTools(
      Front50Service front50Service, OrchestrationJobs orchestrationJobs) {
    return new ApplicationTools(front50Service, orchestrationJobs);
  }

  @Bean
  public DeploymentTools deploymentTools(
      ClouddriverServiceSelector clouddriverServiceSelector, OrchestrationJobs orchestrationJobs) {
    return new DeploymentTools(clouddriverServiceSelector, orchestrationJobs);
  }

  @Bean
  public PipelineTools pipelineTools(
      OrcaServiceSelector orcaServiceSelector,
      Front50Service front50Service,
      McpAccessGuard accessGuard) {
    return new PipelineTools(orcaServiceSelector, front50Service, accessGuard);
  }

  @Bean
  public PipelineConfigTools pipelineConfigTools(
      Front50Service front50Service, OrchestrationJobs orchestrationJobs) {
    return new PipelineConfigTools(front50Service, orchestrationJobs);
  }

  /**
   * Kayenta is an optional Spinnaker service, only wired up (see {@code GateConfig.kayentaService}
   * in gate-web) when {@code services.kayenta.enabled: true}. Mirroring that same property here -
   * rather than using {@code @ConditionalOnBean(KayentaService.class)} - is deliberate:
   * {@code @ConditionalOnBean} across two independently classpath-scanned {@code @Configuration}
   * classes (this one and gate-web's {@code GateConfig}) with no explicit ordering relationship is
   * processing-order-sensitive and not reliable, whereas a property condition is deterministic
   * regardless of scan order. The {@code KayentaService} parameter below is still resolved
   * correctly by normal bean autowiring once both conditions pass.
   */
  @Bean
  @ConditionalOnProperty(name = "services.kayenta.enabled", havingValue = "true")
  public KayentaTools kayentaTools(KayentaService kayentaService, McpAccessGuard accessGuard) {
    return new KayentaTools(kayentaService, accessGuard);
  }

  /** Managed Delivery (Keel) is optional too; see {@code GateConfig.keelService} in gate-web. */
  @Bean
  @ConditionalOnProperty(name = "services.keel.enabled", havingValue = "true")
  public KeelTools keelTools(
      KeelService keelService, ObjectMapper objectMapper, McpAccessGuard accessGuard) {
    return new KeelTools(keelService, objectMapper, accessGuard);
  }

  @Bean
  public ExecutionTools executionTools(OrcaServiceSelector orcaServiceSelector) {
    return new ExecutionTools(orcaServiceSelector);
  }

  @Bean
  public SearchTools searchTools(ClouddriverServiceSelector clouddriverServiceSelector) {
    return new SearchTools(clouddriverServiceSelector);
  }

  @Bean
  public ManualJudgmentTools manualJudgmentTools(
      OrcaServiceSelector orcaServiceSelector, McpAccessGuard accessGuard) {
    return new ManualJudgmentTools(orcaServiceSelector, accessGuard);
  }

  @Bean
  public TaskTools taskTools(
      TaskService taskService,
      OrcaServiceSelector orcaServiceSelector,
      McpAccessGuard accessGuard) {
    return new TaskTools(taskService, orcaServiceSelector, accessGuard);
  }

  @Bean
  public SpinnakerResources spinnakerResources(
      Front50Service front50Service, OrcaServiceSelector orcaServiceSelector) {
    return new SpinnakerResources(front50Service, orcaServiceSelector);
  }

  @Bean
  public SpinnakerPrompts spinnakerPrompts() {
    return new SpinnakerPrompts();
  }
}
