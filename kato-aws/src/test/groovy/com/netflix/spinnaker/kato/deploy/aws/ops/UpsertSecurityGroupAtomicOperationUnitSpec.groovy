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

package com.netflix.spinnaker.kato.deploy.aws.ops

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.ec2.model.UserIdGroupPair
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.aws.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.kato.deploy.aws.description.UpsertSecurityGroupDescription.SecurityGroupIngress
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class UpsertSecurityGroupAtomicOperationUnitSpec extends Specification {
  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def description = new UpsertSecurityGroupDescription().with {
    name = "foo"
    description = "desc"
    securityGroupIngress = [
      new SecurityGroupIngress().with {
        name = "bar"
        startPort = 111
        endPort = 111
        type = UpsertSecurityGroupDescription.IngressType.tcp
        it
      }
    ]
    it
  }

  @Subject op = new UpsertSecurityGroupAtomicOperation(description)

  @Shared
  AmazonEC2 ec2

  def setup() {
    ec2 = Mock(AmazonEC2)
    def clientProvider = Mock(AmazonClientProvider)
    clientProvider.getAmazonEC2(_, _) >> ec2
    op.amazonClientProvider = clientProvider
  }

  void "non-existent security groups should be created"() {
    when:
    op.operate([])

    then:
    2 * ec2.describeSecurityGroups(_) >>> [null, new DescribeSecurityGroupsResult().withSecurityGroups(Mock(SecurityGroup))]
    1 * ec2.createSecurityGroup(_) >> { CreateSecurityGroupRequest request ->
      assert request.groupName == description.name
    }

    1 * ec2.authorizeSecurityGroupIngress(_)
  }

  void "existing permissions are revoked before new ones are applied"() {
    setup:
    def secGrp = Mock(SecurityGroup)
    def ipPerm = new IpPermission().withToPort(80).withFromPort(80).withUserIdGroupPairs([new UserIdGroupPair().withGroupName("grp")])
    secGrp.getIpPermissions() >> [ipPerm]

    when:
    op.operate([])

    then:
    1 * ec2.describeSecurityGroups(_) >> new DescribeSecurityGroupsResult().withSecurityGroups(secGrp)
    1 * ec2.revokeSecurityGroupIngress(_) >> { RevokeSecurityGroupIngressRequest request ->
      assert request.ipPermissions[0].userIdGroupPairs[0].groupName == "grp"
      assert request.ipPermissions[0].fromPort == 80
      assert request.ipPermissions[0].toPort == 80
    }
    1 * ec2.authorizeSecurityGroupIngress(_) >> { AuthorizeSecurityGroupIngressRequest request ->
      assert request.ipPermissions[0].userIdGroupPairs[0].groupName == "bar"
      assert request.ipPermissions[0].fromPort == 111
      assert request.ipPermissions[0].toPort == 111
    }
  }
}
