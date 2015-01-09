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
import org.springframework.stereotype.Component

@Component
class UpsertApplicationTask extends AbstractFront50Task {
  @Override
  void performRequest(String account, Application application) {
    /*
     * Upsert application to all global registries.
     */
    def existingGlobalApplication = front50Service.credentials.findAll {
      it.global
    }.collect {
      def existingGlobalApplication = fetchApplication(it.name, application.name)
      if (existingGlobalApplication) {
        // update all global applications with the new application metadata
        application.updateAccounts((existingGlobalApplication.listAccounts() << account) as Set)
        front50Service.update(it.name, application)
      } else {
        application.updateAccounts((application.listAccounts() << account) as Set)
        front50Service.create(it.name, application.name, application)
      }

      return existingGlobalApplication
    }.find { it }

    /*
     * Upsert application to specific account registry.
     *
     * If the application does not exist, create it using details from the global registry where available.
     */
    def existingApplication = fetchApplication(account, application.name)
    if (existingApplication) {
      application.accounts = null
      front50Service.update(account, application)
    } else {
      if (existingGlobalApplication) {
        existingGlobalApplication.properties.each { k, v ->
          // merge in any properties from the global registry that have not been overridden by the application.
          if (!application."${k}") {
            application."${k}" = v
          }
        }
      }

      application.accounts = null
      front50Service.create(account, application.name, application)
    }
  }

  @Override
  String getNotificationType() {
    return "upsertapplication"
  }
}
