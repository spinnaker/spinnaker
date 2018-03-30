/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("keel")
class KeelProperties {
  var prettyPrintJson: Boolean = false
  var immediatelyRunIntents: Boolean = true
  var maxConvergenceLogEntriesPerIntent: Int = 720 // one entry every 30 s, this will keep 6 hours of logs

  var intentPackages: List<String> = listOf("com.netflix.spinnaker.keel.intent")
  var intentSpecPackages: List<String> = listOf("com.netflix.spinnaker.keel.intent")
  var policyPackages: List<String> = listOf("com.netflix.spinnaker.keel.policy")
  var attributePackages: List<String> = listOf("com.netflix.spinnaker.keel.attribute")
}
