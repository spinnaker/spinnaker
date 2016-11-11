/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.controllers

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.kubernetes.cache.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesLoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProviderTempShim
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import javax.naming.OperationNotSupportedException

@RestController
@RequestMapping("/kubernetes/loadBalancers")
class KubernetesLoadBalancerController implements LoadBalancerProviderTempShim {
  private final Cache cacheView

  @Autowired
  KubernetesLoadBalancerController(Cache cacheView) {
    this.cacheView = cacheView
  }

  // TODO(lwander): Groovy allows this to compile just fine, even though KubernetesLoadBalancer does
  // not implement the LoadBalancerProviderTempShim.list interface.
  @Override
  @RequestMapping(method = RequestMethod.GET)
  List<KubernetesLoadBalancer> list() {
    Collection<String> loadBalancers = cacheView.getIdentifiers(Keys.Namespace.LOAD_BALANCERS.ns)
    loadBalancers.findResults {
      def parse = Keys.parse(it)
      parse ? new KubernetesLoadBalancer(parse.name, parse.namespace, parse.account) : null
    }
  }

  // TODO(lwander): Implement if/when these methods are needed in Deck.
  @Override
  LoadBalancerProviderTempShim.Item get(String name) {
    throw new OperationNotSupportedException("Kubernetes is a special snowflake.")
  }

  @Override
  List<LoadBalancerProviderTempShim.Details> byAccountAndRegionAndName(String account,
                                                                       String region,
                                                                       String name) {
    throw new OperationNotSupportedException("No balancers for you!")
  }
}
