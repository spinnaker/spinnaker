/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cf.controllers

import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.cats.provider.DefaultProviderCache
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cf.cache.Keys
import org.cloudfoundry.client.lib.domain.CloudDomain
import org.cloudfoundry.client.lib.domain.CloudRoute
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.cf.cache.Keys.Namespace.LOAD_BALANCERS

class CloudFoundryLoadBalancerControllerSpec extends Specification {

  @Shared
  CloudFoundryLoadBalancerController controller

  @Shared
  ProviderCache cacheView

  def setup() {
    cacheView = new DefaultProviderCache(new InMemoryCache())
    cacheView.putCacheData(LOAD_BALANCERS.ns, new DefaultCacheData(
        Keys.getLoadBalancerKey('production', 'prod', 'my-region'),
        [
            'name': 'production',
            'nativeRoute': new CloudRoute(null, 'production', new CloudDomain(null, 'cfapps.io', null), 1)
        ],
        [:]
    ))
    cacheView.putCacheData(LOAD_BALANCERS.ns, new DefaultCacheData(
        Keys.getLoadBalancerKey('staging', 'staging', 'my-region'),
        [
            'name': 'staging',
            'nativeRoute': new CloudRoute(null, 'staging', new CloudDomain(null, 'cfapps.io', null), 1)
        ],
        [:]
    ))

    controller = new CloudFoundryLoadBalancerController(cacheView)
  }

  void "look up single load balancer's summary"() {
    when:
    def summary = controller.get('production')

    then:
    summary != null
    summary.name == 'production'

    def account = summary.getOrCreateAccount('prod')
    def region = account.getOrCreateRegion('my-region')

    account.name == 'prod'
    account.regions == [region]

    region.name == 'my-region'
    region.loadBalancers.size() == 1
    region.loadBalancers[0].name == 'production'
    region.loadBalancers[0].region == region.name
    region.loadBalancers[0].account == 'prod'
    region.loadBalancers[0].type == 'cf'

    summary.accounts == [account]
  }

  void "look up all load balancer accounts"() {
    when:
    def summaries = controller.list()

    then:
    summaries != null
    summaries.size() == 2
    summaries.collect {it.name} == ['production', 'staging']
  }

  void "look up load balancers by account, region, and name"() {
    when:
    def summaries = controller.getDetailsInAccountAndRegionByName('prod', 'my-region', 'production')

    then:
    summaries != null
    summaries.size() == 1
    summaries[0].name == 'production'
    summaries[0].nativeRoute.host == 'production'
    summaries[0].nativeRoute.domain.name == 'cfapps.io'
    summaries[0].nativeRoute.name == 'production.cfapps.io'
  }

  void "should return an empty list if account doesn't exist"() {
    when:
    def summaries = controller.getDetailsInAccountAndRegionByName('not-there', null, 'production')

    then:
    summaries != null
    summaries.size() == 0
  }

}
