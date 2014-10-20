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

import com.netflix.frigga.Names
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.Cluster
import com.netflix.spinnaker.oort.model.LoadBalancer
import com.netflix.spinnaker.oort.model.LoadBalancerProvider
import com.netflix.spinnaker.oort.model.ServerGroup
import com.netflix.spinnaker.oort.model.aws.AmazonLoadBalancer
import com.netflix.spinnaker.oort.provider.aws.AwsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.regex.Pattern

import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.CLUSTERS
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.SERVER_GROUPS

@Component
class CatsLoadBalancerProvider implements LoadBalancerProvider<AmazonLoadBalancer> {

  private static final Pattern ACCOUNT_REGION_NAME = Pattern.compile(/([^\/]+)\/([^\/]+)\/(.*)/)

  private final Cache cacheView

  @Autowired
  public CatsLoadBalancerProvider(Cache cacheView) {
    this.cacheView = cacheView
  }

  @Override
  Map<String, Set<AmazonLoadBalancer>> getLoadBalancers() {
    Map<String, Set<AmazonLoadBalancer>> partitionedLb = [:].withDefault { new HashSet<AmazonLoadBalancer>() }
    Collection<AmazonLoadBalancer> allLb = cacheView.getAll(LOAD_BALANCERS.ns).findResults(this.&translate)
    for (AmazonLoadBalancer lb : allLb) {
      partitionedLb[lb.account].add(lb)
    }
  }

  AmazonLoadBalancer translate(CacheData cacheData) {
    Map<String, String> keyParts = Keys.parse(cacheData.id)
    def lb = new AmazonLoadBalancer(keyParts.loadBalancer, keyParts.region)
    lb.account = keyParts.account
    lb.elb = cacheData.attributes
    lb.serverGroups = cacheData.relationships[SERVER_GROUPS.ns]?.collect {
      Map<String, String> sgParts = Keys.parse(it)
      sgParts.cluster
    } ?: []
  }

  Collection<CacheData> resolveRelationshipData(CacheData source, String relationship) {
    source.relationships[relationship] ? cacheView.getAll(relationship, source.relationships[relationship]) : []
  }

  @Override
  Set<AmazonLoadBalancer> getLoadBalancers(String account) {
    def filteredIds = cacheView.getIdentifiers(LOAD_BALANCERS.ns).findAll {
      def parts = Keys.parse(it)
      parts.account == account
    }
    cacheView.getAll(LOAD_BALANCERS.ns, filteredIds).findResults(this.&translate) as Set<AmazonLoadBalancer>
  }

  @Override
  Set<AmazonLoadBalancer> getLoadBalancers(String account, String cluster) {
    Names names = Names.parseName(cluster)
    CacheData clusterData = cacheView.get(CLUSTERS.ns, Keys.getClusterKey(cluster, names.app, account))

    resolveRelationshipData(clusterData, LOAD_BALANCERS.ns).findResults(this.&translate)
  }

  @Override
  Set<AmazonLoadBalancer> getLoadBalancers(String account, String cluster, String type) {
    getLoadBalancers(account, cluster)
  }

  @Override
  Set<AmazonLoadBalancer> getLoadBalancer(String account, String cluster, String type, String loadBalancerName) {
    getLoadBalancers(account, cluster).findAll { it.name == loadBalancerName }
  }

  @Override
  AmazonLoadBalancer getLoadBalancer(String account, String cluster, String type, String loadBalancerName, String region) {
    def lbs = cacheView.getIdentifiers(LOAD_BALANCERS.ns)
    def keys = lbs.findAll {
      def keyParts = Keys.parse(it)
      (keyParts.account == account && keyParts.region == region && keyParts.loadBalancer == loadBalancerName)
    }

    def candidates = cacheView.getAll(LOAD_BALANCERS.ns, keys).findResults(this.&translate)
    if (candidates.isEmpty()) {
      null
    } else {
      candidates.first()
    }
  }
}
