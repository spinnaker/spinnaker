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


package com.netflix.spinnaker.front50.model.application

import com.netflix.astyanax.Keyspace
import com.netflix.spinnaker.front50.exception.NotFoundException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@WebAppConfiguration
@ContextConfiguration(classes = [CassandraSetup])
class CassandraApplicationDAOSpec extends Specification {
  @EnableAutoConfiguration(exclude = [GroovyTemplateAutoConfiguration])
  @ComponentScan("com.netflix.spinnaker.front50")
  static class CassandraSetup {}

  @Autowired
  CassandraApplicationDAO cassandraApplicationDAO

  @Autowired
  Keyspace keyspace

  @Shared
  Map<String, String> newApplicationAttrs = [
      name: "new-application", email: "email@netflix.com", description: "My application", pdApiKey: "pdApiKey",
      accounts: "prod,test", repoProjectKey: "project-key", repoSlug: "repo"
  ]

  void setupSpec() {
    System.setProperty('netflix.environment', 'local')
    System.setProperty('spinnaker.cassandra.enabled', 'true')
    System.setProperty('spinnaker.cassandra.embedded', 'true')
    System.setProperty('spinnaker.cassandra.name', 'global')
    System.setProperty('spinnaker.cassandra.cluster', 'spinnaker')
    System.setProperty('spinnaker.cassandra.keyspace', 'front50')
  }

  void cleanup() {
    cassandraApplicationDAO.truncate()
  }

  void "application name should be based on the 'id' rather than the 'name' attribute"() {
    when:
    def newApplication = cassandraApplicationDAO.create("MY-APP", newApplicationAttrs)

    then:
    def foundApplication = cassandraApplicationDAO.findByName("MY-APP")
    foundApplication.allSetColumnProperties() == newApplication.allSetColumnProperties()
  }

  void "find application by name returns a single application"() {
    when:
    def newApplication = cassandraApplicationDAO.create(newApplicationAttrs.name, newApplicationAttrs)

    then:
    def foundApplication = cassandraApplicationDAO.findByName(newApplicationAttrs.name)
    foundApplication.allSetColumnProperties() == newApplication.allSetColumnProperties()
  }

  void "find application by name should throw exception when it does not exist"() {
    when:
    cassandraApplicationDAO.findByName("does-not-exist")

    then:
    thrown(NotFoundException)
  }

  void "all applications can be retrieved"() {
    when:
    def newApplication = cassandraApplicationDAO.create(newApplicationAttrs.name, newApplicationAttrs)

    then:
    cassandraApplicationDAO.all()*.allColumnProperties() == [newApplication]*.allColumnProperties()
  }

  void "find all applications should throw exception when no applications exist"() {
    when:
    cassandraApplicationDAO.all()

    then:
    thrown(NotFoundException)
  }

  void "applications can be deleted"() {
    when:
    cassandraApplicationDAO.create(newApplicationAttrs.name, newApplicationAttrs)
    cassandraApplicationDAO.delete(newApplicationAttrs.name)
    cassandraApplicationDAO.findByName(newApplicationAttrs.name)

    then:
    thrown(NotFoundException)
  }

  void "applications can be case-insensitively searched for by one or more attributes"() {
    given:
    def newApplication = cassandraApplicationDAO.create(newApplicationAttrs.name, newApplicationAttrs)

    when:
    def foundApplications = cassandraApplicationDAO.search([name: newApplicationAttrs.name])

    then:
    foundApplications*.allColumnProperties() == [newApplication]*.allColumnProperties()

    when:
    foundApplications = cassandraApplicationDAO.search([email: newApplicationAttrs.email.toUpperCase()])

    then:
    foundApplications*.allColumnProperties() == [newApplication]*.allColumnProperties()

    when:
    cassandraApplicationDAO.search([name: newApplicationAttrs.name, group: "does not exist"])

    then:
    thrown(NotFoundException)
  }

  @Unroll
  void "applications can be searched for by account"() {
    given:
    cassandraApplicationDAO.create(newApplicationAttrs.name, newApplicationAttrs)
    cassandraApplicationDAO.create("another-app", [
        name: "another-app", email: "another@netflix.com", accounts: "prod,test,extra"
    ])

    when:
    def foundApplications = cassandraApplicationDAO.search([accounts: searchAccounts])

    then:
    foundApplications.size() == size

    where:
    searchAccounts    | size
    "prod"            | 2
    "test"            | 2
    "prod, test  "    | 2
    "prod,test,extra" | 1
  }

  @Unroll
  void "application '#attribute' can be updated"() {
    given:
    def newApplication = cassandraApplicationDAO.create(newApplicationAttrs.name, newApplicationAttrs)

    when:
    newApplication."${attribute}" = value
    cassandraApplicationDAO.update(newApplicationAttrs.name, [(attribute): value])

    then:
    def foundApplication = cassandraApplicationDAO.findByName(newApplicationAttrs.name)
    foundApplication."${attribute}" == value

    where:
    attribute     | value
    "email"       | "updated@netflix.com"
    "description" | null
    "pdApiKey"    | "another pdApiKey"
    "repoProjectKey" | "project-key"
    "repoSlug" | "repo"
  }

  void "application updates should merge with existing details"() {
    given:
    cassandraApplicationDAO.create(name, [name: name, email: email, owner: owner])

    when:
    cassandraApplicationDAO.update(name, [pdApiKey: pdApiKey, repoProjectKey: "project-key", repoSlug: "repo"])

    then:
    def foundApplication = cassandraApplicationDAO.findByName(name)
    foundApplication.email == email
    foundApplication.owner == owner
    foundApplication.pdApiKey == pdApiKey
    foundApplication.repoProjectKey  == "project-key"
    foundApplication.repoSlug == "repo"

    where:
    name = "another-app"
    email = "another@netflix.com"
    owner = "owner"
    pdApiKey = "pdApiKey"
    repoProjectKey = "project-key"
    repoSlug = "repo"
  }
}

