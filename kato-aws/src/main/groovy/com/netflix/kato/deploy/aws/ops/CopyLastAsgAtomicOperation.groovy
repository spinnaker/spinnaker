package com.netflix.kato.deploy.aws.ops

import com.netflix.kato.deploy.aws.AutoScalingWorker
import com.netflix.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.kato.deploy.aws.handlers.BasicAmazonDeployHandler
import com.netflix.kato.deploy.aws.userdata.UserDataProvider
import com.netflix.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class CopyLastAsgAtomicOperation implements AtomicOperation<Void> {

  @Autowired
  List<UserDataProvider> userDataProviders

  final BasicAmazonDeployDescription description

  CopyLastAsgAtomicOperation(BasicAmazonDeployDescription description) {
    this.description = description
  }

  @Override
  Void operate(List _) {
    for (Map.Entry<String, List<String>> entry : description.availabilityZones) {
      String region = entry.key

      def ancestorAsg = getAncestorAsg(region)
      BasicAmazonDeployDescription newDescription = description.clone()
      newDescription.capacity.min = ancestorAsg.minSize
      newDescription.capacity.max = ancestorAsg.maxSize
      newDescription.capacity.desired = ancestorAsg.desiredCapacity

      new BasicAmazonDeployHandler().handle(description)
    }

    null
  }

  Map getAncestorAsg(String region) {
    def autoScalingWorker = new AutoScalingWorker(application: description.application, region: region,
        environment: description.credentials.environment)
    autoScalingWorker.ancestorAsg
  }
}
