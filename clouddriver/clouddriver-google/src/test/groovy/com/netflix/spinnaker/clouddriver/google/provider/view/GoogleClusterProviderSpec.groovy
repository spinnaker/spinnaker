/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleExternalHttpLoadBalancer
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.LOAD_BALANCERS

class GoogleClusterProviderSpec extends Specification {
  private static final ACCOUNT = "auto"
  private static final REGION = "us-central1"
  private static final SERVER_GROUP = "app-v001"
  private static final LOAD_BALANCER = "external-lb"
  private static final BACKEND_SERVICE = "external-backend-service"

  void "external managed load balancer participates in disabled-state aggregation"() {
    setup:
      def loadBalancer = new GoogleExternalHttpLoadBalancer(
        name: LOAD_BALANCER,
        account: ACCOUNT,
        region: REGION,
        defaultService: new GoogleBackendService(name: BACKEND_SERVICE, backends: []),
        hostRules: [])
      def loadBalancerKey = Keys.getLoadBalancerKey(REGION, ACCOUNT, LOAD_BALANCER)
      def serverGroupCacheData = Mock(CacheData)
      serverGroupCacheData.getAttributes() >> [
        name    : SERVER_GROUP,
        region  : REGION,
        zone    : REGION + "-a",
        disabled: false,
        asg     : [
          (GCEUtil.REGIONAL_LOAD_BALANCER_NAMES): LOAD_BALANCER,
          (GCEUtil.REGION_BACKEND_SERVICE_NAMES): BACKEND_SERVICE,
        ]
      ]
      serverGroupCacheData.getRelationships() >> [(LOAD_BALANCERS.ns): [loadBalancerKey]]
      @Subject def provider = new GoogleClusterProvider(objectMapper: new ObjectMapper())

    when:
      def serverGroup = provider.serverGroupFromCacheData(
        serverGroupCacheData,
        ACCOUNT,
        [],
        [] as Set,
        [loadBalancer] as Set)

    then:
      serverGroup.disabled
  }
}
