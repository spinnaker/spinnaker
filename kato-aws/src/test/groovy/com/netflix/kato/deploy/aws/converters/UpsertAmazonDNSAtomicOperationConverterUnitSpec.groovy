package com.netflix.kato.deploy.aws.converters

import com.netflix.kato.deploy.aws.description.UpsertAmazonDNSDescription
import com.netflix.kato.deploy.aws.ops.dns.UpsertAmazonDNSAtomicOperation
import com.netflix.kato.security.NamedAccountCredentials
import com.netflix.kato.security.NamedAccountCredentialsHolder
import spock.lang.Shared
import spock.lang.Specification

class UpsertAmazonDNSAtomicOperationConverterUnitSpec extends Specification {
  @Shared
  UpsertAmazonDNSAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new UpsertAmazonDNSAtomicOperationConverter()
    def namedAccountCredentialsHolder = Mock(NamedAccountCredentialsHolder)
    def mockCredentials = Mock(NamedAccountCredentials)
    namedAccountCredentialsHolder.getCredentials(_) >> mockCredentials
    converter.namedAccountCredentialsHolder = namedAccountCredentialsHolder
  }

  void "basicAmazonDeployDescription type returns BasicAmazonDeployDescription and DeployAtomicOperation"() {
    setup:
      def input = [type: "CNAME", name: "kato.test.netflix.net.", hostedZoneName: "test.netflix.net.", credentials: "test"]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof UpsertAmazonDNSDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof UpsertAmazonDNSAtomicOperation
  }
}
