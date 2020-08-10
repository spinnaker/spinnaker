/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.front50.model.application

import com.netflix.spinnaker.front50.ServiceAccountsService
import com.netflix.spinnaker.front50.events.ApplicationEventListener
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import com.netflix.spinnaker.front50.exception.ValidationException
import com.netflix.spinnaker.front50.model.notification.HierarchicalLevel
import com.netflix.spinnaker.front50.model.notification.NotificationDAO
import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO
import com.netflix.spinnaker.front50.model.project.Project
import com.netflix.spinnaker.front50.model.project.ProjectDAO
import com.netflix.spinnaker.front50.validator.ApplicationValidator
import com.netflix.spinnaker.front50.validator.HasNameValidator
import spock.lang.Specification
import spock.lang.Subject

class ApplicationServiceSpec extends Specification {

  ApplicationDAO applicationDAO = Mock()
  ProjectDAO projectDAO = Mock()
  NotificationDAO notificationDAO = Mock()
  PipelineDAO pipelineDAO = Mock()
  PipelineStrategyDAO pipelineStrategyDAO = Mock()

  @Subject
  ApplicationService subject = new Fixture().get()

  def "save should validate model"() {
    given:
    ApplicationService subject = new Fixture(
      validators: [new HasNameValidator()]
    ).get()

    when:
    subject.save(new Application())

    then:
    thrown(ValidationException)

    when:
    subject.save(new Application(name: 'ok'))

    then:
    noExceptionThrown()
    1 * applicationDAO.update("OK", _)
  }

  def "save should merge properties from existing record"() {
    when:
    Application result = subject.save(new Application(name: "foo"))

    then:
    1 * applicationDAO.findByName("FOO") >> new Application(
      name: "foo",
      email: "foo@example.com"
    )
    1 * applicationDAO.update("FOO", _)
    result.email == "foo@example.com"
  }

  def "save should merge details from existing record"() {
    when:
    Application result = subject.save(new Application(name: "foo", details: [one: "new"]))

    then:
    1 * applicationDAO.findByName("FOO") >> new Application(
      name: "foo",
      details: [
        one: "one",
        two: "old"
      ]
    )
    1 * applicationDAO.update("FOO", _)
    result.details() == [one: "new", two: "old"]
  }

  def "replace should not merge details from existing record"() {
    when:
    Application result = subject.replace(new Application(name: "foo", details: [one: "new"]))

    then:
    1 * applicationDAO.findByName("FOO") >> new Application(
      name: "foo",
      details: [
        one: "one",
        two: "old"
      ]
    )
    1 * applicationDAO.update("FOO", _)
    result.details() == [one: "new"]
  }

  def "save should invoke pre and post event listeners"() {
    given:
    ApplicationEventListener listener = Mock()
    ApplicationService subject = new Fixture(
      listeners: [listener]
    ).get()

    when:
    subject.save(new Application(name: "foo"))

    then:
    1 * applicationDAO.findByName("FOO") >> { throw new NotFoundException("app not found") }
    1 * listener.supports(ApplicationEventListener.Type.PRE_CREATE) >> true
    1 * listener.supports(ApplicationEventListener.Type.POST_CREATE) >> true
    1 * listener.accept(_)
    1 * applicationDAO.update("FOO", _)
    1 * listener.accept(_)
  }

