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
import com.netflix.spinnaker.gate.services.*
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
  static FlapJackService flapJackService
  static Front50Service front50Service
  static MortService mortService
  static TaskService taskService
  static OortService oortService
  static OrcaService orcaService
  static TagService tagService
  static CredentialsService credentialsService
  static MayoService mayoService
  static KatoService katoService

  void setup() {
    applicationService = Mock(ApplicationService)
    flapJackService = Mock(FlapJackService)
    taskService = Mock(TaskService)
    oortService = Mock(OortService)
    orcaService = Mock(OrcaService)
    mortService = Mock(MortService)
    tagService = Mock(TagService)
    credentialsService = Mock(CredentialsService)
    mayoService = Mock(MayoService)
    katoService = Mock(KatoService)

    def sock = new ServerSocket(0)
    def localPort = sock.localPort
    sock.close()
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
      1 * applicationService.getAll() >> Observable.just([])
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

  void "should call TaskService to create a task for an application"() {
    when:
      api.createTask("foo", task)

    then:
      1 * taskService.create(task) >> Observable.just([:])

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
    MortService mortService() {
      mortService
    }

    @Bean
    Front50Service front50Service() {
      front50Service
    }

    @Bean
    TaskService taskService() {
      taskService
    }

    @Bean
    OrcaService orcaService() {
      orcaService
    }

    @Bean
    ApplicationService applicationService() {
      applicationService
    }

    @Bean
    TagService tagService() {
      tagService
    }

    @Bean
    FlapJackService flapJackService() {
      flapJackService
    }

    @Bean
    CredentialsService credentialsService() {
      credentialsService
    }

    @Bean
    MayoService mayoService() {
      mayoService
    }

    @Bean
    KatoService katoService() {
      katoService
    }

    @Bean
    ApplicationController applicationController() {
      new ApplicationController()
    }
  }
}
