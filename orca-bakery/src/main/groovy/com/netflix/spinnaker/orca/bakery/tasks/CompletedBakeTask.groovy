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

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class CompletedBakeTask implements Task {

  @Autowired
  BakeryService bakery

  @Override
  TaskResult execute(Stage stage) {
    def region = stage.context.region as String
    def bakeStatus = stage.context.status as BakeStatus
    def bake = bakery.lookupBake(region, bakeStatus.resourceId).toBlocking().first()
    // This treatment of ami allows both the aws and gce bake results to be propagated.
    def results = [
      ami: bake.ami ?: bake.imageName,
      imageId: bake.ami ?: bake.imageName,
      artifacts: bake.artifact ? [bake.artifact] : []
    ]
    /**
     * TODO:
     * It would be good to standardize on the key here. "imageId" works for all providers.
     * Problem with "imageName" is that docker images have two components - imageName and imageVersion,
     * so "imageName" is not really a unique identifier. - sthadeshwar
     */
    if (bake.imageName || bake.amiName) {
      results.imageName = bake.imageName ?: bake.amiName
    }

    new TaskResult(ExecutionStatus.SUCCEEDED, results)
  }
}