  void 'should remove application references from projects and delete project if empty'() {
    given:
    def application = new Application(name: 'app1')
    def projects = [
      new Project(id: "1", config: new Project.ProjectConfig(applications: ['app1', 'app2'], clusters: [])),
      new Project(id: "2", config: new Project.ProjectConfig(applications: ['app1', 'app2'], clusters: [
        new Project.ClusterConfig(applications: ['app2', 'app1']),
        new Project.ClusterConfig(applications: ['app2']),
        new Project.ClusterConfig(applications: ['app1'])])
      ),
      new Project(id: "3", config: new Project.ProjectConfig(applications: ['app2', 'app3'], clusters: [])),
      new Project(id: "4", config: new Project.ProjectConfig(applications: ['app2', 'app3'], clusters: [
        new Project.ClusterConfig(applications: ['app2', 'app3'])])
      ),
      new Project(id: "5", config: new Project.ProjectConfig(applications: ['app1', 'app2'], clusters: null)),
      new Project(id: "6", config: new Project.ProjectConfig(applications: ['app1'], clusters: []))
    ]

    when:
    subject.delete("app1")

    then:
    1 * applicationDAO.findByName('APP1') >> application
    1 * applicationDAO.delete('APP1')
    1 * projectDAO.all() >> projects
    1 * projectDAO.update("1", { it.config.applications == ['app2'] && it.config.clusters == []})
    1 * projectDAO.update("2", { it.config.applications == ['app2'] && it.config.clusters.applications == [ ['app2'], ['app2'], [] ]})
    1 * projectDAO.update("5", { it.config.applications == ['app2'] && it.config.clusters == null})
    1 * projectDAO.delete("6")
    1 * notificationDAO.delete(HierarchicalLevel.APPLICATION, "app1")
    1 * pipelineDAO.getPipelinesByApplication("app1") >> []
    1 * pipelineStrategyDAO.getPipelinesByApplication("app1") >> []
    0 * _
  }

  void 'should delete all pipeline, managed service account and strategy configs when deleting an application'() {
    given:
    ServiceAccountsService serviceAccountsService = Mock()
    ApplicationService subject = new Fixture(
      serviceAccountsService: serviceAccountsService
    ).get()

    and:
    def application = new Application(name: 'app1')

    when:
    subject.delete("app1")

    then:
    1 * applicationDAO.findByName('APP1') >> application
    1 * applicationDAO.delete('APP1')
    1 * pipelineDAO.getPipelinesByApplication("app1") >> [new Pipeline(id: 'a'), new Pipeline(id: 'b')]
    1 * pipelineDAO.delete('a')
    1 * pipelineDAO.delete('b')
    1 * projectDAO.all() >> []
    1 * notificationDAO.delete(HierarchicalLevel.APPLICATION, "app1")
    1 * pipelineStrategyDAO.getPipelinesByApplication(_) >> [ new Pipeline(id: 'a') ]
    1 * pipelineStrategyDAO.delete('a')
    1 * serviceAccountsService.deleteManagedServiceAccounts(['a', 'b'])
    0 * _
  }

  def "delete should invoke pre and post event listeners"() {
    given:
    ApplicationEventListener listener = Mock()
    ApplicationService subject = new Fixture(
      listeners: [listener]
    ).get()

    when:
    subject.delete("foo")

    then:
    1 * applicationDAO.findByName("FOO") >> { new Application(name: "foo") }
    1 * listener.supports(ApplicationEventListener.Type.PRE_DELETE) >> true
    1 * listener.supports(ApplicationEventListener.Type.POST_DELETE) >> true
    1 * listener.accept(_)
    1 * projectDAO.all() >> []
    1 * pipelineDAO.getPipelinesByApplication(_) >> []
    1 * pipelineStrategyDAO.getPipelinesByApplication(_) >> []
    1 * applicationDAO.delete("FOO")
    1 * listener.accept(_)
  }

  def "findByName should not throw"() {
    when:
    def result = subject.findByName("foo")

    then:
    noExceptionThrown()
    1 * applicationDAO.findByName(_) >> { throw new NotFoundException("ugh") }
    result == null
  }

  private class Fixture {

    private ApplicationService subject
    private Collection<ApplicationValidator> validators
    private Collection<ApplicationEventListener> listeners
    private ServiceAccountsService serviceAccountsService

    ApplicationService get() {
      subject = new ApplicationService(
        applicationDAO,
        projectDAO,
        notificationDAO,
        pipelineDAO,
        pipelineStrategyDAO,
        validators ?: [],
        listeners ?: [],
        Optional.ofNullable(serviceAccountsService)
      )
    }
  }

  private class ListenToAll implements ApplicationEventListener {

    @Override
    boolean supports(Type type) {
      return true
    }

    @Override
    void accept(ApplicationModelEvent event) {

    }
  }
}
