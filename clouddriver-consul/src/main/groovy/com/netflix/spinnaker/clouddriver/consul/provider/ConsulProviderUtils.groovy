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

package com.netflix.spinnaker.clouddriver.consul.provider

import com.netflix.spinnaker.clouddriver.consul.api.v1.ConsulAgent
import com.netflix.spinnaker.clouddriver.consul.api.v1.model.CheckResult
import com.netflix.spinnaker.clouddriver.consul.config.ConsulConfig
import com.netflix.spinnaker.clouddriver.consul.model.ConsulHealth
import com.netflix.spinnaker.clouddriver.model.DiscoveryHealth
import retrofit.RetrofitError

class ConsulProviderUtils {
  static List<ConsulHealth> getHealths(ConsulConfig config, String agent) {
    try {
      new ConsulAgent(config, agent).api.checks()?.collect { String name, CheckResult result ->
        new ConsulHealth(result: result, source: result.checkId)
      } ?: []
    } catch (RetrofitError e) {
      return []
    }
  }
}
