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
import com.netflix.spinnaker.internal.services.internal.MineService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
/**
 *
 * @author sthadeshwar
 */
@Component
@CompileStatic
class CanaryService {

  @Autowired(required = false)
  MineService mineService

  void generateCanaryResult(String canaryId, int duration, String durationUnit) {
    mineService?.generateCanaryResult(canaryId, duration, durationUnit)
  }

  List<Map> getCanaryAnalysisHistory(String canaryDeploymentId) {
    mineService ? mineService.getCanaryAnalysisHistory(canaryDeploymentId) : []
  }

  Map endCanary(String canaryId, String result, String reason) {
    mineService ? mineService.endCanary(canaryId, result, reason) : [:]
  }

  Map showCanary(String canaryId) {
    mineService ? mineService.showCanary(canaryId) : [:]
  }

  List<String> getCanaryConfigNames() {
    mineService ? mineService.getCanaryConfigNames() : []
  }

}
