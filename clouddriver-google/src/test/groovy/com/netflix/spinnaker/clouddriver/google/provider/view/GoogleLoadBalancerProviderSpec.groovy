/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.google.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.*
import spock.lang.Specification
import spock.lang.Subject


import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.SERVER_GROUPS

class GoogleLoadBalancerProviderSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final LOAD_BALANCER_NAME = "default"
  private static final REGION_EUROPE = "europe-west1"
  private static final SERVER_GROUP_IDS = ["server_group_identifier"]

  void "should return session affinity"() {
    setup:
      def cacheView = Mock(Cache)
      def serverGroup = Mock(CacheData)
      def serverGroupRelationships = [:]
      serverGroupRelationships.put(LOAD_BALANCERS.ns, ["lb_name"])

      def loadBalancerRelations = [:]
    loadBalancerRelations.put(SERVER_GROUPS.ns, [])

      def googleLoadBalancerView = Mock(CacheData)
      def attributes = [
        type: GoogleLoadBalancerType.NETWORK,
        account: ACCOUNT_NAME,
        region: REGION_EUROPE,
        sessionAffinity: GoogleSessionAffinity.CLIENT_IP_PORT_PROTO
      ]
      googleLoadBalancerView.getAttributes() >> attributes
      googleLoadBalancerView.getRelationships() >> loadBalancerRelations

      @Subject def provider = new GoogleLoadBalancerProvider()
      provider.cacheView = cacheView
      provider.objectMapper = new ObjectMapper()

    when:
      def details = provider.byAccountAndRegionAndName(ACCOUNT_NAME, REGION_EUROPE, LOAD_BALANCER_NAME)
    then:
      _ * cacheView.filterIdentifiers(LOAD_BALANCERS.ns, _) >> ["lb_identifier"]
      1 * cacheView.getAll(LOAD_BALANCERS.ns, _, _) >> [googleLoadBalancerView]
      1 * cacheView.filterIdentifiers(SERVER_GROUPS.ns, _) >> SERVER_GROUP_IDS
      1 * cacheView.getAll(SERVER_GROUPS.ns, SERVER_GROUP_IDS) >> [serverGroup]
      1 * serverGroup.getRelationships() >> serverGroupRelationships
      details.size() == 1
      details[0].sessionAffinity == "CLIENT_IP_PORT_PROTO"
  }

}
