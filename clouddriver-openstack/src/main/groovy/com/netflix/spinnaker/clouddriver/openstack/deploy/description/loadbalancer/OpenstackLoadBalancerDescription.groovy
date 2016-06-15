/*
 * Copyright 2016 Target, Inc.
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
package com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer

import com.netflix.spinnaker.clouddriver.openstack.deploy.description.OpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.domain.PoolHealthMonitor
import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerMethod
import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerProtocol
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class OpenstackLoadBalancerDescription extends OpenstackAtomicOperationDescription {
  String region
  String id
  String name
  String floatingIpId
  LoadBalancerProtocol protocol
  LoadBalancerMethod method
  String subnetId
  int externalPort
  int internalPort
  PoolHealthMonitor healthMonitor
}
