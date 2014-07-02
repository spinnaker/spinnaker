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
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.bakery.api.Bake
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
class CreateBakeTask implements Task {

  @Autowired
  BakeryService bakery

  @Autowired
  ObjectMapper mapper

  @Override
  TaskResult execute(TaskContext context) {
    def region = context.inputs."bake.region" as String
    def bake = bakeFromContext(context)

    def bakeStatus = bakery.createBake(region, bake).toBlockingObservable().single()

    new DefaultTaskResult(TaskResult.Status.SUCCEEDED, ["bake.status": bakeStatus])
  }

  private Bake bakeFromContext(TaskContext context) {
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.convertValue(context.getInputs("bake"), Bake)
  }
}
