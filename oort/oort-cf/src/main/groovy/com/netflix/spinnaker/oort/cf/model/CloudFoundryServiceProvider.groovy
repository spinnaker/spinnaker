/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.oort.cf.model
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.oort.model.LoadBalancerProvider
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import java.util.concurrent.Callable

/**
 * @author Greg Turnquist
 */
@Component
@CompileStatic
class CloudFoundryServiceProvider implements LoadBalancerProvider<CloudFoundryService> {

  @Autowired
  Registry registry

  @Autowired
  CloudFoundryResourceRetriever cloudFoundryResourceRetriever

  Timer loadBalancers
  Timer loadBalancersByAccountCluster
  Timer applicationLoadBalancers

  @PostConstruct
  void init() {
    String[] tags = ['className', this.class.simpleName]
    loadBalancers = registry.timer('loadBalancers', tags)
    loadBalancersByAccountCluster = registry.timer('loadBalancersByAccountCluster', tags)
    applicationLoadBalancers = registry.timer('applicationLoadBalancers', tags)
  }

  @Override
  Map<String, Set<CloudFoundryService>> getLoadBalancers() {
    loadBalancers.record({
      Collections.unmodifiableMap(
        cloudFoundryResourceRetriever.servicesByAccount
      )
    } as Callable<Map<String, Set<CloudFoundryService>>>)
  }

  @Override
  Set<CloudFoundryService> getLoadBalancers(String account) {
    this.getLoadBalancers()[account]
  }

  @Override
  Set<CloudFoundryService> getLoadBalancers(String account, String cluster) {
    loadBalancersByAccountCluster.record({
      Collections.unmodifiableSet(
        cloudFoundryResourceRetriever.clustersByAccountAndClusterName[account][cluster].services
      )
    } as Callable<Set<CloudFoundryService>>)
  }

  @Override
  Set<CloudFoundryService> getLoadBalancers(String account, String cluster, String type) {
    new HashSet<>(getLoadBalancers(account, cluster).findAll { it.type == type })
  }

  @Override
  Set<CloudFoundryService> getLoadBalancer(String account, String cluster, String type, String loadBalancerName) {
    new HashSet<>(getLoadBalancers(account, cluster, type).findAll { it.name == loadBalancerName })
  }

  @Override
  CloudFoundryService getLoadBalancer(String account, String cluster, String type, String loadBalancerName, String region) {
    getLoadBalancer(account, cluster, type, loadBalancerName).find {}
  }

  @Override
  Set<CloudFoundryService> getApplicationLoadBalancers(String application) {
    applicationLoadBalancers.record({
      Set<CloudFoundryService> services = new HashSet<>()
      cloudFoundryResourceRetriever.clustersByApplicationName[application].each { cluster ->
        services.addAll(cluster.services)
      }
      Collections.unmodifiableSet(
          services
      )
    } as Callable<Set<CloudFoundryService>>)
  }

}
