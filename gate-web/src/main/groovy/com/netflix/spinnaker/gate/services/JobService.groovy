/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.config.InsightConfiguration
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class JobService {

  @Autowired
  ClouddriverServiceSelector clouddriverServiceSelector

  @Autowired
  OrcaServiceSelector orcaServiceSelector


  @Autowired
  InsightConfiguration insightConfiguration

  @Autowired
  ProviderLookupService providerLookupService

  List getPreconfiguredJobs() {
    Retrofit2SyncCall.execute(orcaServiceSelector.select().getPreconfiguredJobs())
  }

  Map getForApplicationAndAccountAndRegion(String applicationName, String account, String region, String name, String selectorKey) {
    try {
      def context = getContext(applicationName, account, region, name)
      return Retrofit2SyncCall.execute(clouddriverServiceSelector.select().getJobDetails(applicationName, account, region, name, "")) + [
          "insightActions": insightConfiguration.job.collect { it.applyContext(context) }
      ]
    } catch (SpinnakerHttpException e) {
      if (e.responseCode == 404) {
        return [:]
      }
      throw e
    }
  }

  static Map<String, String> getContext(String application, String account, String region, String jobName) {
    return ["application": application, "account": account, "region": region, "jobName": jobName]
  }
}

