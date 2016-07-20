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

package com.netflix.spinnaker.clouddriver.consul.deploy.ops

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.consul.api.v1.ConsulKeyValueStore
import com.netflix.spinnaker.clouddriver.consul.api.v1.model.KeyValuePair
import com.netflix.spinnaker.clouddriver.consul.config.ConsulConfig
import com.netflix.spinnaker.clouddriver.consul.config.ConsulProperties
import com.netflix.spinnaker.clouddriver.consul.deploy.description.ConsulLoadBalancerDescription
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class UpsertConsulLoadBalancer {
  // This operation is a bit odd, since services in Consul don't exist until they are attached to an instance - and
  // when a service is first registered, it doesn't have any instances attached. Therefore this operation simply sticks
  // the service in Consuls KV API for later retrieval & use.
  static void operate(ConsulConfig config, ConsulLoadBalancerDescription description) {
    // who comes up with these names??
    def jsonSlurper = new JsonSlurper()
    def objectMapper = new ObjectMapper()

    def kvApi = new ConsulKeyValueStore(config).api
    List<KeyValuePair> services = kvApi.getKey(description.name, description.datacenter, false)

    ConsulLoadBalancerDescription oldDescription = new ConsulLoadBalancerDescription()
    if (!services) {
      // No services registered, nothing to override.
    } else if (services.size() == 1) {
      def servicePair = services[0]
      assert(servicePair.key == description.name)
      assert(servicePair.value)
      def serviceObject = jsonSlurper.parse(servicePair.value.decodeBase64())
      oldDescription = objectMapper.convertValue(serviceObject, ConsulLoadBalancerDescription)
    } else {
      // Shouldn't be possible - but could be a misconfigured environment on behalf of the user
      throw new IllegalStateException("Too many services registered under name ${description.name} in dc ${description.datacenter}.")
    }

    description.check = description.check ?: oldDescription.check
    description.port = description.port ?: oldDescription.port
    description.tags = description.tags != null ? description.tags : oldDescription.tags // null check to enable tags = []

    def serializedDescription = JsonOutput.toJson(description)
    kvApi.putKey(description.name, serializedDescription, description.datacenter)

    return
  }
}
