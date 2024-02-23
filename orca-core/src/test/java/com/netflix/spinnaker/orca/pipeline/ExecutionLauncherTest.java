/*
 * Copyright 2021 Salesforce.com, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.config.ExecutionConfigurationProperties;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.test.YamlFileApplicationContextInitializer;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ContextConfiguration;

@ExtendWith(MockitoExtension.class)
@ContextConfiguration(
    classes = ExecutionConfigurationProperties.class,
    initializers = ExecutionLauncherTest.class)
@EnableConfigurationProperties
@SpringBootTest
public class ExecutionLauncherTest extends YamlFileApplicationContextInitializer {
  private ObjectMapper objectMapper;
  @Mock private ExecutionRepository executionRepository;
  @Mock private ExecutionRunner executionRunner;
  private Clock clock;
  private Optional<PipelineValidator> pipelineValidator;
  private Optional<Registry> registry;
  @Mock private ApplicationEventPublisher applicationEventPublisher;
  private ExecutionLauncher executionLauncher;

  // autowiring this so that it can mimic how springboot will initialize it at app startup
  @Autowired private ExecutionConfigurationProperties executionConfigurationProperties;

  @Override
  protected String getResourceLocation() {
    return "classpath:execution-launcher-properties.yml";
  }

  @BeforeEach
  public void setup() {
    objectMapper = new ObjectMapper();
    clock = Clock.systemUTC();
    pipelineValidator = Optional.empty();
    registry = Optional.empty();
    MDC.clear();

    executionLauncher =
        new ExecutionLauncher(
            objectMapper,
            executionRepository,
            executionRunner,
            clock,
            applicationEventPublisher,
            pipelineValidator,
            registry,
            executionConfigurationProperties);
  }

  @DisplayName(
      "when blockOrchestrationExecutions: true, fail ad-hoc executions that are not"
          + " explicitly allowed in configuration")
  @Test
  public void testBlockingOfOrchestrationExecutionsThatAreDisabled() throws Exception {
    // when:
    // deploy manifest action is not allowed to be performed based on the config in
    // getResourceLocation()
    PipelineExecution pipelineExecution =
        executionLauncher.start(
            ExecutionType.ORCHESTRATION, getConfigJson("ad-hoc/deploy-manifest.json"));

    // then:
    verify(executionRepository).store(pipelineExecution);
    // verify that the failure reason is what we expect
    verify(executionRepository)
        .cancel(
            ExecutionType.ORCHESTRATION,
            pipelineExecution.getId(),
            "system",
            "Failed on startup: ad-hoc execution of type: deployManifest has been explicitly disabled");
  }

  @DisplayName(
      "when blockOrchestrationExecutions: false, any orchestration should be allowed to run")
  @Test
  public void testOrchestrationExecutionsThatAreDisabledWithFlagTurnedOff() throws Exception {
    ExecutionConfigurationProperties executionConfigurationProperties =
        new ExecutionConfigurationProperties();
    executionConfigurationProperties.setBlockOrchestrationExecutions(false);
    // setup
    executionLauncher =
        new ExecutionLauncher(
            objectMapper,
            executionRepository,
            executionRunner,
            clock,
            applicationEventPublisher,
            pipelineValidator,
            registry,
            executionConfigurationProperties);

    // when
    // deployManifest orchestration type should now be able to run
    PipelineExecution pipelineExecution =
        executionLauncher.start(
            ExecutionType.ORCHESTRATION, getConfigJson("ad-hoc/deploy-manifest.json"));

    // then
    // verify that the execution runner attempted to start the execution as expected
    verify(executionRunner).start(pipelineExecution);
    // verify that no errors were thrown such as the explicitly disabled ones
    verify(executionRepository, never()).updateStatus(any(), anyString(), any());
    verify(executionRepository, never()).cancel(any(), anyString(), anyString(), anyString());
  }

  @DisplayName(
      "when blockOrchestrationExecutions: true, explicitly allowed orchestrations should still be able to run")
  @Test
  public void testThatExplicitlyAllowedOrchestrationExecutionsCanBePerformed() throws Exception {
    // when
    // update application is an explicitly allowed action as defined in the config at
    // getResourceLocation()
    PipelineExecution pipelineExecution =
        executionLauncher.start(
            ExecutionType.ORCHESTRATION, getConfigJson("ad-hoc/update-application.json"));
    // then
    // verify that the execution runner attempted to start the execution as expected
    verify(executionRunner).start(pipelineExecution);
    // verify that no errors were thrown such as the explicitly disabled ones
    verify(executionRepository, never()).updateStatus(any(), anyString(), any());
    verify(executionRepository, never()).cancel(any(), anyString(), anyString(), anyString());
  }

  @DisplayName(
      "when blockOrchestrationExecutions: true, and if an allowed orchestration defines an allow list, then"
          + " users in that allow list should be allowed to run")
  @Test
  public void testOrchestrationUserAllowListAllowsSpecifiedUsers() throws Exception {
    // when
    PipelineExecution pipelineExecution =
        executionLauncher.start(
            ExecutionType.ORCHESTRATION, getConfigJson("ad-hoc/save-pipeline-permitted-user.json"));
    // then
    // verify that the execution runner attempted to start the execution as expected
    verify(executionRunner).start(pipelineExecution);
    // verify that no errors were thrown such as the explicitly disabled ones
    verify(executionRepository, never()).updateStatus(any(), anyString(), any());
    verify(executionRepository, never()).cancel(any(), anyString(), anyString(), anyString());
  }

  @DisplayName(
      "when blockOrchestrationExecutions: true, and if an allowed orchestration defines an allow list, then"
          + " users not inn that allow list should be blocked from running that orchestration")
  @Test
  public void testOrchestrationExecutionWhenUserIsNotInAllowList() throws Exception {
    // when
    PipelineExecution pipelineExecution =
        executionLauncher.start(
            ExecutionType.ORCHESTRATION, getConfigJson("ad-hoc/save-pipeline-blocked-user.json"));

    // then
    verify(executionRepository).store(pipelineExecution);
    // verify that the failure reason is what we expect
    verify(executionRepository)
        .cancel(
            ExecutionType.ORCHESTRATION,
            pipelineExecution.getId(),
            "system",
            "Failed on startup: ad-hoc execution of type: savePipeline has been"
                + " disabled for user: not-explicitly-permitted-user@abc.com");
  }

  @DisplayName(
      "when includeAllowedAccounts: true, then the orchestration should contain Spinnaker accounts")
  @Test
  public void testIncludeSpinnakerAccountsInOrchestration() throws Exception {
    // given
    MDC.put(Header.USER.getHeader(), "SpinnakerUser");
    MDC.put(Header.ACCOUNTS.getHeader(), "Account1,Account2");

    // override properties to allow orchestration executions
    ExecutionConfigurationProperties executionConfigurationProperties =
        new ExecutionConfigurationProperties();
    executionConfigurationProperties.setBlockOrchestrationExecutions(false);
    executionLauncher =
        new ExecutionLauncher(
            objectMapper,
            executionRepository,
            executionRunner,
            clock,
            applicationEventPublisher,
            pipelineValidator,
            registry,
            executionConfigurationProperties);

    // when
    PipelineExecution pipelineExecution =
        executionLauncher.start(
            ExecutionType.ORCHESTRATION, getConfigJson("ad-hoc/deploy-manifest.json"));

    // then
    // verify that the execution runner attempted to start the execution as expected
    verify(executionRunner).start(pipelineExecution);
    // verify that accounts are set in the pipeline execution
    assertThat(pipelineExecution.getAuthentication().getAllowedAccounts())
        .isEqualTo(Set.of("Account1", "Account2"));
  }

  @DisplayName(
      "when includeAllowedAccounts: false, then the orchestration should not contain Spinnaker accounts")
  @Test
  public void testExcludeSpinnakerAccountsFromOrchestration() throws Exception {
    // given
    MDC.put(Header.USER.getHeader(), "SpinnakerUser");
    MDC.put(Header.ACCOUNTS.getHeader(), "Account1,Account2");

    // override properties to 1. allow orchestration executions and 2. set includeAllowedAccounts to
    // false
    ExecutionConfigurationProperties executionConfigurationProperties =
        new ExecutionConfigurationProperties();
    executionConfigurationProperties.setBlockOrchestrationExecutions(false);
    executionConfigurationProperties.setIncludeAllowedAccounts(false);
    executionLauncher =
        new ExecutionLauncher(
            objectMapper,
            executionRepository,
            executionRunner,
            clock,
            applicationEventPublisher,
            pipelineValidator,
            registry,
            executionConfigurationProperties);

    // when
    PipelineExecution pipelineExecution =
        executionLauncher.start(
            ExecutionType.ORCHESTRATION, getConfigJson("ad-hoc/deploy-manifest.json"));

    // then
    // verify that the execution runner attempted to start the execution as expected
    verify(executionRunner).start(pipelineExecution);
    // verify that accounts are not set in the pipeline execution
    assertThat(pipelineExecution.getAuthentication().getAllowedAccounts()).isEqualTo(Set.of());
  }

  @DisplayName(
      "when includeAllowedAccounts: true, then the pipeline should contain Spinnaker accounts")
  @Test
  public void testIncludeSpinnakerAccountsInPipeline() throws Exception {
    // given
    MDC.put(Header.USER.getHeader(), "SpinnakerUser");
    MDC.put(Header.ACCOUNTS.getHeader(), "Account1,Account2");

    // when
    PipelineExecution pipelineExecution =
        executionLauncher.start(
            ExecutionType.PIPELINE, getConfigJson("ad-hoc/deploy-manifest.json"));

    // then
    // verify that the execution runner attempted to start the execution as expected
    verify(executionRunner).start(pipelineExecution);
    // verify that accounts are set in the pipeline execution
    assertThat(pipelineExecution.getAuthentication().getAllowedAccounts())
        .isEqualTo(Set.of("Account1", "Account2"));
  }

  @DisplayName(
      "when includeAllowedAccounts: false, then the pipeline should not contain Spinnaker accounts")
  @Test
  public void testExcludeSpinnakerAccountsFromPipeline() throws Exception {
    // given
    MDC.put(Header.USER.getHeader(), "SpinnakerUser");
    MDC.put(Header.ACCOUNTS.getHeader(), "Account1,Account2");

    // override properties to set includeAllowedAccounts to false
    ExecutionConfigurationProperties executionConfigurationProperties =
        new ExecutionConfigurationProperties();
    executionConfigurationProperties.setIncludeAllowedAccounts(false);
    executionLauncher =
        new ExecutionLauncher(
            objectMapper,
            executionRepository,
            executionRunner,
            clock,
            applicationEventPublisher,
            pipelineValidator,
            registry,
            executionConfigurationProperties);

    // when
    PipelineExecution pipelineExecution =
        executionLauncher.start(
            ExecutionType.PIPELINE, getConfigJson("ad-hoc/deploy-manifest.json"));

    // then
    // verify that the execution runner attempted to start the execution as expected
    verify(executionRunner).start(pipelineExecution);
    // verify that accounts are not set in the pipeline execution
    assertThat(pipelineExecution.getAuthentication().getAllowedAccounts()).isEqualTo(Set.of());
  }

  private Map<String, Object> getConfigJson(String resource) throws Exception {
    return objectMapper.readValue(
        ExecutionLauncherTest.class.getResourceAsStream(resource), Map.class);
  }
}
