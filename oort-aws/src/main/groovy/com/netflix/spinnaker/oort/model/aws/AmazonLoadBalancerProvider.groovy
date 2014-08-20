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

package com.netflix.spinnaker.oort.model.aws

import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.netflix.frigga.Names
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.model.Cluster
import com.netflix.spinnaker.oort.model.ClusterProvider
import com.netflix.spinnaker.oort.model.LoadBalancerProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AmazonLoadBalancerProvider implements LoadBalancerProvider<AmazonLoadBalancer> {
  @Autowired
  CacheService cacheService

  @Autowired
  List<ClusterProvider> clusterProviders

  @Override
  Map<String, Set<AmazonLoadBalancer>> getLoadBalancers() {
    def keys = cacheService.keysByType(Keys.Namespace.LOAD_BALANCERS)
    keys.collectEntries { key ->
      def loadBalancer = retrieve(key)
      [(loadBalancer.name): loadBalancer]
    }
  }

  @Override
  Set<AmazonLoadBalancer> getLoadBalancers(String account) {
    def keys = cacheService.keysByType(Keys.Namespace.LOAD_BALANCERS)
    def accountKeys = keys.findAll { key ->
      def parts = Keys.parse(key)
      parts.account == account
    }
    accountKeys.collect { key ->
      retrieve(key)
    }
  }

  @Override
  Set<AmazonLoadBalancer> getLoadBalancers(String account, String clusterName) {
    def names = Names.parseName(clusterName)

    clusterProviders.collectMany {
      it.getCluster(names.app.toLowerCase(), account, clusterName).loadBalancers
    }.collect { AmazonLoadBalancer loadBalancer ->
      getLoadBalancer(account, clusterName, loadBalancer.type, loadBalancer.name, loadBalancer.region)
    }
  }

  @Override
  Set<AmazonLoadBalancer> getLoadBalancers(String account, String clusterName, String type) {
    def names = Names.parseName(clusterName)

    clusterProviders.collect {
      it.getCluster(names.app.toLowerCase(), account, clusterName)
    }.findAll { Cluster cluster ->
      cluster.type == type
    }.collectMany {
      it.loadBalancers ?: []
    }.collect { AmazonLoadBalancer loadBalancer ->
      getLoadBalancer(account, clusterName, type, loadBalancer.name, loadBalancer.region)
    }
  }

  @Override
  Set<AmazonLoadBalancer> getLoadBalancer(String account, String clusterName, String type, String loadBalancerName) {
    def names = Names.parseName(clusterName)

    def lbs = clusterProviders.collectMany {
      def cluster = it.getCluster(names.app.toLowerCase(), account, clusterName)
      cluster.loadBalancers.findAll { it.type == type && it.name == loadBalancerName }
    }.collect { AmazonLoadBalancer loadBalancer ->
       getLoadBalancer(account, clusterName, type, loadBalancerName, loadBalancer.region)
    }
    lbs
  }

  @Override
  AmazonLoadBalancer getLoadBalancer(String account, String cluster, String type, String loadBalancerName, String region) {
    def loadBalancer = cacheService.retrieve(Keys.getLoadBalancerKey(loadBalancerName, account, region), LoadBalancerDescription)
    def alb = new AmazonLoadBalancer(loadBalancerName, region)
    alb.elb = loadBalancer
    alb
  }

  private AmazonLoadBalancer retrieve(String key) {
    def parts = key.split(':')
    def account = parts[1]
    def region = parts[2]
    def name = parts[3]
    def loadBalancer = new AmazonLoadBalancer(name, region)
    loadBalancer.elb = cacheService.retrieve(Keys.getLoadBalancerKey(name, account, region), LoadBalancerDescription)
    loadBalancer
  }

}
