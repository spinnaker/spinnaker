/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.instance

import com.netflix.spinnaker.clouddriver.openstack.deploy.description.instance.OpenstackInstancesRegistrationDescription
import groovy.util.logging.Slf4j

/**
 * Each instance in the set of instances will be deregistered from each load balancer in the set of load balancers.
 */
@Slf4j
class DeregisterOpenstackInstancesAtomicOperation extends AbstractRegistrationOpenstackInstancesAtomicOperation {

  String basePhase = 'DEREGISTER'
  Boolean action = Boolean.FALSE
  String verb = 'deregistering'
  String preposition = 'from'

  /*
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deregisterInstancesFromLoadBalancer": { "loadBalancerIds": ["2112e340-4714-492c-b9db-e45e1b1102c5"], "instanceIds": ["155e68a7-a7dd-433a-b2c1-c8d6d38fb89a"], "account": "test", "region": "TTCOSCORE1" }} ]' localhost:7002/openstack/ops
   * curl -X GET -H "Accept: application/json" localhost:7002/task/1
   */
  DeregisterOpenstackInstancesAtomicOperation(OpenstackInstancesRegistrationDescription description) {
    super(description)
  }

}
