/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.clouddriver.aws.model

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification

class AmazonClusterSpec extends Specification {
  void "should serialize null loadBalancers and serverGroups as empty arrays"() {
    def objectMapper = new ObjectMapper()

    when:
    def nullCluster = objectMapper.convertValue(
      new AmazonCluster(loadBalancers: null, serverGroups: null), Map
    )

    then:
    nullCluster.loadBalancers.isEmpty()
    nullCluster.serverGroups.isEmpty()

    when:
    def nonNullCluster = objectMapper.convertValue(
      new AmazonCluster(
        loadBalancers: [new AmazonLoadBalancer(account: "account", name: "loadBalancer1", region: "region")],
        serverGroups: [new AmazonServerGroup(name: "serverGroup1", instances: [])]
      ),
      Map
    )

    then:
    nonNullCluster.loadBalancers*.name == ["loadBalancer1"]
    nonNullCluster.serverGroups*.name == ["serverGroup1"]
  }
}
