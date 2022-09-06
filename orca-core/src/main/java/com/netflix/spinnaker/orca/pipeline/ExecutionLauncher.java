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

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.TERMINAL;
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.exceptions.HasAdditionalAttributes;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import com.netflix.spinnaker.kork.exceptions.UserException;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger;
import com.netflix.spinnaker.orca.config.ExecutionConfigurationProperties;
import com.netflix.spinnaker.orca.events.BeforeInitialExecutionPersist;
import com.netflix.spinnaker.orca.pipeline.model.PipelineBuilder;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

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
  private final ExecutionConfigurationProperties executionConfigurationProperties;

  @Autowired
  public ExecutionLauncher(
      ObjectMapper objectMapper,
      ExecutionRepository executionRepository,
      ExecutionRunner executionRunner,
      Clock clock,
      ApplicationEventPublisher applicationEventPublisher,
      Optional<PipelineValidator> pipelineValidator,
      Optional<Registry> registry,
      ExecutionConfigurationProperties executionConfigurationProperties) {
    this.objectMapper = objectMapper;
    this.executionRepository = executionRepository;
    this.executionRunner = executionRunner;
    this.clock = clock;
    this.applicationEventPublisher = applicationEventPublisher;
    this.pipelineValidator = pipelineValidator;
    this.registry = registry;
    this.executionConfigurationProperties = executionConfigurationProperties;
  }

  public PipelineExecution start(ExecutionType type, Map<String, Object> config) {
    final PipelineExecution execution = parse(type, config);
    final PipelineExecution existingExecution = checkForCorrelatedExecution(execution);
    if (existingExecution != null) {
      return existingExecution;
    }

    checkRunnable(execution);

    persistExecution(execution);

    try {
      checkIfPipelineCanBeStarted(execution);
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
  public PipelineExecution fail(ExecutionType type, Map<String, Object> config, Exception e) {
    final PipelineExecution execution = parse(type, config);

    persistExecution(execution);

    handleStartupFailure(execution, e);

    return execution;
  }

  private void checkRunnable(PipelineExecution execution) {
    if (execution.getType() == PIPELINE) {
      pipelineValidator.ifPresent(it -> it.checkRunnable(execution));
    }
  }

  public PipelineExecution start(PipelineExecution execution) {
    executionRunner.start(execution);
    return execution;
  }

  private PipelineExecution checkForCorrelatedExecution(PipelineExecution execution) {
    if (execution.getTrigger().getCorrelationId() == null) {
      return null;
    }

    Trigger trigger = execution.getTrigger();

    try {
      PipelineExecution o =
          executionRepository.retrieveByCorrelationId(
              execution.getType(), trigger.getCorrelationId());
      log.info(
          "Found pre-existing {} by correlation id (id: {}, correlationId: {})",
          execution.getType(),
          o.getId(),
          trigger.getCorrelationId());
      return o;
    } catch (ExecutionNotFoundException e) {
      // Swallow
    }

    return null;
  }

  @SuppressWarnings("unchecked")
  private void handleStartupFailure(PipelineExecution execution, Throwable failure) {
    final String canceledBy = "system";
    String reason = "Failed on startup: " + failure.getMessage();

    if (failure instanceof HasAdditionalAttributes) {
      HasAdditionalAttributes exceptionWithAttributes = (HasAdditionalAttributes) failure;
      if (exceptionWithAttributes.getAdditionalAttributes().containsKey("errors")) {
        List<Map<String, Object>> errors =
            ((List<Map<String, Object>>)
                exceptionWithAttributes.getAdditionalAttributes().get("errors"));
        reason +=
            errors.stream()
                .flatMap(
                    error ->
                        error.entrySet().stream()
                            .filter(entry -> !entry.getKey().equals("severity")))
                .map(
                    entry ->
                        "\n" + WordUtils.capitalizeFully(entry.getKey()) + ": " + entry.getValue())
                .collect(Collectors.joining("\n", "\n", ""));
      }
    }

    if (failure instanceof UserException) {
      log.warn(
          "Failed to start {} {} due to user error or misconfiguration",
          execution.getType(),
          execution.getId(),
          failure);
    } else {
      log.error("Failed to start {} {}", execution.getType(), execution.getId(), failure);
    }
    execution.updateStatus(TERMINAL);
    executionRepository.updateStatus(execution);
    executionRepository.cancel(execution.getType(), execution.getId(), canceledBy, reason);
  }

  private PipelineExecution parse(ExecutionType type, Map<String, Object> config) {
    switch (type) {
      case PIPELINE:
        return parsePipeline(config);
      case ORCHESTRATION:
        return parseOrchestration(config);
      default:
        throw new SystemException("Unexpected execution type " + type.toString());
    }
  }

  private PipelineExecution parsePipeline(Map<String, Object> config) {
    // TODO: can we not just annotate the class properly to avoid all this?
    return new PipelineBuilder(getString(config, "application"))
        .withId(getString(config, "executionId"))
        .withName(getString(config, "name"))
        .withPipelineConfigId(getString(config, "id"))
        .withTrigger(objectMapper.convertValue(config.get("trigger"), Trigger.class))
        .withStages(getList(config, "stages"))
        .withLimitConcurrent(getBoolean(config, "limitConcurrent"))
        .withMaxConcurrentExecutions(getInt(config, "maxConcurrentExecutions"))
        .withKeepWaitingPipelines(getBoolean(config, "keepWaitingPipelines"))
        .withNotifications(getList(config, "notifications"))
        .withInitialConfig(getMap(config, "initialConfig"))
        .withOrigin(getString(config, "origin"))
        .withStartTimeExpiry(getString(config, "startTimeExpiry"))
        .withSource(
            (config.get("source") == null)
                ? null
                : objectMapper.convertValue(
                    config.get("source"), PipelineExecution.PipelineSource.class))
        .withSpelEvaluator(getString(config, "spelEvaluator"))
        .withTemplateVariables(getMap(config, "templateVariables"))
        .build();
  }

  private PipelineExecution parseOrchestration(Map<String, Object> config) {
    PipelineExecution orchestration =
        PipelineExecutionImpl.newOrchestration(getString(config, "application"));
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
      StageExecutionImpl stage = new StageExecutionImpl(orchestration, type, context);
      orchestration.getStages().add(stage);
    }

    if (config.get("trigger") != null) {
      Trigger trigger = objectMapper.convertValue(config.get("trigger"), Trigger.class);
      orchestration.setTrigger(trigger);
      if (!trigger.getNotifications().isEmpty()) {
        orchestration.setNotifications(trigger.getNotifications());
      }
    }

    orchestration.setBuildTime(clock.millis());

    PipelineExecution.AuthenticationDetails auth = new PipelineExecution.AuthenticationDetails();
    auth.setUser(getString(config, "user"));

    orchestration.setAuthentication(
        PipelineExecutionImpl.AuthenticationHelper.build().orElse(auth));
    orchestration.setOrigin((String) config.getOrDefault("origin", "unknown"));
    orchestration.setStartTimeExpiry((Long) config.get("startTimeExpiry"));
    orchestration.setSpelEvaluator(getString(config, "spelEvaluator"));

    return orchestration;
  }

  /** Persist the initial execution configuration. */
  private void persistExecution(PipelineExecution execution) {
    applicationEventPublisher.publishEvent(new BeforeInitialExecutionPersist(this, execution));
    executionRepository.store(execution);
  }

  private static boolean getBoolean(Map<String, ?> map, String key) {
    return parseBoolean(getString(map, key));
  }

  private static int getInt(Map<String, ?> map, String key) {
    return map.containsKey(key) ? parseInt(getString(map, key)) : 0;
  }

  private static String getString(Map<String, ?> map, String key) {
    return map.containsKey(key) ? map.get(key).toString() : null;
  }

  private static <K, V> Map<K, V> getMap(Map<String, ?> map, String key) {
    Map<K, V> result = (Map<K, V>) map.get(key);
    return result == null ? emptyMap() : result;
  }

  private static List<Map<String, Object>> getList(Map<String, ?> map, String key) {
    List<Map<String, Object>> result = (List<Map<String, Object>>) map.get(key);
    return result == null ? emptyList() : result;
  }

  private static <E extends Enum<E>> E getEnum(Map<String, ?> map, String key, Class<E> type) {
    String value = (String) map.get(key);
    return value != null ? Enum.valueOf(type, value) : null;
  }

  /**
   * for security reasons, we only want to allow a few explicitly configured ad-hoc operations.
   * Every other operation should be blocked. Depending on how the allowed ad-hoc operations are
   * configured, we also check to see if the user performing this action is allowed to run this
   * operation
   *
   * @param execution pipeline execution
   * @throws AccessDeniedException if the orchestration action is explicitly disabled or if the user
   *     is not allowed to perform that action
   */
  private void checkIfPipelineCanBeStarted(PipelineExecution execution) {
    // for now only orchestration execution types are checked to see if they need to be blocked
    if (execution.getType() == ExecutionType.ORCHESTRATION
        && executionConfigurationProperties.isBlockOrchestrationExecutions()) {
      for (StageExecution stage : execution.getStages()) {
        // get details about the orchestration type from the config
        Optional<ExecutionConfigurationProperties.OrchestrationExecution> orchestrationType =
            this.executionConfigurationProperties.getAllowedOrchestrationType(stage.getType());

        // if we don't find the orchestration type in the config, that means it is configured to
        // be disabled
        if (orchestrationType.isEmpty()) {
          log.warn(
              "ad-hoc execution of type: {} has been explicitly disabled. Such actions can only be run "
                  + "via spinnaker pipelines",
              stage.getType());
          throw new AccessDeniedException(
              "ad-hoc execution of type: " + stage.getType() + " has been explicitly disabled");
        }

        // if all users are allowed to execute this orchestration task, then check is complete
        if (orchestrationType.get().isAllowAllUsers()) {
          return;
        }

        // get user info to check if the user is permitted to perform this orchestration action
        String user = stage.getExecution().getAuthentication().getUser();
        if (user == null || user.isEmpty()) {
          log.warn(
              "user details could not be found for ad-hoc execution of type: {}. This execution will "
                  + "be blocked",
              stage.getType());
          throw new AccessDeniedException(
              "user details could not be found for ad-hoc execution of type: "
                  + stage.getType()
                  + ". This execution will be blocked");
        }

        // check if the configured orchestration type is configured to only allow a subset of
        // permitted users, and if this user is in that set
        if (!orchestrationType.get().getPermittedUsers().contains(user)) {
          log.warn(
              "ad-hoc execution of type: {} has been explicitly disabled. Such actions can only be run "
                  + "by a subset of users, of which {} is not a member.",
              stage.getType(),
              user);
          throw new AccessDeniedException(
              "ad-hoc execution of type: "
                  + stage.getType()
                  + " has been disabled for user: "
                  + user);
        }
      }
    }
  }
}
