/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.gate.config

import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import retrofit.Endpoint

import javax.annotation.PostConstruct

import static retrofit.Endpoints.newFixedEndpoint
import static retrofit.Endpoints.newFixedEndpoint
import static retrofit.Endpoints.newFixedEndpoint

@CompileStatic
@Component
@ConfigurationProperties
class ServiceConfiguration {
  List<String> healthCheckableServices
  List<String> discoveryHosts
  Map<String, Service> services = [:]
  Map<String, Service> integrations = [:]

  @Autowired
  ApplicationContext ctx

  @PostConstruct
  void postConstruct() {
    // this check is done in a @PostConstruct to avoid Spring's list merging in @ConfigurationProperties (vs. overriding)
    healthCheckableServices = healthCheckableServices ?: [
      "orca", "clouddriver", "echo", "igor", "flex", "front50", "mahe", "mine", "keel"
    ]
  }

  Service getService(String name) {
    (services + integrations)[name]
  }

  Endpoint getServiceEndpoint(String serviceName, String dynamicName = null) {
    Service service = getService(serviceName)

    if (service == null) {
      throw new IllegalArgumentException("Unknown service ${serviceName}")
    }

    Endpoint endpoint
    if (dynamicName == null) {
      // TODO: move Netflix-specific logic out of the OSS implementation
      endpoint = discoveryHosts && service.vipAddress ?
        newFixedEndpoint("niws://${service.vipAddress}")
        : newFixedEndpoint(service.baseUrl)
    } else {
      if (!service.getConfig().containsKey("dynamicEndpoints")) {
        throw new IllegalArgumentException("Unknown dynamicEndpoint ${dynamicName} for service ${serviceName}")
      }
      endpoint = newFixedEndpoint(((Map<String, String>) service.getConfig().get("dynamicEndpoints")).get(dynamicName))
    }

    return endpoint
  }


}
