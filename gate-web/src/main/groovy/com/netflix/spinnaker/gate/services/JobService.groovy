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
import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@CompileStatic
@Component
class JobService {
  private static final String GROUP = "jobs"

  @Autowired
  ClouddriverService clouddriverService

  @Autowired
  InsightConfiguration insightConfiguration

  @Autowired
  ProviderLookupService providerLookupService

  List getForApplication(String applicationName, String expand, String selectorKey) {
    String commandKey = Boolean.valueOf(expand) ? "getExpandedJobsForApplication" : "getJobsForApplication"
    HystrixFactory.newListCommand(GROUP, commandKey) {
      clouddriverService.getJobs(applicationName, expand)
    } execute()
  }

  Map getForApplicationAndAccountAndRegion(String applicationName, String account, String region, String name, String selectorKey) {
    HystrixFactory.newMapCommand(GROUP, "getJobsForApplicationAccountAndRegion-${providerLookupService.providerForAccount(account)}", {
      try {
        def context = getContext(applicationName, account, region, name)
        return clouddriverService.getJobDetails(applicationName, account, region, name) + [
            "insightActions": insightConfiguration.job.collect { it.applyContext(context) }
        ]
      } catch (RetrofitError e) {
        if (e.response?.status == 404) {
          return [:]
        }
        throw e
      }
    }) execute()
  }

  static Map<String, String> getContext(String application, String account, String region, String jobName) {
    return ["application": application, "account": account, "region": region, "jobName": jobName]
  }
}

