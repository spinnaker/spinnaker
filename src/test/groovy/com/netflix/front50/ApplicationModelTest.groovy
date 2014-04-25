package com.netflix.front50

import com.netflix.front50.exception.NoPrimaryKeyException
import spock.lang.Specification

/**
 * Created by aglover on 4/20/14.
 */
class ApplicationModelTest extends Specification {

    void 'save should result in a newly created application'() {
        def dao = Mock(ApplicationDAO)
        dao.create(_, _) >> new Application(name:"TEST_APP", email:"aglover@netflix.com")

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
        def dao = Mock(ApplicationDAO)

        def application = new Application()
        application.email = 'aglover@netflix.com'
        when:
        application.save()

        then:
        thrown(NoPrimaryKeyException)
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
}
