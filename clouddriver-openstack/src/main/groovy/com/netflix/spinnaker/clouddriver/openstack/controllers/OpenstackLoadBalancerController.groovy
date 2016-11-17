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

package com.netflix.spinnaker.clouddriver.openstack.controllers

import com.netflix.spinnaker.clouddriver.model.LoadBalancerProviderTempShim
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancer
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancerSummary
import com.netflix.spinnaker.clouddriver.openstack.provider.view.OpenstackLoadBalancerProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * TODO Refactor when addressing https://github.com/spinnaker/spinnaker/issues/807
 */
@Component
class OpenstackLoadBalancerController implements LoadBalancerProviderTempShim {

  final String cloudProvider = OpenstackCloudProvider.ID

  OpenstackLoadBalancerProvider provider

  @Autowired
  OpenstackLoadBalancerController(final OpenstackLoadBalancerProvider provider) {
    this.provider = provider
  }

  // TODO: OpenstackLoadBalancerSummary is not a LoadBalancerProviderTempShim.Item, but still
  // compiles anyway because of groovy magic.
  List<OpenstackLoadBalancerSummary> list() {
    provider.getLoadBalancers('*', '*', '*').collect { lb ->
      new OpenstackLoadBalancerSummary(account: lb.account, region: lb.region, id: lb.id, name: lb.name)
    }.sort { it.name }
  }

  LoadBalancerProviderTempShim.Item get(String name) {
    throw new UnsupportedOperationException("TODO: Support a single getter")
  }

  List<OpenstackLoadBalancer.View> byAccountAndRegionAndName(String account,
                                                             String region,
                                                             String name) {
    provider.getLoadBalancers(account, region, name) as List
  }
}
