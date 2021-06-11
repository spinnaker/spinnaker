/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.front50.controllers.v2

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.front50.config.FiatConfigurationProperties
import com.netflix.spinnaker.front50.jackson.Front50ApiModule
import com.netflix.spinnaker.front50.model.DefaultObjectKeyLoader
import com.netflix.spinnaker.front50.model.SqlStorageService
import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.model.application.ApplicationDAO
import com.netflix.spinnaker.front50.model.application.ApplicationService
import com.netflix.spinnaker.front50.model.application.DefaultApplicationDAO
import com.netflix.spinnaker.front50.model.notification.NotificationDAO
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO
import com.netflix.spinnaker.front50.model.project.ProjectDAO
import com.netflix.spinnaker.front50.validator.HasEmailValidator
import com.netflix.spinnaker.front50.validator.HasNameValidator
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator
import com.netflix.spinnaker.kork.web.exceptions.GenericExceptionHandlers
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import io.github.resilience4j.circuitbreaker.internal.InMemoryCircuitBreakerRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.support.StaticMessageSource
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import rx.schedulers.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Subject

import java.time.Clock
import java.util.concurrent.Executors

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

abstract class ApplicationsControllerTck extends Specification {
  ObjectMapper objectMapper = new ObjectMapper()

  MockMvc mockMvc
  ApplicationsController controller
  ApplicationService applicationService

  def projectDAO = Mock(ProjectDAO)
  def pipelineDAO = Mock(PipelineDAO)
  def pipelineStrategyDAO = Mock(PipelineStrategyDAO)
  def fiatStatus = Mock(FiatStatus)

  @Subject
  ApplicationDAO dao

  void setup() {
    objectMapper.registerModule(new Front50ApiModule())

    this.dao = createApplicationDAO()
    this.applicationService = new ApplicationService(
      dao,
      projectDAO,
      Mock(NotificationDAO),
      pipelineDAO,
      pipelineStrategyDAO,
      [new HasNameValidator(), new HasEmailValidator()],
      Collections.emptyList(),
      Optional.empty()
    )
    this.controller = new ApplicationsController(
      new StaticMessageSource(),
      dao,
      Optional.empty(),
      Optional.empty(),
      new FiatConfigurationProperties(),
      fiatStatus,
      applicationService
    )

    MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter = new MappingJackson2HttpMessageConverter();
    mappingJackson2HttpMessageConverter.setObjectMapper(objectMapper)

    this.mockMvc = MockMvcBuilders
      .standaloneSetup(controller)
      .setMessageConverters(mappingJackson2HttpMessageConverter)
      .setControllerAdvice(
        new GenericExceptionHandlers(
          new ExceptionMessageDecorator(Mock(ObjectProvider))
        )
      )
      .build()
  }

  abstract ApplicationDAO createApplicationDAO()

  def "should create a new application"() {
    given:
    def sampleApp = new Application(name: "SAMPLEAPP", email: "web@netflix.com", lastModifiedBy: "anonymous")

    when:
    def response = mockMvc
      .perform(
        post("/v2/applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(sampleApp))
      )

    then:
    response.andExpect status().isOk()
    toMap(dao.findByName(sampleApp.name)) == toMap(sampleApp)
  }

  def "should not create an application missing a name"() {
    given:
    def applicationMissingName = new Application(email: "web@netflix.com")

    when:
    def response = mockMvc
      .perform(
        post("/v2/applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(applicationMissingName))
      )

    then:
    response.andExpect status().is4xxClientError()
  }

  def "should return all applications"() {
    given:
    [
      new Application(
        name: "SAMPLEAPP",
        email: "web1@netflix.com",
        createTs: "1265752693581l",
        updateTs: "1265752693581l"
      ),
      new Application(
        name: "SAMPLEAPP-2",
        email: "web2@netflix.com",
        createTs: "1265752693581l",
        updateTs: "1265752693581l"
      )
    ].each {
      dao.create(it.name, it)
    }

    when:
    def response = mockMvc.perform(get("/v2/applications"))

    then:
    response.andExpect status().isOk()
    response.andExpect content().string(objectMapper.writeValueAsString(dao.all().sort { it.name }))
  }

  def "should update an application"() {
    given:
    def sampleApp = new Application(name: "SAMPLEAPP", email: "web@netflix.com", lastModifiedBy: "anonymous")
    dao.create("SAMPLEAPP", new Application())

    when:
    def response = mockMvc
      .perform(
        patch("/v2/applications/SAMPLEAPP")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(sampleApp))
      )

    then:
    response.andExpect status().isOk()
    toMap(dao.findByName("SAMPLEAPP")) == toMap(sampleApp)
  }

  def "should not update an application if no name is provided"() {
    given:
    def sampleApp = new Application(email: "web@netflix.com", owner: "Andy McEntee")

    when:
    def response = mockMvc
      .perform(
        patch("/v2/applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(sampleApp))
      )

    then:
    response.andExpect status().is4xxClientError()
  }

  def "should not update an application if provided name doesn't match the application name"() {
    given:
    def sampleApp = new Application(email: "web@netflix.com", name: "SAMPLEAPP")

    when:
    def response = mockMvc
      .perform(
        patch("/v2/applications/SAMPLEAPPWRONG")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(sampleApp))
      )

    then:
    response.andExpect status().is4xxClientError()
  }

  def "should merge properties with existing ones on update"() {
    given:
    def app = "SAMPLEAPP"
    def newEmail = "new@netflix.com"
    def dynamicPropertyToUpdate = "new dynamic property"
    def description = "old"
    def unchangedDynamicProperty = "no change"
    def brandNewDynamicProperty = "brand new"
    def existingApp = new Application(
      name: app,
      email: "old@netflix.com",
      description: description,
      dynamicPropertyToUpdate: "old dynamic property",
      unchangedDynamicProperty: unchangedDynamicProperty
    )

    dao.create(app, existingApp)

    def updates = new Application(
      name: app,
      email: newEmail,
      dynamicPropertyToUpdate: dynamicPropertyToUpdate,
      brandNewDynamicProperty: brandNewDynamicProperty
    )

    when:
    def response = mockMvc.perform(
      patch("/v2/applications/SAMPLEAPP")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(updates))
    )

    then:
    response.andExpect status().isOk()
    toMap(dao.findByName(app)) == toMap(new Application(
      name: app,
      description: description,
      email: newEmail,
      dynamicPropertyToUpdate: dynamicPropertyToUpdate,
      unchangedDynamicProperty: unchangedDynamicProperty,
      brandNewDynamicProperty: brandNewDynamicProperty,
      lastModifiedBy: "anonymous"
    ))
  }

