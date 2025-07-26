/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.core.Observable

@RestController
class ProjectController {
  @Autowired
  ExecutionRepository executionRepository

  @Autowired(required = false)
  Front50Service front50Service

  @RequestMapping(value = "/projects/{projectId}/pipelines", method = RequestMethod.GET)
  List<PipelineExecution> list(@PathVariable String projectId,
                               @RequestParam(value="limit", defaultValue="5") int limit,
                               @RequestParam(value = "statuses", required = false) String statuses) {
    if (!front50Service) {
      throw new UnsupportedOperationException("Front50 is not enabled, no way to retrieve projects. Fix this by setting front50.enabled: true")
    }
    if (!limit) {
      return []
    }

    def pipelineConfigIds = []
    try {
      def project = front50Service.getProject(projectId)
      pipelineConfigIds = project.config.pipelineConfigs*.pipelineConfigId
    } catch (SpinnakerHttpException e) {
      if (e.responseCode == 404) {
        return []
      }

      throw e
    }

    statuses = statuses ?: ExecutionStatus.values()*.toString().join(",")
    def executionCriteria = new ExecutionRepository.ExecutionCriteria(
      pageSize: limit,
      statuses: (statuses.split(",") as Collection)
    )

    def allPipelines = Observable.merge(pipelineConfigIds.collect {
      executionRepository.retrievePipelinesForPipelineConfigId(it, executionCriteria)
    }).subscribeOn(Schedulers.io()).toList().blockingGet().sort(startTimeOrId)

    return allPipelines
  }

  private static Closure startTimeOrId = { a, b ->
    def aStartTime = a.startTime ?: 0
    def bStartTime = b.startTime ?: 0

    return aStartTime.compareTo(bStartTime) ?: b.id <=> a.id
  }
}
