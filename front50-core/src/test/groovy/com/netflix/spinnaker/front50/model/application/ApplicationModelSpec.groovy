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

import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.validator.HasEmailValidator
import com.netflix.spinnaker.front50.validator.HasNameValidator
import spock.lang.Specification

/**
 * Created by aglover on 4/20/14.
 */
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

  void 'should support obtaining list of properties who have values'() {
    def application = new Application()
    application.setName("TEST_APP")
    application.email = 'aglover@netflix.com'
    application.pdApiKey = ''
    application.owner = null
    application.repoProjectKey = "project-key"
    application.repoSlug = "repo"

    def props = application.allSetColumnProperties()

    expect:
    props != null
    props.size() == 5
    props['name'] == 'TEST_APP'
    props['email'] == "aglover@netflix.com"
    props['pdApiKey'] == ''
    props['repoProjectKey'] == "project-key"
    props['repoSlug'] == "repo"
  }

  void 'update should update the underlying model'() {
    def dao = Mock(ApplicationDAO)

    def application = new Application()
    application.dao = dao

    application.setName("TEST_APP")
    application.email = 'aglover@netflix.com'

    when:
    application.update(["email": "cameron@netflix.com"])

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
    application.update(["email": "cameron@netflix.com"])

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

  void 'cannot delete w/o a name'() {
    def dao = Mock(ApplicationDAO)
    def app = new Application()
    app.dao = dao

    when:
    app.delete()

    then:
    thrown(NotFoundException)
  }

  void 'cannot delete an app that does not exist'() {
    def dao = Mock(ApplicationDAO)
    def app = new Application(name: "APP")
    app.dao = dao

    when:
    app.delete()

    then:
    1 * dao.findByName("APP") >> { throw new NotFoundException() }
    thrown(NotFoundException)
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
}
