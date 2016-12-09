/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.UpsertAppEngineLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.appengine.deploy.ops.UpsertAppEngineLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class UpsertAppEngineLoadBalancerAtomicOperationConverterSpec extends Specification {
  private static final ACCOUNT_NAME = "my-appengine-account"
  private static final LOAD_BALANCER_NAME = "default"
  private static final TRAFFIC_SPLIT = [
    allocations: ["app-stack-detail-v000": "0.6", "app-stack-detail-v001": "0.4"],
    shardBy: "IP"
  ]
  private static final MIGRATE_TRAFFIC = false

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  UpsertAppEngineLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    converter = new UpsertAppEngineLoadBalancerAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(AppEngineNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "upsertAppEngineLoadBalancerDescription type returns UpsertAppEngineLoadBalancerDescription and UpsertAppEngineLoadBalancerAtomicOperation"() {
    setup:
      def input = [
        credentials: ACCOUNT_NAME,
        loadBalancerName: LOAD_BALANCER_NAME,
        split: TRAFFIC_SPLIT,
        migrateTraffic: MIGRATE_TRAFFIC
      ]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof UpsertAppEngineLoadBalancerDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof UpsertAppEngineLoadBalancerAtomicOperation
  }
}
