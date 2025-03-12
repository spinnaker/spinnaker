package com.netflix.spinnaker.orca.bakery.api

import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.orca.bakery.config.BakeryConfiguration
import spock.lang.Specification


class BakeRequestSpec extends Specification {

  def "it snakes the other"() {
    given:
    def json = '''\
    {
      "templateFileLocation": "C:/windows/system32",
      "extendedAttributes": {
        "a_snake_attribute": "hiss",
        "aCamelAttribute": "humps"
      }
    }'''.stripIndent()
    def mapper = BakeryConfiguration.bakeryConfiguredObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
    def bakeReq = mapper.readValue(json, BakeRequest)

    when:
    def output = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(bakeReq).trim()

    then:
    output == '''\
    {
      "extended_attributes" : {
        "aCamelAttribute" : "humps",
        "a_snake_attribute" : "hiss"
      },
      "template_file_location" : "C:/windows/system32"
    }'''.stripIndent()
  }

}
