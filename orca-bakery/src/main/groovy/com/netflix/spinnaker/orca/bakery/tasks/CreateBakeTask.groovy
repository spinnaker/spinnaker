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

package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.bakery.api.BakeRequest
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class CreateBakeTask implements Task {

  @Autowired BakeryService bakery
  @Autowired ObjectMapper mapper

  @Override
  TaskResult execute(Stage stage) {
    String region = stage.context.region
    def bake = bakeFromContext(stage)

    def bakeStatus = bakery.createBake(region, bake).toBlocking().single()

    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [status: bakeStatus])
  }

  @CompileDynamic
  private BakeRequest bakeFromContext(Stage stage) {
    BakeRequest request = mapper.convertValue(stage.context, BakeRequest)
    if (stage.execution instanceof Pipeline) {
      Map trigger = ((Pipeline) stage.execution).trigger

      return request.copyWith( packageName : findPackage(trigger, request) )
    }
    return request
  }

  @CompileDynamic
  private String findPackage(Map trigger, BakeRequest request) {
    List<Map> artifacts = trigger.buildInfo?.artifacts
    if (!artifacts) {
      return request.packageName
    }

    String prefix = "${request.packageName}${request.baseOs.packageType.versionDelimiter}"
    String fileExtension = ".${request.baseOs.packageType.packageType}"

    Map artifact = artifacts.find { it.fileName.startsWith(prefix) && it.fileName.endsWith(fileExtension) }

    if (artifact) {
      return artifact.fileName.substring(0, artifact.fileName.lastIndexOf(fileExtension))
    }

    throw new IllegalStateException("Unable to find deployable artifact starting with ${prefix} and ending with ${fileExtension} in ${artifacts}")
  }
}
