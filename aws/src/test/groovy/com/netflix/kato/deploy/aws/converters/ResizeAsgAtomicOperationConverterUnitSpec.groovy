package com.netflix.kato.deploy.aws.converters

import com.netflix.kato.deploy.aws.description.ResizeAsgDescription
import com.netflix.kato.deploy.aws.ops.ResizeAsgAtomicOperation
import com.netflix.kato.security.NamedAccountCredentials
import com.netflix.kato.security.NamedAccountCredentialsHolder
import spock.lang.Shared
import spock.lang.Specification

class ResizeAsgAtomicOperationConverterUnitSpec extends Specification {
  @Shared
  ResizeAsgAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new ResizeAsgAtomicOperationConverter()
    def namedAccountCredentialsHolder = Mock(NamedAccountCredentialsHolder)
    def mockCredentials = Mock(NamedAccountCredentials)
    namedAccountCredentialsHolder.getCredentials(_) >> mockCredentials
    converter.namedAccountCredentialsHolder = namedAccountCredentialsHolder
  }

  void "shrinkClusterDescription type returns ShrinkClusterDescription and ShrinkClusterAtomicOperation"() {
    setup:
      def input = [asgName: "myasg-stack-v000", regions: ["us-west-1"], credentials: "test"]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof ResizeAsgDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof ResizeAsgAtomicOperation
  }
}
