package com.netflix.spinnaker.gate.swagger

import com.netflix.spinnaker.gate.Main
import com.netflix.spinnaker.gate.security.GateSystemTest
import com.netflix.spinnaker.gate.security.YamlFileApplicationContextInitializer
import com.netflix.spinnaker.gate.services.internal.IgorService
import groovy.util.logging.Slf4j
import groovy.json.JsonSlurper
import org.apache.commons.io.FileUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
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
  // Enable Controllers we want to document in the spec here.
  properties = ["services.kayenta.enabled=true","services.kayenta.canary-config-store=true",
    "services.keel.enabled=true", "spring.application.name=gate", 'services.fiat.baseUrl=https://localhost', 'services.keel.baseUrl=https://localhost', "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER" ])
class GenerateSwaggerSpec extends Specification {

  @Autowired
  WebApplicationContext wac

  @MockitoBean
  private IgorService igorService

  MockMvc mockMvc

  def setup() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build()
  }

  def "generate and write swagger spec to file"() {
    given:
    Boolean written = false

    when:
    mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
      .andDo({ result ->
      log.info('Generating swagger spec and writing to "swagger.json".')
      FileUtils.writeStringToFile(new File('swagger.json'), result.getResponse().getContentAsString())
      written = true
    })

    then:
    written

    and: 'plugin schemas retain their public required fields'
    def spec = new JsonSlurper().parseText(new File('swagger.json').text)
    spec.openapi.startsWith('3.')
    spec.components.schemas.SpinnakerPluginInfo.required.contains('releases')
    spec.components.schemas.SpinnakerPluginRelease.required.containsAll(['preferred', 'remoteExtensions'])
    spec.components.schemas.SpinnakerPluginDescriptor.required.contains('unsafe')
    spec.components.schemas.RemoteExtensionConfig.required.containsAll(['id', 'transport', 'type'])
    spec.components.schemas.RemoteExtensionTransportConfig.required.contains('http')
    spec.components.schemas.Http.required.containsAll(['config', 'headers', 'queryParams', 'url'])
    spec.components.schemas.Headers.required.containsAll(['invokeHeaders', 'readHeaders', 'writeHeaders'])
    spec.components.schemas.SimpleProxyConfig.required.containsAll(['id', 'uri'])
    spec.components.schemas.DeckPluginVersion.required.containsAll(['id', 'version'])

  }
}
