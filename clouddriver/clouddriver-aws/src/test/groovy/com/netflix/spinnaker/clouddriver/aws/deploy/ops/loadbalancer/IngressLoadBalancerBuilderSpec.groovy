/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer

import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.SecurityGroup
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory
import spock.lang.Specification
import spock.lang.Subject

class IngressLoadBalancerBuilderSpec extends Specification {

  def securityGroupLookup = Mock(SecurityGroupLookupFactory.SecurityGroupLookup)
  def securityGroupLookupFactory = Stub(SecurityGroupLookupFactory) {
    getInstance("us-east-1") >> securityGroupLookup
  }

  def elbSecurityGroup = new SecurityGroup()
    .withVpcId("vpcId")
    .withGroupId("sg-1234")
    .withGroupName("kato-elb")

  def applicationSecurityGroup = new SecurityGroup()
    .withVpcId("vpcId")
    .withGroupId("sg-1111")
    .withGroupName("kato")

  def elbSecurityGroupUpdater = Mock(SecurityGroupLookupFactory.SecurityGroupUpdater)
  def appSecurityGroupUpdater = Mock(SecurityGroupLookupFactory.SecurityGroupUpdater)
  def credentials = TestCredential.named('bar')

  @Subject IngressLoadBalancerBuilder builder = new IngressLoadBalancerBuilder()

  void "should add ingress if not already present"() {
    given:
    Set<Integer> ports = [7001, 8501]

    when:
    builder.ingressApplicationLoadBalancerGroup("kato", "us-east-1", "bar", credentials, "vpcId", ports, securityGroupLookupFactory)

    then:
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato-elb', 'vpcId') >> Optional.of(elbSecurityGroupUpdater)
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato', 'vpcId') >> Optional.of(appSecurityGroupUpdater)
    1 * elbSecurityGroupUpdater.getSecurityGroup() >> elbSecurityGroup
    1 * appSecurityGroupUpdater.getSecurityGroup() >> applicationSecurityGroup
    1 * appSecurityGroupUpdater.addIngress(_) >> {
      def permissions = it[0] as List<IpPermission>
      assert permissions.size() == 2
      assert 7001 in permissions*.fromPort && 8501 in permissions*.fromPort
      assert 7001 in permissions*.toPort && 8501 in permissions*.toPort
      assert elbSecurityGroup.groupId in permissions[0].userIdGroupPairs*.groupId
      assert elbSecurityGroup.groupId in permissions[1].userIdGroupPairs*.groupId
    }
  }

  void "should auto-create application load balancer security group"() {
    given:
    Set<Integer> ports = [7001, 8501]

    when:
    builder.ingressApplicationLoadBalancerGroup("kato", "us-east-1", "bar", credentials, "vpcId", ports, securityGroupLookupFactory)

    then: "an application elb group should be created and ingressed properly"
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato-elb', 'vpcId') >> Optional.empty()
    1 * securityGroupLookup.createSecurityGroup(_) >> elbSecurityGroupUpdater
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato', 'vpcId') >> Optional.of(appSecurityGroupUpdater)
    1 * elbSecurityGroupUpdater.getSecurityGroup() >> elbSecurityGroup
    1 * appSecurityGroupUpdater.getSecurityGroup() >> applicationSecurityGroup
    1 * appSecurityGroupUpdater.addIngress(_) >> {
      def permissions = it[0] as List<IpPermission>
      assert permissions.size() == 2
      assert permissions*.fromPort.sort() == [7001, 8501] && permissions*.toPort.sort() == [7001, 8501]
      assert elbSecurityGroup.groupId in permissions[0].userIdGroupPairs*.groupId
      assert elbSecurityGroup.groupId in permissions[1].userIdGroupPairs*.groupId
    }
  }

  void "should auto-create application load balancer and application security groups"() {
    given:
    Set<Integer> ports = [7001, 8501]

    when:
    builder.ingressApplicationLoadBalancerGroup("kato", "us-east-1", "bar", credentials, "vpcId", ports, securityGroupLookupFactory)

    then:
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato-elb', 'vpcId') >> Optional.empty()
    1 * securityGroupLookup.getSecurityGroupByName('bar', 'kato', 'vpcId') >> Optional.empty()
    1 * securityGroupLookup.createSecurityGroup( { it.name == 'kato-elb'}) >> elbSecurityGroupUpdater
    1 * securityGroupLookup.createSecurityGroup( { it.name == 'kato'}) >> appSecurityGroupUpdater
    1 * elbSecurityGroupUpdater.getSecurityGroup() >> elbSecurityGroup
    1 * appSecurityGroupUpdater.getSecurityGroup() >> applicationSecurityGroup
    1 * appSecurityGroupUpdater.addIngress(_) >> {
      def permissions = it[0] as List<IpPermission>
      assert permissions.size() == 2
      assert permissions*.fromPort.sort() == [7001, 8501] && permissions*.toPort.sort() == [7001, 8501]
      assert elbSecurityGroup.groupId in permissions[0].userIdGroupPairs*.groupId
      assert elbSecurityGroup.groupId in permissions[1].userIdGroupPairs*.groupId
    }

  }

}
