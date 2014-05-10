package com.netflix.front50

import org.springframework.boot.actuate.endpoint.HealthEndpoint
import org.springframework.boot.actuate.endpoint.mvc.EndpointMvcAdapter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

/**
 * Created by aglover on 5/10/14.
 */
class HealthCheckTest extends Specification {

    MockMvc mockMvc
    HealthCheck healthCheck

    void setup() {
        this.healthCheck = new HealthCheck()
        this.mockMvc = standaloneSetup(new EndpointMvcAdapter(
                new HealthEndpoint(this.healthCheck))).setMessageConverters new MappingJackson2HttpMessageConverter() build()
    }

    void 'health check should return 5xx error if dao is not working'() {
        def application = Mock(SimpleDBDAO)
        this.healthCheck.dao = application

        when:
        def response = mockMvc.perform(get("/health"))

        then:
        response.andExpect status().is5xxServerError()
    }

    void 'health check should return Ok'() {
        def application = Mock(SimpleDBDAO)
        application.isHealthly() >> true
        this.healthCheck.dao = application

        when:
        def response = mockMvc.perform(get("/health"))

        then:
        response.andExpect status().isOk()
    }
}