  def "should find an application by name"() {
    given:
    def sampleApp = new Application(
      name: "SAMPLEAPP",
      type: "Standalone App",
      email: "web@netflix.com",
      createTs: "1265752693581l",
      updateTs: "1265752693581l"
    )

    dao.create(sampleApp.name, sampleApp)

    when:
    def response = mockMvc.perform(
      get("/v2/applications/SAMPLEAPP")
    )

    then:
    response.andExpect status().isOk()
    toMap(dao.findByName(sampleApp.name)) == toMap(sampleApp)
  }

  def "should return 404 when passing an invalid name"() {
    when:
    def response = mockMvc.perform(
      get("/v2/applications/blah")
    )

    then:
    response.andExpect status().is(404)
  }

  def "should remove an application"() {
    given:
    dao.create("SAMPLEAPP", new Application())

    when:
    def response = mockMvc.perform(
      delete("/v2/applications/SAMPLEAPP")
    )

    then:
    1 * projectDAO.all() >> { return Collections.emptyList() }
    1 * pipelineDAO.getPipelinesByApplication("sampleapp") >> { return Collections.emptyList() }
    1 * pipelineStrategyDAO.getPipelinesByApplication("sampleapp") >> { return Collections.emptyList() }
    response.andExpect status().isNoContent()

    when:
    dao.findByName("SAMPLEAPP")

    then:
    thrown(NotFoundException)
  }

  def "should search for applications"() {
    given:
    [
      new Application(
        name: "SAMPLEAPP",
        email: "web1@netflix.com",
        createTs: "1265752693581l",
        updateTs: "1265752693581l"
      ),
      new Application(
        name: "SAMPLEAPP-2",
        email: "web2@netflix.com",
        createTs: "1265752693581l",
        updateTs: "1265752693581l")
    ].each {
      dao.create(it.name, it)
    }

    when:
    def response = mockMvc.perform(
      get("/v2/applications?email=web1@netflix.com")
    )

    then:
    response.andExpect status().isOk()
    response.andExpect content().string(objectMapper.writeValueAsString([dao.findByName("SAMPLEAPP")]))

    when:
    response = mockMvc.perform(
      get("/v2/applications?name=non-existent")
    )

    then:
    response.andExpect status().isOk()
    notThrown(NotFoundException)
    response.andExpect content().string("[]")


    when:
    response = mockMvc.perform(
      get("/v2/applications?name=sample&pageSize=9999")
    )

    then:
    response.andExpect status().isOk()
    response.andExpect content().string(objectMapper.writeValueAsString([dao.findByName("SAMPLEAPP"), dao.findByName("SAMPLEAPP-2")]))

    when:
    response = mockMvc.perform(
      get("/v2/applications?name=sample&pageSize=1")
    )

    then:
    response.andExpect status().isOk()
    response.andExpect content().string(objectMapper.writeValueAsString([dao.findByName("SAMPLEAPP")]))
  }

  private Map toMap(Application application) {
    def map = objectMapper.convertValue(application, Map)
    map.remove("updateTs")
    map.remove("createTs")
    return map
  }
}

class SqlApplicationsControllerTck extends ApplicationsControllerTck {
  def scheduler = Schedulers.from(Executors.newFixedThreadPool(1))

  @AutoCleanup("close")
  SqlTestUtil.TestDatabase currentDatabase = SqlTestUtil.initTcMysqlDatabase()

  void cleanup() {
    SqlTestUtil.cleanupDb(currentDatabase.context)
  }

  @Override
  ApplicationDAO createApplicationDAO() {
    def registry = new NoopRegistry()

    def storageService = new SqlStorageService(
      objectMapper,
      registry,
      currentDatabase.context,
      Clock.systemDefaultZone(),
      new SqlRetryProperties(),
      100,
      "default"
    )

    return new DefaultApplicationDAO(
      storageService,
      scheduler,
      new DefaultObjectKeyLoader(storageService),
      0,
      false,
      new NoopRegistry(),
      new InMemoryCircuitBreakerRegistry()
    )
  }
}

