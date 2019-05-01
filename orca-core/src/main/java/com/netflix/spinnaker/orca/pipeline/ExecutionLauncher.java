/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.events.BeforeInitialExecutionPersist;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.PipelineBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Trigger;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.netflix.spinnaker.orca.pipeline.model.Execution.AuthenticationDetails;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;
import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

@Component
public class ExecutionLauncher {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final ObjectMapper objectMapper;
  private final ExecutionRepository executionRepository;
  private final ExecutionRunner executionRunner;
  private final Clock clock;
  private final Optional<PipelineValidator> pipelineValidator;
  private final Optional<Registry> registry;
  private final ApplicationEventPublisher applicationEventPublisher;

  @Autowired
  public ExecutionLauncher(ObjectMapper objectMapper,
                           ExecutionRepository executionRepository,
                           ExecutionRunner executionRunner,
                           Clock clock,
                           ApplicationEventPublisher applicationEventPublisher,
                           Optional<PipelineValidator> pipelineValidator,
                           Optional<Registry> registry) {
    this.objectMapper = objectMapper;
    this.executionRepository = executionRepository;
    this.executionRunner = executionRunner;
    this.clock = clock;
    this.applicationEventPublisher = applicationEventPublisher;
    this.pipelineValidator = pipelineValidator;
    this.registry = registry;
  }

  public Execution start(ExecutionType type, String configJson) throws Exception {
    final Execution execution = parse(type, configJson);

    final Execution existingExecution = checkForCorrelatedExecution(execution);
    if (existingExecution != null) {
      return existingExecution;
    }

    checkRunnable(execution);

    persistExecution(execution);

    try {
      start(execution);
    } catch (Throwable t) {
      handleStartupFailure(execution, t);
    }

    return execution;
  }

  /**
   * Log that an execution failed; useful if a pipeline failed validation and we want to persist the
   * failure to the execution history but don't actually want to attempt to run the execution.
   *
   * @param e the exception that was thrown during pipeline validation
   */
  public Execution fail(ExecutionType type, String configJson, Exception e) throws Exception {
    final Execution execution = parse(type, configJson);

    persistExecution(execution);

    handleStartupFailure(execution, e);

    return execution;
  }

  private void checkRunnable(Execution execution) {
    if (execution.getType() == PIPELINE) {
      pipelineValidator.ifPresent(it -> it.checkRunnable(execution));
    }
  }

  public Execution start(Execution execution) throws Exception {
    executionRunner.start(execution);
    return execution;
  }

  private Execution checkForCorrelatedExecution(Execution execution) {
    if (execution.getTrigger().getCorrelationId() == null) {
      return null;
    }

    Trigger trigger = execution.getTrigger();

    try {
      Execution o = executionRepository.retrieveByCorrelationId(
        execution.getType(),
        trigger.getCorrelationId()
      );
      log.info("Found pre-existing " + execution.getType() + " by correlation id (id: " +
        o.getId() + ", correlationId: " +
        trigger.getCorrelationId() +
        ")");
      return o;
    } catch (ExecutionNotFoundException e) {
      // Swallow
    }

    return null;
  }

  private Execution handleStartupFailure(Execution execution, Throwable failure) {
    final String canceledBy = "system";
    final String reason = "Failed on startup: " + failure.getMessage();
    final ExecutionStatus status = ExecutionStatus.TERMINAL;

    log.error("Failed to start {} {}", execution.getType(), execution.getId(), failure);
    executionRepository.updateStatus(execution.getType(), execution.getId(), status);
    executionRepository.cancel(execution.getType(), execution.getId(), canceledBy, reason);
    return executionRepository.retrieve(execution.getType(), execution.getId());
  }

  private Execution parse(ExecutionType type, String configJson) throws IOException {
    if (type == PIPELINE) {
      return parsePipeline(configJson);
    } else {
      return parseOrchestration(configJson);
    }
  }

  private Execution parsePipeline(String configJson) throws IOException {
    // TODO: can we not just annotate the class properly to avoid all this?
    Map<String, Serializable> config = objectMapper.readValue(configJson, Map.class);
    return new PipelineBuilder(getString(config, "application"))
      .withName(getString(config, "name"))
      .withPipelineConfigId(getString(config, "id"))
      .withTrigger(objectMapper.convertValue(config.get("trigger"), Trigger.class))
      .withStages((List<Map<String, Object>>) config.get("stages"))
      .withLimitConcurrent(getBoolean(config, "limitConcurrent"))
      .withKeepWaitingPipelines(getBoolean(config, "keepWaitingPipelines"))
      .withNotifications((List<Map<String, Object>>) config.get("notifications"))
      .withOrigin(getString(config, "origin"))
      .withStartTimeExpiry(getString(config, "startTimeExpiry"))
      .withSource((config.get("source") == null) ? null : objectMapper.convertValue(config.get("source"), Execution.PipelineSource.class))
      .build();
  }

  private Execution parseOrchestration(String configJson) throws IOException {
    @SuppressWarnings("unchecked")
    Map<String, Serializable> config = objectMapper.readValue(configJson, Map.class);
    Execution orchestration = Execution.newOrchestration(getString(config, "application"));
    if (config.containsKey("name")) {
      orchestration.setDescription(getString(config, "name"));
    }
    if (config.containsKey("description")) {
      orchestration.setDescription(getString(config, "description"));
    }

    for (Map<String, Object> context : getList(config, "stages")) {
      String type = context.remove("type").toString();

      String providerType = getString(context, "providerType");
      if (providerType != null && !providerType.equals("aws") && !providerType.equals("titus")) {
        type += format("_%s", providerType);
      }

      // TODO: need to check it's valid?
      Stage stage = new Stage(orchestration, type, context);
      orchestration.getStages().add(stage);
    }

    if (config.get("trigger") != null) {
      orchestration.setTrigger(objectMapper.convertValue(config.get("trigger"), Trigger.class));
    }

    orchestration.setBuildTime(clock.millis());
    orchestration.setAuthentication(AuthenticationDetails.build().orElse(new AuthenticationDetails()));
    orchestration.setOrigin((String) config.getOrDefault("origin", "unknown"));
    orchestration.setStartTimeExpiry((Long) config.get("startTimeExpiry"));

    return orchestration;
  }

  /**
   * Persist the initial execution configuration.
   */
  private void persistExecution(Execution execution) {
    applicationEventPublisher.publishEvent(new BeforeInitialExecutionPersist(this, execution));
    executionRepository.store(execution);
  }

  private final boolean getBoolean(Map<String, ?> map, String key) {
    return parseBoolean(getString(map, key));
  }

  private final String getString(Map<String, ?> map, String key) {
    return map.containsKey(key) ? map.get(key).toString() : null;
  }

  private final <K, V> Map<K, V> getMap(Map<String, ?> map, String key) {
    Map<K, V> result = (Map<K, V>) map.get(key);
    return result == null ? emptyMap() : result;
  }

  private final List<Map<String, Object>> getList(Map<String, ?> map, String key) {
    List<Map<String, Object>> result = (List<Map<String, Object>>) map.get(key);
    return result == null ? emptyList() : result;
  }

  private final <E extends Enum<E>> E getEnum(Map<String, ?> map, String key, Class<E> type) {
    String value = (String) map.get(key);
    return value != null ? Enum.valueOf(type, value) : null;
  }
}
