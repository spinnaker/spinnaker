/*
 * Copyright 2014 Netflix, Inc.
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



package com.netflix.bluespar.oort.remoting

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.InheritConstructors
import org.springframework.web.client.RestTemplate

class DiscoverableRemoteResource implements RemoteResource {
  private static final String UP_STATUS = "UP"

  RestTemplate restTemplate
  ObjectMapper objectMapper = new ObjectMapper()

  private final String app
  private final String discoveryUrl

  DiscoverableRemoteResource(String app, String discoveryUrl, RestTemplate restTemplate = new RestTemplate()) {
    this.app = app
    this.discoveryUrl = discoveryUrl
    this.restTemplate = restTemplate
  }

  Map get(String uri) {
    restTemplate.getForObject("$location/$uri", Map)
  }

  List query(String uri) {
    def json = restTemplate.getForObject("$location/$uri", String)
    objectMapper.readValue(json, List)
  }

  String getLocation() {
    locateResource(app, discoveryUrl, restTemplate)
  }

  private static String locateResource(String app, String discoveryUrl, RestTemplate restTemplate) {
    def response = restTemplate.getForObject(discoveryUrl, Map)
    def upInstance = response.instances.find { it.status.name == UP_STATUS }
    if (!upInstance) {
      throw new RemoteResourceNotFoundException("Could not locate resource $app.")
    }
    upInstance.homePageUrl
  }

  @InheritConstructors
  static class RemoteResourceNotFoundException extends RuntimeException {}
}
