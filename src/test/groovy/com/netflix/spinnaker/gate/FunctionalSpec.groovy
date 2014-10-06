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

package com.netflix.spinnaker.gate

import com.netflix.spinnaker.gate.controllers.ApplicationController
import com.netflix.spinnaker.gate.services.ApplicationService
import com.netflix.spinnaker.gate.services.OortService
import com.netflix.spinnaker.gate.services.PondService
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.RestAdapter
import retrofit.client.OkClient
import rx.Observable
import spock.lang.Shared
import spock.lang.Specification

class FunctionalSpec extends Specification {

  @Shared
  Api api

  static ApplicationService applicationService
  static OortService oortService
  static PondService pondService

  void setup() {
    applicationService = Mock(ApplicationService)
    oortService = Mock(OortService)
    pondService = Mock(PondService)

    def localPort = new ServerSocket(0).localPort
    System.setProperty("server.port", localPort.toString())
    def spring = new SpringApplication()
    spring.setSources([FunctionalConfiguration] as Set)
    spring.run()

    api = new RestAdapter.Builder()
        .setEndpoint("http://localhost:${localPort}")
        .setClient(new OkClient())
        .setLogLevel(RestAdapter.LogLevel.FULL)
        .build()
        .create(Api)
  }

  def cleanup() {
  }

  void "should call ApplicationService for applications"() {
    when:
      api.applications

    then:
      1 * applicationService.getAll() >> Observable.just([:])
  }

  void "should call ApplicationService for a single application"() {
    when:
      api.getApplication(name)

    then:
      1 * applicationService.get(name) >> Observable.just([:])

    where:
      name = "foo"
  }

  void "should call ApplicationService for an application's tasks"() {
    when:
      api.getTasks(name)

    then:
      1 * applicationService.getTasks(name) >> Observable.just([])

    where:
      name = "foo"
  }

  void "should call ApplicationService to create a task for an application"() {
    when:
      api.createTask("foo", task)

    then:
      1 * applicationService.create(task) >> Observable.just([:])

    where:
      name = "foo"
      task = [type: "deploy"]
  }

  @EnableAutoConfiguration
  @Configuration
  static class FunctionalConfiguration {

    @Bean
    OortService oortService() {
      oortService
    }

    @Bean
    PondService pondService() {
      pondService
    }

    @Bean
    ApplicationService applicationService() {
      applicationService
    }

    @Bean
    ApplicationController applicationController() {
      new ApplicationController()
    }
  }
}
