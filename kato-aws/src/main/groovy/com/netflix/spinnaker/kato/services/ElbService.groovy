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
package com.netflix.spinnaker.kato.services
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.Instance
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest
import groovy.transform.Canonical

@Canonical
class ElbService {

  final ThrottleService throttleService
  final AmazonElasticLoadBalancing amazonElasticLoadBalancing

  void registerInstancesWithLoadBalancer(Collection<String> loadBalancerNames, Collection<String> instanceIds) {
    changeRegistrationOfInstancesWithLoadBalancer(loadBalancerNames, instanceIds) { String loadBalancerName, List<Instance> instances ->
      amazonElasticLoadBalancing.registerInstancesWithLoadBalancer(new RegisterInstancesWithLoadBalancerRequest(loadBalancerName, instances))
    }
  }

  void deregisterInstancesFromLoadBalancer(Collection<String> loadBalancerNames, Collection<String> instanceIds) {
    changeRegistrationOfInstancesWithLoadBalancer(loadBalancerNames, instanceIds) { String loadBalancerName, List<Instance> instances ->
      amazonElasticLoadBalancing.deregisterInstancesFromLoadBalancer(new DeregisterInstancesFromLoadBalancerRequest(loadBalancerName, instances))
    }
  }

  private void changeRegistrationOfInstancesWithLoadBalancer(Collection<String> loadBalancerNames, Collection<String> instanceIds,
      Closure actOnInstancesAndLoadBalancer) {
    if (instanceIds && loadBalancerNames) {
      def instances = instanceIds.collect { new Instance(instanceId: it) }
      loadBalancerNames.eachWithIndex{ String loadBalancerName, int index ->
        if (index > 0) {
          throttleService.sleepMillis(250)
        }
        actOnInstancesAndLoadBalancer(loadBalancerName, instances)
      }
    }
  }
}
