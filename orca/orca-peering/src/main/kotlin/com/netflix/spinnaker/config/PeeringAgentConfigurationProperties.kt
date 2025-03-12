/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("pollers.peering")
class PeeringAgentConfigurationProperties {
  var enabled: Boolean = false
  var intervalMs: Long = 5000
  var poolName: String? = null
  var peerId: String? = null
  var threadCount: Int = 30
  var chunkSize: Int = 100
  var clockDriftMs: Long = 5000

  // Only read via dynamicConfigService, here for completeness
  var maxAllowedDeleteCount: Int = 1000
}
