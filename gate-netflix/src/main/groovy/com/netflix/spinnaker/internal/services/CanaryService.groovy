/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.internal.services

import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.internal.services.internal.MineService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import static com.netflix.spinnaker.gate.retrofit.UpstreamBadRequest.classifyError

/**
 *
 * @author sthadeshwar
 */
@Component
@CompileStatic
class CanaryService {

  private static final String HYSTRIX_GROUP = "canaries"

  @Autowired(required = false)
  MineService mineService

  void generateCanaryResult(String canaryId, int duration, String durationUnit) {
    HystrixFactory.newVoidCommand(HYSTRIX_GROUP, "generateCanaryResult", {
      try {
        mineService?.generateCanaryResult(canaryId, duration, durationUnit)
      } catch (RetrofitError error) {
        throw classifyError(error)
      }
    }).execute()
  }

  List<Map> getCanaryAnalysisHistory(String canaryDeploymentId) {
    HystrixFactory.newListCommand(HYSTRIX_GROUP, "getCanaryAnalysisHistory", {
      try {
        mineService ? mineService.getCanaryAnalysisHistory(canaryDeploymentId) : []
      } catch (RetrofitError error) {
        throw classifyError(error)
      }
    }).execute()
  }

  Map endCanary(String canaryId, String result, String reason) {
    HystrixFactory.newMapCommand(HYSTRIX_GROUP, "endCanary", {
      try {
        mineService ? mineService.endCanary(canaryId, result, reason) : [:]
      } catch (RetrofitError error) {
        throw classifyError(error)
      }
    }).execute()
  }

  Map showCanary(String canaryId) {
    HystrixFactory.newMapCommand(HYSTRIX_GROUP, "showCanary", {
      try {
        mineService ? mineService.showCanary(canaryId) : [:]
      } catch (RetrofitError error) {
        throw classifyError(error)
      }
    }).execute()
  }

  List<String> getCanaryConfigNames() {
    HystrixFactory.newListCommand(HYSTRIX_GROUP, "getCanaryConfigNames", {
      try {
        mineService ? mineService.getCanaryConfigNames() : []
      } catch (RetrofitError error) {
        throw classifyError(error)
      }
    }).execute()
  }

}
