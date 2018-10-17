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

import com.amazonaws.ClientConfiguration
import com.amazonaws.services.s3.AmazonS3Client
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.front50.controllers.SimpleExceptionHandlerExceptionResolver
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.DefaultObjectKeyLoader
import com.netflix.spinnaker.front50.model.S3StorageService
import com.netflix.spinnaker.front50.model.StorageService
import com.netflix.spinnaker.front50.model.project.DefaultProjectDAO
import com.netflix.spinnaker.front50.model.project.Project
import com.netflix.spinnaker.front50.model.project.ProjectDAO
import com.netflix.spinnaker.front50.utils.S3TestHelper
import org.springframework.context.support.StaticMessageSource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver
import rx.schedulers.Schedulers
import spock.lang.*

import java.util.concurrent.Executors

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

abstract class ProjectsControllerTck extends Specification {
  static final int BAD_REQUEST = 400

  @Shared
  ObjectMapper objectMapper = new ObjectMapper()

  @Shared
  MockMvc mockMvc

  @Shared
  ProjectsController controller

  @Subject
  ProjectDAO dao

  void setup() {
    this.dao = createProjectDAO()

    this.controller = new ProjectsController(
        projectDAO: dao,
        messageSource: new StaticMessageSource()
    )
    this.mockMvc = MockMvcBuilders
      .standaloneSetup(controller)
      .setHandlerExceptionResolvers(createExceptionResolver())
      .build()
  }

  private static ExceptionHandlerExceptionResolver createExceptionResolver() {
    def resolver = new SimpleExceptionHandlerExceptionResolver()
    resolver.afterPropertiesSet()
    return resolver
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
    response.status == BAD_REQUEST
    response.errorMessage == "A Project named ${project.name} already exists"

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

@IgnoreIf({ S3TestHelper.s3ProxyUnavailable() })
class S3ProjectsControllerTck extends ProjectsControllerTck {
  @Shared
  def scheduler = Schedulers.from(Executors.newFixedThreadPool(1))

  @Shared
  ProjectDAO projectDAO



  @Override
  ProjectDAO createProjectDAO() {
    def amazonS3 = new AmazonS3Client(new ClientConfiguration())
    amazonS3.setEndpoint("http://127.0.0.1:9999")
    S3TestHelper.setupBucket(amazonS3, "front50")

    StorageService storageService = new S3StorageService(new ObjectMapper(), amazonS3, "front50", "test", false, "us-east-1", true, 10_000)
    projectDAO = new DefaultProjectDAO(storageService, scheduler, new DefaultObjectKeyLoader(storageService), 0, false, new NoopRegistry())

    return projectDAO
  }
}
