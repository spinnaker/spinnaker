package com.netflix.front50

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.front50.exception.NotFoundException
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Created by aglover on 4/18/14.
 */
class Front50ControllerTest extends Specification {
    MockMvc mockMvc
    Front50Controller controller

    void setup() {
        this.controller = new Front50Controller()
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    void 'a post w/a new application should yeild a success'() {
        def sampleApp = new Application("SAMPLEAPP", "Standalone App", "web@netflix.com", "Kevin McEntee",
                "netflix.com application", "Standalone Application", null, null, null, null)
        def application = new Application()

        def dao = Mock(ApplicationDAO)
        dao.create(_,_) >> sampleApp
        application.dao = dao

        this.controller.application = application

        when:
        def response = mockMvc.perform(post("/name/SAMPLEAPP").
                contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(sampleApp)))

        then:
        response.andExpect status().isOk()
        response.andExpect content().string(new ObjectMapper().writeValueAsString(sampleApp))
    }

    void 'a get w/a name should return a JSON document for the found app'() {
        def sampleApp = new Application("SAMPLEAPP", "Standalone App", "web@netflix.com", "Kevin McEntee",
                "netflix.com application", "Standalone Application", null, null, "1265752693581l", "1265752693581l")
        def application = Mock(Application)
        application.findByName("SAMPLEAPP") >> sampleApp
        this.controller.application = application
        def response = mockMvc.perform(get("/name/SAMPLEAPP"))

        expect:
        response.andExpect status().isOk()
        response.andExpect content().string(new ObjectMapper().writeValueAsString(sampleApp))
    }

    void 'a get w/a invalid name should return 404'() {
        def application = Mock(Application)
        application.findByName(_) >> { throw new NotFoundException("not found!") }
        this.controller.application = application
        def response = mockMvc.perform(get("/name/blah"))

        expect:
        response.andExpect status().is(404)
    }

    void 'index should return a list of applications'() {
        def sampleApps = [new Application("SAMPLEAPP", "Standalone App", "web@netflix.com", "Kevin McEntee",
                "netflix.com application", "Standalone Application", null, null, "1265752693581l", "1265752693581l"),
                          new Application("SAMPLEAPP-2", "Standalone App", "web@netflix.com", "Kevin McEntee",
                                  "netflix.com application", "Standalone Application", null, null, "1265752693581l", "1265752693581l")]
        def application = Mock(Application)
        application.findAll() >> sampleApps
        this.controller.application = application

        when:
        def response = mockMvc.perform(get("/"))

        then:
        response.andExpect status().isOk()
        response.andExpect content().string(new ObjectMapper().writeValueAsString(sampleApps))
    }

}
