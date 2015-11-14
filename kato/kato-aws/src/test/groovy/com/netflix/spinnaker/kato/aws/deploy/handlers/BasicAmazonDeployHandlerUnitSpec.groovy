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

package com.netflix.spinnaker.kato.aws.deploy.handlers

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.BlockDeviceMapping
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.amazonaws.services.autoscaling.model.Ebs
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeImagesResult
import com.amazonaws.services.ec2.model.DescribeVpcClassicLinkResult
import com.amazonaws.services.ec2.model.Image
import com.amazonaws.services.ec2.model.VpcClassicLink
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.AsgReferenceCopier
import com.netflix.spinnaker.kato.aws.deploy.AutoScalingWorker
import com.netflix.spinnaker.kato.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.kato.aws.deploy.ops.loadbalancer.UpsertAmazonLoadBalancerResult
import com.netflix.spinnaker.kato.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.kato.aws.services.AsgService
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory.RegionScopedProvider
import com.netflix.spinnaker.kato.config.KatoAWSConfig
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class BasicAmazonDeployHandlerUnitSpec extends Specification {

  @Subject
  BasicAmazonDeployHandler handler

  @Shared
  NetflixAmazonCredentials testCredentials = new NetflixAmazonCredentials(
    "test", "test", "test", "test", null, null, null, null, null, null, null, null, null, null, null
  )

  @Shared
  Task task = Mock(Task)

  AmazonEC2 amazonEC2

  List<AmazonBlockDevice> blockDevices

  def setup() {
    amazonEC2 = Mock(AmazonEC2)
    amazonEC2.describeImages(_) >> new DescribeImagesResult().withImages(new Image().withImageId("ami-12345"))
    this.blockDevices = [new AmazonBlockDevice(deviceName: "/dev/sdb", virtualName: "ephemeral0")]
    def rspf = Stub(RegionScopedProviderFactory) {
      forRegion(_, _) >> Stub(RegionScopedProviderFactory.RegionScopedProvider) {
        getAutoScaling() >> Stub(AmazonAutoScaling)
        getAmazonEC2() >> amazonEC2
      }
    }
    def defaults = new KatoAWSConfig.DeployDefaults(iamRole: 'IamRole')
    def credsRepo = new MapBackedAccountCredentialsRepository()
    credsRepo.save('baz', TestCredential.named('baz'))
    this.handler = new BasicAmazonDeployHandler(rspf, credsRepo, defaults)
    Task task = Stub(Task) {
      getResultObjects() >> []
    }
    TaskRepository.threadLocalTask.set(task)
  }

  void "handler supports basic deploy description type"() {
    given:
    def description = new BasicAmazonDeployDescription()

    expect:
    handler.handles description
  }

  void "handler invokes a deploy feature for each specified region"() {
    setup:
    def deployCallCounts = 0
    AutoScalingWorker.metaClass.deploy = { deployCallCounts++; "foo" }
    def description = new BasicAmazonDeployDescription(amiName: "ami-12345")
    description.availabilityZones = ["us-west-1": [], "us-east-1": []]
    description.credentials = TestCredential.named('baz')

    when:
    def results = handler.handle(description, [])

    then:
    2 == deployCallCounts
    results.serverGroupNames == ['us-west-1:foo', 'us-east-1:foo']
    2 * amazonEC2.describeVpcClassicLink() >> new DescribeVpcClassicLinkResult()
  }

  void "load balancer names are derived from prior execution results"() {
    setup:
    def setlbCalls = 0
    AutoScalingWorker.metaClass.deploy = {}
    AutoScalingWorker.metaClass.setLoadBalancers = { setlbCalls++ }
    def description = new BasicAmazonDeployDescription(amiName: "ami-12345")
    description.availabilityZones = ["us-east-1": []]
    description.credentials = TestCredential.named('baz')

    when:
    handler.handle(description, [new UpsertAmazonLoadBalancerResult(loadBalancers: ["us-east-1": new UpsertAmazonLoadBalancerResult.LoadBalancer("lb", "lb1.nflx")])])

    then:
    setlbCalls
    1 * amazonEC2.describeVpcClassicLink() >> new DescribeVpcClassicLinkResult()
  }

  void "should populate classic link VPC Id when classic link is enabled"() {
    setup:
    AutoScalingWorker.metaClass.deploy = {
      assert classicLinkVpcId == "vpc-456"
      "foo"
    }
    def description = new BasicAmazonDeployDescription(amiName: "ami-12345")
    description.availabilityZones = ["us-west-1": []]
    description.credentials = TestCredential.named('baz')

    when:
    handler.handle(description, [])

    then:
    1 * amazonEC2.describeVpcClassicLink() >> new DescribeVpcClassicLinkResult(vpcs: [
      new VpcClassicLink(vpcId: "vpc-123", classicLinkEnabled: false),
      new VpcClassicLink(vpcId: "vpc-456", classicLinkEnabled: true),
      new VpcClassicLink(vpcId: "vpc-789", classicLinkEnabled: false)
      ])
  }

  void "should send instance class block devices to AutoScalingWorker when matched and none are specified"() {
    setup:
    def deployCallCounts = 0
    AutoScalingWorker.metaClass.deploy = { deployCallCounts++; "foo" }
    def setBlockDevices = []
    AutoScalingWorker.metaClass.setBlockDevices = { List<AmazonBlockDevice> blockDevices ->
      setBlockDevices = blockDevices
    }
    def description = new BasicAmazonDeployDescription(amiName: "ami-12345")
    description.instanceType = "m3.medium"
    description.availabilityZones = ["us-west-1": [], "us-east-1": []]
    description.credentials = TestCredential.named('baz')

    when:
    def results = handler.handle(description, [])

    then:
    2 == deployCallCounts
    results.serverGroupNames == ['us-west-1:foo', 'us-east-1:foo']
    setBlockDevices == this.blockDevices
    2 * amazonEC2.describeVpcClassicLink() >> new DescribeVpcClassicLinkResult()
    2 * amazonEC2.describeImages(_) >> new DescribeImagesResult().withImages(new Image().withImageId('ami-12345')
        .withVirtualizationType('hvm'))
  }

  void "should favour explicit description block devices over default config"() {
    setup:
    def deployCallCounts = 0
    AutoScalingWorker.metaClass.deploy = { deployCallCounts++; "foo" }
    List<AmazonBlockDevice> setBlockDevices = []
    AutoScalingWorker.metaClass.setBlockDevices = { List<AmazonBlockDevice> blockDevices ->
      setBlockDevices = blockDevices
    }
    def description = new BasicAmazonDeployDescription(amiName: "ami-12345")
    description.instanceType = "m3.medium"
    description.blockDevices = [new AmazonBlockDevice(deviceName: "/dev/sdb", size: 125)]
    description.availabilityZones = ["us-west-1": [], "us-east-1": []]
    description.credentials = TestCredential.named('baz')

    when:
    def results = handler.handle(description, [])

    then:
    2 == deployCallCounts
    results.serverGroupNames == ['us-west-1:foo', 'us-east-1:foo']
    setBlockDevices.size()
    setBlockDevices == description.blockDevices
    2 * amazonEC2.describeVpcClassicLink() >> new DescribeVpcClassicLinkResult()
    2 * amazonEC2.describeImages(_) >> new DescribeImagesResult().withImages(new Image().withImageId('ami-12345')
        .withVirtualizationType('hvm'))
  }

  void "should resolve amiId from amiName"() {
    setup:
    def deployCallCounts = 0
    AutoScalingWorker.metaClass.deploy = { deployCallCounts++; "foo" }

    def description = new BasicAmazonDeployDescription(amiName: "the-greatest-ami-in-the-world", availabilityZones: ['us-west-1': []])
    description.credentials = TestCredential.named('baz')
    description.instanceType = "m3.medium"

    when:
    def results = handler.handle(description, [])

    then:
    1 * amazonEC2.describeImages(_) >> { DescribeImagesRequest req ->
      assert req.filters.size() == 1
      assert req.filters.first().name == 'name'
      assert req.filters.first().values == ['the-greatest-ami-in-the-world']

      return new DescribeImagesResult().withImages(new Image().withImageId('ami-12345').withVirtualizationType('hvm'))
    }
    1 * amazonEC2.describeVpcClassicLink() >> new DescribeVpcClassicLinkResult()
    deployCallCounts == 1
  }

  @Unroll
  void "should copy spot price and block devices from source provider if not specified explicitly"() {
    given:
    def asgService = Mock(AsgService) {
      (launchConfig ? 1 : 0) * getLaunchConfiguration(_) >> {
        return new LaunchConfiguration()
          .withSpotPrice("OLD_SPOT")
          .withBlockDeviceMappings(new BlockDeviceMapping().withDeviceName("OLD_DEVICE")
        )
      }
    }
    def sourceRegionScopedProvider = Mock(RegionScopedProvider) {
      (launchConfig ? 1 : 0) * getAsgService() >> { return asgService }
      1 * getAutoScaling() >> {
        return Mock(AmazonAutoScaling) {
          1 * describeAutoScalingGroups(_) >> {
            return new DescribeAutoScalingGroupsResult().withAutoScalingGroups(
              new AutoScalingGroup().withLaunchConfigurationName(launchConfig))
          }
        }
      }
    }

    when:
    def targetDescription = handler.copySourceAttributes(
      sourceRegionScopedProvider, "sourceAsg", null, description
    )

    then:
    targetDescription.spotPrice == expectedSpotPrice
    targetDescription.blockDevices*.deviceName == expectedBlockDevices

    where:
    description                                                                                   | launchConfig   || expectedSpotPrice || expectedBlockDevices
    new BasicAmazonDeployDescription()                                                            | "launchConfig" || "OLD_SPOT"        || ["OLD_DEVICE"]
    new BasicAmazonDeployDescription(spotPrice: "SPOT")                                           | "launchConfig" || "SPOT"            || ["OLD_DEVICE"]
    new BasicAmazonDeployDescription(blockDevices: [])                                            | "launchConfig" || "OLD_SPOT"        || []
    new BasicAmazonDeployDescription(blockDevices: [new AmazonBlockDevice(deviceName: "DEVICE")]) | "launchConfig" || "OLD_SPOT"        || ["DEVICE"]
    new BasicAmazonDeployDescription(spotPrice: "SPOT", blockDevices: [])                         | null           || "SPOT"            || []
  }

  void 'should fail if useSourceCapacity requested, and source not available'() {
    given:
    def description = new BasicAmazonDeployDescription(capacity: descriptionCapacity)
    def sourceRegionScopedProvider = null

    when:
    handler.copySourceAttributes(
      sourceRegionScopedProvider, "sourceAsg", useSource, description
    )

    then:
    thrown(IllegalStateException)

    where:
    useSource = true
    descriptionCapacity = new BasicAmazonDeployDescription.Capacity(5, 5, 5)
  }

  void 'should fail if ASG not found and useSourceCapacity requested'() {
    given:
    def description = new BasicAmazonDeployDescription(capacity: descriptionCapacity)
    def sourceRegionScopedProvider = Stub(RegionScopedProvider) {
      getAutoScaling() >> Stub(AmazonAutoScaling) {
        describeAutoScalingGroups(_) >> new DescribeAutoScalingGroupsResult()
      }
    }

    when:
    handler.copySourceAttributes(
      sourceRegionScopedProvider, "sourceAsg", useSource, description
    )

    then:
    thrown(IllegalStateException)

    where:
    useSource = true
    descriptionCapacity = new BasicAmazonDeployDescription.Capacity(5, 5, 5)
  }

  void 'should copy capacity from source if specified'() {
    given:
    def description = new BasicAmazonDeployDescription(capacity: descriptionCapacity)
    def asgService = Stub(AsgService) {
      getLaunchConfiguration(_) >> new LaunchConfiguration()
    }
    def sourceRegionScopedProvider = Stub(RegionScopedProvider) {
      getAsgService() >> asgService
      getAutoScaling() >> Stub(AmazonAutoScaling) {
        describeAutoScalingGroups(_) >> {
          new DescribeAutoScalingGroupsResult().withAutoScalingGroups(
            new AutoScalingGroup()
              .withLaunchConfigurationName('lc')
              .withMinSize(sourceCapacity.min)
              .withMaxSize(sourceCapacity.max)
              .withDesiredCapacity(sourceCapacity.desired)

          )
        }
      }
    }

    when:
    def targetDescription = handler.copySourceAttributes(
      sourceRegionScopedProvider, "sourceAsg", useSource, description
    )

    then:
    targetDescription.capacity == expectedCapacity

    where:
    useSource << [null, false, true]
    descriptionCapacity = new BasicAmazonDeployDescription.Capacity(5, 5, 5)
    sourceCapacity = new BasicAmazonDeployDescription.Capacity(7, 7, 7)
    expectedCapacity = useSource ? sourceCapacity : descriptionCapacity
  }

  @Unroll
  void "should copy scaling policies and scheduled actions"() {
    given:
    def sourceRegionScopedProvider = Mock(RegionScopedProvider) {
      1 * getAsgReferenceCopier(testCredentials, targetRegion) >> {
        return Mock(AsgReferenceCopier) {
          1 * copyScalingPoliciesWithAlarms(task, sourceAsgName, targetAsgName)
          1 * copyScheduledActionsForAsg(task, sourceAsgName, targetAsgName)
        }
      }
    }

    expect:
    handler.copyScalingPoliciesAndScheduledActions(
      task, sourceRegionScopedProvider, testCredentials, sourceAsgName, targetRegion, targetAsgName
    )

    where:
    sourceAsgName | targetRegion | targetAsgName
    "sourceAsg"   | "us-west-1"  | "targetAsg"

  }

  @Unroll
  void "should convert block device mappings to AmazonBlockDevices"() {
    expect:
    handler.convertBlockDevices([sourceDevice]) == [targetDevice]

    where:
    sourceDevice                                                                                        || targetDevice
    new BlockDeviceMapping().withDeviceName("Device1").withVirtualName("virtualName")                   || new AmazonBlockDevice("Device1", "virtualName", null, null, null, null, null)
    new BlockDeviceMapping().withDeviceName("Device1").withEbs(new Ebs().withIops(500))                 || new AmazonBlockDevice("Device1", null, null, null, null, 500, null)
    new BlockDeviceMapping().withDeviceName("Device1").withEbs(new Ebs().withDeleteOnTermination(true)) || new AmazonBlockDevice("Device1", null, null, null, true, null, null)
    new BlockDeviceMapping().withDeviceName("Device1").withEbs(new Ebs().withVolumeSize(1024))          || new AmazonBlockDevice("Device1", null, 1024, null, null, null, null)
    new BlockDeviceMapping().withDeviceName("Device1").withEbs(new Ebs().withVolumeType("volumeType"))  || new AmazonBlockDevice("Device1", null, null, "volumeType", null, null, null)
    new BlockDeviceMapping().withDeviceName("Device1").withEbs(new Ebs().withSnapshotId("snapshotId"))  || new AmazonBlockDevice("Device1", null, null, null, null, null, "snapshotId")
  }

  @Unroll
  void "should throw exception when instance type does not match image virtualization type"() {
    setup:
    def description = new BasicAmazonDeployDescription(amiName: "a-terrible-ami", availabilityZones: ['us-west-1': []])
    description.credentials = TestCredential.named('baz')
    description.instanceType = instanceType

    when:
    handler.handle(description, [])

    then:
    1 * amazonEC2.describeImages(_) >> new DescribeImagesResult().withImages(new Image().withImageId('ami-12345')
        .withVirtualizationType(virtualizationType))
    1 * amazonEC2.describeVpcClassicLink() >> new DescribeVpcClassicLinkResult()
    thrown IllegalArgumentException

    where:
    instanceType  | virtualizationType
    'c1.large'    | 'hvm'
    'r3.xlarge'   | 'paravirtual'
  }

  @Unroll
  void "should not throw exception when instance type matches image virtualization type or is unknown"() {
    setup:
    def description = new BasicAmazonDeployDescription(amiName: "a-cool-ami", availabilityZones: ['us-west-1': []])
    description.credentials = TestCredential.named('baz')
    description.instanceType = instanceType

    when:
    handler.handle(description, [])

    then:
    1 * amazonEC2.describeImages(_) >> new DescribeImagesResult().withImages(new Image().withImageId('ami-12345')
        .withVirtualizationType(virtualizationType))
    1 * amazonEC2.describeVpcClassicLink() >> new DescribeVpcClassicLinkResult()

    where:
    instanceType  | virtualizationType
    'm1.large'    | 'pv'
    'm4.medium'   | 'hvm'
    'c3.large'    | 'hvm'
    'c3.xlarge'   | 'paravirtual'
    'mystery.big' | 'hvm'
    'mystery.big' | 'paravirtual'
    'what.the'    | 'heck'
  }
}
