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



package com.netflix.spinnaker.front50.controllers

import com.amazonaws.ClientConfiguration
import com.amazonaws.services.s3.AmazonS3Client
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.S3ApplicationDAO
import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.model.application.ApplicationDAO
import com.netflix.spinnaker.front50.model.application.CassandraApplicationDAO
import com.netflix.spinnaker.front50.model.notification.NotificationDAO
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO
import com.netflix.spinnaker.front50.model.project.ProjectDAO
import com.netflix.spinnaker.front50.utils.CassandraTestHelper
import com.netflix.spinnaker.front50.utils.S3TestHelper
import com.netflix.spinnaker.front50.validator.HasEmailValidator
import com.netflix.spinnaker.front50.validator.HasNameValidator
import org.springframework.context.support.StaticMessageSource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import rx.schedulers.Schedulers
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.Executors

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

abstract class ApplicationsControllerTck extends Specification {
  @Shared
  ObjectMapper objectMapper = new ObjectMapper()

  @Shared
  MockMvc mockMvc

  @Shared
  ApplicationsController controller

  @Subject
  ApplicationDAO dao

  @Shared
  ProjectDAO projectDAO = Stub(ProjectDAO)

  @Shared
  NotificationDAO notificationDAO = Stub(NotificationDAO)

  @Shared
  PipelineDAO pipelineDAO = Stub(PipelineDAO)

  @Shared
  PipelineStrategyDAO pipelineStrategyDAO = Stub(PipelineStrategyDAO)

  void setup() {
    this.dao = createApplicationDAO()
    this.controller = new ApplicationsController(
        applicationDAO: dao,
        projectDAO: projectDAO,
        notificationDAO: notificationDAO,
        pipelineStrategyDAO: pipelineStrategyDAO,
        pipelineDAO: pipelineDAO,
        applicationValidators: [new HasNameValidator(), new HasEmailValidator()],
        messageSource: new StaticMessageSource()
    )
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
  }

  abstract ApplicationDAO createApplicationDAO()

  void 'a put should update an application'() {
    setup:
    def owner = "Andy McEntee"
    def sampleApp = new Application(name: "SAMPLEAPP", email: "web@netflix.com", owner: owner)
    dao.create("SAMPLEAPP", new Application())

    when:
    def response = mockMvc.perform(put("/default/applications").
      contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(sampleApp)))

