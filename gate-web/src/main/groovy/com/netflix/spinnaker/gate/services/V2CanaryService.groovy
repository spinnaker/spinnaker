/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.gate.services

import static com.netflix.spinnaker.gate.retrofit.UpstreamBadRequest.classifyError

import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.KayentaService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
@CompileStatic
@ConditionalOnExpression('${services.kayenta.enabled:false}')
class V2CanaryService {

  private static final String HYSTRIX_GROUP = "v2-canaries"

  @Autowired
  KayentaService kayentaService

  List getCredentials() {
    return HystrixFactory.newListCommand(HYSTRIX_GROUP, "getCanaryCredentials", {
      try {
        return kayentaService.getCredentials()
      } catch (RetrofitError error) {
        throw classifyError(error)
      }
    }).execute() as List
  }

  List listMetricsServiceMetadata(String filter, String metricsAccountName) {
    return HystrixFactory.newListCommand(HYSTRIX_GROUP, "listMetricsServiceMetadata", {
      try {
        return kayentaService.listMetricsServiceMetadata(filter, metricsAccountName)
      } catch (RetrofitError error) {
        throw classifyError(error)
      }
    }).execute() as List
  }

  List listJudges() {
    return HystrixFactory.newListCommand(HYSTRIX_GROUP, "listCanaryJudges", {
      try {
        return kayentaService.listJudges()
      } catch (RetrofitError error) {
        throw classifyError(error)
      }
    }).execute() as List
  }

  Map getCanaryResults(String canaryExecutionId, String storageAccountName) {
    return HystrixFactory.newMapCommand(HYSTRIX_GROUP, "getCanaryResults", {
      try {
        return kayentaService.getCanaryResult(canaryExecutionId, storageAccountName)
      } catch (RetrofitError error) {
        throw classifyError(error)
      }
    }).execute() as Map
  }

  List getMetricSetPairList(String metricSetPairListId, String storageAccountName) {
    return HystrixFactory.newListCommand(HYSTRIX_GROUP, "getMetricSetPairList", {
      try {
        return kayentaService.getMetricSetPairList(metricSetPairListId, storageAccountName)
      } catch (RetrofitError error) {
        throw classifyError(error)
      }
    }).execute() as List
  }
}
