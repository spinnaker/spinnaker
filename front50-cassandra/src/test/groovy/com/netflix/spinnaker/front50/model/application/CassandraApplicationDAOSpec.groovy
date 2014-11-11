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
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration
import spock.lang.Shared
import spock.lang.Specification

@WebAppConfiguration
@ContextConfiguration(classes = [CassandraSetup])
class CassandraApplicationDAOSpec extends Specification {
  @EnableAutoConfiguration
  @ComponentScan("com.netflix.spinnaker.front50")
  static class CassandraSetup {}

  @Autowired
  CassandraApplicationDAO cassandraApplicationDAO

  @Autowired
  Keyspace keyspace

  @Shared
  Map<String, String> newApplicationAttrs = [
      name: "new-application", email: "email@netflix.com", description: "My application", pdApiKey: "pdApiKey"
  ]

  void setupSpec() {
    System.setProperty('netflix.environment', 'local')
    System.setProperty('global.cassandra.enabled', 'true')
    System.setProperty('global.cassandra.embedded', 'true')
    System.setProperty('global.cassandra.name', 'global')
    System.setProperty('global.cassandra.cluster', 'spinnaker')
    System.setProperty('global.cassandra.keyspace', 'front50')
  }

  void cleanup() {
    cassandraApplicationDAO.truncate()
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

    then:
    cassandraApplicationDAO.all().size() == 1

    when:
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

  void "applications can be updated"() {
    given:
    def newApplication = cassandraApplicationDAO.create(newApplicationAttrs.name, newApplicationAttrs)

    when:
    newApplication.email = "updated@netflix.com"
    cassandraApplicationDAO.update(newApplicationAttrs.name, newApplication.allSetColumnProperties())

    then:
    def foundApplication = cassandraApplicationDAO.findByName(newApplicationAttrs.name)
    foundApplication.email == newApplication.email
    foundApplication.updateTs != null

    when:
    newApplication.description = null
    cassandraApplicationDAO.update(newApplicationAttrs.name, [description: null])

    then:
    cassandraApplicationDAO.findByName(newApplicationAttrs.name).description == null

    when:
    newApplication.pdApiKey = "another pdApiKey"
    cassandraApplicationDAO.update(newApplicationAttrs.name, [pdApiKey: newApplication.pdApiKey])

    then:
    cassandraApplicationDAO.findByName(newApplicationAttrs.name).pdApiKey == newApplication.pdApiKey
  }
}

