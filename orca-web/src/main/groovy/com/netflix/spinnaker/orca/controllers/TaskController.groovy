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
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.model.OrchestrationViewModel
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Trigger
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.access.prepost.PreFilter
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import rx.schedulers.Schedulers

import java.nio.charset.Charset
import java.time.Clock
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import static com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.START_TIME_OR_ID

@Slf4j
@RestController
class TaskController {
  @Autowired(required = false)
  Front50Service front50Service

  @Autowired
  ExecutionRepository executionRepository

  @Autowired
  ExecutionRunner executionRunner

  @Autowired
  Collection<StageDefinitionBuilder> stageBuilders

  @Autowired
  ContextParameterProcessor contextParameterProcessor

  @Autowired
  ObjectMapper mapper

  @Autowired
  Registry registry

  @Autowired
  StageDefinitionBuilderFactory stageDefinitionBuilderFactory

  @Value('${tasks.daysOfExecutionHistory:14}')
  int daysOfExecutionHistory

  @Value('${tasks.numberOfOldPipelineExecutionsToInclude:2}')
  int numberOfOldPipelineExecutionsToInclude

  Clock clock = Clock.systemUTC()

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/applications/{application}/tasks", method = RequestMethod.GET)
  List<OrchestrationViewModel> list(
    @PathVariable String application,
    @RequestParam(value = "page", defaultValue = "1") int page,
    @RequestParam(value = "limit", defaultValue = "3500") int limit,
    @RequestParam(value = "statuses", required = false) String statuses
  ) {
    statuses = statuses ?: ExecutionStatus.values()*.toString().join(",")
    def executionCriteria = new ExecutionRepository.ExecutionCriteria()
      .setPage(page)
      .setLimit(limit)
      .setStatuses(statuses.split(",") as Collection)
      .setStartTimeCutoff(
        clock
          .instant()
          .atZone(ZoneOffset.UTC)
          .minusDays(daysOfExecutionHistory)
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

  Execution getOrchestration(String id) {
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

  @PreAuthorize("hasPermission(this.getOrchestration(#id)?.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/tasks/{id}/cancel", method = RequestMethod.PUT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  void cancelTask(@PathVariable String id) {
    executionRepository.cancel(ORCHESTRATION, id, AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"), null)
    executionRepository.updateStatus(ORCHESTRATION, id, ExecutionStatus.CANCELED)
  }

  @PreFilter("hasPermission(this.getOrchestration(filterObject)?.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/tasks/cancel", method = RequestMethod.PUT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  void cancelTasks(@RequestBody List<String> taskIds) {
    taskIds.each {
      executionRepository.cancel(ORCHESTRATION, it, AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"), null)
      executionRepository.updateStatus(ORCHESTRATION, it, ExecutionStatus.CANCELED)
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
  List<Execution> listSubsetOfPipelines(
    @RequestParam(value = "pipelineConfigIds", required = false) String pipelineConfigIds,
    @RequestParam(value = "executionIds", required = false) String executionIds,
    @RequestParam(value = "limit", required = false) Integer limit,
    @RequestParam(value = "statuses", required = false) String statuses,
    @RequestParam(value = "expand", defaultValue = "true") boolean expand) {
    statuses = statuses ?: ExecutionStatus.values()*.toString().join(",")
    limit = limit ?: 1
    ExecutionRepository.ExecutionCriteria executionCriteria = new ExecutionRepository.ExecutionCriteria(
      limit: limit,
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

      List<Execution> executions = rx.Observable.from(ids.collect {
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

    List<Execution> allPipelines = rx.Observable.merge(ids.collect {
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
  List<Execution> searchForPipelinesByTrigger(
    @PathVariable(value = "application") String application,
    @RequestParam(value = "triggerTypes", required = false) String triggerTypes,
    @RequestParam(value = "pipelineName", required = false) String pipelineName,
    @RequestParam(value = "eventId", required = false) String eventId,
    @RequestParam(value = "trigger", required = false) String encodedTriggerParams,
    @RequestParam(value = "triggerTimeStartBoundary", defaultValue = "0") long triggerTimeStartBoundary,
    @RequestParam(value = "triggerTimeEndBoundary", defaultValue = "9223372036854775807" /* Long.MAX_VALUE */) long triggerTimeEndBoundary,
    @RequestParam(value = "statuses", required = false) String statuses,
    @RequestParam(value = "startIndex", defaultValue =  "0") int startIndex,
    @RequestParam(value = "size", defaultValue = "10") int size,
    @RequestParam(value = "reverse", defaultValue = "false") boolean reverse,
    @RequestParam(value = "expand", defaultValue = "false") boolean expand
    // TODO(joonlim): May make sense to add a summary boolean so that, when true, this returns a condensed summary rather than complete execution objects.
  ) {
    validateSearchForPipelinesByTriggerParameters(triggerTimeStartBoundary, triggerTimeEndBoundary, startIndex, size)

    final Map triggerParams = decodeTriggerParams(encodedTriggerParams) // Returned map will be empty if encodedTriggerParams is null

    Set<String> triggerTypesAsSet = (triggerTypes && triggerTypes != "*") ? triggerTypes.split(",") as Set : null // null means all trigger types
    Set<String> statusesAsSet = (statuses && statuses != "*") ? statuses.split(",") as Set : null // null means all statuses

    // Filter by application
    List<String> pipelineConfigIds = application == "*" ?
    getPipelineConfigIdsOfReadableApplications() :
    front50Service.getPipelines(application, false)*.id as List<String>

    List<Execution> pipelineExecutions = executionRepository.retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(pipelineConfigIds, triggerTimeStartBoundary, triggerTimeEndBoundary, size)
      .subscribeOn(Schedulers.io())
      .filter{
        // Filter by pipeline name
        if (pipelineName && pipelineName != it.name) {
          return false
        }
        // Filter by trigger type
        if (triggerTypesAsSet && !triggerTypesAsSet.contains(it.getTrigger().type)) {
          return false
        }
        // Filter by statuses
        if (statusesAsSet && !statusesAsSet.contains(it.getStatus().toString())) {
          return false
        }
        // Filter by event ID
        if (eventId && eventId != it.getTrigger().other.eventId) {
          return false
        }
        // Filter by trigger params
        return compareTriggerWithTriggerSubset(it.getTrigger(), triggerParams)
      }
      .toList()
      .toBlocking()
      .single()
      .sort(reverseBuildTime)

    if (reverse) {
      pipelineExecutions.reverse(true)
    }

    List<Execution> rval
    if (startIndex >= pipelineExecutions.size()) {
      rval = []
    } else {
      rval = pipelineExecutions.subList(startIndex, Math.min(pipelineExecutions.size(), startIndex + size))
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
  Execution getPipeline(@PathVariable String id) {
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

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/pipelines/{id}/cancel", method = RequestMethod.PUT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  void cancel(
    @PathVariable String id, @RequestParam(required = false) String reason,
    @RequestParam(defaultValue = "false") boolean force) {
    executionRepository.retrieve(PIPELINE, id).with { pipeline ->
      executionRunner.cancel(
        pipeline,
        AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"),
        reason
      )
    }
    executionRepository.updateStatus(PIPELINE, id, ExecutionStatus.CANCELED)
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/pipelines/{id}/pause", method = RequestMethod.PUT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  void pause(@PathVariable String id) {
    executionRepository.pause(PIPELINE, id, AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"))
    def pipeline = executionRepository.retrieve(PIPELINE, id)
    executionRunner.reschedule(pipeline)
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/pipelines/{id}/resume", method = RequestMethod.PUT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  void resume(@PathVariable String id) {
    executionRepository.resume(PIPELINE, id, AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"))
    def pipeline = executionRepository.retrieve(PIPELINE, id)
    executionRunner.unpause(pipeline)
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

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/pipelines/{id}/stages/{stageId}", method = RequestMethod.PATCH)
  Execution updatePipelineStage(
    @PathVariable String id,
    @PathVariable String stageId, @RequestBody Map context) {
    def pipeline = executionRepository.retrieve(PIPELINE, id)
    def stage = pipeline.stages.find { it.id == stageId }
    if (stage) {
      stage.context.putAll(context)
      validateStageUpdate(stage)

      stage.lastModified = new Stage.LastModifiedDetails(
        user: AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"),
        allowedAccounts: AuthenticatedRequest.getSpinnakerAccounts().orElse(null)?.split(",") ?: [],
        lastModifiedTime: System.currentTimeMillis()
      )

      // `lastModifiedBy` is deprecated (pending a update to deck)
      stage.context["lastModifiedBy"] = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")

      executionRepository.storeStage(stage)

      executionRunner.reschedule(pipeline)
    }
    pipeline
  }

  // If other execution mutations need validation, factor this out.
  void validateStageUpdate(Stage stage) {
    if (stage.context.manualSkip
        && !stageDefinitionBuilderFactory.builderFor(stage)?.canManuallySkip()) {
      throw new CannotUpdateExecutionStage("Cannot manually skip stage.")
    }
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/pipelines/{id}/stages/{stageId}/restart", method = RequestMethod.PUT)
  Execution retryPipelineStage(
    @PathVariable String id, @PathVariable String stageId) {
    def pipeline = executionRepository.retrieve(PIPELINE, id)
    executionRunner.restart(pipeline, stageId)
    pipeline
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/pipelines/{id}/evaluateExpression", method = RequestMethod.GET)
  Map evaluateExpressionForExecution(@PathVariable("id") String id,
                                     @RequestParam("expression")
                                       String expression) {
    def execution = executionRepository.retrieve(PIPELINE, id)
    def evaluated = contextParameterProcessor.process(
      [expression: expression],
      [execution: execution],
      true
    )
    return [result: evaluated?.expression, detail: evaluated?.expressionEvaluationSummary]
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/v2/applications/{application}/pipelines", method = RequestMethod.GET)
  List<Execution> getApplicationPipelines(@PathVariable String application,
                                          @RequestParam(value = "limit", defaultValue = "5")
                                            int limit,
                                          @RequestParam(value = "statuses", required = false)
                                            String statuses,
                                         @RequestParam(value = "expand", defaultValue = "true") Boolean expand) {
    return getPipelinesForApplication(application, limit, statuses, expand)
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/applications/{application}/pipelines", method = RequestMethod.GET)
  List<Execution> getPipelinesForApplication(@PathVariable String application,
                                             @RequestParam(value = "limit", defaultValue = "5")
                                               int limit,
                                             @RequestParam(value = "statuses", required = false)
                                               String statuses,
                                            @RequestParam(value = "expand", defaultValue = "true") Boolean expand) {
    if (!front50Service) {
      throw new UnsupportedOperationException("Cannot lookup pipelines, front50 has not been enabled. Fix this by setting front50.enabled: true")
    }

    if (!limit) {
      return []
    }

    statuses = statuses ?: ExecutionStatus.values()*.toString().join(",")
    def executionCriteria = new ExecutionRepository.ExecutionCriteria(
      limit: limit,
      statuses: (statuses.split(",") as Collection)
    )

    def pipelineConfigIds = front50Service.getPipelines(application, false)*.id as List<String>
    def strategyConfigIds = front50Service.getStrategies(application)*.id as List<String>
    def allIds = pipelineConfigIds + strategyConfigIds

    def allPipelines = rx.Observable.merge(allIds.collect {
      executionRepository.retrievePipelinesForPipelineConfigId(it, executionCriteria)
    }).subscribeOn(Schedulers.io()).toList().toBlocking().single().sort(startTimeOrId)

    if (!expand) {
      unexpandPipelineExecutions(allPipelines)
    }

    return filterPipelinesByHistoryCutoff(allPipelines, limit)
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
    if (trigger.type.toLowerCase() != "pipeline") {
      return
    }
    ((List) trigger.parentExecution.stages).clear()
    if (trigger.parentExecution.trigger.type.toLowerCase() == "pipeline") {
      clearTriggerStages((Map) trigger.parentExecution.trigger)
    }
  }

  private List<Execution> filterPipelinesByHistoryCutoff(List<Execution> pipelines, int limit) {
    // TODO-AJ The eventual goal is to return `allPipelines` without the need to group + filter below (WIP)
    def cutoffTime = (new Date(clock.millis()) - daysOfExecutionHistory).time

    def pipelinesSatisfyingCutoff = []
    pipelines.groupBy {
      it.pipelineConfigId
    }.values().each { List<Execution> pipelinesGroup ->
      def sortedPipelinesGroup = pipelinesGroup.sort(startTimeOrId).reverse()
      def recentPipelines = sortedPipelinesGroup.findAll {
        !it.startTime || it.startTime > cutoffTime
      }
      if (!recentPipelines && sortedPipelinesGroup) {
        // no pipeline executions within `daysOfExecutionHistory` so include the first `numberOfOldPipelineExecutionsToInclude`
        def upperBounds = Math.min(sortedPipelinesGroup.size(), numberOfOldPipelineExecutionsToInclude) - 1
        recentPipelines = sortedPipelinesGroup[0..upperBounds]
      }

      pipelinesSatisfyingCutoff.addAll(recentPipelines.subList(0, Math.min(recentPipelines.size(), limit)))
    }

    return pipelinesSatisfyingCutoff.sort(startTimeOrId)
  }
  // TODO(joonlim): Consider adding expand flag to RedisExecutionRepository's buildExecution method so that
  // these fields are never added in the first place.
  private static unexpandPipelineExecutions(List<Execution> pipelineExecutions) {
    pipelineExecutions.each { pipelineExecution ->
      clearTriggerStages(pipelineExecution.trigger.other) // remove from the "other" field - that is what Jackson works against
      pipelineExecution.getStages().each { stage ->
        if (stage.context?.group) {
          // TODO: consider making "group" a top-level field on the Stage model
          // for now, retain group in the context, as it is needed for collapsing templated pipelines in the UI
          stage.context = [ group: stage.context.group ]
        } else {
          stage.context = [:]
        }
        stage.outputs = [:]
        stage.tasks = []
      }
    }
  }

  private static Closure reverseBuildTime = { a, b ->
    def aBuildTime = a.buildTime ?: 0
    def bBuildTime = b.buildTime ?: 0

    return bBuildTime <=> aBuildTime ?: b.id <=> a.id
  }

  private static Closure startTimeOrId = { a, b ->
    def aStartTime = a.startTime ?: 0
    def bStartTime = b.startTime ?: 0

    return aStartTime <=> bStartTime ?: b.id <=> a.id
  }

  private OrchestrationViewModel convert(Execution orchestration) {
    def variables = [:]
    for (stage in orchestration.stages) {
      for (entry in stage.context.entrySet()) {
        variables[entry.key] = entry.value
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
      .map{ applicationName -> front50Service.getPipelines(applicationName, false)*.id as List<String> }
      .flatMap{ c -> c.stream() }
      .collect(Collectors.toList())

    return pipelineConfigIds
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
