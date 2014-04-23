package com.netflix.front50

import spock.lang.Specification

/**
 * Created by aglover on 4/20/14.
 */
class ApplicationModelTest extends Specification {

    void 'find apps by name'() {

        def dao = Mock(ApplicationDAO)
        dao.findByName(_) >> new Application(email: "web@netflix.com")
        Application.dao = dao

        def app = Application.findByName("SAMPLEAPP")

        expect:
        app.email == 'web@netflix.com'
    }

    void 'find all apps'() {
        def dao = Mock(ApplicationDAO)
        dao.all() >> [new Application(email: "web@netflix.com"), new Application(name: "test")]
        Application.dao = dao

        def apps = Application.findAll()

        expect:
        apps != null
        apps.size() == 2
    }
}
