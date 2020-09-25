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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.config.ErrorConfiguration
import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties
import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.gate.config.ServiceConfiguration
import com.netflix.spinnaker.gate.controllers.ApplicationController
import com.netflix.spinnaker.gate.controllers.PipelineController
import com.netflix.spinnaker.gate.services.*
import com.netflix.spinnaker.gate.services.internal.*
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.dynamicconfig.SpringDynamicConfigService
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import retrofit.RetrofitError
import retrofit.RestAdapter;
import retrofit.client.OkClient
import retrofit.mime.TypedInput
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.ExecutorService

class FunctionalSpec extends Specification {
  @Shared
  ObjectMapper objectMapper = new ObjectMapper()

  @Shared
  Api api

  static ApplicationService applicationService
  static ExecutionHistoryService executionHistoryService
  static ExecutorService executorService
  static Front50Service front50Service
  static ClouddriverService clouddriverService
  static ClouddriverServiceSelector clouddriverServiceSelector
  static TaskService taskService
  static OrcaService orcaService
  static OrcaServiceSelector orcaServiceSelector
  static CredentialsService credentialsService
  static PipelineService pipelineService
  static ServiceConfiguration serviceConfiguration
  static AccountLookupService accountLookupService
  static FiatStatus fiatStatus

  ConfigurableApplicationContext ctx

  void setup() {
    applicationService = Mock(ApplicationService)
    orcaServiceSelector = Mock(OrcaServiceSelector)
    executionHistoryService = new ExecutionHistoryService(orcaServiceSelector: orcaServiceSelector)
    executorService = Mock(ExecutorService)
    taskService = Mock(TaskService)
    clouddriverService = Mock(ClouddriverService)
    clouddriverServiceSelector = Mock(ClouddriverServiceSelector)
    orcaService = Mock(OrcaService)
    credentialsService = Mock(CredentialsService)
    accountLookupService = Mock(AccountLookupService)
    pipelineService = Mock(PipelineService)
    serviceConfiguration = new ServiceConfiguration()
    fiatStatus = Mock(FiatStatus)


    def sock = new ServerSocket(0)
    def localPort = sock.localPort
    sock.close()
    System.setProperty("server.port", localPort.toString())
    System.setProperty("saml.enabled", "false")
    System.setProperty('spring.session.store-type', 'NONE')
    System.setProperty("spring.main.allow-bean-definition-overriding", "true")
    System.setProperty("spring.profiles.active", "test")
    System.setProperty("retrofit.enabled", "false")
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
      1 * applicationService.getAllApplications() >> []
  }

  void "should call ApplicationService for a single application"() {
    when:
      api.getApplication(name)

    then:
      1 * applicationService.getApplication(name, true) >> [name: name]

    where:
      name = "foo"
  }

  void "should 404 if ApplicationService does not return an application"() {
    when:
      api.getApplication(name)

    then:
      1 * applicationService.getApplication(name, true) >> null

      RetrofitError exception = thrown()
      exception.response.status == 404

    where:
      name = "foo"
  }

  void "should call ApplicationService for an application's tasks"() {
    when:
      api.getTasks(name, null, null, "RUNNING,TERMINAL")

    then:
      1 * orcaServiceSelector.select() >> { orcaService }
      1 * orcaService.getTasks(name, null, null, "RUNNING,TERMINAL") >> []

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

  Map toMap(TypedInput typedInput) {
    return objectMapper.readValue(typedInput.in().text, Map)
  }

  @Order(10)
  @Import(ErrorConfiguration)
  @EnableAutoConfiguration(exclude = [GroovyTemplateAutoConfiguration, GsonAutoConfiguration])
  private static class FunctionalConfiguration extends WebSecurityConfigurerAdapter {

    @Bean
    ClouddriverServiceSelector clouddriverSelector() {
      clouddriverServiceSelector
    }

    @Bean
    ClouddriverService clouddriverService() {
      clouddriverService
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
    OrcaServiceSelector orcaServiceSelector() {
      orcaServiceSelector
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
    AccountLookupService accountLookupService() {
      accountLookupService
    }

    @Bean
    RestAdapter.LogLevel retrofitLogLevel() {
      return RestAdapter.LogLevel.BASIC
   }

    @Bean
    PipelineController pipelineController() {
      new PipelineController()
    }

    @Bean
    ApplicationController applicationController() {
      new ApplicationController()
    }

    @Bean
    FiatClientConfigurationProperties fiatClientConfigurationProperties() {
      new FiatClientConfigurationProperties(enabled: false)
    }

    @Bean
    SpringDynamicConfigService dynamicConfigService() {
      new SpringDynamicConfigService()
    }

    @Bean
    FiatStatus fiatStatus(DynamicConfigService dynamicConfigService,
                          FiatClientConfigurationProperties fiatClientConfigurationProperties) {
      new FiatStatus(
        new NoopRegistry(),
        dynamicConfigService,
        fiatClientConfigurationProperties
      )
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
      http
        .csrf().disable()
        .authorizeRequests().antMatchers("/**").permitAll()
    }
  }
}
