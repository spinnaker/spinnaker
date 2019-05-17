/*
 * Copyright 2016 Pivotal, Inc.
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

package com.netflix.spinnaker.front50.redis

import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.redis.config.EmbeddedRedisConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@SpringBootTest(classes = [RedisConfig, EmbeddedRedisConfig])
@TestPropertySource(properties = ["spinnaker.redis.enabled = true"])
@DirtiesContext
class RedisApplicationDAOSpec extends Specification {
  @Autowired
  RedisApplicationDAO redisApplicationDAO

  @Shared
  Map<String, String> newApplicationAttrs = [
      name: "new-application", email: "email@netflix.com", description: "My application", pdApiKey: "pdApiKey",
      accounts: "prod,test", repoProjectKey: "project-key", repoSlug: "repo", repoType: "github", trafficGuards: []
  ]

  @Shared
  Application application = new Application([
    name       : "app1",
    description: "My description",
    details: [
      trafficGuards: []
    ]
  ])

  void cleanup() {
    deleteAll()
  }

  void "application name should be based on the 'id' rather than the 'name' attribute"() {
    when:
    def newApplication = redisApplicationDAO.create("MY-APP", new Application(newApplicationAttrs))

    then:
    def foundApplication = redisApplicationDAO.findByName("MY-APP")
    foundApplication.persistedProperties == newApplication.persistedProperties
    foundApplication.name == newApplication.name
    foundApplication.id == foundApplication.name.toLowerCase()
  }

  void "find application by name returns a single application"() {
    when:
    def newApplication = redisApplicationDAO.create(newApplicationAttrs.name, new Application(newApplicationAttrs))

    then:
    def foundApplication = redisApplicationDAO.findByName(newApplicationAttrs.name)
    foundApplication.persistedProperties == newApplication.persistedProperties
  }

  void "find application by name should throw exception when it does not exist"() {
    when:
    redisApplicationDAO.findByName("does-not-exist")

    then:
    thrown(NotFoundException)
  }

  void "all applications can be retrieved"() {
    when:
    def newApplication = redisApplicationDAO.create(newApplicationAttrs.name, new Application(newApplicationAttrs))


    then:
    redisApplicationDAO.all()*.persistedProperties == [newApplication]*.persistedProperties
  }

  void "find all applications should throw exception when no applications exist"() {
    when:
    redisApplicationDAO.all()

    then:
    thrown(NotFoundException)
  }

  void "applications can be deleted"() {
    when:
    redisApplicationDAO.create(newApplicationAttrs.name, new Application(newApplicationAttrs))
    redisApplicationDAO.delete(newApplicationAttrs.name)
    redisApplicationDAO.findByName(newApplicationAttrs.name)

    then:
    thrown(NotFoundException)
  }

  void "applications can be case-insensitively searched for by one or more attributes"() {
    given:
    def newApplication = redisApplicationDAO.create(newApplicationAttrs.name, new Application(newApplicationAttrs))

    when:
    def foundApplications = redisApplicationDAO.search([name: newApplicationAttrs.name])

    then:
    foundApplications*.persistedProperties == [newApplication]*.persistedProperties

    when:
    foundApplications = redisApplicationDAO.search([email: newApplicationAttrs.email.toUpperCase()])

    then:
    foundApplications*.persistedProperties == [newApplication]*.persistedProperties

    when:
    def result = redisApplicationDAO.search([name: newApplicationAttrs.name, group: "does not exist"])

    then:
    result.isEmpty()

    when:
    foundApplications = redisApplicationDAO.search([name: "app"])

    then:
    foundApplications*.persistedProperties == [newApplication]*.persistedProperties
  }

  @Unroll
  void "application '#attribute' can be updated"() {
    given:
    def newApplication = redisApplicationDAO.create(newApplicationAttrs.name, new Application(newApplicationAttrs))

    when:
    newApplication."${attribute}" = value
    redisApplicationDAO.update(newApplicationAttrs.name, newApplication)

    then:
    def foundApplication = redisApplicationDAO.findByName(newApplicationAttrs.name)
    foundApplication."${attribute}" == value

    where:
    attribute                        | value
    "email"                          | "updated@netflix.com"
    "description"                    | null
    "cloudProviders"                 | "aws,titan"
  }

  @Unroll
  void "dynamic attribute '#attribute' can be updated"() {
    given:
    deleteAll()
    def newApplication = redisApplicationDAO.create(newApplicationAttrs.name, new Application(newApplicationAttrs))

    when:
    newApplication."${attribute}" = value
    redisApplicationDAO.update(newApplicationAttrs.name, newApplication)

    then:
    def foundApplication = redisApplicationDAO.findByName(newApplicationAttrs.name)
    foundApplication.details()."${attribute}" == value

    where:
    attribute                        | value
    "pdApiKey"                       | "another pdApiKey"
    "repoProjectKey"                 | "project-key"
    "repoSlug"                       | "repo"
    "repoType"                       | "github"
    "platformHealthOnly"             | "true"
    "platformHealthOnlyShowOverride" | "false"
    "adHocField"                     | "postHocValidation"
//    "someMap"                        | [ key1: "a", key2: 2, nested: [ subkey: "33", something: true]] // TODO Handle maps within maps -> https://jira.spring.io/browse/DATAREDIS-489?focusedCommentId=128546&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-128546
  }

  void "should support create"() {
    given:
    redisApplicationDAO.create("app1", application)

    when:
    def apps = redisApplicationDAO.all()

    then:
    apps.size() == 1
    apps[0].applicationEventListeners == null
    apps[0].createTs == application.createTs
    apps[0].dao == application.dao
    apps[0].description == application.description
    apps[0].details == application.details
    apps[0].email == application.email
    apps[0].log == application.log
    apps[0].name == application.name
    apps[0].updateTs == application.updateTs
    apps[0].validators == null
  }

  void "should support find by name"() {
    given:
    redisApplicationDAO.create("app1", application)

    when:
    def foundApp = redisApplicationDAO.findByName("app1")

    then:
    foundApp.applicationEventListeners == null
    foundApp.createTs == application.createTs
    foundApp.dao == application.dao
    foundApp.description == application.description
    foundApp.details == application.details
    foundApp.email == application.email
    foundApp.log == application.log
    foundApp.name == application.name
    foundApp.updateTs == application.updateTs
    foundApp.validators == null
  }

  void "should support update"() {
    given:
    redisApplicationDAO.create("app1", application)
    def foundApp = redisApplicationDAO.findByName("app1")


    when:
    application.email = 'updated@example.com'
    foundApp.email = application.email
    redisApplicationDAO.update(foundApp.id, foundApp)
    def updatedProject = redisApplicationDAO.findById(foundApp.id)

    then:
    updatedProject.id == application.id
    updatedProject.createTs == application.createTs
    updatedProject.email == application.email
    updatedProject.lastModified > application.lastModified ?: 0
    updatedProject.updateTs > application.updateTs ?: 0
  }

  void "should support delete"() {
    given:
    redisApplicationDAO.create("app1", application)

    when:
    redisApplicationDAO.delete(application.id)
    redisApplicationDAO.findByName(application.name)

    then:
    thrown(NotFoundException)
  }

  void "should fail on fetching non-existent app"() {
    when:
    redisApplicationDAO.findById("doesn't exist")

    then:
    thrown(NotFoundException)
  }

  void "should support bulk behaviors"() {
    when:
    redisApplicationDAO.bulkImport([
      new Application([
        name       : "app1",
        email: "greg@example.com"
      ]),
      new Application([
        name       : "app2",
        email: "mark@example.com"
      ])
    ])

    then:
    def allApps = redisApplicationDAO.all()
    allApps.size() == 2
    allApps.collect {it.name}.containsAll(['APP1', 'APP2'])
    allApps.collect {it.email}.containsAll(['greg@example.com', 'mark@example.com'])

    expect:
    redisApplicationDAO.healthy
  }


  void deleteAll() {
    redisApplicationDAO.redisTemplate.delete(RedisApplicationDAO.BOOK_KEEPING_KEY)
  }
}
