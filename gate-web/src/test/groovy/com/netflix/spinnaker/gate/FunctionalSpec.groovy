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

import com.netflix.spinnaker.gate.config.ServiceConfiguration
import com.netflix.spinnaker.gate.controllers.ApplicationController
import com.netflix.spinnaker.gate.controllers.PipelineController
import com.netflix.spinnaker.gate.services.ApplicationService
import com.netflix.spinnaker.gate.services.CredentialsService
import com.netflix.spinnaker.gate.services.ExecutionHistoryService
import com.netflix.spinnaker.gate.services.PipelineService
import com.netflix.spinnaker.gate.services.TaskService
import com.netflix.spinnaker.gate.services.commands.ThrottledRequestException
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.gate.services.internal.KatoService
import com.netflix.spinnaker.gate.services.internal.MortService
import com.netflix.spinnaker.gate.services.internal.OortService
import com.netflix.spinnaker.gate.services.internal.OrcaService
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration
import org.springframework.context.ConfigurableApplicationContext
import retrofit.RetrofitError

import java.util.concurrent.ExecutorService
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.RestAdapter
import retrofit.client.OkClient
import spock.lang.Shared
import spock.lang.Specification

class FunctionalSpec extends Specification {

  @Shared
  Api api

  static ApplicationService applicationService
  static ExecutionHistoryService executionHistoryService
  static ExecutorService executorService
  static Front50Service front50Service
  static MortService mortService
  static TaskService taskService
  static OortService oortService
  static OrcaService orcaService
  static CredentialsService credentialsService
  static KatoService katoService
  static PipelineService pipelineService
  static ServiceConfiguration serviceConfiguration

  ConfigurableApplicationContext ctx

  void setup() {
    applicationService = Mock(ApplicationService)
    executionHistoryService = new ExecutionHistoryService(orcaService: orcaService)
    executorService = Mock(ExecutorService)
    taskService = Mock(TaskService)
    oortService = Mock(OortService)
    orcaService = Mock(OrcaService)
    mortService = Mock(MortService)
    credentialsService = Mock(CredentialsService)
    katoService = Mock(KatoService)
    pipelineService = Mock(PipelineService)
    serviceConfiguration = new ServiceConfiguration()

    def sock = new ServerSocket(0)
    def localPort = sock.localPort
    sock.close()
    System.setProperty("server.port", localPort.toString())
    System.setProperty("saml.enabled", "false")
    System.setProperty('security.basic.enabled', 'false')
    System.setProperty('management.security.enabled', 'false')
    def spring = new SpringApplication()
    spring.setSources([FunctionalConfiguration] as Set)
    ctx = spring.run()

    api = new RestAdapter.Builder()
        .setEndpoint("http://localhost:${localPort}")
        .setClient(new OkClient())
        .setLogLevel(RestAdapter.LogLevel.FULL)
        .build()
        .create(Api)
  }

  def cleanup() {
    ctx.close()
  }

  void "should call ApplicationService for applications"() {
    when:
      api.applications

    then:
      1 * applicationService.getAll() >> []
  }

  void "should call ApplicationService for a single application"() {
    when:
      api.getApplication(name)

    then:
      1 * applicationService.get(name) >> [name: name]

    where:
      name = "foo"
  }

  void "should 404 if ApplicationService does not return an application"() {
    when:
      api.getApplication(name)

    then:
      1 * applicationService.get(name) >> null

      RetrofitError exception = thrown()
      exception.response.status == 404

    where:
      name = "foo"
  }

  void "should call ApplicationService for an application's tasks"() {
    when:
      api.getTasks(name, null, "RUNNING,TERMINAL")

    then:
      1 * orcaService.getTasks(name, null, "RUNNING,TERMINAL") >> []

    where:
      name = "foo"
  }

  void "should call TaskService to create a task for an application"() {
    when:
      api.createTask("foo", task)

    then:
      1 * taskService.createAppTask('foo', task) >> [:]

    where:
      name = "foo"
      task = [type: "deploy"]
  }

  void "should throw ThrottledRequestException if result served from hystrix fallback"() {
    when:
    def tasks = executionHistoryService.getTasks("app", 5, null)

    then:
    1 * orcaService.getTasks("app", 5, null) >> { return ["1"] }
    tasks == ["1"]

    when:
      executionHistoryService.getTasks("app", 10, "RUNNING")

    then:
      1 * orcaService.getTasks("app", 10, "RUNNING") >> { throw new IllegalStateException() }
      thrown(ThrottledRequestException)

    when:
    executionHistoryService.getPipelines("app", 5, "TERMINAL")

    then:
    1 * orcaService.getPipelines("app", 5, "TERMINAL") >> { throw new IllegalStateException() }
    thrown(ThrottledRequestException)
  }

  @EnableAutoConfiguration(exclude = [SecurityAutoConfiguration, GroovyTemplateAutoConfiguration])
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
    ExecutionHistoryService executionHistoryService() {
      executionHistoryService
    }

    @Bean
    CredentialsService credentialsService() {
      credentialsService
    }

    @Bean
    KatoService katoService() {
      katoService
    }

    @Bean
    ExecutorService executorService() {
      executorService
    }

    @Bean
    PipelineService pipelineService() {
      pipelineService
    }

    @Bean
    ServiceConfiguration serviceConfiguration() {
      serviceConfiguration
    }

    @Bean
    PipelineController pipelineController() {
      new PipelineController()
    }

    @Bean
    ApplicationController applicationController() {
      new ApplicationController()
    }
  }
}
