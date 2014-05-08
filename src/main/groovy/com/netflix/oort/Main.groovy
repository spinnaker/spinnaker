package com.netflix.oort

import com.netflix.oort.remoting.*
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.*
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestTemplate

@EnableAutoConfiguration
@Configuration
@ComponentScan
@EnableScheduling
class Main {

  static void main(_) {
    SpringApplication.run this, [] as String[]
  }

  @Bean
  AggregateRemoteResource edda() {
    def remoteResources = ["us-east-1", "us-west-1", "us-west-2", "eu-west-1"].collectEntries {
      [(it): new SimpleRemoteResource("entrypoints_v2", it)]
    }
    new AggregateRemoteResource(remoteResources)
  }

  @Bean
  RemoteResource front50() {
    new SimpleRemoteResource("front50", "us-west-1")
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
