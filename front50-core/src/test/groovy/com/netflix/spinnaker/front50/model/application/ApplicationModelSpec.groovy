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

  void 'save should result in an exception is no email is provided'() {
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
    def app = new Application()
    app.name = "TEST_APP"
    app.dao = dao

    when:
    app.delete()

    then:
    1 * dao.delete("TEST_APP")
    1 * dao.findByName("TEST_APP") >> new Application(name: "TEST_APP")
  }

  void 'deleting a non-existent application should no-op'() {
    def dao = Mock(ApplicationDAO)
    def app = new Application(name: "APP")
    app.dao = dao

    when:
    app.delete()

    then:
    1 * dao.findByName("APP") >> { throw new NotFoundException() }
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

  void 'should return empty collection if no apps exist'() {
    def dao = Mock(ApplicationDAO) {
      1 * all() >> { throw new NotFoundException() }
    }

    def apps = new Application(dao: dao).findAll()

    expect:
    apps.isEmpty()
  }

  void 'should return empty collection if no apps match search criteria'() {
    def dao = Mock(ApplicationDAO) {
      1 * search(_) >> { throw new NotFoundException() }
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
}
