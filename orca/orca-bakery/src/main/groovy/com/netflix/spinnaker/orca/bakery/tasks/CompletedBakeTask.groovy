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

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.bakery.BakerySelector
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Component
@CompileStatic
class CompletedBakeTask implements Task {

  @Autowired
  BakerySelector bakerySelector

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    def bakery = bakerySelector.select(stage)
    def bakeStatus = stage.context.status as BakeStatus
    def bake = Retrofit2SyncCall.execute(bakery.service.lookupBake(stage.context.region as String, bakeStatus.resourceId))
    // This treatment of ami allows both the aws and gce bake results to be propagated.
    def results = [
      ami: bake.ami ?: bake.imageName,
      imageId: bake.ami ?: bake.imageName,
      artifacts: bake.artifact ? [bake.artifact] : []
    ]

    if (bake.baseAmi != null) {
      results.put("baseAmiId", bake.baseAmi)
    }

    if (stage.context.cloudProvider) {
      results.cloudProvider = stage.context.cloudProvider
    }
    /**
     * TODO:
     * It would be good to standardize on the key here. "imageId" works for all providers.
     * Problem with "imageName" is that docker images have two components - imageName and imageVersion,
     * so "imageName" is not really a unique identifier. - sthadeshwar
     */
    if (bake.imageName || bake.amiName) {
      results.imageName = bake.imageName ?: bake.amiName
    }

    TaskResult.builder(ExecutionStatus.SUCCEEDED).context(results).build()
  }
}
