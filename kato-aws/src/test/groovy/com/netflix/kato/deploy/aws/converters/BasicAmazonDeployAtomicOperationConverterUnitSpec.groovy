package com.netflix.kato.deploy.aws.converters

import com.netflix.kato.deploy.DeployAtomicOperation
import com.netflix.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.kato.security.NamedAccountCredentials
import com.netflix.kato.security.NamedAccountCredentialsHolder
import spock.lang.Shared
import spock.lang.Specification

class BasicAmazonDeployAtomicOperationConverterUnitSpec extends Specification {

  @Shared
  BasicAmazonDeployAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new BasicAmazonDeployAtomicOperationConverter()
    def namedAccountCredentialsHolder = Mock(NamedAccountCredentialsHolder)
    def mockCredentials = Mock(NamedAccountCredentials)
    namedAccountCredentialsHolder.getCredentials(_) >> mockCredentials
    converter.namedAccountCredentialsHolder = namedAccountCredentialsHolder
  }

  void "basicAmazonDeployDescription type returns BasicAmazonDeployDescription and DeployAtomicOperation"() {
    setup:
      def input = [application: "asgard", amiName: "ami-000", stack: "asgard-test", instanceType: "m3.medium",
                   availabilityZones: ["us-west-1": ["us-west-1a"]], capacity: [min: 1, max: 2, desired: 5],
                   credentials: "test"]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof BasicAmazonDeployDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof DeployAtomicOperation
  }
}
