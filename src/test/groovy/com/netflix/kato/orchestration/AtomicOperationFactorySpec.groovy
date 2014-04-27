package com.netflix.kato.orchestration

import com.netflix.kato.deploy.DeployAtomicOperation
import com.netflix.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.kato.deploy.aws.description.ShrinkClusterDescription
import com.netflix.kato.deploy.aws.ops.CopyLastAsgAtomicOperation
import com.netflix.kato.deploy.aws.ops.ShrinkClusterAtomicOperation
import com.netflix.kato.security.NamedAccountCredentials
import com.netflix.kato.security.NamedAccountCredentialsHolder
import spock.lang.Shared
import spock.lang.Specification

class AtomicOperationFactorySpec extends Specification {

  @Shared
  NamedAccountCredentialsHolder namedAccountCredentialsHolder

  def setupSpec() {
    namedAccountCredentialsHolder = Mock(NamedAccountCredentialsHolder)
    def mockCredentials = Mock(NamedAccountCredentials)
    namedAccountCredentialsHolder.getCredentials(_) >> mockCredentials
    AtomicOperationFactory.metaClass.'static'.getNamedAccountCredentialsHolder = { namedAccountCredentialsHolder }
  }

  void "basicAmazonDeployDescription type returns BasicAmazonDeployDescription and DeployAtomicOperation"() {
    setup:
      def input = [application: "asgard", amiName: "ami-000", clusterName: "asgard-test", instanceType: "m3.medium",
                   availabilityZones: ["us-west-1": ["us-west-1a"]], capacity: [min: 1, max: 2, desired: 5],
                   credentials: "test"]

    when:
      def description = AtomicOperationFactory.basicAmazonDeployDescription.convertDescription(input)

    then:
      description instanceof BasicAmazonDeployDescription

    when:
      def operation = AtomicOperationFactory.basicAmazonDeployDescription.convertOperation(input)

    then:
      operation instanceof DeployAtomicOperation
  }

  void "copyLastAsgDescription type returns BasicAmazonDeployDescription and CopyLastAsgAtomicOperation"() {
    setup:
      def input = [application: "asgard", amiName: "ami-000", clusterName: "asgard-test", instanceType: "m3.medium",
                   availabilityZones: ["us-west-1": ["us-west-1a"]], credentials: "test"]

    when:
      def description = AtomicOperationFactory.copyLastAsgDeployDescription.convertDescription(input)

    then:
      description instanceof BasicAmazonDeployDescription

    when:
      def operation = AtomicOperationFactory.copyLastAsgDeployDescription.convertOperation(input)

    then:
      operation instanceof CopyLastAsgAtomicOperation
  }

  void "shrinkClusterDescription type returns ShrinkClusterDescription and ShrinkClusterAtomicOperation"() {
    setup:
      def input = [application: "asgard", clusterName: "asgard-test", regions: ["us-west-1"], credentials: "test"]

    when:
      def description = AtomicOperationFactory.shrinkClusterDescription.convertDescription(input)

    then:
      description instanceof ShrinkClusterDescription

    when:
      def operation = AtomicOperationFactory.shrinkClusterDescription.convertOperation(input)

    then:
      operation instanceof ShrinkClusterAtomicOperation
  }
}
