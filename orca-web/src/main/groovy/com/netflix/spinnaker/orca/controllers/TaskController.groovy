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
import com.netflix.spinnaker.orca.pipeline.PipelineStartTracker
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.AbstractStage
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import rx.schedulers.Schedulers

import java.time.Clock

import static com.netflix.spinnaker.orca.pipeline.model.Execution.V2_EXECUTION_ENGINE

@RestController
class TaskController {
  @Autowired
  Front50Service front50Service

  @Autowired
  ExecutionRepository executionRepository

  @Autowired
  PipelineStartTracker startTracker

  @Autowired
  PipelineStarter pipelineStarter

  @Autowired
  ExecutionRunner executionRunner

  @Autowired
  Collection<StageDefinitionBuilder> stageBuilders

  @Value('${tasks.daysOfExecutionHistory:14}')
  int daysOfExecutionHistory

  @Value('${tasks.numberOfOldPipelineExecutionsToInclude:2}')
  int numberOfOldPipelineExecutionsToInclude

  Clock clock = Clock.systemUTC()

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/applications/{application}/tasks", method = RequestMethod.GET)
  List<Orchestration> list(@PathVariable String application,
                           @RequestParam(value = "limit", defaultValue = "3500") int limit,
                           @RequestParam(value = "statuses", required = false) String statuses) {
    statuses = statuses ?: ExecutionStatus.values()*.toString().join(",")
    def executionCriteria = new ExecutionRepository.ExecutionCriteria(
      limit: limit,
      statuses: (statuses.split(",") as Collection)
    )

    def startTimeCutoff = (new Date(clock.millis()) - daysOfExecutionHistory).time
    executionRepository.retrieveOrchestrationsForApplication(application, executionCriteria)
                       .filter({ Orchestration orchestration -> !orchestration.startTime || (orchestration.startTime > startTimeCutoff) })
                       .map({ Orchestration orchestration -> convert(orchestration) })
                       .subscribeOn(Schedulers.io()).toList().toBlocking().single().sort(startTimeOrId)
  }

  @PreAuthorize("@fiatPermissionEvaluator.storeWholePermission()")
  @PostFilter("hasPermission(filterObject.application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/tasks", method = RequestMethod.GET)
  List<OrchestrationViewModel> list() {
    executionRepository.retrieveOrchestrations().toBlocking().iterator.collect { convert it }
  }

  // @PostAuthorize("hasPermission(returnObject.application, 'APPLICATION', 'READ')")
  //
  // This endpoint is unsecured because of the create application process, where Deck immediately
  // queries this endpoint to check on the status of creating a new application before the
  // application permissions have been propagated. Furthermore, given that the ID is a hard-to-guess
  // GUID, it's unlikely than an attacker would be able to guess the identifier for any task.
  @RequestMapping(value = "/tasks/{id}", method = RequestMethod.GET)
  OrchestrationViewModel getTask(@PathVariable String id) {
    convert executionRepository.retrieveOrchestration(id)
  }

  Orchestration getOrchestration(String id) {
    executionRepository.retrieveOrchestration(id)
  }

  @PreAuthorize("hasPermission(this.getOrchestration(#id)?.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/tasks/{id}", method = RequestMethod.DELETE)
  void deleteTask(@PathVariable String id) {
    executionRepository.deleteOrchestration(id)
  }

