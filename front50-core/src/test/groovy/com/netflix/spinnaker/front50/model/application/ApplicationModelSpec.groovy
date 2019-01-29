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

import com.netflix.spinnaker.front50.events.ApplicationEventListener
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.notification.HierarchicalLevel
import com.netflix.spinnaker.front50.model.notification.NotificationDAO
import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO
import com.netflix.spinnaker.front50.model.project.Project
import com.netflix.spinnaker.front50.model.project.Project.ClusterConfig
import com.netflix.spinnaker.front50.model.project.Project.ProjectConfig
import com.netflix.spinnaker.front50.model.project.ProjectDAO
import com.netflix.spinnaker.front50.validator.HasEmailValidator
import com.netflix.spinnaker.front50.validator.HasNameValidator
import spock.lang.Specification

class ApplicationModelSpec extends Specification {

  void 'from is similar to clone'() {
    def application = new Application()
    application.setName("TEST_APP")
    application.email = 'aglover@netflix.com'

    def app2 = new Application()
    def dao = Mock(ApplicationDAO)
    app2.dao = dao
    app2.initialize(application)

    expect:
    app2.name == "TEST_APP"
    app2.email == "aglover@netflix.com"
    app2.description == null
    app2.dao != null
  }

  void 'clear should clear ONLY column attributes'() {
    def application = new Application()
    application.setName("TEST_APP")
    application.email = 'aglover@netflix.com'
    def dao = Mock(ApplicationDAO)
    application.dao = dao
    application.clear()

    expect:
    application.dao != null
    application.name == null
  }

  void 'should support adding dynamic properties'() {
    def application = new Application()
    application.pdApiKey = ''
    application.owner = null
    application.repoProjectKey = "project-key"
    application.repoSlug = "repo"
    application.repoType = "github"

    def props = application.details()

    expect:
    props != null
    props.size() == 5
    props['pdApiKey'] == ''
    props['owner'] == null
    props['repoProjectKey'] == "project-key"
    props['repoSlug'] == "repo"
    props['repoType'] == 'github'
  }

  void 'update should update the underlying model'() {
    def dao = Mock(ApplicationDAO)

    def application = new Application()
    application.dao = dao

    application.setName("TEST_APP")
    application.email = 'aglover@netflix.com'

    when:
    application.update(new Application(email: "cameron@netflix.com"))

    then:
    notThrown(Exception)
    1 * dao.update("TEST_APP", _)
  }

  void 'update should throw up w/o a name'() {
    def dao = Mock(ApplicationDAO)

    def application = new Application()
    application.dao = dao
    application.validators = [new HasNameValidator()]

    application.email = 'aglover@netflix.com'

    when:
    application.update(new Application(email: "cameron@netflix.com"))

    then:
    thrown(Application.ValidationException)
  }

  void 'save should result in a newly created application'() {
    def dao = Mock(ApplicationDAO)
    dao.create(_, _) >> new Application(name: "TEST_APP", email: "aglover@netflix.com")

    def application = new Application()
    application.dao = dao

    application.setName("TEST_APP")
    application.email = 'aglover@netflix.com'
    def napp = application.save()

    expect:
    napp != null
    napp.name == application.name
    napp.email == application.email
  }

  void 'save should result in an exception is no name is provided'() {
    def application = new Application()
    application.validators = [new HasNameValidator()]

    application.email = 'aglover@netflix.com'
    when:
    application.save()

    then:
    thrown(Application.ValidationException)
  }

  void 'save should result in an exception if no email is provided'() {
    def application = new Application()
    application.validators = [new HasEmailValidator()]

    application.name = 'TEST-APP'
    when:
    application.save()

    then:
    thrown(Application.ValidationException)
  }

  void 'delete should just work'() {
    def dao = Mock(ApplicationDAO)
    def projectDao = Stub(ProjectDAO) {
      all() >> []
    }
    def notificationDao = Mock(NotificationDAO)
    def pipelineDao = Stub(PipelineDAO) {
      getPipelinesByApplication(_, _) >> []
    }
    def pipelineStrategyDao = Stub(PipelineStrategyDAO) {
      getPipelinesByApplication(_) >> []
    }
    def app = new Application()
    app.name = "TEST_APP"
    app.dao = dao
    app.projectDao = projectDao
    app.notificationDao = notificationDao
    app.pipelineDao = pipelineDao
    app.pipelineStrategyDao = pipelineStrategyDao

    when:
    app.delete()

    then:
    1 * dao.delete("TEST_APP")
    1 * dao.findByName("TEST_APP") >> new Application(name: "TEST_APP")
    1 * notificationDao.delete(HierarchicalLevel.APPLICATION, "TEST_APP")
  }

  void 'deleting a non-existent application should no-op'() {
    def dao = Mock(ApplicationDAO)
    def app = new Application(name: "APP")
    app.dao = dao

    when:
    app.delete()

    then:
    1 * dao.findByName("APP") >> { throw new NotFoundException("app does not exist") }
    notThrown(NotFoundException)
  }

  void 'find apps by name'() {
    def dao = Mock(ApplicationDAO)
    dao.findByName(_) >> new Application(email: "web@netflix.com")

    def application = new Application()
    application.dao = dao

    def app = application.findByName("SAMPLEAPP")

    expect:
    app.email == 'web@netflix.com'
  }

  void 'find all apps'() {
    def dao = Mock(ApplicationDAO)
    dao.all() >> [new Application(email: "web@netflix.com"), new Application(name: "test")]

    def application = new Application()
    application.dao = dao

    def apps = application.findAll()

    expect:
    apps != null
    apps.size() == 2
  }

