package com.netflix.spinnaker.gate.swagger

import com.netflix.spinnaker.gate.Main
import com.netflix.spinnaker.gate.security.GateSystemTest
import com.netflix.spinnaker.gate.security.YamlFileApplicationContextInitializer
import com.netflix.spinnaker.gate.services.internal.IgorService
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification

import org.springframework.http.MediaType

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

@Slf4j
@GateSystemTest
@ContextConfiguration(
  classes = [Main],
  initializers = YamlFileApplicationContextInitializer
)
@TestPropertySource(
  properties = [ "services.kayenta.enabled=true","services.kayenta.canary-config-store=true",
    "spring.application.name=gate" ]) // Enable Controllers we want to document in the spec here.
class GenerateSwaggerSpec extends Specification {

  @Autowired
  WebApplicationContext wac

  @MockBean
  private IgorService igorService

  MockMvc mockMvc

  def setup() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build()
  }

  def "generate and write swagger spec to file"() {
    given:
    Boolean written = false

    when:
    mockMvc.perform(get("/v2/api-docs").accept(MediaType.APPLICATION_JSON))
      .andDo({ result ->
      log.info('Generating swagger spec and writing to "swagger.json".')
      FileUtils.writeStringToFile(new File('swagger.json'), result.getResponse().getContentAsString())
      written = true
    })

    then:
    written
  }
}
