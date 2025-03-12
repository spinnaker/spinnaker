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

package com.netflix.spinnaker.orca.applications.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.applications.utils.ApplicationNameValidator
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.front50.tasks.AbstractFront50Task
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.lang.Nullable
import org.springframework.stereotype.Component

@Slf4j
@Component
@CompileStatic
class UpsertApplicationTask extends AbstractFront50Task implements ApplicationNameValidator {

  UpsertApplicationTask(@Nullable Front50Service front50Service,
                        ObjectMapper mapper,
                        DynamicConfigService configService) {
    super(front50Service, mapper, configService)
  }

  @Override
  long getBackoffPeriod() {
    return this.configService
        .getConfig(
            Long.class,
            "tasks.upsert-application.backoff-ms",
           10000L
        )
  }

  @Override
  long getTimeout() {
    return this.configService
        .getConfig(
            Long.class,
            "tasks.upsert-application.timeout-ms",
            3600000L
        )
  }

  @Override
  TaskResult performRequest(Application application) {
    Map<String, Object> outputs = [:]
    outputs.previousState = [:]

    /*
     * Upsert application to all global registries.
     */

    def validationErrors = validate(application)
    if (validationErrors) {
      throw new IllegalArgumentException("Invalid application name, errors: ${validationErrors}")
    }

    def existingApplication = fetchApplication(application.name)
    if (existingApplication) {
      outputs.previousState = existingApplication
      log.info("Updating application (name: ${application.name})")
      front50Service.update(application.name, application)
    } else {
      log.info("Creating application (name: ${application.name})")
      front50Service.create(application)
      if (application.permission?.permissions == null) {
        application.setPermissions(Permissions.EMPTY)
      }
    }

    if (application.permission?.permissions != null) {
      front50Service.updatePermission(application.name, application.permission)
    }

    outputs.newState = application ?: [:]
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).outputs(outputs).build()
  }

  @Override
  String getNotificationType() {
    return "upsertapplication"
  }
}
