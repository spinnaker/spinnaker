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
import retrofit.RetrofitError

class UpdateApplicationTask extends AbstractFront50Task {
  @Override
  void performRequest(String account, Application application) {
    front50Service.update(account, application)

    front50Service.credentials.findAll { it.global }.collect { it.name }.each { String globalAccountName ->
      try {
        def globalApplication = front50Service.get(globalAccountName, application.name)
        if (globalApplication.listAccounts().contains(account)) {
          // application exists in global registry and is already associated with target account, should be updated.
          application.updateAccounts((globalApplication.listAccounts() << account) as Set)
          front50Service.update(globalAccountName, application)
        }
      } catch (RetrofitError e) {
        if (e.response.status != 404) {
          throw e
        }
      }
    }
  }
}
