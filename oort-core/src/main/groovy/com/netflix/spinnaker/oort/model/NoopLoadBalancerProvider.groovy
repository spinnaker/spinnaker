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

package com.netflix.spinnaker.oort.model

class NoopLoadBalancerProvider implements LoadBalancerProvider<LoadBalancer> {
  @Override
  Map<String, Set<LoadBalancer>> getLoadBalancers() {
    Collections.emptyMap()
  }

  @Override
  Set<LoadBalancer> getLoadBalancers(String account) {
    Collections.emptySet()
  }

  @Override
  Set<LoadBalancer> getLoadBalancers(String account, String cluster) {
    Collections.emptySet()
  }

  @Override
  Set<LoadBalancer> getLoadBalancers(String account, String cluster, String type) {
    Collections.emptySet()
  }

  @Override
  Set<LoadBalancer> getLoadBalancer(String account, String cluster, String type, String loadBalancerName) {
    Collections.emptySet()
  }

  @Override
  LoadBalancer getLoadBalancer(String account, String cluster, String type, String loadBalancerName, String region) {
    null
  }

  @Override
  Set<LoadBalancer> getApplicationLoadBalancers(String application) {
    Collections.emptySet()
  }
}
