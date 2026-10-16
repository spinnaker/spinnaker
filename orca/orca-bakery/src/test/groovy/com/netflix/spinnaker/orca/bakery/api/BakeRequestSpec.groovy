package com.netflix.spinnaker.orca.bakery.api

import com.netflix.spinnaker.orca.bakery.config.BakeryConfiguration
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import java.util.HashMap
import tools.jackson.databind.SerializationFeature
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
    def mapper = BakeryConfiguration.bakeryConfiguredObjectMapper()
      .rebuild()
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
      .build()
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

  def "it accepts an empty package artifacts list"() {
    given:
    def mapper = BakeryConfiguration.bakeryConfiguredObjectMapper()
    def requestMap = new HashMap()
    requestMap.put('packageArtifacts', [])

    when:
    def bakeReq = mapper.convertValue(requestMap, BakeRequest)

    then:
    bakeReq.packageArtifacts.empty
  }

  def "it binds bake fields from an orca context map"() {
    given:
    def mapper = OrcaObjectMapper.newInstance()
    def requestMap = [
      user             : 'bran',
      package          : 'hodor',
      baseOs           : 'ubuntu',
      baseLabel        : 'release',
      cloudProviderType: 'aws',
      packageArtifacts : [Artifact.builder().name('hodor').type('deb').build()]
    ]

    when:
    def bakeReq = mapper.convertValue(requestMap, BakeRequest)

    then:
    with(bakeReq) {
      user == 'bran'
      packageName == 'hodor'
      baseOs == 'ubuntu'
      baseLabel == 'release'
      cloudProviderType == BakeRequest.CloudProviderType.aws
      packageArtifacts*.name == ['hodor']
    }
  }

}
