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

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import org.springframework.beans.factory.annotation.Autowired
import retrofit.RetrofitError

@CompileStatic
class CompletedBakeTask implements Task {

  @Autowired BakeryService bakery

  @Override
  TaskResult execute(TaskContext context) {
    def region = context.inputs."bake.region" as String
    def bakeStatus = context.inputs."bake.status" as BakeStatus
    try {
      def bake = bakery.lookupBake(region, bakeStatus.resourceId).toBlocking().first()
      new DefaultTaskResult(TaskResult.Status.SUCCEEDED, ["bake.ami": bake.ami])
    } catch (RetrofitError e) {
      // TODO: attach some reporting info here
      new DefaultTaskResult(TaskResult.Status.FAILED)
    }
  }
}
