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

import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryLoadBalancer
import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryResourceRetriever
import com.netflix.spinnaker.clouddriver.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.cloudfoundry.client.lib.domain.CloudDomain
import org.cloudfoundry.client.lib.domain.CloudRoute
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author Greg Turnquist
 */
class CloudFoundryLoadBalancerControllerSpec extends Specification {

  @Shared
  CloudFoundryLoadBalancerController controller

  @Shared
  AccountCredentialsProvider provider

  @Shared
  CloudFoundryResourceRetriever cfResourceRetriever

  def setup() {
    def repository = new MapBackedAccountCredentialsRepository()
    repository.save('test', new CloudFoundryAccountCredentials(name: 'test', username: "me@example.com", password: "my-password"))
    provider = new DefaultAccountCredentialsProvider(repository)

    cfResourceRetriever = Mock(CloudFoundryResourceRetriever)

    controller = new CloudFoundryLoadBalancerController(
        accountCredentialsProvider: provider,
        cfResourceRetriever       : cfResourceRetriever
    )
  }

  void "look up single load balancer's summary"() {
    when:
    def summary = controller.get('production')

    then:
    1 * cfResourceRetriever.getLoadBalancersByAccount() >> {
      ['test': [
          new CloudFoundryLoadBalancer(name: 'production', nativeRoute: new CloudRoute(null, 'production', new CloudDomain(null, 'cfapps.io', null), 1)),
          new CloudFoundryLoadBalancer(name: 'staging', nativeRoute: new CloudRoute(null, 'staging', new CloudDomain(null, 'cfapps.io', null), 1)),
        ]
      ]
    }
    0 * cfResourceRetriever._

    summary != null
    summary.name == 'production'

    def account = summary.getOrCreateAccount('my-account')
    account.name == 'my-account'
    account.regions == []

    def region = account.getOrCreateRegion('my-region')
    region.name == 'my-region'
    region.loadBalancers == []

    summary.accounts == [account]
  }

  void "look up all load balancer accounts"() {
    when:
    def summaries = controller.list()

    then:
    1 * cfResourceRetriever.getLoadBalancersByAccount() >> {
      ['test': [
          new CloudFoundryLoadBalancer(name: 'production', nativeRoute: new CloudRoute(null, 'production', new CloudDomain(null, 'cfapps.io', null), 1)),
          new CloudFoundryLoadBalancer(name: 'staging', nativeRoute: new CloudRoute(null, 'staging', new CloudDomain(null, 'cfapps.io', null), 1)),
      ]
      ]
    }
    0 * cfResourceRetriever._

    summaries != null
    summaries.size() == 2
    summaries.collect {it.name} == ['production', 'staging']
  }

  void "look up load balancers by account, region, and name"() {
    when:
    def summaries = controller.getDetailsInAccountAndRegionByName('test', null, 'production')

    then:
    1 * cfResourceRetriever.getLoadBalancersByAccount() >> {
      ['test': [
          new CloudFoundryLoadBalancer(name: 'production', nativeRoute: new CloudRoute(null, 'production', new CloudDomain(null, 'cfapps.io', null), 1)),
          new CloudFoundryLoadBalancer(name: 'staging', nativeRoute: new CloudRoute(null, 'staging', new CloudDomain(null, 'cfapps.io', null), 1)),
      ]
      ]
    }
    0 * cfResourceRetriever._

    summaries != null
    summaries.size() == 1
    summaries[0] == [loadBalancerName: 'production', ipAddress: 'production.cfapps.io', dnsname: 'production.cfapps.io']
  }

  void "should return an empty list if account doesn't exist"() {
    when:
    def summaries = controller.getDetailsInAccountAndRegionByName('not-there', null, 'production')

    then:
    1 * cfResourceRetriever.getLoadBalancersByAccount() >> {
      ['test': [
          new CloudFoundryLoadBalancer(name: 'production', nativeRoute: new CloudRoute(null, 'production', new CloudDomain(null, 'cfapps.io', null), 1)),
          new CloudFoundryLoadBalancer(name: 'staging', nativeRoute: new CloudRoute(null, 'staging', new CloudDomain(null, 'cfapps.io', null), 1)),
      ]
      ]
    }
    0 * cfResourceRetriever._

    summaries != null
    summaries.size() == 0
  }

}