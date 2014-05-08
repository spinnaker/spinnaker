package com.netflix.oort.remoting

import groovy.transform.InheritConstructors
import org.springframework.web.client.RestTemplate

class SimpleRemoteResource implements RemoteResource {
  private static final String UP_STATUS = "UP"
  private static final String DISCOVERY_LOOKUP_FMT = "http://entrypoints-v2.%s.%s.netflix.net:7001/REST/v2/discovery/applications/%s"

  RestTemplate restTemplate = new RestTemplate()

  private final String location

  SimpleRemoteResource(String app, String region="us-east-1", String env="test", RestTemplate restTemplate = new RestTemplate()) {
    this.location = locateResource(app, region, env, restTemplate)
    this.restTemplate = restTemplate
  }

  Map get(String uri) {
    restTemplate.getForObject("$location/$uri", Map)
  }

  List query(String uri) {
    restTemplate.getForObject("$location/$uri", List)
  }

  private static String locateResource(String app, String region, String env, RestTemplate restTemplate) {
    def response = restTemplate.getForObject(String.format(DISCOVERY_LOOKUP_FMT, region, env, app), Map)
    def upInstance = response.instances.find { it.status.name == UP_STATUS }
    if (!upInstance) {
      throw new RemoteResourceNotFoundException("Could not locate resource $app in $region's $env environment.")
    }
    upInstance.homePageUrl
  }

  @InheritConstructors
  static class RemoteResourceNotFoundException extends RuntimeException {}
}
