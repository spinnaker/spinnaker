/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.TaskControllerConfigurationProperties
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.api.pipeline.models.*
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.model.OrchestrationViewModel
import com.netflix.spinnaker.orca.pipeline.CompoundExecutionOperator
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.util.ExpressionUtils
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.http.HttpStatus
import org.springframework.lang.Nullable
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.access.prepost.PreFilter
import org.springframework.web.bind.annotation.*
import rx.schedulers.Schedulers

import java.nio.charset.Charset
import java.time.Clock
import java.time.ZoneOffset
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import static com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.*
import static com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator
import static com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import static java.time.temporal.ChronoUnit.DAYS

@Slf4j
@RestController
class TaskController {
  Front50Service front50Service
  ExecutionRepository executionRepository
  ExecutionRunner executionRunner
  CompoundExecutionOperator executionOperator
  Collection<StageDefinitionBuilder> stageBuilders
  ContextParameterProcessor contextParameterProcessor
  ExpressionUtils expressionUtils
  ObjectMapper mapper
  Registry registry
  StageDefinitionBuilderFactory stageDefinitionBuilderFactory
  TaskControllerConfigurationProperties configurationProperties
  Clock clock

  TaskController(@Nullable Front50Service front50Service,
                 ExecutionRepository executionRepository,
                 ExecutionRunner executionRunner,
                 CompoundExecutionOperator executionOperator,
                 Collection<StageDefinitionBuilder> stageBuilders,
                 ContextParameterProcessor contextParameterProcessor,
                 ExpressionUtils expressionUtils,
                 ObjectMapper mapper,
                 Registry registry,
                 StageDefinitionBuilderFactory stageDefinitionBuilderFactory,
                 TaskControllerConfigurationProperties configurationProperties
  ) {
    this.front50Service = front50Service
    this.executionRepository = executionRepository
    this.executionRunner = executionRunner
    this.executionOperator = executionOperator
    this.stageBuilders = stageBuilders
    this.contextParameterProcessor = contextParameterProcessor
    this.expressionUtils = expressionUtils
    this.mapper = mapper
    this.registry = registry
    this.stageDefinitionBuilderFactory = stageDefinitionBuilderFactory
    this.configurationProperties = configurationProperties
    this.clock = Clock.systemUTC()
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/applications/{application}/tasks", method = RequestMethod.GET)
  List<OrchestrationViewModel> list(
    @PathVariable String application,
    @RequestParam(value = "page", defaultValue = "1") int page,
    @RequestParam(value = "limit", defaultValue = "3500") int limit,
    @RequestParam(value = "statuses", required = false) String statuses
  ) {
    statuses = statuses ?: ExecutionStatus.values()*.toString().join(",")
    def executionCriteria = new ExecutionCriteria()
      .setPage(page)
      .setPageSize(limit)
      .setStatuses(statuses.split(",") as Collection)
      .setStartTimeCutoff(
        clock
          .instant()
          .atZone(ZoneOffset.UTC)
          .minusDays(this.configurationProperties.getDaysOfExecutionHistory())
          .toInstant()
      )

    executionRepository.retrieveOrchestrationsForApplication(
      application,
      executionCriteria,
      START_TIME_OR_ID
    ).collect { convert(it) }
  }

  @PreAuthorize("@fiatPermissionEvaluator.storeWholePermission()")
  @PostFilter("hasPermission(filterObject.application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/tasks", method = RequestMethod.GET)
  List<OrchestrationViewModel> list() {
    executionRepository.retrieve(ORCHESTRATION).toBlocking().iterator.collect {
      convert it
    }
  }

  // @PostAuthorize("hasPermission(returnObject.application, 'APPLICATION', 'READ')")
  //
  // This endpoint is unsecured because of the create application process, where Deck immediately
  // queries this endpoint to check on the status of creating a new application before the
  // application permissions have been propagated. Furthermore, given that the ID is a hard-to-guess
  // GUID, it's unlikely than an attacker would be able to guess the identifier for any task.
  @RequestMapping(value = "/tasks/{id}", method = RequestMethod.GET)
  OrchestrationViewModel getTask(@PathVariable String id) {
    convert executionRepository.retrieve(ORCHESTRATION, id)
  }

  PipelineExecution getOrchestration(String id) {
    executionRepository.retrieve(ORCHESTRATION, id)
  }

  @PreAuthorize("hasPermission(this.getOrchestration(#id)?.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/tasks/{id}", method = RequestMethod.DELETE)
  void deleteTask(@PathVariable String id) {
    executionRepository.retrieve(ORCHESTRATION, id).with {
      if (it.status.complete) {
        executionRepository.delete(ORCHESTRATION, id)
      } else {
        log.warn("Not deleting $ORCHESTRATION $id as it is $it.status")
        throw new CannotDeleteRunningExecution(ORCHESTRATION, id)
      }
    }
  }

  @PreAuthorize("hasPermission(this.getOrchestration(#id)?.application, 'APPLICATION', 'EXECUTE')")
  @RequestMapping(value = "/tasks/{id}/cancel", method = RequestMethod.PUT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  void cancelTask(@PathVariable String id) {
    executionOperator.cancel(ORCHESTRATION, id)
  }

  @PreFilter("hasPermission(this.getOrchestration(filterObject)?.application, 'APPLICATION', 'EXECUTE')")
  @RequestMapping(value = "/tasks/cancel", method = RequestMethod.PUT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  void cancelTasks(@RequestBody List<String> taskIds) {
    taskIds.each {
      executionOperator.cancel(ORCHESTRATION, it)
    }
  }

/**
 * Retrieves an ad-hoc collection of executions based on a number of user-supplied parameters. Either executionIds or
 * pipelineConfigIds must be supplied in order to return any results. If both are supplied, an IllegalArgumentException
 * will be thrown.
 * @param pipelineConfigIds A comma-separated list of pipeline config ids
 * @param executionIds A comma-separated list of execution ids; if specified, limit and statuses parameters will be
 * ignored
 * @param limit (optional) Number of most recent executions to retrieve per pipeline config; defaults to 1, ignored if
 * executionIds is specified
 * @param statuses (optional) Execution statuses to filter results by; defaults to all, ignored if executionIds is
 * specified
 * @param expand (optional) Expands each execution object in the resulting list. If this value is missing,
 * it is defaulted to true.
 * @return
 */
  @PostFilter("hasPermission(filterObject.application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/pipelines", method = RequestMethod.GET)
  List<PipelineExecution> listSubsetOfPipelines(
    @RequestParam(value = "pipelineConfigIds", required = false) String pipelineConfigIds,
    @RequestParam(value = "executionIds", required = false) String executionIds,
    @RequestParam(value = "limit", required = false) Integer limit,
    @RequestParam(value = "statuses", required = false) String statuses,
    @RequestParam(value = "expand", defaultValue = "true") boolean expand) {
    statuses = statuses ?: ExecutionStatus.values()*.toString().join(",")
    limit = limit ?: 1
    ExecutionCriteria executionCriteria = new ExecutionCriteria(
      pageSize: limit,
      statuses: (statuses.split(",") as Collection)
    )

    if (!pipelineConfigIds && !executionIds) {
      return []
    }

    if (pipelineConfigIds && executionIds) {
      throw new IllegalArgumentException("Only pipelineConfigIds OR executionIds can be specified")
    }

    if (executionIds) {
      List<String> ids = executionIds.split(',')

      List<PipelineExecution> executions = rx.Observable.from(ids.collect {
        try {
          executionRepository.retrieve(PIPELINE, it)
        } catch (ExecutionNotFoundException e) {
          null
        }
      }).subscribeOn(Schedulers.io()).toList().toBlocking().single().findAll()

      if (!expand) {
        unexpandPipelineExecutions(executions)
      }

      return executions
    }
    List<String> ids = pipelineConfigIds.split(',')

    List<PipelineExecution> allPipelines = rx.Observable.merge(ids.collect {
      executionRepository.retrievePipelinesForPipelineConfigId(it, executionCriteria)
    }).subscribeOn(Schedulers.io()).toList().toBlocking().single().sort(startTimeOrId)

    if (!expand) {
      unexpandPipelineExecutions(allPipelines)
    }

    return filterPipelinesByHistoryCutoff(allPipelines, limit)
  }

  /**
   * Search for pipeline executions using a combination of criteria. The returned list is sorted by
   * buildTime (trigger time) in reverse order so that newer executions are first in the list.
   *
   * @param application Only includes executions that are part of this application. If this value is
   * '*', includes executions of all applications.
   * @param triggerTypes (optional) Only includes executions that were triggered by a trigger with a
   * type that is equal to a type provided in this field. The list of trigger types should be a
   * comma-delimited string. If this value is missing, includes executions of all trigger types.
   * @param pipelineName (optional) Only includes executions that with this pipeline name.
   * @param eventId (optional) Only includes executions that were triggered by a trigger with this
   * eventId. This only applies to triggers that return a response message when called.
   * @param trigger (optional) Only includes executions that were triggered by a trigger that
   * matches the subset of fields provided by this value. This value should be a base64-encoded
   * string of a JSON representation of a trigger object. The comparison succeeds if the execution
   * trigger contains all the fields of the input trigger, the fields are of the same type, and each
   * value of the field "matches". The term "matches" is specific for each field's type:
   * - For Strings: A String value in the execution's trigger matches the input trigger's String
   * value if the former equals the latter (case-insensitive) OR if the former matches the latter as
   * a regular expression.
   * - For Maps: A Map value in the execution's trigger matches the input trigger's Map value if the
   * former contains all keys of the latter and their values match.
   * - For Collections: A Collection value in the execution's trigger matches the input trigger's
   * Collection value if the former has a unique element that matches each element of the latter.
   * - Every other value is compared using the Java "equals" method (Groovy "==" operator)
   * @param triggerTimeStartBoundary (optional) Only includes executions that were built at or after
   * the given time, represented as a Unix timestamp in ms (UTC). This value must be >= 0 and <=
   * the value of [triggerTimeEndBoundary], if provided. If this value is missing, it is defaulted
   * to 0.
   * @param triggerTimeEndBoundary (optional) Only includes executions that were built at or before
   * the given time, represented as a Unix timestamp in ms (UTC). This value must be <=
   * 9223372036854775807 (Long.MAX_VALUE) and >= the value of [triggerTimeStartBoundary], if
   * provided. If this value is missing, it is defaulted to 9223372036854775807.
   * @param statuses (optional) Only includes executions with a status that is equal to a status
   * provided in this field. The list of statuses should be given as a comma-delimited string. If
   * this value is missing, includes executions of all statuses. Allowed statuses are: NOT_STARTED,
   * RUNNING, PAUSED, SUSPENDED, SUCCEEDED, FAILED_CONTINUE, TERMINAL, CANCELED, REDIRECT, STOPPED,
   * SKIPPED, BUFFERED. @see com.netflix.spinnaker.orca.ExecutionStatus for more info.
   * @param startIndex (optional) Sets the first item of the resulting list for pagination. The list
   * is 0-indexed. This value must be >= 0. If this value is missing, it is defaulted to 0.
   * @param size (optional) Sets the size of the resulting list for pagination. This value must be >
   * 0. If this value is missing, it is defaulted to 10.
   * @param reverse (optional) Reverses the resulting list before it is paginated. If this value is
   * missing, it is defaulted to false.
   * @param expand (optional) Expands each execution object in the resulting list. If this value is
   * missing, it is defaulted to false.
   * @return
   */
  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/applications/{application}/pipelines/search", method = RequestMethod.GET)
  List<PipelineExecution> searchForPipelinesByTrigger(
    @PathVariable(value = "application") String application,
    @RequestParam(value = "triggerTypes", required = false) String triggerTypes,
    @RequestParam(value = "pipelineName", required = false) String pipelineName,
    @RequestParam(value = "eventId", required = false) String eventId,
    @RequestParam(value = "trigger", required = false) String encodedTriggerParams,
    @RequestParam(value = "triggerTimeStartBoundary", defaultValue = "0") long triggerTimeStartBoundary,
    @RequestParam(value = "triggerTimeEndBoundary", defaultValue = "9223372036854775807" /* Long.MAX_VALUE */) long triggerTimeEndBoundary,
    @RequestParam(value = "statuses", required = false) String statuses,
    @RequestParam(value = "startIndex", defaultValue = "0") int startIndex,
    @RequestParam(value = "size", defaultValue = "10") int size,
    @RequestParam(value = "reverse", defaultValue = "false") boolean reverse,
    @RequestParam(value = "expand", defaultValue = "false") boolean expand
    // TODO(joonlim): May make sense to add a summary boolean so that, when true, this returns a condensed summary rather than complete execution objects.
  ) {
    validateSearchForPipelinesByTriggerParameters(triggerTimeStartBoundary, triggerTimeEndBoundary, startIndex, size)

    ExecutionComparator sortType = BUILD_TIME_DESC
    if (reverse) {
      sortType = BUILD_TIME_ASC
    }

    // Returned map will be empty if encodedTriggerParams is null
    final Map triggerParams = decodeTriggerParams(encodedTriggerParams)

    Set<String> triggerTypesAsSet = (triggerTypes && triggerTypes != "*")
      ? triggerTypes.split(",") as Set
      : null // null means all trigger types

    // Filter by application (and pipeline name, if that parameter has been given in addition to application name)
    List<String> pipelineConfigIds
    if (application == "*") {
      pipelineConfigIds = getPipelineConfigIdsOfReadableApplications()
    } else {
      List<Map<String, Object>> pipelines = front50Service.getPipelines(application, false)
      pipelines = pipelines.stream().filter({ pipeline ->
        if (pipelineName != null && pipelineName != "") {
          return pipeline.get("name") == pipelineName
        } else {
          return true
        }
      }).collect(Collectors.toList())
      pipelineConfigIds = pipelines*.id as List<String>
    }

    ExecutionCriteria executionCriteria = new ExecutionCriteria()
      .setSortType(sortType)
      .setPageSize(size * 2)

    if (statuses != null && statuses != "") {
      executionCriteria.setStatuses(statuses.split(",").toList())
    }

    List<PipelineExecution> matchingExecutions = new ArrayList<>()

    int page = 1
    while (matchingExecutions.size() < size) {
      List<PipelineExecution> executions = executionRepository.retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
          pipelineConfigIds,
          triggerTimeStartBoundary,
          triggerTimeEndBoundary,
          executionCriteria.setPage(page)
      )

      matchingExecutions.addAll(
          executions
              .stream()
              .filter({
                // Filter by trigger type
                if (triggerTypesAsSet && !triggerTypesAsSet.contains(it.getTrigger().type)) {
                  return false
                }
                // Filter by event ID
                if (eventId && eventId != it.getTrigger().other.eventId) {
                  return false
                }
                // Filter by trigger params
                return compareTriggerWithTriggerSubset(it.getTrigger(), triggerParams)
              })
              .collect(Collectors.toList())
      )

      if (executions.size() < executionCriteria.pageSize) {
        break
      }

      page++
    }

    List<PipelineExecution> rval
    if (startIndex >= matchingExecutions.size()) {
      rval = []
    } else {
      rval = matchingExecutions.subList(startIndex, Math.min(matchingExecutions.size(), startIndex + size))
    }

    if (!expand) {
      unexpandPipelineExecutions(rval)
    }

    return rval
  }

  private boolean compareTriggerWithTriggerSubset(Trigger trigger, Map triggerSubset) {
    Map triggerAsMap = mapper.convertValue(trigger, Map.class)

    long startMillis = clock.millis()
    boolean result = checkObjectMatchesSubset(triggerAsMap, triggerSubset)
    long ellapsedMillis = clock.millis() - startMillis

    registry.timer("trigger.comparison").record(ellapsedMillis, TimeUnit.MILLISECONDS)

    return result
  }

  @PostAuthorize("hasPermission(returnObject.application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/pipelines/{id}", method = RequestMethod.GET)
  PipelineExecution getPipeline(@PathVariable String id) {
    executionRepository.retrieve(PIPELINE, id)
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/pipelines/{id}", method = RequestMethod.DELETE)
  void deletePipeline(@PathVariable String id) {
    executionRepository.retrieve(PIPELINE, id).with {
      if (it.status.complete) {
        executionRepository.delete(PIPELINE, id)
      } else {
        log.warn("Not deleting $PIPELINE $id as it is $it.status")
        throw new CannotDeleteRunningExecution(PIPELINE, id)
      }
    }
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'EXECUTE')")
  @RequestMapping(value = "/pipelines/{id}/cancel", method = RequestMethod.PUT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  void cancel(
    @PathVariable String id, @RequestParam(required = false) String reason,
    @RequestParam(defaultValue = "false") boolean force) {
    executionOperator.cancel(PIPELINE, id, AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"), reason)
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'EXECUTE')")
  @RequestMapping(value = "/pipelines/{id}/pause", method = RequestMethod.PUT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  void pause(@PathVariable String id) {
    executionOperator.pause(PIPELINE, id, AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"))
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'EXECUTE')")
  @RequestMapping(value = "/pipelines/{id}/resume", method = RequestMethod.PUT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  void resume(@PathVariable String id) {
    executionOperator.resume(PIPELINE, id, AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"), false)
  }

  @PreAuthorize("@fiatPermissionEvaluator.storeWholePermission()")
  @PostFilter("hasPermission(this.getPipeline(filterObject)?.application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/pipelines/running", method = RequestMethod.GET)
  @ResponseStatus(HttpStatus.GONE)
  List<String> runningPipelines() {
    []
  }

  @PreAuthorize("@fiatPermissionEvaluator.storeWholePermission()")
  @PostFilter("hasPermission(this.getPipeline(filterObject)?.application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/pipelines/waiting", method = RequestMethod.GET)
  @ResponseStatus(HttpStatus.GONE)
  List<String> waitingPipelines() {
    []
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'EXECUTE')")
  @RequestMapping(value = "/pipelines/{id}/stages/{stageId}", method = RequestMethod.PATCH)
  PipelineExecution updatePipelineStage(
    @PathVariable String id,
    @PathVariable String stageId, @RequestBody Map context) {
    return executionOperator.updateStage(PIPELINE, id, stageId,
        { stage ->
          stage.context.putAll(context)
          validateStageUpdate(stage)
          stage.lastModified = new StageExecution.LastModifiedDetails(
              user: AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"),
              allowedAccounts: AuthenticatedRequest.getSpinnakerAccounts().orElse(null)?.split(",") ?: [],
              lastModifiedTime: System.currentTimeMillis()
          )

          // `lastModifiedBy` is deprecated (pending a update to deck)
          stage.context["lastModifiedBy"] = stage.lastModified.user
        })
  }

  // If other execution mutations need validation, factor this out.
  void validateStageUpdate(StageExecution stage) {
    if (stage.context.manualSkip
      && !stageDefinitionBuilderFactory.builderFor(stage)?.canManuallySkip(stage)) {
      throw new CannotUpdateExecutionStage("Cannot manually skip stage.")
    }
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'EXECUTE')")
  @RequestMapping(value = "/pipelines/{id}/stages/{stageId}/restart", method = RequestMethod.PUT)
  PipelineExecution retryPipelineStage(
    @PathVariable String id, @PathVariable String stageId, @RequestBody Map restartDetails) {
    return executionOperator.restartStage(id, stageId, restartDetails)
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/pipelines/{id}/evaluateExpression", method = RequestMethod.GET)
  Map evaluateExpressionForExecution(@PathVariable("id") String id,
                                     @RequestParam("expression")
                                       String expression) {
    def execution = executionRepository.retrieve(PIPELINE, id)
    def context = [
      execution: execution,
      trigger  : mapper.convertValue(execution.trigger, Map.class)
    ]

    def evaluated = contextParameterProcessor.process(
      [expression: expression],
      context,
      true
    )
    return [result: evaluated?.expression, detail: evaluated?.expressionEvaluationSummary]
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/pipelines/{id}/{stageId}/evaluateExpression", method = RequestMethod.GET)
  Map evaluateExpressionForExecutionAtStage(@PathVariable("id") String id,
                                            @PathVariable("stageId") String stageId,
                                            @RequestParam("expression") String expression) {
    def execution = executionRepository.retrieve(PIPELINE, id)
    def stage = execution.stages.find { it.id == stageId }

    if (stage == null) {
      throw new NotFoundException("Stage $stageId not found in execution $id")
    }

    def evaluated = contextParameterProcessor.process(
      [expression: expression],
      contextParameterProcessor.buildExecutionContext(stage),
      true
    )
    return [result: evaluated?.expression, detail: evaluated?.expressionEvaluationSummary]
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'READ')")
  @PostMapping("/pipelines/{id}/evaluateVariables")
  Map evaluateVariables(@PathVariable("id") String id,
                        @RequestParam(value = "requisiteStageRefIds", defaultValue = "") String requisiteStageRefIds,
                        @RequestParam(value = "spelVersion", defaultValue = "") String spelVersionOverride,
                        @RequestBody List<Map<String, String>> expressions) {
    def execution = executionRepository.retrieve(PIPELINE, id)

    return expressionUtils.evaluateVariables(execution,
      requisiteStageRefIds.split("[,;\\s]").findAll({ it -> !it.empty }),
      spelVersionOverride,
      expressions)
  }

/**
 * Adds trigger and execution to stage context so that expression evaluation can be tested.
 * This is not great, because it's brittle, but it's very useful to be able to test expressions.
 */
  private Map<String, Object> augmentContext(StageExecution stage) {
    Map<String, Object> augmentedContext = stage.context
    augmentedContext.put("trigger", stage.execution.trigger)
    augmentedContext.put("execution", stage.execution)
    return augmentedContext
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/v2/applications/{application}/pipelines", method = RequestMethod.GET)
  List<PipelineExecution> getApplicationPipelines(@PathVariable String application,
                                                      @RequestParam(value = "limit", defaultValue = "5")
                                            int limit,
                                                      @RequestParam(value = "statuses", required = false)
                                            String statuses,
                                                      @RequestParam(value = "expand", defaultValue = "true") Boolean expand) {
    return getPipelinesForApplication(application, limit, statuses, expand)
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/applications/{application}/pipelines", method = RequestMethod.GET)
  List<PipelineExecution> getPipelinesForApplication(@PathVariable String application,
                                                     @RequestParam(value = "limit", defaultValue = "5") int limit,
                                                     @RequestParam(value = "statuses", required = false) String statuses,
                                                     @RequestParam(value = "expand", defaultValue = "true") Boolean expand) {
    if (!front50Service) {
      throw new UnsupportedOperationException("Cannot lookup pipelines, front50 has not been enabled. Fix this by setting front50.enabled: true")
    }

    if (!limit) {
      return []
    }
    statuses = statuses ?: ExecutionStatus.values()*.toString().join(",")
    def executionCriteria = new ExecutionCriteria(
      pageSize: limit,
      statuses: (statuses.split(",") as Collection)
    )

    // get all relevant pipeline and strategy configs from front50
    def pipelineConfigIds = front50Service.getPipelines(application, false)*.id as List<String>
    log.debug("received ${pipelineConfigIds.size()} pipelines for application: $application from front50")
    def strategyConfigIds = front50Service.getStrategies(application)*.id as List<String>
    log.debug("received ${strategyConfigIds.size()} strategies for application: $application from front50")

    def allFront50PipelineConfigIds = pipelineConfigIds + strategyConfigIds

    List<PipelineExecution> allPipelineExecutions = []

    if (this.configurationProperties.getOptimizeExecutionRetrieval()) {
      allPipelineExecutions.addAll(
          optimizedGetPipelineExecutions(application, allFront50PipelineConfigIds, executionCriteria)
      )
    } else {
      allPipelineExecutions = rx.Observable.merge(allFront50PipelineConfigIds.collect {
        log.debug("processing pipeline config id: $it")
        executionRepository.retrievePipelinesForPipelineConfigId(it, executionCriteria)
      }).subscribeOn(Schedulers.io()).toList().toBlocking().single()
    }

    allPipelineExecutions.sort(startTimeOrId)
    if (!expand) {
      log.debug("unexpanding pipeline executions")
      unexpandPipelineExecutions(allPipelineExecutions)
    }

    log.debug("filtering pipelines by history")
    return filterPipelinesByHistoryCutoff(allPipelineExecutions, limit)
  }

  private static void validateSearchForPipelinesByTriggerParameters(long triggerTimeStartBoundary, long triggerTimeEndBoundary, int startIndex, int size) {
    if (triggerTimeStartBoundary < 0) {
      throw new IllegalArgumentException(String.format("triggerTimeStartBoundary must be >= 0: triggerTimeStartBoundary=%s", triggerTimeStartBoundary))
    }
    if (triggerTimeEndBoundary < 0) {
      throw new IllegalArgumentException(String.format("triggerTimeEndBoundary must be >= 0: triggerTimeEndBoundary=%s", triggerTimeEndBoundary))
    }
    if (triggerTimeStartBoundary > triggerTimeEndBoundary) {
      throw new IllegalArgumentException(String.format("triggerTimeStartBoundary must be <= triggerTimeEndBoundary: triggerTimeStartBoundary=%s, triggerTimeEndBoundary=%s", triggerTimeStartBoundary, triggerTimeEndBoundary))
    }
    if (startIndex < 0) {
      throw new IllegalArgumentException(String.format("startIndex must be >= 0: startIndex=%s", startIndex))
    }
    if (size <= 0) {
      throw new IllegalArgumentException(String.format("size must be > 0: size=%s", size))
    }
  }

  private Map decodeTriggerParams(String encodedTriggerParams) {
    Map triggerParams
    if (encodedTriggerParams != null) {
      try {
        byte[] decoded = Base64.getDecoder().decode(encodedTriggerParams)
        String str = new String(decoded, Charset.forName("UTF-8"))
        triggerParams = mapper.readValue(str, Map.class)
      } catch (Exception e) {
        throw new RuntimeException("Failed to parse encoded trigger", e)
      }
    } else {
      triggerParams = new HashMap()
    }
    return Collections.unmodifiableMap(triggerParams)
  }

  private static void clearTriggerStages(Map trigger) {
    if (trigger.type?.toLowerCase() != "pipeline") {
      return
    }
    ((List) trigger.parentExecution.stages).clear()
    if (trigger.parentExecution.trigger.type.toLowerCase() == "pipeline") {
      clearTriggerStages((Map) trigger.parentExecution.trigger)
    }
  }

  private List<PipelineExecution> filterPipelinesByHistoryCutoff(List<PipelineExecution> pipelines, int limit) {
    // TODO-AJ The eventual goal is to return `allPipelines` without the need to group + filter below (WIP)
    def cutoffTime = clock.instant().minus(this.configurationProperties.getDaysOfExecutionHistory(), DAYS).toEpochMilli()

    def pipelinesSatisfyingCutoff = []
    pipelines.groupBy {
      it.pipelineConfigId
    }.values().each { List<PipelineExecution> pipelinesGroup ->
      def sortedPipelinesGroup = pipelinesGroup.sort(startTimeOrId).reverse()
      def recentPipelines = sortedPipelinesGroup.findAll {
        !it.startTime || it.startTime > cutoffTime
      }
      if (!recentPipelines && sortedPipelinesGroup) {
        // no pipeline executions within `this.configurationProperties.getDaysOfExecutionHistory()` so include
        // the first `this.configurationProperties.numberOfOldPipelineExecutionsToInclude()`
        def upperBounds = Math.min(sortedPipelinesGroup.size(), this.getConfigurationProperties().getNumberOfOldPipelineExecutionsToInclude()) - 1
        recentPipelines = sortedPipelinesGroup[0..upperBounds]
      }

      pipelinesSatisfyingCutoff.addAll(recentPipelines.subList(0, Math.min(recentPipelines.size(), limit)))
    }

    return pipelinesSatisfyingCutoff.sort(startTimeOrId)
  }
  // TODO(joonlim): Consider adding expand flag to RedisExecutionRepository's buildExecution method so that
  // these fields are never added in the first place.
  private static unexpandPipelineExecutions(List<PipelineExecution> pipelineExecutions) {
    pipelineExecutions.each { pipelineExecution ->
      clearTriggerStages(pipelineExecution.trigger.other) // remove from the "other" field - that is what Jackson works against
      pipelineExecution.getStages().each { stage ->
        if (stage.context?.containsKey("group")) {
          // TODO: consider making "group" a top-level field on the Stage model
          // for now, retain group in the context, as it is needed for collapsing templated pipelines in the UI
          stage.context = [group: stage.context.group]
        } else {
          stage.context = [:]
        }
        stage.outputs = [:]
        stage.tasks = []
      }
    }
  }

  private static Closure startTimeOrId = { a, b ->
    def aStartTime = a.startTime ?: 0
    def bStartTime = b.startTime ?: 0

    return aStartTime <=> bStartTime ?: b.id <=> a.id
  }

  static boolean shouldReplace(Map.Entry<String, Object> entry, Map variables) {
    // a duplicate key with an empty value should
    // not overwrite a previous key with a non empty value
    return isNullOrEmpty(variables[entry.key]) || !isNullOrEmpty(entry.value)
  }

  private OrchestrationViewModel convert(PipelineExecution orchestration) {
    def variables = [:]
    for (stage in orchestration.stages) {
      for (entry in stage.context.entrySet()) {
        if (shouldReplace(entry, variables)) {
          variables[entry.key] = entry.value
        }
      }
    }
    new OrchestrationViewModel(
      id: orchestration.id,
      name: orchestration.description,
      application: orchestration.application,
      status: orchestration.getStatus(),
      variables: variables.collect { key, value ->
        [
          "key"  : key,
          "value": value
        ]
      },
      steps: orchestration.stages.tasks.flatten(),
      buildTime: orchestration.buildTime,
      startTime: orchestration.startTime,
      endTime: orchestration.endTime,
      execution: orchestration
    )
  }

  @PostAuthorize("hasPermission(returnObject.application, 'APPLICATION', 'READ')")
  private List<String> getPipelineConfigIdsOfReadableApplications() {
    List<String> applicationNames = front50Service.getAllApplications()*.name as List<String>
    List<String> pipelineConfigIds = applicationNames.stream()
      .map { applicationName -> front50Service.getPipelines(applicationName, false)*.id as List<String> }
      .flatMap { c -> c.stream() }
      .collect(Collectors.toList())

    return pipelineConfigIds
  }

  private static boolean isNullOrEmpty(Object value) {
    if (value == null) {
      return true
    } else if (value instanceof Collection) {
      return value.isEmpty()
    } else if (value instanceof String) {
      return value.length() == 0
    } else if (value == [:]) {
      return true
    }
    return false
  }

  @VisibleForTesting
  private static boolean checkObjectMatchesSubset(Object object, Object subset) {
    if (String.isInstance(object) && String.isInstance(subset)) {
      return checkStringMatchesSubset((String) object, (String) subset)
    } else if (Map.isInstance(object) && Map.isInstance(subset)) {
      return checkMapMatchesSubset((Map) object, (Map) subset)
    } else if (Collection.isInstance(object) && Collection.isInstance(subset)) {
      return checkCollectionMatchesSubset((Collection) object, (Collection) subset)
    } else {
      return object == subset
    }
  }

  private static boolean checkStringMatchesSubset(String string, String subset) {
    // string matches subset if the former equals the latter (case-insensitive) OR if the former
    // matches the latter as a regular expression
    return string.equalsIgnoreCase(subset) ||
      string.matches(subset)
  }

  private static boolean checkMapMatchesSubset(Map map, Map subset) {
    // map matches subset if the former contains all keys of the latter and their values match
    for (Object key : subset.keySet()) {
      if (!checkObjectMatchesSubset(map.get(key), subset.get(key))) {
        return false
      }
    }
    return true
  }

  private static boolean checkCollectionMatchesSubset(Collection collection, Collection subset) {
    // collection matches subset if the former has a unique element that matches each element of the
    // latter.
    //   * this means that an item in subset may not match to more than one item in object, which
    //     means that we should check every permutation of object to avoid greedily stopping
    //     on the first item that matches.
    //     e.g., Given,
    //             collection: [ { "name": "a", "version": "1" }, { "name": "a" } ]
    //           Without checking all permutations, this will match:
    //             subset: [ { "name": "a", "version": "1" }, { "name": "a" } ]
    //           but will not match:
    //             subset: [ { "name": "a" }, { "name", "version": "1" } ]
    //           because the first item in subset will greedily match the first item in collection,
    //           leaving the second items in both, which do not match. This is fixed by checking
    //           all permutations of object.
    if (subset.size() > collection.size()) {
      return false
    }
    if (subset.isEmpty()) {
      return true
    }

    PermutationGenerator subsetPermutations = new PermutationGenerator(subset)
    for (List subsetPermutation : subsetPermutations) {
      List collectionCopy = new ArrayList(collection) // copy this because we will be removing items
      boolean matchedAllItems = true

      for (Object subsetItem : subsetPermutation) {
        boolean matchedItem = false
        for (int i = 0; i < collectionCopy.size(); i++) {
          Object collectionItem = collectionCopy.get(i)
          if (checkObjectMatchesSubset(collectionItem, subsetItem)) {
            collectionCopy.remove(i) // remove to make sure to not match the same item more than once
            matchedItem = true
            break
          }
        }

        if (!matchedItem) {
          matchedAllItems = false
          break
        }
      }

      if (matchedAllItems) {
        return true
      }
    }
    // Failed to match for all permutations
    return false
  }

  /**
   *   this optimized flow speeds up the execution retrieval process for all pipelines in an application. It
   *   does it in three steps:
   *<p>
   *  1. It compares the list of pipeline config ids obtained from front50 with what is stored in the orca db itself.
   *      Rationale: We can ignore those process config ids that have no executions. The absence of a pipeline config
   *      id from the orca db indicates the same. So to reduce the number of config ids to process, we
   *      intersect the result obtained from front50 and orca db, which gives us the reduced list.
   *      Note: this could be further optimized by cutting front50 out from the picture completely.
   *      But I do not know what other side-effects that may cause, hence I am going ahead with the above logic.
   *
   *<p>
   *   2. It then uses the list of pipeline config ids obtained from step 1 and gets all the valid executions
   *   associated with each one of them. The valid executions are found after applying the execution criteria.
   *
   *<p>
   *   3. It then processes n pipeline executions at a time to retrieve the complete execution details. In addition,
   *      we make use of a configured thread pool to process multiple batches of n executions in parallel.
   */
  private List<PipelineExecution> optimizedGetPipelineExecutions(String application,
  List<String> front50PipelineConfigIds, ExecutionCriteria executionCriteria) {
    List<PipelineExecution> finalResult = []
    log.info("running optimized execution retrieval process with: " +
        "${this.configurationProperties.getMaxExecutionRetrievalThreads()} threads and processing" +
        " ${this.configurationProperties.getMaxNumberOfPipelineExecutionsToProcess()} pipeline executions at a time")

    List<String> commonPipelineConfigIdsInFront50AndOrca
    try {
      List<String> allOrcaPipelineConfigIds = executionRepository.retrievePipelineConfigIdsForApplication(application)
      log.info("found ${allOrcaPipelineConfigIds.size()} pipeline config ids for application: $application in orca")
      commonPipelineConfigIdsInFront50AndOrca = front50PipelineConfigIds.intersect(allOrcaPipelineConfigIds)
      log.info("found ${commonPipelineConfigIdsInFront50AndOrca.size()} pipeline config ids that are common in orca " +
          "and front50 for application: $application. " +
          "Saved ${front50PipelineConfigIds.size() - commonPipelineConfigIdsInFront50AndOrca.size()} extra pipeline " +
          "config id queries")
    } catch (Exception e) {
      log.warn("retrieving pipeline config ids from orca db failed. using the result obtained from front50 ", e)
      commonPipelineConfigIdsInFront50AndOrca = front50PipelineConfigIds
    }

    if (commonPipelineConfigIdsInFront50AndOrca.size() == 0 ) {
      log.info("no pipeline config ids found.")
      return finalResult
    }

    // get complete list of executions based on the execution criteria
    log.info("filtering pipeline executions based on the execution criteria: " +
        "limit: ${executionCriteria.getPageSize()}, statuses: ${executionCriteria.getStatuses()}")
    List<String> filteredPipelineExecutionIds = executionRepository.retrieveAndFilterPipelineExecutionIdsForApplication(
        application,
        commonPipelineConfigIdsInFront50AndOrca,
        executionCriteria
    )
    if (filteredPipelineExecutionIds.size() == 0) {
      log.info("no pipeline executions found")
      return finalResult
    }

    // need to define a new executor service since we want a dedicated set of threads to be made available for every
    // new request for performance reasons
    ExecutorService executorService = Executors.newFixedThreadPool(
        this.configurationProperties.getMaxExecutionRetrievalThreads(),
        new ThreadFactoryBuilder()
            .setNameFormat("application-" + application  + "-%d")
            .build())

    try {
      List<Future<Collection<PipelineExecution>>> futures = new ArrayList<>(filteredPipelineExecutionIds.size())
      log.info("processing ${filteredPipelineExecutionIds.size()} pipeline executions")

      // process a chunk of the executions at a time
      filteredPipelineExecutionIds
          .collate(this.configurationProperties.getMaxNumberOfPipelineExecutionsToProcess())
          .each { List<String> chunkedExecutions ->
            futures.add(executorService.submit({
              try {
                List<PipelineExecution> result = executionRepository.retrievePipelineExecutionDetailsForApplication(
                    application,
                    chunkedExecutions,
                    this.configurationProperties.getExecutionRetrievalTimeoutSeconds()
                )
                log.debug("completed execution retrieval for ${result.size()} executions")
                return result
              } catch (Exception e) { // handle exceptions such as query timeouts etc.
                log.error("error occurred while retrieving these executions: ${chunkedExecutions.toString()} " +
                    "for application: ${application}.", e)
                // in case of errors, this will return partial results. We are going with this best-effort approach
                // because the UI keeps refreshing the executions view frequently. Hence, the user will eventually see
                // these executions via one of the subsequent calls. Partial data is better than an exception at this
                // point since the latter will result in a UI devoid of any executions.
                //
                return []
              }
            } as Callable<Collection<PipelineExecution>>))
          }

      futures.each { Future<Collection<PipelineExecution>> future -> finalResult.addAll(future.get()) }
      return finalResult
    } finally {
      try {
        executorService.shutdownNow()   // attempt to shutdown the executor service
      } catch (Exception e) {
        log.error("shutting down the executor service failed", e)
      }
    }
  }

  @InheritConstructors
  @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
  private static class FeatureNotEnabledException extends RuntimeException {}

  @ResponseStatus(HttpStatus.CONFLICT)
  private static class CannotDeleteRunningExecution extends RuntimeException {
    CannotDeleteRunningExecution(ExecutionType type, String id) {
      super("Cannot delete a running $type, please cancel it first.")
    }
  }

  @InheritConstructors
  @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
  private static class CannotUpdateExecutionStage extends RuntimeException {}
}
