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

package com.netflix.spinnaker.oort.provider.aws.view

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.oort.model.LoadBalancerProvider
import com.netflix.spinnaker.oort.model.aws.AmazonLoadBalancer

class CatsLoadBalancerProvider implements LoadBalancerProvider<AmazonLoadBalancer> {

  Cache cacheView

  @Override
  Map<String, Set<AmazonLoadBalancer>> getLoadBalancers() {
    return null
  }

  @Override
  Set<AmazonLoadBalancer> getLoadBalancers(String account) {
    return null
  }

  @Override
  Set<AmazonLoadBalancer> getLoadBalancers(String account, String cluster) {
    return null
  }

  @Override
  Set<AmazonLoadBalancer> getLoadBalancers(String account, String cluster, String type) {
    return null
  }

  @Override
  Set<AmazonLoadBalancer> getLoadBalancer(String account, String cluster, String type, String loadBalancerName) {
    return null
  }

  @Override
  AmazonLoadBalancer getLoadBalancer(String account, String cluster, String type, String loadBalancerName, String region) {
    return null
  }
}
