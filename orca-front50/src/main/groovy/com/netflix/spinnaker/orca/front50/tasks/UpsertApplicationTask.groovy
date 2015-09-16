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

import com.netflix.spinnaker.orca.front50.model.Application
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Slf4j
@Component
@CompileStatic
class UpsertApplicationTask extends AbstractFront50Task {
  @Override
  Map<String, Object> performRequest(String account, Application application) {
    Map<String, Object> outputs = [:]
    outputs.previousState = [:]

    /*
     * Upsert application to all global registries.
     */
    front50Service.credentials.findAll { it.global }.each {
      def existingGlobalApplication = fetchApplication(it.name, application.name)
      if (existingGlobalApplication) {
        outputs.previousState = existingGlobalApplication

        application.updateAccounts((existingGlobalApplication.listAccounts() << account) as Set)

        log.info("Updating application (name: ${application.name}, account: ${it.name})")
        front50Service.update(it.name, application)
      } else {
        application.updateAccounts((application.listAccounts() << account) as Set)

        log.info("Creating application (name: ${application.name}, account: ${it.name})")
        front50Service.create(it.name, application.name, application)
      }
    }.find { it }

    outputs.newState = application ?: [:]
    outputs
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private static void mergeApplicationProperties(Application source, Application target) {
    source.properties.each { k, v ->
      // merge in any properties from the global registry that have not been overridden by the application.
      if (!target."${k}") {
        target."${k}" = v
      }
    }
  }

  @Override
  String getNotificationType() {
    return "upsertapplication"
  }
}
