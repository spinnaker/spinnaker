/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.model

import com.google.api.services.appengine.v1.model.Service
import com.google.api.services.appengine.v1.model.Version
import com.netflix.spinnaker.clouddriver.model.HealthState

import java.text.SimpleDateFormat

class AppEngineModelUtil {
  private static final dateFormats = ["yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"]
    .collect { new SimpleDateFormat(it) }

  static Long translateTime(String time) {
    for (SimpleDateFormat dateFormat: dateFormats) {
      try {
        return dateFormat.parse(time).getTime()
      } catch (e) { }
    }

    null
  }

  static AppEngineScalingPolicy getScalingPolicy(Version version) {
    if (version.getAutomaticScaling()) {
      return new AppEngineScalingPolicy(version.getAutomaticScaling())
    } else if (version.getBasicScaling()) {
      return new AppEngineScalingPolicy(version.getBasicScaling())
    } else if (version.getManualScaling()) {
      return new AppEngineScalingPolicy(version.getManualScaling())
    }
  }

  static getInstanceHealthState(Version version, Service service) {
    def allocations = service.getSplit()?.getAllocations()
    allocations?.containsKey(version.getId()) ? HealthState.Up : HealthState.OutOfService
  }
}
