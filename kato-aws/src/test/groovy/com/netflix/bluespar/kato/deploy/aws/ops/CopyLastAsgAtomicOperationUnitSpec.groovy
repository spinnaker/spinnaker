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

import com.amazonaws.auth.AWSCredentials
import com.netflix.bluespar.kato.deploy.DeploymentResult
import com.netflix.bluespar.kato.deploy.aws.AutoScalingWorker
import com.netflix.bluespar.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.bluespar.kato.deploy.aws.handlers.BasicAmazonDeployHandler
import com.netflix.bluespar.kato.security.aws.AmazonCredentials
import com.netflix.bluespar.kato.deploy.aws.ops.CopyLastAsgAtomicOperation
import spock.lang.Specification

class CopyLastAsgAtomicOperationUnitSpec extends Specification {

  void "operation builds description based on ancestor asg"() {
    setup:
    def deployHandler = Mock(BasicAmazonDeployHandler)
    List<BasicAmazonDeployDescription> descriptions = []
    deployHandler.handle(_, _) >> { BasicAmazonDeployDescription desc, _ -> descriptions << desc; new DeploymentResult() }
    def description = new BasicAmazonDeployDescription(application: "asgard")
    description.availabilityZones = ['us-west-1': []]
    description.credentials = new AmazonCredentials(Mock(AWSCredentials), "baz")
    AutoScalingWorker.metaClass.getAncestorAsg = { [minSize: 1, maxSize: 2, desiredCapacity: 5] }
    def op = new CopyLastAsgAtomicOperation(description)
    op.basicAmazonDeployHandler = deployHandler

    when:
    op.operate([])

    then:
    descriptions
    descriptions.first().capacity.min == 1
    descriptions.first().capacity.max == 2
    descriptions.first().capacity.desired == 5
  }
}
