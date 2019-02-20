/*
 * Copyright 2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.redis

import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.project.Project
import com.netflix.spinnaker.front50.redis.config.EmbeddedRedisConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification

@SpringBootTest(classes = [RedisConfig, EmbeddedRedisConfig])
@TestPropertySource(properties = ["spinnaker.redis.enabled = true"])
class RedisProjectDAOSpec extends Specification {
  @Autowired
  RedisProjectDAO redisProjectDAO

  void cleanup() {
    deleteAll()
  }

  def "should support standard create/refresh/findAll/delete behaviors"() {
    given:
    def project = new Project([
        name       : "app1",
        email: "greg@example.com"
    ])
    redisProjectDAO.create("app1", project)

    when:
    def foundProject = redisProjectDAO.findByName("app1")

    then:
    //foundProject == project // TODO: Handle nested types not indexed
    foundProject.id == project.id
    foundProject.createTs == project.createTs
    foundProject.email == project.email
    foundProject.lastModified == project.lastModified
    foundProject.updateTs == project.updateTs

    when:
    def foundProjects = redisProjectDAO.all()

    then:
    //foundProjects == [project] // TODO: Handle nested types not indexed
    foundProjects.size() == 1
    foundProjects[0].id == project.id
    foundProjects[0].createTs == project.createTs
    foundProjects[0].email == project.email
    foundProjects[0].lastModified == project.lastModified
    foundProjects[0].updateTs == project.updateTs

    when:
    project.email = 'updated@example.com'
    foundProject.email = project.email
    redisProjectDAO.update(foundProject.id, foundProject)
    def updatedProject = redisProjectDAO.findById(foundProject.id)

    then:
    updatedProject.id == project.id
    updatedProject.createTs == project.createTs
    updatedProject.email == project.email
    updatedProject.lastModified > project.lastModified ?: 0
    updatedProject.updateTs > project.updateTs ?: 0

    when:
    redisProjectDAO.delete(project.id)
    redisProjectDAO.findByName(project.name)

    then:
    thrown(NotFoundException)

    then:
    redisProjectDAO.all().isEmpty()

    when:
    redisProjectDAO.bulkImport([
        new Project([
            name       : "app1",
            email: "greg@example.com"
        ]),
        new Project([
            name       : "app2",
            email: "mark@example.com"
        ])
    ])

    then:
    def allProjects = redisProjectDAO.all()
    allProjects.size() == 2
    allProjects.collect {it.name}.containsAll(['app1', 'app2'])
    allProjects.collect {it.email}.containsAll(['greg@example.com', 'mark@example.com'])

    expect:
    redisProjectDAO.healthy == true
  }

  def "should fail on fetching non-existent project"() {
    when:
    redisProjectDAO.findById("doesn't exist")

    then:
    thrown(NotFoundException)
  }

  void deleteAll() {
    redisProjectDAO.redisTemplate.delete(RedisProjectDAO.BOOK_KEEPING_KEY)
  }


}
