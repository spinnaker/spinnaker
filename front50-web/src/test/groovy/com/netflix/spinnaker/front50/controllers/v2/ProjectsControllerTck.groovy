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
import com.netflix.spinnaker.front50.config.StorageServiceConfigurationProperties
import com.netflix.spinnaker.front50.model.DefaultObjectKeyLoader
import com.netflix.spinnaker.front50.model.SqlStorageService
import com.netflix.spinnaker.front50.model.project.DefaultProjectDAO
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator
import com.netflix.spinnaker.kork.web.exceptions.GenericExceptionHandlers
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import com.netflix.spinnaker.front50.model.project.Project
import com.netflix.spinnaker.front50.model.project.ProjectDAO
import io.github.resilience4j.circuitbreaker.internal.InMemoryCircuitBreakerRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import rx.schedulers.Schedulers
import spock.lang.*

import java.time.Clock
import java.util.concurrent.Executors

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

abstract class ProjectsControllerTck extends Specification {
  ObjectMapper objectMapper = new ObjectMapper()

  MockMvc mockMvc

  ProjectsController controller

  @Subject
  ProjectDAO dao

  void setup() {
    this.dao = createProjectDAO()

    this.controller = new ProjectsController(dao)
    this.mockMvc = MockMvcBuilders
      .standaloneSetup(controller)
      .setControllerAdvice(
        new GenericExceptionHandlers(
          new ExceptionMessageDecorator(Mock(ObjectProvider))
        )
      )
      .build()
  }

  abstract ProjectDAO createProjectDAO()

  @Unroll
  void "should search by projectName or applicationName"() {
    given:
    if (project != null) {
      dao.create(null, project)
    }

    when:
    def response = mockMvc.perform(get("/v2/projects/search?q=" + criteria))

    then:
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString([dao.findByName(project.name)]))

    where:
    criteria       | project
    "Project1"     | new Project(name: "Project1")
    "Application1" | new Project(name: "Project1", config: new Project.ProjectConfig(
      applications: ["Application1", "Application2"]
    ))
  }

  def "should search for projects"() {
    given:
    [
      new Project(
        name: "Project1",
        email: "web1@netflix.com",
        config: new Project.ProjectConfig(
          applications: ["app1", "app2"],
          clusters: [ new Project.ClusterConfig(
            account: "test",
            stack: "test"
          ) ],
          pipelineConfigs: [ new Project.PipelineConfig(
            pipelineConfigId: "pipelineId",
            application: "app1"
          )]
        )
      ),
      new Project(
        name: "Project",
        email: "web1@netflix.com",
        config: new Project.ProjectConfig(
          applications: ["app3", "app4"],
          clusters: [ new Project.ClusterConfig(
            account: "test",
            stack: "prod"
          ) ],
          pipelineConfigs: [ new Project.PipelineConfig(
            pipelineConfigId: "pipelineId2",
            application: "app3"
          )]
        )
      )
    ].each {
      dao.create(null, it)
    }

    when:
    def response = mockMvc.perform(
      get("/v2/projects?name=Project1")
    )

    then:
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString([dao.findByName("Project1")]))

    when:
    response = mockMvc.perform(
      get("/v2/projects?name=non-existent")
    )

    then:
    response.andExpect status().isOk()
    notThrown(NotFoundException)
    response.andExpect content().string("[]")


    when:
    response = mockMvc.perform(
      get("/v2/projects?applications=app3,app4")
    )

    then:
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString([dao.findByName("Project")]))

    when:
    response = mockMvc.perform(
      get("/v2/projects?name=Project&pageSize=2")
    )

    then:
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString([ dao.findByName("Project"), dao.findByName("Project1")]))


    when:
    response = mockMvc.perform(
      get("/v2/projects?name=Project&pageSize=1")
    )

    then: "should show the most relevant result"
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString([ dao.findByName("Project")]))


    when:
    response = mockMvc.perform(
      get("/v2/projects?stack=prod")
    )

    then:
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString([ dao.findByName("Project")]))
  }

  void "should fetch all projects"() {
    given:
    [new Project(name: "Project1"), new Project(name: "Project2")].each {
      dao.create(null, it)
    }

    expect:
    mockMvc.perform(
      get("/v2/projects")
    ).andExpect content().string(new ObjectMapper().writeValueAsString(dao.all()))
  }

  @Unroll
  void "should fetch projects by name or id"() {
    given:
    def project = dao.create(null, new Project(name: "Project1"))

    expect:
    extractMap(mockMvc.perform(get("/v2/projects/" + project.name)).andReturn().response.contentAsString) == toMap(project)
    extractMap(mockMvc.perform(get("/v2/projects/" + project.id)).andReturn().response.contentAsString) == toMap(project)
    mockMvc.perform(get("/v2/projects/DOES_NOT_EXIST")).andExpect(status().is4xxClientError())
  }

  void "should create projects"() {
    given:
    def project = new Project(name: "Project1")

    when:
    def response1 = mockMvc.perform(
      post("/v2/projects").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(project))
    )

    then:
    response1.andExpect(status().isOk())
    dao.findByName(project.name) != null

    when:
    def response2 = mockMvc.perform(
      post("/v2/projects").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(project))
    )

    then:
    // should fail when attempting to create a project with a duplicate name
    response2.andExpect(status().is4xxClientError())
  }

  void "should update existing project"() {
    given:
    def project = dao.create(null, new Project(name: "Project1"))

    // apply an update
    project.email = "default@netflix.com"

    //FIXME fix race condition
    sleep(2500)
    when:
    def response = mockMvc.perform(
      put("/v2/projects/" + project.id).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(project))
    )

    then:
    response.andExpect(status().isOk())

    dao.findByName(project.name).email == project.email

    when:
    dao.create(null, new Project(name: "Project2"))
    project.name = "Project2"

    response = mockMvc.perform(
      put("/v2/projects/" + project.id).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(project))
    ).andReturn().response

    then:
    response.status == 400
    response.errorMessage == "A Project named '${project.name}' already exists"

  }

  void "should delete an existing project"() {
    given:
    def project = dao.create(null, new Project(name: "Project1"))

    when:
    def response = mockMvc.perform(
      delete("/v2/projects/" + project.id)
    )

    then:
    response.andExpect(status().isAccepted())

    when:
    dao.findByName(project.name)

    then:
    thrown(NotFoundException)
  }

  Map extractMap(String content) {
    def result = objectMapper.readValue(content, Map) as Map
    result
  }

  private Map toMap(Project project) {
    return objectMapper.convertValue(project, Map)
  }
}

class SqlProjectsControllerTck extends ProjectsControllerTck {
  def scheduler = Schedulers.from(Executors.newFixedThreadPool(1))

  @AutoCleanup("close")
  SqlTestUtil.TestDatabase currentDatabase = SqlTestUtil.initTcMysqlDatabase()

  void cleanup() {
    SqlTestUtil.cleanupDb(currentDatabase.context)
  }

  @Override
  ProjectDAO createProjectDAO() {
    def registry = new NoopRegistry()

    def storageService = new SqlStorageService(
      new ObjectMapper(),
      registry,
      currentDatabase.context,
      Clock.systemDefaultZone(),
      new SqlRetryProperties(),
      100,
      "default"
    )

    return new DefaultProjectDAO(
      storageService,
      scheduler,
      new DefaultObjectKeyLoader(storageService),
      new StorageServiceConfigurationProperties.PerObjectType(),
      new NoopRegistry(),
      new InMemoryCircuitBreakerRegistry()
    )
  }
}