  @PreAuthorize("hasPermission(this.getOrchestration(#id)?.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/tasks/{id}/cancel", method = RequestMethod.PUT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  void cancelTask(@PathVariable String id) {
    executionRepository.cancel(id, AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"))
  }

  @RequestMapping(value = "/pipelines", method = RequestMethod.GET)
  List<Pipeline> listLatestPipelines(@RequestParam(value = "pipelineConfigIds") String pipelineConfigIds,
                                     @RequestParam(value = "statuses", required = false) String statuses) {
    statuses = statuses ?: ExecutionStatus.values()*.toString().join(",")
    def executionCriteria = new ExecutionRepository.ExecutionCriteria(
      limit: 1,
      statuses: (statuses.split(",") as Collection)
    )

    def ids = pipelineConfigIds.split(',')

    def allPipelines = rx.Observable.merge(ids.collect {
      executionRepository.retrievePipelinesForPipelineConfigId(it, executionCriteria)
    }).subscribeOn(Schedulers.io()).toList().toBlocking().single().sort(startTimeOrId)

    return filterPipelinesByHistoryCutoff(allPipelines)
  }

  @PostAuthorize("hasPermission(returnObject.application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/pipelines/{id}", method = RequestMethod.GET)
  Pipeline getPipeline(@PathVariable String id) {
    executionRepository.retrievePipeline(id)
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/pipelines/{id}", method = RequestMethod.DELETE)
  void deletePipeline(@PathVariable String id) {
    executionRepository.deletePipeline(id)
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/pipelines/{id}/cancel", method = RequestMethod.PUT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  void cancel(@PathVariable String id) {
    executionRepository.cancel(id, AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"))
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/pipelines/{id}/pause", method = RequestMethod.PUT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  void pause(@PathVariable String id) {
    executionRepository.pause(id, AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"))
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/pipelines/{id}/resume", method = RequestMethod.PUT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  void resume(@PathVariable String id) {
    executionRepository.resume(id, AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"))
  }

  @PreAuthorize("@fiatPermissionEvaluator.storeWholePermission()")
  @PostFilter("hasPermission(this.getPipeline(filterObject)?.application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/pipelines/running", method = RequestMethod.GET)
  List<String> runningPipelines() {
    startTracker.getAllStartedExecutions()
  }

  @PreAuthorize("@fiatPermissionEvaluator.storeWholePermission()")
  @PostFilter("hasPermission(this.getPipeline(filterObject)?.application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/pipelines/waiting", method = RequestMethod.GET)
  List<String> waitingPipelines() {
    startTracker.getAllWaitingExecutions()
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/pipelines/{id}/stages/{stageId}", method = RequestMethod.PATCH)
  Pipeline updatePipelineStage(@PathVariable String id, @PathVariable String stageId, @RequestBody Map context) {
    def pipeline = executionRepository.retrievePipeline(id)
    def stage = pipeline.stages.find { it.id == stageId } as PipelineStage
    if (stage) {
      stage.context.putAll(context)

      stage.lastModified = new AbstractStage.LastModifiedDetails(
        user: AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"),
        allowedAccounts: AuthenticatedRequest.getSpinnakerAccounts().orElse(null)?.split(",") ?: [],
        lastModifiedTime: System.currentTimeMillis()
      )

      // `lastModifiedBy` is deprecated (pending a update to deck)
      stage.context["lastModifiedBy"] = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")

      executionRepository.storeStage(stage)
    }
    pipeline
  }

  @PreAuthorize("hasPermission(this.getPipeline(#id)?.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "/pipelines/{id}/stages/{stageId}/restart", method = RequestMethod.PUT)
  Pipeline retryPipelineStage(@PathVariable String id, @PathVariable String stageId) {
    def pipeline = executionRepository.retrievePipeline(id)
    def stage = pipeline.stages.find { it.id == stageId } as PipelineStage
    if (stage) {
      def stageBuilder = stageBuilders.find { it.type == stage.type }
      stage = stageBuilder.prepareStageForRestart(executionRepository, stage, stageBuilders)
      executionRepository.storeStage(stage)
      if (pipeline.executionEngine == V2_EXECUTION_ENGINE) {
        executionRunner.resume(pipeline)
      } else {
        pipelineStarter.resume(pipeline)
      }
    }
    pipeline
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/v2/applications/{application}/pipelines", method = RequestMethod.GET)
  List<Pipeline> getApplicationPipelines(@PathVariable String application,
                                         @RequestParam(value = "limit", defaultValue = "5") int limit,
                                         @RequestParam(value = "statuses", required = false) String statuses) {
    return getPipelinesForApplication(application, limit, statuses)
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/applications/{application}/pipelines", method = RequestMethod.GET)
  List<Pipeline> getPipelinesForApplication(@PathVariable String application,
                                            @RequestParam(value = "limit", defaultValue = "5") int limit,
                                            @RequestParam(value = "statuses", required = false) String statuses) {
    if (!limit) {
      return []
    }

    statuses = statuses ?: ExecutionStatus.values()*.toString().join(",")
    def executionCriteria = new ExecutionRepository.ExecutionCriteria(
      limit: limit,
      statuses: (statuses.split(",") as Collection)
    )

    def pipelineConfigIds = front50Service.getPipelines(application)*.id as List<String>
    def strategyConfigIds = front50Service.getStrategies(application)*.id as List<String>
    def allIds = pipelineConfigIds + strategyConfigIds

    def allPipelines = rx.Observable.merge(allIds.collect {
      executionRepository.retrievePipelinesForPipelineConfigId(it, executionCriteria)
    }).subscribeOn(Schedulers.io()).toList().toBlocking().single().sort(startTimeOrId)

    return filterPipelinesByHistoryCutoff(allPipelines)
  }

  private List<Pipeline> filterPipelinesByHistoryCutoff(List<Pipeline> pipelines) {
    // TODO-AJ The eventual goal is to return `allPipelines` without the need to group + filter below (WIP)
    def cutoffTime = (new Date(clock.millis()) - daysOfExecutionHistory).time

    def pipelinesSatisfyingCutoff = []
    pipelines.groupBy { it.pipelineConfigId }.values().each { List<Pipeline> pipelinesGroup ->
      def sortedPipelinesGroup = pipelinesGroup.sort(startTimeOrId).reverse()
      def recentPipelines = sortedPipelinesGroup.findAll { !it.startTime || it.startTime > cutoffTime }
      if (!recentPipelines && sortedPipelinesGroup) {
        // no pipeline executions within `daysOfExecutionHistory` so include the first `numberOfOldPipelineExecutionsToInclude`
        def upperBounds = Math.min(sortedPipelinesGroup.size(), numberOfOldPipelineExecutionsToInclude) - 1
        recentPipelines = sortedPipelinesGroup[0..upperBounds]
      }

      pipelinesSatisfyingCutoff.addAll(recentPipelines)
    }

    return pipelinesSatisfyingCutoff.sort(startTimeOrId)
  }

  private static Closure startTimeOrId = { a, b ->
    def aStartTime = a.startTime ?: 0
    def bStartTime = b.startTime ?: 0

    return aStartTime <=> bStartTime ?: b.id <=> a.id
  }

  private OrchestrationViewModel convert(Orchestration orchestration) {
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

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(ExecutionNotFoundException)
  void notFound() {}
}
