package com.netflix.spinnaker.front50.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification
import spock.lang.Unroll
import spock.mock.DetachedMockFactory

import javax.servlet.http.HttpServletResponse

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = [PipelineController])
@ContextConfiguration(classes = [TestConfiguration, PipelineController])
class PipelineControllerSpec extends Specification {

  @Autowired
  private MockMvc mockMvc

  @Unroll
  def "should fail to save if application is missing, empty or blank"() {
    given:
    Map pipelineData = [
      name: "some pipeline with no application",
      application: application
    ]

    when:
    HttpServletResponse response = mockMvc
      .perform(
        post("/pipelines")
          .contentType(MediaType.APPLICATION_JSON)
          .content(new ObjectMapper().writeValueAsString(pipelineData))
      )
      .andReturn()
      .response

    then:
    response.status == HttpStatus.UNPROCESSABLE_ENTITY.value()

    where:
    application << [ null, "", "      "]
  }

  @Unroll
  def "should fail to save if pipeline name is missing, empty or blank"() {
    given:
    Map pipelineData = [
      name: name,
      application: "some application"
    ]

    when:
    HttpServletResponse response = mockMvc
      .perform(
        post("/pipelines")
          .contentType(MediaType.APPLICATION_JSON)
          .content(new ObjectMapper().writeValueAsString(pipelineData))
      )
      .andReturn()
      .response

    then:
    response.status == HttpStatus.UNPROCESSABLE_ENTITY.value()

    where:
    name << [ null, "", "      "]
  }

  @Configuration
  private static class TestConfiguration {
      DetachedMockFactory detachedMockFactory = new DetachedMockFactory()
      @Bean
      PipelineDAO pipelineDAO() {
        detachedMockFactory.Stub(PipelineDAO)
      }
  }

}
