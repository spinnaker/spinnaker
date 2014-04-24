package com.netflix.front50

import spock.lang.Specification

/**
 * Created by aglover on 4/20/14.
 */
class ApplicationModelTest extends Specification {

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
