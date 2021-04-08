/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.model

import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import org.junit.jupiter.api.BeforeEach
import spock.lang.Specification
import spock.lang.Unroll

class AmazonServerGroupSpec extends Specification {

  ServerGroup serverGroup

  @BeforeEach
  def setup() {
    serverGroup = new AmazonServerGroup()
  }

  @Unroll
  void "getting instance status counts for all #state instances"() {
    given:
      serverGroup.instances = [buildAmazonInstance(state)]

    when:
      ServerGroup.InstanceCounts counts =  serverGroup.getInstanceCounts()

    then:
      counts.total == 1
      counts.up == (state == HealthState.Up ? 1 : 0)
      counts.down == (state == HealthState.Down ? 1 : 0)
      counts.unknown == (state == HealthState.Unknown ? 1 : 0)
      counts.starting == (state == HealthState.Starting ? 1 : 0)
      counts.outOfService == (state == HealthState.OutOfService ? 1 : 0)

    where:
      state                     | _
      HealthState.Up            | _
      HealthState.Down          | _
      HealthState.Unknown       | _
      HealthState.Starting      | _
      HealthState.OutOfService  | _
  }

  void 'server group capacity should use min/max/desired values from asg map'() {
    when:
    AmazonServerGroup amazonServerGroup = new AmazonServerGroup(asg: [minSize: minSize, desiredCapacity: desiredCapacity, maxSize: maxSize])

    then:
    amazonServerGroup.capacity != null
    amazonServerGroup.capacity.min == expectedMin
    amazonServerGroup.capacity.desired == expectedDesired
    amazonServerGroup.capacity.max == expectedMax

    where:
    minSize | desiredCapacity | maxSize || expectedMin | expectedDesired | expectedMax
    0       | 0               | 0       || 0           | 0               | 0
    1       | 2               | 3       || 1           | 2               | 3
  }

  Instance buildAmazonInstance(HealthState state) {
    def instance = Mock(AmazonInstance)
    instance.getHealthState() >> state
    instance
  }

  void 'server group instance type is extracted as expected for asg with launch configuration'() {
    when:
    serverGroup.launchConfig = [
      application: "app",
      createdTime: 1612794814579,
      imageId: "ami-1",
      instanceType: "some.type.1",
      launchConfigurationARN: "arn:aws:autoscaling:us-east-1:00000000:launchConfiguration:000-000-000:launchConfigurationName/app-stack-v000-000",
      launchConfigurationName: "app-stack-v000-000"
    ]

    then:
    serverGroup.getInstanceType() == "some.type.1"
  }

  void 'server group instance type is extracted as expected for asg with launch template'() {
    when:
    serverGroup.launchTemplate = [
      createTime: 1612794814579,
      launchTemplateData: [
        imageId: "ami-1",
        instanceType: "some.type.1",
      ],
      launchTemplateId: "lt-1",
      launchTemplateName: "app-stack-v000-000",
      version: "1"
    ]

    then:
    serverGroup.getInstanceType() == "some.type.1"
  }

  @Unroll
  void 'server group instance type is extracted as expected for asg with mixed instances policy'() {
    when:
    serverGroup.mixedInstancesPolicy = new AmazonServerGroup.MixedInstancesPolicySettings().tap {
      instancesDiversification = [
        onDemandAllocationStrategy: "prioritized",
        onDemandBaseCapacity: 1,
        onDemandPercentageAboveBaseCapacity: 50,
        spotAllocationStrategy: "lowest-price",
        spotInstancePools: 4,
        spotMaxPrice: "1"
      ]
      launchTemplates = [
        [
          createTime: 1612794814579,
          launchTemplateData: [
            imageId: "ami-1",
            instanceType: "some.type.1",
          ],
          launchTemplateId: "lt-1",
          launchTemplateName: "app-stack-v000-000",
          versionNumber: 1,
        ]]
      launchTemplateOverridesForInstanceType = overrides
    }

    then:
    serverGroup.getInstanceType() == expectedInstanceType

    where:
    overrides                                                 || expectedInstanceType
    null                                                      || "some.type.1"
    [[instanceType: "some.type.2", weightedCapacity: "2"],
     [instanceType: "some.type.3", weightedCapacity: "4"]]    || null
    [[instanceType: "some.type.2", weightedCapacity: "2"]]    || null
  }

