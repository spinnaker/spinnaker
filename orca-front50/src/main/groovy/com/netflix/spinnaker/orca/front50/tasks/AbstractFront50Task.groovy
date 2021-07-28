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


package com.netflix.spinnaker.orca.front50.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import org.springframework.lang.Nullable
import retrofit.RetrofitError

import javax.annotation.Nonnull
import java.util.concurrent.TimeUnit

abstract class AbstractFront50Task implements RetryableTask {
  Front50Service front50Service
  final ObjectMapper mapper
  final DynamicConfigService configService

  AbstractFront50Task(@Nullable Front50Service front50Service,
                      ObjectMapper mapper,
                      DynamicConfigService configService) {
    this.front50Service = front50Service
    this.mapper = mapper
    this.configService = configService
  }

  abstract  TaskResult performRequest(Application application)
  abstract String getNotificationType()

  abstract long getBackoffPeriod()

  abstract long getTimeout()

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    if (!front50Service) {
      throw new UnsupportedOperationException("Front50 was not enabled. Fix this by setting front50.enabled: true")
    }

    def application = mapper.convertValue(stage.context.application, Application)
    if (stage.context.user){
      application.user = stage.context.user
    }
    def missingInputs = []

    if (!application.name) {
      missingInputs << 'application.name'
    }

    if (missingInputs) {
      throw new IllegalArgumentException("Missing one or more required task parameters (${missingInputs.join(", ")})")
    }

    def outputs = [
      "notification.type": getNotificationType(),
      "application.name": application.name
    ]
    TaskResult taskResult = performRequest(application)
    outputs << taskResult.outputs

    return TaskResult.builder(taskResult.status).context(outputs).build()
  }

  Application fetchApplication(String applicationName) {
    try {
      return front50Service.get(applicationName)
    } catch (RetrofitError e) {
      if (e.response?.status == 404) {
        return null
      }

      throw e
    }
  }
}
