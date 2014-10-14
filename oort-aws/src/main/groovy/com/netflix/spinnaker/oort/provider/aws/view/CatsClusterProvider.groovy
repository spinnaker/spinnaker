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

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.Application
import com.netflix.spinnaker.oort.model.Cluster
import com.netflix.spinnaker.oort.model.ClusterProvider
import com.netflix.spinnaker.oort.model.Instance
import com.netflix.spinnaker.oort.model.LoadBalancer
import com.netflix.spinnaker.oort.model.ServerGroup
import com.netflix.spinnaker.oort.model.aws.AmazonCluster
import com.netflix.spinnaker.oort.model.aws.AmazonLoadBalancer
import com.netflix.spinnaker.oort.model.aws.AmazonServerGroup
import com.netflix.spinnaker.oort.provider.aws.AwsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.regex.Pattern

import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.CLUSTERS
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.HEALTH
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.IMAGES
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.LAUNCH_CONFIGS
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.SERVER_GROUPS

@Component
class CatsClusterProvider implements ClusterProvider<AmazonCluster> {

  private final Cache cacheView

  @Autowired
  CatsClusterProvider(Cache cacheView) {
    this.cacheView = cacheView
  }

  @Override
  Map<String, Set<AmazonCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(CLUSTERS.ns)
    Collection<AmazonCluster> clusters = clusterData.findResults this.&translate
    mapResponse(clusters)
  }

  @Override
  Map<String, Set<AmazonCluster>> getClusters(String applicationName) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName))
    if (application == null) {
      return [:]
    }

    Collection<AmazonCluster> clusters = resolveRelationshipData(application, CLUSTERS.ns).findResults this.&translate
    mapResponse(clusters)
  }

  Map<String, Set<AmazonCluster>> mapResponse(Collection<AmazonCluster> clusters) {
    clusters.groupBy { it.accountName }.collectEntries { k, v -> [k, new HashSet(v)] }
  }

  AmazonCluster translate(CacheData clusterData) {
    Map<String, String> clusterKey = Keys.parse(clusterData.id)

    def cluster = new AmazonCluster()
    cluster.accountName = clusterKey.account
    cluster.name = clusterKey.cluster

    Collection<AmazonLoadBalancer> loadBalancers = resolveRelationshipData(clusterData, LOAD_BALANCERS.ns).findResults this.&translateLoadBalancer
    cluster.loadBalancers.addAll(loadBalancers)

    Collection<AmazonServerGroup> serverGroups = resolveRelationshipData(clusterData, SERVER_GROUPS.ns).findResults this.&translateServerGroup
    cluster.serverGroups.addAll(serverGroups)

    cluster
  }

  AmazonLoadBalancer translateLoadBalancer(CacheData loadBalancerData) {
    Map<String, String> lbKey = Keys.parse(loadBalancerData.id)


    new AmazonLoadBalancer(lbKey.loadBalancer, lbKey.region)
  }

  AmazonServerGroup translateServerGroup(CacheData serverGroupData) {
    Map<String, String> serverGroupKey = Keys.parse(serverGroupData.id)
    
    def sg = new AmazonServerGroup(serverGroupKey.serverGroup, 'aws', serverGroupKey.region)
    sg.putAll(serverGroupData.attributes)

    sg.instances = resolveRelationshipData(serverGroupData, INSTANCES.ns).collect { CacheData instance ->
      Map<String, String> instanceKey = Keys.parse(instance.id)
      Collection<Map<String, Object>> healths = []
      for (String healthProvider : AwsProvider.HEALTH_PROVIDERS) {
        def health = cacheView.get(HEALTH.ns, Keys.getInstanceHealthKey(instance.id, instanceKey.account, instanceKey.region, healthProvider))
        if (health) {
          healths.add(health.attributes)
        }
      }

      boolean healthy = healths.any { it.state == 'Up' } && !healths.any { it.state == 'Down' }
      [
        name: instance.attributes.instanceId,
        instance: instance.attributes,
        healths: healths,
        isHealthy: healthy
      ]
    }

    if (serverGroupData.relationships[LAUNCH_CONFIGS.ns]) {
      CacheData lc = serverGroupData.relationships[LAUNCH_CONFIGS.ns].findResult { String lcId ->
        cacheView.get(LAUNCH_CONFIGS.ns, lcId)
      }
      if (lc) {
        sg.launchConfig = lc.attributes
        CacheData image = lc.relationships[IMAGES.ns].findResult { String imageId ->
          cacheView.get(IMAGES.ns, imageId)
        }
        if (image) {
          sg.image = image.attributes
          String appVersionTag = image.attributes.tags?.find { it.key == "appversion" }?.value
          if (appVersionTag) {
            def appVersion = AppVersion.parseName(appVersionTag)
            if (appVersion) {
              Map buildInfo = [package_name: appVersion.packageName, version: appVersion.version, commit: appVersion.commit] as Map<Object, Object>
              if (appVersion.buildJobName) {
                buildInfo.jenkins = [name: appVersion.buildJobName, number: appVersion.buildNumber]
              }
              def buildHost = image.attributes.tags.find { it.key == "build_host" }?.value
              if (buildHost) {
                ((Map) buildInfo.jenkins).host = buildHost
              }
            }
          }

        }
      }
    }
    sg
  }

  Collection<CacheData> resolveRelationshipData(CacheData source, String relationship) {
    resolveRelationshipData(source, relationship) { true }
  }

  Collection<CacheData> resolveRelationshipData(CacheData source, String relationship, Closure<Boolean> relFilter) {
    source.relationships[relationship]?.findResults { if (relFilter(it)) { cacheView.get(relationship, it) } else null } ?: [] as Collection
  }

  @Override
  Set<AmazonCluster> getClusters(String applicationName, String account) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName))
    if (application == null) {
      return [] as Set
    }
    resolveRelationshipData(application, CLUSTERS.ns) { Keys.parse(it).account == account }.findResults(this.&translate).findAll { } as Set<AmazonCluster>
  }

  @Override
  AmazonCluster getCluster(String application, String account, String name) {
    CacheData cluster = cacheView.get(CLUSTERS.ns, Keys.getClusterKey(name, application, account))
    if (cluster == null) {
      null
    } else {
      translate(cluster)
    }
  }
}
