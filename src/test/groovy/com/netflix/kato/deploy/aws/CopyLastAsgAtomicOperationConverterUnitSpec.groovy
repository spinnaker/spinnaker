package com.netflix.kato.deploy.aws

import com.netflix.kato.deploy.aws.converters.CopyLastAsgAtomicOperationConverter
import com.netflix.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.kato.deploy.aws.ops.CopyLastAsgAtomicOperation
import com.netflix.kato.security.NamedAccountCredentials
import com.netflix.kato.security.NamedAccountCredentialsHolder
import spock.lang.Shared
import spock.lang.Specification

class CopyLastAsgAtomicOperationConverterUnitSpec extends Specification {

  @Shared
  CopyLastAsgAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new CopyLastAsgAtomicOperationConverter()
    def namedAccountCredentialsHolder = Mock(NamedAccountCredentialsHolder)
    def mockCredentials = Mock(NamedAccountCredentials)
    namedAccountCredentialsHolder.getCredentials(_) >> mockCredentials
    converter.namedAccountCredentialsHolder = namedAccountCredentialsHolder
  }

  void "copyLastAsgDescription type returns BasicAmazonDeployDescription and CopyLastAsgAtomicOperation"() {
    setup:
      def input = [application: "asgard", amiName: "ami-000", clusterName: "asgard-test", instanceType: "m3.medium",
                   availabilityZones: ["us-west-1": ["us-west-1a"]], credentials: "test"]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof BasicAmazonDeployDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof CopyLastAsgAtomicOperation
  }
}
