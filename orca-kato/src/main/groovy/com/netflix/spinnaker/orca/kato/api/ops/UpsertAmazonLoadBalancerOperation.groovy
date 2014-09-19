/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.api.ops

import com.google.common.base.Optional
import com.netflix.spinnaker.orca.kato.api.LoadBalancerListener
import com.netflix.spinnaker.orca.kato.api.Operation
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

@CompileStatic
@EqualsAndHashCode
class UpsertAmazonLoadBalancerOperation extends Operation {
  Optional<String> clusterName
  Optional<String> name
  String subnetType
  Optional<Set<String>> securityGroups
  Map<String, List<String>> availabilityZones
  String healthCheck
  String credentials
  List<LoadBalancerListener> listeners

}
