package com.netflix.kato.deploy.aws.ops

import com.amazonaws.auth.AWSCredentials
import com.netflix.kato.deploy.aws.AutoScalingWorker
import com.netflix.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.kato.deploy.aws.handlers.BasicAmazonDeployHandler
import com.netflix.kato.security.aws.AmazonCredentials
import spock.lang.Specification

class CopyLastAsgAtomicOperationUnitSpec extends Specification {

  void "operation builds description based on ancestor asg"() {
    setup:
      def description = new BasicAmazonDeployDescription(application: "asgard")
      description.availabilityZones = ['us-west-1': []]
      description.credentials = new AmazonCredentials(Mock(AWSCredentials), "baz")
      AutoScalingWorker.metaClass.getAncestorAsg = { [minSize: 1, maxSize: 2, desiredCapacity: 5] }
      List<BasicAmazonDeployDescription> descriptions = []
      BasicAmazonDeployHandler.metaClass.handle = { BasicAmazonDeployDescription desc -> descriptions << desc }

    when:
      new CopyLastAsgAtomicOperation(description).operate([])

    then:
      descriptions
      descriptions.first().capacity.min == 1
      descriptions.first().capacity.max == 2
      descriptions.first().capacity.desired == 5
  }
}
