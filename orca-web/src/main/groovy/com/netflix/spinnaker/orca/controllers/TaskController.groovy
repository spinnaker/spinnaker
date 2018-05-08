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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.model.OrchestrationViewModel
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.transform.InheritConstructors
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

import java.time.Clock

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import static java.time.ZoneOffset.UTC

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

  @Value('${tasks.daysOfExecutionHistory:14}')
  int daysOfExecutionHistory

  @Value('${tasks.numberOfOldPipelineExecutionsToInclude:2}')
  int numberOfOldPipelineExecutionsToInclude

  Clock clock = Clock.systemUTC()

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/applications/{application}/tasks", method = RequestMethod.GET)
  List<OrchestrationViewModel> list(@PathVariable String application,
                                    @RequestParam(value = "limit", defaultValue = "3500") int limit,
                                    @RequestParam(value = "statuses", required = false) String statuses) {
    statuses = statuses ?: ExecutionStatus.values()*.toString().join(",")
    def executionCriteria = new ExecutionRepository.ExecutionCriteria(
      limit: limit,
      statuses: (statuses.split(",") as Collection)
    )

    def startTimeCutoff = clock
      .instant()
      .atZone(UTC)
      .minusDays(daysOfExecutionHistory)
      .toInstant()
      .toEpochMilli()

    def orchestrations = executionRepository
      .retrieveOrchestrationsForApplication(application, executionCriteria)
      .toList()
      .findAll({ Execution orchestration -> !orchestration.startTime || (orchestration.startTime > startTimeCutoff) })
    orchestrations.sort(startTimeOrId)

    orchestrations
      .collect({ Execution orchestration -> convert(orchestration) })
      .subList(0, Math.min(orchestrations.size(), limit))
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

  @RequestMapping(value = "/pipelines", method = RequestMethod.GET)
  List<Execution> listLatestPipelines(
    @RequestParam(value = "pipelineConfigIds") String pipelineConfigIds,
    @RequestParam(value = "limit", required = false) Integer limit,
    @RequestParam(value = "statuses", required = false) String statuses) {
    statuses = statuses ?: ExecutionStatus.values()*.toString().join(",")
    limit = limit ?: 1
    def executionCriteria = new ExecutionRepository.ExecutionCriteria(
      limit: limit,
      statuses: (statuses.split(",") as Collection)
    )

    def ids = pipelineConfigIds.split(',')

    def allPipelines = rx.Observable.merge(ids.collect {
      rx.Observable.from(executionRepository.retrievePipelinesForPipelineConfigId(it, executionCriteria))
    }).subscribeOn(Schedulers.io()).toList().toBlocking().single()
    allPipelines.sort(startTimeOrId)

    return filterPipelinesByHistoryCutoff(allPipelines, limit)
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
      false
    )
    return [result: evaluated?.expression]
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

    def allPipelines = allIds.collect {
      executionRepository.retrievePipelinesForPipelineConfigId(it, executionCriteria)
    }.flatten()
    allPipelines.sort(startTimeOrId)

    if (!expand) {
      allPipelines.each { pipeline ->
        clearTriggerStages(pipeline.trigger.other) // remove from the "other" field - that is what Jackson works against
        pipeline.getStages().each { stage ->
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

    return filterPipelinesByHistoryCutoff(allPipelines, limit)
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
      def sortedPipelinesGroup = pipelinesGroup
      sortedPipelinesGroup.sort(startTimeOrId)
      sortedPipelinesGroup = sortedPipelinesGroup.reverse()

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
    pipelinesSatisfyingCutoff.sort(startTimeOrId)

    return pipelinesSatisfyingCutoff
  }

  private static Comparator<Execution> startTimeOrId = { a, b ->
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

  @InheritConstructors
  @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
  private static class FeatureNotEnabledException extends RuntimeException {}

  @ResponseStatus(HttpStatus.CONFLICT)
  private static class CannotDeleteRunningExecution extends RuntimeException {
    CannotDeleteRunningExecution(ExecutionType type, String id) {
      super("Cannot delete a running $type, please cancel it first.")
    }
  }
}
