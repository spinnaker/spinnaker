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

package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.internal.MineService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import static com.netflix.spinnaker.gate.retrofit.UpstreamBadRequest.classifyError

@Component
@CompileStatic
@ConditionalOnProperty('services.mine.enabled')
class CanaryService {

  @Autowired
  MineService mineService

  void generateCanaryResult(String canaryId, int duration, String durationUnit) {
    try {
      mineService?.generateCanaryResult(canaryId, duration, durationUnit, "")
    } catch (RetrofitError error) {
      throw classifyError(error)
    }
  }

  List<Map> getCanaryAnalysisHistory(String canaryDeploymentId) {
    try {
      mineService ? mineService.getCanaryAnalysisHistory(canaryDeploymentId) : []
    } catch (RetrofitError error) {
      throw classifyError(error)
    }
  }

  Map endCanary(String canaryId, String result, String reason) {
    try {
      mineService ? mineService.endCanary(canaryId, result, reason, "") : [:]
    } catch (RetrofitError error) {
      throw classifyError(error)
    }
  }

  Map showCanary(String canaryId) {
    try {
      mineService ? mineService.showCanary(canaryId) : [:]
    } catch (RetrofitError error) {
      throw classifyError(error)
    }
  }

  List<String> getCanaryConfigNames(String application) {
    try {
      mineService ? mineService.getCanaryConfigNames(application) : []
    } catch (RetrofitError error) {
      throw classifyError(error)
    }
  }

  List<Map> canaryConfigsForApplication(String applicationName) {
    try {
      mineService ? mineService.canaryConfigsForApplication(applicationName) : []
    } catch (RetrofitError error) {
      throw classifyError(error)
    }
  }

}
