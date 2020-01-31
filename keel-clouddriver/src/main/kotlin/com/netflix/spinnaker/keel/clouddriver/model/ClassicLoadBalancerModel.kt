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

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.netflix.spinnaker.keel.api.Moniker

data class ClassicLoadBalancerModel(
  override val moniker: Moniker?,
  override val loadBalancerName: String,
  override val loadBalancerType: String = "classic",
  override val availabilityZones: Set<String>,

  @JsonAlias("vpcid", "vpcId")
  override val vpcId: String,

  override val subnets: Set<String>,
  override val scheme: String?,
  override val idleTimeout: Int,
  override val securityGroups: Set<String>,
  val listenerDescriptions: List<ClassicLoadBalancerListenerDescription>,
  val healthCheck: ClassicLoadBalancerHealthCheck,
  @get:JsonAnyGetter val properties: Map<String, Any?> = emptyMap()
) : AmazonLoadBalancer {
  data class ClassicLoadBalancerListenerDescription(
    val listener: ClassicLoadBalancerListener
  )

  data class ClassicLoadBalancerListener(
    val protocol: String,
    val loadBalancerPort: Int,
    val instanceProtocol: String,
    val instancePort: Int,
    val sslcertificateId: String?
  )

  data class ClassicLoadBalancerHealthCheck(
    val target: String,
    val interval: Int,
    val timeout: Int,
    val unhealthyThreshold: Int,
    val healthyThreshold: Int
  )
}