    then:
    response.andExpect status().isOk()
    toMap(dao.findByName("SAMPLEAPP")) == toMap(sampleApp)
  }

  void 'a put should not update an application if no name is provided'() {
    setup:
    def sampleApp = new Application(email: "web@netflix.com", owner: "Andy McEntee")

    when:
    def response = mockMvc.perform(put("/default/applications").
      contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(sampleApp)))

    then:
    response.andExpect status().is4xxClientError()
  }

  void 'a put should merge properties with existing ones'() {
    setup:
    def app = "SAMPLEAPP"
    def newEmail = "new@netflix.com"
    def dynamicPropertyToUpdate = "new dynamic property"
    def description = "old"
    def unchangedDynamicProperty = "no change"
    def brandNewDynamicProperty = "brand new"
    def existingApp = new Application(name: app, email: "old@netflix.com", description: description,
        dynamicPropertyToUpdate: "old dynamic property",
        unchangedDynamicProperty: unchangedDynamicProperty)

    dao.create(app, existingApp)

    def updates = new Application(name: app, email: newEmail,
        dynamicPropertyToUpdate: dynamicPropertyToUpdate,
        brandNewDynamicProperty: brandNewDynamicProperty)

    when:
    def response = mockMvc.perform(put("/default/applications").
        contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(updates)))

    then:
    response.andExpect status().isOk()
    toMap(dao.findByName(app)) == toMap(new Application(
        name: app,
        description: description,
        email: newEmail,
        dynamicPropertyToUpdate: dynamicPropertyToUpdate,
        unchangedDynamicProperty: unchangedDynamicProperty,
        brandNewDynamicProperty: brandNewDynamicProperty
    ))
  }

  void 'a post w/o a name will throw an error'() {
    setup:
    def sampleApp = new Application(type: "Standalone App", email: "web@netflix.com", owner: "Kevin McEntee",
      description: "netflix.com application", group: "Standalone Application")

    when:
    def response = mockMvc.perform(post("/default/applications/name/app").
      contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(sampleApp)))

    then:
    response.andExpect status().is4xxClientError()
  }

  void 'a post w/an existing application will throw an error'() {
    setup:
    def sampleApp = new Application(name: "SAMPLEAPP", email: "web@netflix.com", description: "an application")
    dao.create(sampleApp.name, sampleApp)

    when:
    def response = mockMvc.perform(post("/default/applications/name/SAMPLEAPP").
        contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(sampleApp)))

    then:
    response.andExpect status().is4xxClientError()
  }

  void 'a post w/a new application should yield a success'() {
    setup:
    def sampleApp = new Application(name: "SAMPLEAPP", type: "Standalone App", email: "web@netflix.com")

    when:
    def response = mockMvc.perform(post("/default/applications/name/SAMPLEAPP").
      contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(sampleApp)))

    then:
    response.andExpect status().isOk()
    toMap(dao.findByName(sampleApp.name)) == toMap(sampleApp)
  }

  void 'a get w/a name should return a JSON document for the found app'() {
    setup:
    def sampleApp = new Application(name:"SAMPLEAPP", type: "Standalone App", email: "web@netflix.com",
        createTs: "1265752693581l", updateTs: "1265752693581l")
    dao.create(sampleApp.name, sampleApp)

    when:
    def response = mockMvc.perform(get("/default/applications/name/SAMPLEAPP"))

    then:
    response.andExpect status().isOk()
    toMap(dao.findByName(sampleApp.name)) == toMap(sampleApp)
  }

  void 'a get w/a invalid name should return 404'() {
    when:
    def response = mockMvc.perform(get("/default/applications/name/blah"))

    then:
    response.andExpect status().is(404)
  }

  void 'delete should remove an app'() {
    given:
    dao.create("SAMPLEAPP", new Application())

    when:
    def response = mockMvc.perform(delete("/default/applications/name/SAMPLEAPP"))

    then:
    response.andExpect status().isAccepted()

    when:
    dao.findByName("SAMPLEAPP")

    then:
    thrown(NotFoundException)


  }

  void 'index should return a list of applications'() {
    setup:
    [new Application(name: "SAMPLEAPP", email: "web1@netflix.com", createTs: "1265752693581l",
        updateTs: "1265752693581l"),
     new Application(name: "SAMPLEAPP-2", email: "web2@netflix.com", createTs:  "1265752693581l",
         updateTs: "1265752693581l")].each {
      dao.create(it.name, it)
    }

    when:
    def response = mockMvc.perform(get("/${account}/applications"))

    then:
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString(dao.all()))

    where:
    account = "default"
  }

  void "search hits the dao"() {
    setup:
    [new Application(name: "SAMPLEAPP", email: "web1@netflix.com", createTs: "1265752693581l",
      updateTs: "1265752693581l"),
                      new Application(name: "SAMPLEAPP-2", email: "web2@netflix.com", createTs:  "1265752693581l",
      updateTs: "1265752693581l")].each {
      dao.create(it.name, it)
    }

    when:
    def response = mockMvc.perform(get("/${account}/applications/search?email=web1@netflix.com"))

    then:
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString([dao.findByName("SAMPLEAPP")]))

    where:
    account = "default"
  }

  private Map toMap(Application application) {
    def map = objectMapper.convertValue(application, Map)
    map.remove("updateTs")
    map.remove("createTs")
    return map
  }
}

class CassandraApplicationsControllerTck extends ApplicationsControllerTck {
  @Shared
  CassandraTestHelper cassandraHelper = new CassandraTestHelper()

  @Shared
  CassandraApplicationDAO applicationDAO

  @Override
  ApplicationDAO createApplicationDAO() {
    applicationDAO = new CassandraApplicationDAO(keyspace: cassandraHelper.keyspace, objectMapper: objectMapper)
    applicationDAO.init()

    applicationDAO.runQuery('''TRUNCATE application''')

    return applicationDAO
  }
}

@IgnoreIf({ S3TestHelper.s3ProxyUnavailable() })
class S3ApplicationsControllerTck extends ApplicationsControllerTck {
  @Shared
  def scheduler = Schedulers.from(Executors.newFixedThreadPool(1))

  @Shared
  S3ApplicationDAO s3ApplicationDAO

  @Override
  ApplicationDAO createApplicationDAO() {
    def amazonS3 = new AmazonS3Client(new ClientConfiguration())
    amazonS3.setEndpoint("http://127.0.0.1:9999")
    S3TestHelper.setupBucket(amazonS3, "front50")

    s3ApplicationDAO = new S3ApplicationDAO(new ObjectMapper(), amazonS3, scheduler, 0, "front50", "test")
    return s3ApplicationDAO
  }
}

