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
package com.netflix.spinnaker.keel.clouddriver.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.netflix.spinnaker.keel.model.Moniker

data class ClassicLoadBalancerModel(
  val moniker: Moniker?,
  val loadBalancerName: String,
  val loadBalancerType: String = "classic",
  val availabilityZones: Set<String>,
  val vpcid: String,
  val subnets: Set<String>,
  val scheme: String?,
  val listenerDescriptions: List<LoadBalancerListenerDescription>,
  val healthCheck: LoadBalancerHealthCheck,
  val idleTimeout: Int,
  val securityGroups: Set<String>,
  @get:JsonAnyGetter val properties: Map<String, Any?> = emptyMap()
) {
  data class LoadBalancerListenerDescription(
    val listener: LoadBalancerListener
  )

  data class LoadBalancerListener(
    val protocol: String,
    val loadBalancerPort: Int,
    val instanceProtocol: String,
    val instancePort: Int,
    val sslcertificateId: String?
  )

  data class LoadBalancerHealthCheck(
    val target: String,
    val interval: Int,
    val timeout: Int,
    val unhealthyThreshold: Int,
    val healthyThreshold: Int
  )
}
