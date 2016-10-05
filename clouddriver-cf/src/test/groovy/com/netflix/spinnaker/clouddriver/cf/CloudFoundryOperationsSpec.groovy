/*
 * Copyright 2015 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cf

import com.netflix.spinnaker.clouddriver.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.clouddriver.core.CloudDriverConfig
import com.netflix.spinnaker.clouddriver.core.CloudProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.cf.deploy.handlers.CloudFoundryDeployHandler
import com.netflix.spinnaker.clouddriver.cf.utils.CloudFoundryClientFactory
import groovy.json.JsonSlurper
import org.cloudfoundry.client.lib.CloudFoundryClient
import org.cloudfoundry.client.lib.CloudFoundryOperations
import org.cloudfoundry.client.lib.domain.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration
import org.springframework.boot.test.IntegrationTest
import org.springframework.boot.test.WebIntegrationTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.context.support.GenericWebApplicationContext
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import java.lang.annotation.Annotation

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup

/**
 * Test cases for the {@link com.netflix.spinnaker.kato.controllers.OperationsController} based on Cloud Foundry
 * operations. Uses Spring MVC Test.
 *
 * @see http://docs.spring.io/spring/docs/current/spring-framework-reference/html/testing.html#spring-mvc-test-framework
 *
 */
@ContextConfiguration(classes = [TestConfiguration])
@SpringBootTest(properties=['cf.enabled:true', 'services.front50.enabled:false'],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CloudFoundryOperationsSpec extends Specification {

  @Autowired
  GenericWebApplicationContext context

  @Autowired
  TaskRepository taskRepository

  @Shared
  MockMvc mvc

  def setup() {
    def runningInstance = Stub(InstanceInfo) {
      getState() >> InstanceState.RUNNING
    }

    def stubInstancesInfo = Stub(InstancesInfo) {
      getInstances() >> [runningInstance]
    }

    def stubApplication = Stub(CloudApplication) {
      getName() >> 'my-neat-app'
    }

    def log = "Application 'my-neat-app' is staged"

    def stubDomain = Stub(CloudDomain) {
      getName() >> "cfapps.io"
    }

    def stubClient = Stub(CloudFoundryClient) {
      // Read out some stubbed log data
      getStagingLogs(_, 0) >> log
      getStagingLogs(_, log.length()) >> null
      // Get mock instances
      getApplicationInstances(_) >> stubInstancesInfo
      getApplication(_) >> stubApplication
      getDefaultDomain() >> stubDomain
    }

    Task task = Stub(Task) {
      getResultObjects() >> []
    }
    TaskRepository.threadLocalTask.set(task)

    context.getBean(CloudFoundryDeployHandler).clientFactory =
        new TestCloudFoundryClientFactory(stubClient: stubClient)

    mvc = webAppContextSetup(context).build()
  }

  @Ignore('Reinstate when end-to-end deployment is working')
  void "pushing to Cloud Foundry should work"() {
    setup:
    def text = getJsonContent("cf-deploy.json")

    when:
    def resultAction = mvc.perform(
        post('/ops')
            .content(text)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
    )

    then:
    def response = resultAction
      .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
      .andReturn().response.getContentAsString()

    String taskId = new JsonSlurper().parseText(response).id

    while (!taskRepository.get(taskId).status?.isCompleted()) {
      Thread.sleep(1000L)
    }

    when:
    def history = taskRepository.get(taskId).history
        .collect{[phase: it.phase, status: it.status, completed: it.isCompleted()]}
    def statusHistory = history.collect{it.status}

    then:
    statusHistory.contains("Checking status of application 'my-neat-app'")
    statusHistory.contains("1 of 1 instances running (1 running)")
    statusHistory.contains("Application 'my-neat-app' is available at 'http://my-neat-app.cfapps.io'")
    statusHistory.contains("Server Groups: [] created.")
    statusHistory.contains("Orchestration completed.")
  }

  @Ignore('Reinstate when end-to-end deployment is working')
  void "pushing to Cloud Foundry should support setting number of instances"() {
    setup:
    def text = getJsonContent("cf-deploy-instances.json")

    when:
    def resultAction = mvc.perform(
        post('/ops')
            .content(text)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
    )

    then:
    def response = resultAction
        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
        .andReturn().response.getContentAsString()

    String taskId = new JsonSlurper().parseText(response).id

    while (!taskRepository.get(taskId).status?.isCompleted()) {
      Thread.sleep(1000L)
    }

    when:
    def history = taskRepository.get(taskId).history
        .collect{[phase: it.phase, status: it.status, completed: it.isCompleted()]}
    def statusHistory = history.collect{it.status}

    then:
    statusHistory.contains("Setting the number of instances to 5")
  }

  @Ignore('Reinstate when end-to-end deployment is working')
  void "pushing to Cloud Foundry should support setting custom URIs"() {
    setup:
    def text = getJsonContent("cf-deploy-urls.json")

    when:
    def resultAction = mvc.perform(
        post('/ops')
            .content(text)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
    )

    then:
    def response = resultAction
        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
        .andReturn().response.getContentAsString()

    String taskId = new JsonSlurper().parseText(response).id

    while (!taskRepository.get(taskId).status?.isCompleted()) {
      Thread.sleep(1000L)
    }

    when:
    def history = taskRepository.get(taskId).history
        .collect{[phase: it.phase, status: it.status, completed: it.isCompleted()]}
    def statusHistory = history.collect{it.status}

    then:
    statusHistory.contains("Application 'my-neat-app' is available at 'http://my-neat-app-blue,http://my-neat-app-green'")
  }

  @Ignore('Reinstate when end-to-end deployment is working')
  void "pushing to Cloud Foundry should support setting custom domains"() {
    setup:
    def text = getJsonContent("cf-deploy-domains.json")

    when:
    def resultAction = mvc.perform(
        post('/ops')
            .content(text)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
    )

    then:
    def response = resultAction
        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
        .andReturn().response.getContentAsString()

    String taskId = new JsonSlurper().parseText(response).id

    while (!taskRepository.get(taskId).status?.isCompleted()) {
      Thread.sleep(1000L)
    }

    when:
    def history = taskRepository.get(taskId).history
        .collect{[phase: it.phase, status: it.status, completed: it.isCompleted()]}
    def statusHistory = history.collect{it.status}

    then:
    statusHistory.contains("Adding 'example.com' to list of registered domains")
    statusHistory.contains("Adding 'example.de' to list of registered domains")
  }

  private String getJsonContent(String jsonFilename) {
    def pathToJar = context.getResource("classpath:cool-app.jar").file.absolutePath
    def jsonFile = context.getResource("classpath:${jsonFilename}").file
    jsonFile.getText(Charsets.UTF_8.name()).replaceAll("cool-app.jar", pathToJar)
  }

  @Configuration
  @ComponentScan(["com.netflix.spinnaker.kato.config", "com.netflix.spinnaker.kato.controllers"])
  @Import([CloudDriverConfig])
  @EnableAutoConfiguration(exclude = [BatchAutoConfiguration, GroovyTemplateAutoConfiguration])
  static class TestConfiguration {

    @Bean
    AccountCredentials accountCredentials() {
      new CloudFoundryAccountCredentials(name: "me@example.com", password: "testpassword")
    }

    @Bean
    AccountCredentialsProvider credentialsProvider(AccountCredentialsRepository repository,
                                                   AccountCredentials accountCredentials) {
      repository.save("test", accountCredentials)
      new DefaultAccountCredentialsProvider(repository)
    }

    @Bean
    CloudProvider cloudProvider() {
      new CloudProvider() {
        String id = 'cloud-foundry'
        String displayName = 'Cloud Foundry'
        Class<? extends Annotation> operationAnnotationType = null
      }
    }
  }

  static class TestCloudFoundryClientFactory implements CloudFoundryClientFactory {

    CloudFoundryOperations stubClient

    @Override
    CloudFoundryOperations createCloudFoundryClient(CloudFoundryAccountCredentials credentials,
                                                boolean trustSelfSignedCerts) {
      stubClient
    }
  }

}