  void 'should convert cloudProviders to a String if it is a list'() {
    def listApp = new Application(cloudProviders: ['a','b'])
    def normalApp = new Application(cloudProviders: 'a,b,c')

    expect:
    listApp.cloudProviders == 'a,b'
    normalApp.cloudProviders == 'a,b,c'
  }

  void 'should properly initialize cloudProviders from either a String or a list'() {
    def listApp = new Application(cloudProviders: ['a','b'])
    def normalApp = new Application(cloudProviders: 'a,b,c')

    def newApp = new Application()

    expect:
    newApp.initialize(listApp).cloudProviders == 'a,b'
    newApp.initialize(normalApp).cloudProviders == 'a,b,c'
  }

  void 'should return empty collection if no apps exist'() {
    def dao = Mock(ApplicationDAO) {
      1 * all() >> { throw new NotFoundException("no apps found") }
    }

    def apps = new Application(dao: dao).findAll()

    expect:
    apps.isEmpty()
  }

  void 'should return empty collection if no apps match search criteria'() {
    def dao = Mock(ApplicationDAO) {
      1 * search(_) >> { throw new NotFoundException("no apps found") }
    }

    def apps = new Application(dao: dao).search([:])

    expect:
    apps.isEmpty()
  }

  void 'should invoke rollback methods on exception'() {
    given:
    def application = new Application()
    def preListener = Mock(ApplicationEventListener)
    def postListener = Mock(ApplicationEventListener)
    def failingPostListener = Mock(ApplicationEventListener)

    def onSuccessInvoked = false
    def onSuccess = { Application original, Application updated ->
      onSuccessInvoked = true
      return application
    }

    def onRollbackInvoked = false
    def onRollback = { Application original, Application updated ->
      onRollbackInvoked = true
      return null
    }

    when:
    application.perform([preListener], [postListener, failingPostListener], onSuccess, onRollback, application, application)

    then:
    thrown(RuntimeException)

    1 * preListener.call(_, _) >> { return application }
    1 * preListener.rollback(_)
    onSuccessInvoked
    onRollbackInvoked
    1 * postListener.call(_, _)
    1 * postListener.rollback(_)
    1 * failingPostListener.call(_, _) >> { throw new IllegalStateException("Expected") }
    0 * _
  }

  void 'should remove application references from projects and delete project if empty'() {
    given:
    def application = new Application(name: 'app1')
    def projects = [
            new Project(id: "1", config: new ProjectConfig(applications: ['app1', 'app2'], clusters: [])),
            new Project(id: "2", config: new ProjectConfig(applications: ['app1', 'app2'], clusters: [
                    new ClusterConfig(applications: ['app2', 'app1']),
                    new ClusterConfig(applications: ['app2']),
                    new ClusterConfig(applications: ['app1'])])
            ),
            new Project(id: "3", config: new ProjectConfig(applications: ['app2', 'app3'], clusters: [])),
            new Project(id: "4", config: new ProjectConfig(applications: ['app2', 'app3'], clusters: [
                    new ClusterConfig(applications: ['app2', 'app3'])])
            ),
            new Project(id: "5", config: new ProjectConfig(applications: ['app1'], clusters: []))
    ]

    ProjectDAO projectDao = Mock(ProjectDAO)
    ApplicationDAO dao = Mock(ApplicationDAO)
    NotificationDAO notificationDao = Stub(NotificationDAO) {
      delete(_) >> []
    }
    PipelineDAO pipelineDao = Stub(PipelineDAO) {
      getPipelinesByApplication(_, _) >> []
    }
    PipelineStrategyDAO pipelineStrategyDao = Stub(PipelineStrategyDAO) {
      getPipelinesByApplication(_) >> []
    }

    application.dao = dao
    application.notificationDao = notificationDao
    application.projectDao = projectDao
    application.pipelineDao = pipelineDao
    application.pipelineStrategyDao = pipelineStrategyDao

    when:
    application.delete()

    then:
    1 * dao.findByName('APP1') >> application
    1 * dao.delete('APP1')
    1 * projectDao.all() >> projects
    1 * projectDao.update("1", { it.config.applications == ['app2'] && it.config.clusters == []})
    1 * projectDao.update("2", { it.config.applications == ['app2'] && it.config.clusters.applications == [ ['app2'], ['app2'], [] ]})
    1 * projectDao.delete("5")
    0 * _
  }

  void 'should delete all pipeline and strategy configs when deleting an application'() {
    given:
    def application = new Application(name: 'app1')
    ProjectDAO projectDao = Stub(ProjectDAO) {
      all() >> []
    }
    ApplicationDAO dao = Mock(ApplicationDAO)
    NotificationDAO notificationDao = Stub(NotificationDAO) {
      delete(_) >> []
    }
    PipelineDAO pipelineDao = Mock(PipelineDAO)
    PipelineStrategyDAO pipelineStrategyDao = Mock(PipelineStrategyDAO)

    application.dao = dao
    application.notificationDao = notificationDao
    application.projectDao = projectDao
    application.pipelineDao = pipelineDao
    application.pipelineStrategyDao = pipelineStrategyDao

    when:
    application.delete()

    then:
    1 * dao.findByName('APP1') >> application
    1 * dao.delete('APP1')
    1 * pipelineDao.getPipelinesByApplication(_, _) >> [new Pipeline(id: 'a'), new Pipeline(id: 'b')]
    1 * pipelineDao.delete('a')
    1 * pipelineDao.delete('b')
    1 * pipelineStrategyDao.getPipelinesByApplication(_) >> [ new Pipeline(id: 'a') ]
    1 * pipelineStrategyDao.delete('a')
    0 * _
  }
}
