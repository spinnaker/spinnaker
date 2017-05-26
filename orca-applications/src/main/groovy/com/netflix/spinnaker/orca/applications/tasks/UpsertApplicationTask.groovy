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

import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.front50.tasks.AbstractFront50Task
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Slf4j
@Component
@CompileStatic
class UpsertApplicationTask extends AbstractFront50Task {
  @Override
  Map<String, Object> performRequest(Application application) {
    Map<String, Object> outputs = [:]
    outputs.previousState = [:]

    /*
     * Upsert application to all global registries.
     */

    def existingApplication = fetchApplication(application.name)
    if (existingApplication) {
      outputs.previousState = existingApplication
      log.info("Updating application (name: ${application.name})")
      front50Service.update(application.name, application)
    } else {
      log.info("Creating application (name: ${application.name})")
      front50Service.create(application)
    }

    try {
      front50Service.updatePermission(application.name, application.permission)
    } catch (RetrofitError re) {
      log.error("Could not create or update application permission", re)
    }

    outputs.newState = application ?: [:]
    outputs
  }

  @Override
  String getNotificationType() {
    return "upsertapplication"
  }
}
