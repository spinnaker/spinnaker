/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.bluespar.kato.deploy.aws.ops

import com.netflix.bluespar.kato.deploy.aws.AutoScalingWorker
import com.netflix.bluespar.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.bluespar.kato.deploy.aws.handlers.BasicAmazonDeployHandler
import com.netflix.bluespar.kato.deploy.aws.userdata.UserDataProvider
import com.netflix.bluespar.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class CopyLastAsgAtomicOperation implements AtomicOperation<Void> {

  @Autowired
  List<UserDataProvider> userDataProviders

  @Autowired
  BasicAmazonDeployHandler basicAmazonDeployHandler

  final BasicAmazonDeployDescription description

  CopyLastAsgAtomicOperation(BasicAmazonDeployDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    for (Map.Entry<String, List<String>> entry : description.availabilityZones) {
      String region = entry.key

      def ancestorAsg = getAncestorAsg(region)
      BasicAmazonDeployDescription newDescription = description.clone()
      newDescription.capacity.min = ancestorAsg.minSize
      newDescription.capacity.max = ancestorAsg.maxSize
      newDescription.capacity.desired = ancestorAsg.desiredCapacity

      basicAmazonDeployHandler.handle(description, priorOutputs)
    }

    null
  }

  Map getAncestorAsg(String region) {
    def autoScalingWorker = new AutoScalingWorker(application: description.application, region: region,
      environment: description.credentials.environment)
    autoScalingWorker.ancestorAsg
  }
}
