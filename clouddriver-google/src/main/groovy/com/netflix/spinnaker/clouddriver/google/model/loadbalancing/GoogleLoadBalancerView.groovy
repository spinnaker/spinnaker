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

package com.netflix.spinnaker.clouddriver.google.model.loadbalancing

import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import groovy.transform.Canonical

@Canonical
abstract class GoogleLoadBalancerView implements LoadBalancer {
  final String type = GoogleCloudProvider.GCE
  String loadBalancerType

  String name
  String account
  String region
  Long createdTime
  String ipAddress
  String ipProtocol
  String portRange
  String targetPool
  GoogleHealthCheck.View healthCheck

  Set<LoadBalancerServerGroup> serverGroups = new HashSet<>()
}
