package com.netflix.kato.deploy.aws

import com.netflix.kato.deploy.aws.description.ShrinkClusterDescription
import com.netflix.kato.deploy.aws.ops.ShrinkClusterAtomicOperation
import com.netflix.kato.security.NamedAccountCredentials
import com.netflix.kato.security.NamedAccountCredentialsHolder
import spock.lang.Shared
import spock.lang.Specification

class ShrinkClusterAtomicOperationConverterUnitSpec extends Specification {

  @Shared
  ShrinkClusterAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new ShrinkClusterAtomicOperationConverter()
    def namedAccountCredentialsHolder = Mock(NamedAccountCredentialsHolder)
    def mockCredentials = Mock(NamedAccountCredentials)
    namedAccountCredentialsHolder.getCredentials(_) >> mockCredentials
    converter.namedAccountCredentialsHolder = namedAccountCredentialsHolder
  }

  void "shrinkClusterDescription type returns ShrinkClusterDescription and ShrinkClusterAtomicOperation"() {
    setup:
      def input = [application: "asgard", clusterName: "asgard-test", regions: ["us-west-1"], credentials: "test"]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof ShrinkClusterDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof ShrinkClusterAtomicOperation
  }
}
