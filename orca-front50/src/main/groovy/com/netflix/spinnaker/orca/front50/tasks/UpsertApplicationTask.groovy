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
    outputs.previousState = fetchApplication(account, application.name) ?: [:]

    /*
     * Upsert application to all global registries.
     */
    def existingGlobalApplication = front50Service.credentials.findAll {
      it.global
    }.collect {
      def existingGlobalApplication = fetchApplication(it.name, application.name)
      if (existingGlobalApplication) {
        existingGlobalApplication.listAccounts().each {
          if (fetchApplication(it, application.name)) {
            try {
              // propagate updates to all other per-account registries that this global application is associated with
              log.info("Updating application (name: ${application.name}, account: ${it})")
              front50Service.update(it, application)
            } catch (RetrofitError e) {
              log.error("Unable to update application (name: ${application.name}, account: ${it})", e)
            }
          }
        }

        // ensure global and non-global account associations are merged prior to updating
        application.updateAccounts((existingGlobalApplication.listAccounts() << account) as Set)

        log.info("Updating application (name: ${application.name}, account: ${it.name})")
        front50Service.update(it.name, application)
      } else {
        application.updateAccounts((application.listAccounts() << account) as Set)

        log.info("Creating application (name: ${application.name}, account: ${it.name})")
        front50Service.create(it.name, application.name, application)
      }

      return existingGlobalApplication
    }.find { it }

    // avoid propagating account associations to non-global application registries
    application.accounts = null
    if (existingGlobalApplication) {
      existingGlobalApplication.accounts = null
    }

    /*
     * Upsert application to specific account registry.
     *
     * If the application does not exist, create it using details from the global registry where available.
     */
    def existingApplication = fetchApplication(account, application.name)
    if (existingApplication) {
      log.info("Updating application (name: ${application.name}, account: ${account})")
      front50Service.update(account, application)
    } else {
      if (existingGlobalApplication) {
        mergeApplicationProperties(existingGlobalApplication, application)
      }

      log.info("Creating application (name: ${application.name}, account: ${account})")
      front50Service.create(account, application.name, application)
    }

    outputs.newState = fetchApplication(account, application.name) ?: [:]
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