  void 'server group launch template specification is null for asg with launch configuration'() {
    when:
    serverGroup.asg = [launchConfigurationName: "app-stack-v000-000"]
    def ltSpec = serverGroup.getLaunchTemplateSpecification()

    then:
    ltSpec == null
  }

  void 'server group launch template specification is identified as expected for asg with launch template'() {
    when:
    serverGroup.asg = [launchTemplate: [
        launchTemplateId: "lt-1",
        launchTemplateName: "app-stack-v000-000",
        version: "1"
    ]]
    def ltSpec = serverGroup.getLaunchTemplateSpecification()

    then:
    ltSpec.launchTemplateId == "lt-1"
    ltSpec.launchTemplateName == "app-stack-v000-000"
    ltSpec.version == "1"
  }

  void 'server group launch template specification is identified as expected for asg with mixed instances policy'() {
    when:
    serverGroup.asg = [
        mixedInstancesPolicy: [
          instancesDistribution: [
            onDemandAllocationStrategy: "prioritized",
            onDemandBaseCapacity: 1,
            onDemandPercentageAboveBaseCapacity: 50,
            spotAllocationStrategy: "lowest-price",
            spotInstancePools: 4,
            spotMaxPrice: "1"
          ],
          launchTemplate: [
            launchTemplateSpecification: [
              launchTemplateId: "lt-1",
              launchTemplateName: "app-stack-v000-000",
              version: "1"
            ]
          ]
        ]
      ]
    def ltSpec = serverGroup.getLaunchTemplateSpecification()

    then:
    ltSpec.launchTemplateId == "lt-1"
    ltSpec.launchTemplateName == "app-stack-v000-000"
    ltSpec.version == "1"
  }

  void 'security group is extracted as expected for asg with launch configuration'() {
    when:
    serverGroup.launchConfig = [
      application: "app",
      createdTime: 1612794814579,
      imageId: "ami-1",
      instanceType: "some.type.1",
      launchConfigurationARN: "arn:aws:autoscaling:us-east-1:00000000:launchConfiguration:000-000-000:launchConfigurationName/app-stack-v000-000",
      launchConfigurationName: "app-stack-v000-000",
      securityGroups: ["sg-123"]
    ]

    then:
    serverGroup.getSecurityGroups() == ["sg-123"].toSet()
  }

  @Unroll
  void 'security group is extracted as expected for asg with launch template'() {
    when:
    serverGroup.launchTemplate = [
      createTime: 1612794814579,
      launchTemplateData: [
        imageId: "ami-1",
        instanceType: "some.type.1",
        securityGroupIds: secGroupIds,
        networkInterfaces: networkInterfaceInput
      ],
      launchTemplateId: "lt-1",
      launchTemplateName: "app-stack-v000-000",
      version: "1"
    ]

    then:
    serverGroup.getSecurityGroups() == expectedSecGroupsIds.toSet()

    where:
    secGroupIds   | networkInterfaceInput  || expectedSecGroupsIds
    null          | [[deviceIndex: 0,
                      groups: ["sg-123"]]] ||  ["sg-123"]
    ["sg-123"]    |    null                ||  ["sg-123"]
    null          |    null                ||  []
  }

  @Unroll
  void 'security group is extracted as expected for asg with mixed instances policy'() {
    when:
    serverGroup.mixedInstancesPolicy = new AmazonServerGroup.MixedInstancesPolicySettings().tap {
      allowedInstanceTypes = ["some.type.1"]
      instancesDiversification = [
        onDemandAllocationStrategy: "prioritized",
        onDemandBaseCapacity: 1,
        onDemandPercentageAboveBaseCapacity: 50,
        spotAllocationStrategy: "lowest-price",
        spotInstancePools: 4,
        spotMaxPrice: "1"
      ]
      launchTemplates = [[
        createTime: 1612794814579,
        launchTemplateData: [
          imageId: "ami-1",
          instanceType: "some.type.1",
          securityGroupIds: secGroupIds,
          networkInterfaces: networkInterfaceInput
        ],
        launchTemplateId: "lt-1",
        launchTemplateName: "app-stack-v000-000",
        versionNumber: 1,
      ]]
    }

    then:
    serverGroup.getSecurityGroups() == expectedSecGroupsIds.toSet()

    where:
    secGroupIds   |  networkInterfaceInput  || expectedSecGroupsIds
    null          | [[deviceIndex: 0,
                      groups: ["sg-123"]]]  ||  ["sg-123"]
    ["sg-123"]    |    null                 ||  ["sg-123"]
    null          |    null                 ||  []
  }
}
