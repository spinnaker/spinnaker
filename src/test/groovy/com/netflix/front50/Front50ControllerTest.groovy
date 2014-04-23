package com.netflix.front50

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Created by aglover on 4/18/14.
 */
class Front50ControllerTest extends Specification {
    MockMvc mockMvc
    def app

    void setup() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(new Front50Controller()).build()
        this.app = new Application("SAMPLEAPP", "Standalone App", "web@netflix.com", "Kevin McEntee",
                "netflix.com application", "Standalone Application", null, null, 1265752693581l, 1265752693581l);
    }

    void 'testing spock works'() {
        expect:
        this.mockMvc.perform(get("/")).andExpect(status().isOk())
        this.mockMvc.perform(get("/")).andExpect(content().string(new ObjectMapper().writeValueAsString(this.app)))
    }

    void 'testing spock works again'() {
        def expectedResult = new ObjectMapper().writeValueAsString(this.app)

        when:
        def response = mockMvc.perform(get("/"))

        then:
        response.andExpect status().isOk()
        response.andExpect content().string(expectedResult)
    }

}
