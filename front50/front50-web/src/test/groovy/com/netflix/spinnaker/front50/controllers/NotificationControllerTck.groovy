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


package com.netflix.spinnaker.front50.controllers

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.config.Front50SqlProperties
import com.netflix.spinnaker.front50.config.StorageServiceConfigurationProperties
import com.netflix.spinnaker.front50.model.DefaultObjectKeyLoader
import com.netflix.spinnaker.front50.model.SqlStorageService
import com.netflix.spinnaker.front50.model.notification.DefaultNotificationDAO
import com.netflix.spinnaker.front50.model.notification.HierarchicalLevel
import com.netflix.spinnaker.front50.model.notification.Notification
import com.netflix.spinnaker.front50.model.notification.NotificationDAO
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import io.github.resilience4j.circuitbreaker.internal.InMemoryCircuitBreakerRegistry
import org.springframework.context.support.StaticMessageSource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import io.reactivex.rxjava3.schedulers.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import java.time.Clock
import java.util.concurrent.Executors

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

abstract class NotificationControllerTck extends Specification {
  ObjectMapper objectMapper = new ObjectMapper()

  MockMvc mockMvc

  NotificationController controller

  NotificationDAO dao

  def global = new Notification([
    email: [[
              "level": "global",
              "when": [
                "pipeline.complete",
                "pipeline.failed"
              ],
              "type": "email",
              "address": "default@netflix.com"
            ]]
  ])

  void setup() {
    this.dao = createNotificationDAO()
    this.controller = new NotificationController(dao)
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
  }

  abstract NotificationDAO createNotificationDAO()

  void "should create global notifications"() {
    when:
    def response1 = mockMvc.perform(
      post("/notifications/global").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(global))
    )

    then:
    response1.andExpect(status().isOk())
    with(dao.getGlobal()){
      email == this.global.email
      type == this.global.type
      address == this.global.address
    }

    dao.all().size() == 1

    when:
    def response2 = mockMvc.perform(
      get("/notifications")
    )

    then:
    with(objectMapper.readValue(response2.andReturn().response.contentAsString,new TypeReference<List<Map>>(){})[0]) {
      email == this.global.email
      type == this.global.type
      address == this.global.address
    }

  }

  void "should save application notification"() {
    given:
    dao.saveGlobal(global)
    def application = new Notification([
      application: "my-application",
      hipchat: [[
                  "level": "application",
                  "when": [
                    "pipeline.complete",
                    "pipeline.failed"
                  ],
                  "type": "hipchat",
                  "address": "hipchatuser"
                ]]
    ])

    def expectedApplication = new Notification(application + global)

    when:
    def response1 = mockMvc.perform(
      post("/notifications/application/" + application.application).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(application))
    )

    then:
    response1.andExpect(status().isOk())

    when:
    def response2 = mockMvc.perform(
      get("/notifications/application/" + application.application)
    )

    then:
    response2.andExpect(status().isOk())


    with(objectMapper.readValue(response2.andReturn().response.contentAsString, Map)) {
      hipchat == application.hipchat
      type == application.type
      address == application.address
    }

  }

  void "should delete application and global notifications"() {
    given:
    dao.save(HierarchicalLevel.APPLICATION, "my-application", new Notification([
      application: "my-application",
      hipchat: [[
                  "level": "application",
                  "when": [
                    "pipeline.complete",
                    "pipeline.failed"
                  ],
                  "type": "hipchat",
                  "address": "hipchatuser"
                ]]
    ]))

    when:
    def response2 = mockMvc.perform(
      delete("/notifications/application/my-application")
    )

    then:
    response2.andExpect(status().isOk())
    dao.all().isEmpty()
  }
}


class SqlNotificationControllerTck extends NotificationControllerTck {
  def scheduler = Schedulers.from(Executors.newFixedThreadPool(1))

  @AutoCleanup("close")
  SqlTestUtil.TestDatabase currentDatabase = SqlTestUtil.initTcMysqlDatabase()

  void cleanup() {
    SqlTestUtil.cleanupDb(currentDatabase.context)
  }

  @Override
  NotificationDAO createNotificationDAO() {
    def registry = new NoopRegistry()

    def storageService = new SqlStorageService(
      new ObjectMapper(),
      registry,
      currentDatabase.context,
      Clock.systemDefaultZone(),
      new SqlRetryProperties(),
      100,
      "default",
      new Front50SqlProperties()
    )

    return new DefaultNotificationDAO(
      storageService,
      scheduler,
      new DefaultObjectKeyLoader(storageService),
      new StorageServiceConfigurationProperties.PerObjectType(),
      new NoopRegistry(),
      new InMemoryCircuitBreakerRegistry()
    )
  }
}
