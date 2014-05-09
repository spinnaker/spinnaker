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

package com.netflix.asgard.oort

import com.netflix.asgard.oort.remoting.AggregateRemoteResource
import com.netflix.asgard.oort.remoting.DiscoverableRemoteResource
import com.netflix.asgard.oort.remoting.RemoteResource
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestTemplate
import org.springsource.loaded.agent.SpringLoadedPreProcessor
import org.springsource.loaded.agent.SpringPlugin

@EnableAutoConfiguration
@Configuration
@ComponentScan
@EnableScheduling
class Main {

  static void main(_) {
    def springPlugin = SpringLoadedPreProcessor.globalPlugins.find { it instanceof SpringPlugin }
    if (springPlugin) {
      SpringLoadedPreProcessor.unregisterGlobalPlugin springPlugin
    }
    SpringApplication.run this, [] as String[]
  }

  @Value('${discovery.url.format}')
  String discoveryUrlFormat

  @Bean
  AggregateRemoteResource edda() {
    def appName = "entrypoints_v2"
    def remoteResources = ["us-east-1", "us-west-1", "us-west-2", "eu-west-1"].collectEntries {
      [(it): new DiscoverableRemoteResource(appName, String.format(discoveryUrlFormat, it, appName))]
    }
    new AggregateRemoteResource(remoteResources)
  }

  @Bean
  RemoteResource front50() {
    def appName = "front50"
    new DiscoverableRemoteResource(appName, String.format(discoveryUrlFormat, "us-west-1", appName))
  }

  @Bean
  RemoteResource bakery() {
    new RemoteResource() {
      final RestTemplate restTemplate = new RestTemplate()
      final String location = "http://bakery.test.netflix.net:7001"

      Map get(String uri) {
        restTemplate.getForObject "$location/$uri", Map
      }

      List query(String uri) {
        restTemplate.getForObject "$location/$uri", List
      }
    }
  }

}
