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
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.gate.retrofit.UpstreamBadRequest.classifyError

@Component
@CompileStatic
@ConditionalOnProperty('services.mine.enabled')
class CanaryService {

  @Autowired
  MineService mineService

  void generateCanaryResult(String canaryId, int duration, String durationUnit) {
    try {
      Retrofit2SyncCall.execute(mineService?.generateCanaryResult(canaryId, duration, durationUnit, ""))
    } catch (SpinnakerServerException error) {
      throw classifyError(error)
    }
  }

  List<Map> getCanaryAnalysisHistory(String canaryDeploymentId) {
    try {
      mineService ? Retrofit2SyncCall.execute(mineService.getCanaryAnalysisHistory(canaryDeploymentId)) : []
    } catch (SpinnakerServerException error) {
      throw classifyError(error)
    }
  }

  Map endCanary(String canaryId, String result, String reason) {
    try {
      mineService ? Retrofit2SyncCall.execute(mineService.endCanary(canaryId, result, reason, "")) : [:]
    } catch (SpinnakerServerException error) {
      throw classifyError(error)
    }
  }

  Map showCanary(String canaryId) {
    try {
      mineService ? Retrofit2SyncCall.execute(mineService.showCanary(canaryId)) : [:]
    } catch (SpinnakerServerException error) {
      throw classifyError(error)
    }
  }

  List<String> getCanaryConfigNames(String application) {
    try {
      mineService ? Retrofit2SyncCall.execute(mineService.getCanaryConfigNames(application)) : []
    } catch (SpinnakerServerException error) {
      throw classifyError(error)
    }
  }

  List<Map> canaryConfigsForApplication(String applicationName) {
    try {
      mineService ? Retrofit2SyncCall.execute(mineService.canaryConfigsForApplication(applicationName)) : []
    } catch (SpinnakerServerException error) {
      throw classifyError(error)
    }
  }

}
