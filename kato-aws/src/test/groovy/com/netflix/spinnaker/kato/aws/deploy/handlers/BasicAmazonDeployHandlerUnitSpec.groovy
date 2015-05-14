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
import com.amazonaws.services.ec2.model.Image
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.AsgReferenceCopier
import com.netflix.spinnaker.kato.aws.deploy.AutoScalingWorker
import com.netflix.spinnaker.kato.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.kato.aws.deploy.ops.loadbalancer.UpsertAmazonLoadBalancerResult
import com.netflix.spinnaker.kato.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.kato.aws.model.AmazonInstanceClassBlockDevice
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
    "test", "test", null, null, null, null, null, null, null, null
  )

  @Shared
  Task task = Mock(Task)

  AmazonEC2 amazonEC2

  List<AmazonBlockDevice> blockDevices

  def setup() {
    amazonEC2 = Mock(AmazonEC2)
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAutoScaling(_, _) >> Mock(AmazonAutoScaling)
      getAmazonEC2(_, _) >> amazonEC2
    }

    amazonEC2.describeImages(_) >> new DescribeImagesResult().withImages(new Image().withImageId("ami-12345"))
    this.blockDevices = [new AmazonBlockDevice(deviceName: "/dev/sdb", virtualName: "ephemeral0")]
    def rspf = Stub(RegionScopedProviderFactory) {
      forRegion(_, _) >> Stub(RegionScopedProviderFactory.RegionScopedProvider)
    }
    def defaults = new KatoAWSConfig.DeployDefaults(iamRole: 'IamRole', instanceClassBlockDevices: [new AmazonInstanceClassBlockDevice(instanceClass: "m3", blockDevices: this.blockDevices)])
    def credsRepo = new MapBackedAccountCredentialsRepository()
    credsRepo.save('baz', TestCredential.named('baz'))
    this.handler = new BasicAmazonDeployHandler([], mockAmazonClientProvider, rspf, credsRepo, defaults)
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
  }

  void "should resolve amiId from amiName"() {
    setup:
    def deployCallCounts = 0
    AutoScalingWorker.metaClass.deploy = { deployCallCounts++; "foo" }

    def description = new BasicAmazonDeployDescription(amiName: "the-greatest-ami-in-the-world", availabilityZones: ['us-west-1': []])
    description.credentials = TestCredential.named('baz')

    when:
    def results = handler.handle(description, [])

    then:
    1 * amazonEC2.describeImages(_) >> { DescribeImagesRequest req ->
      assert req.filters.size() == 1
      assert req.filters.first().name == 'name'
      assert req.filters.first().values == ['the-greatest-ami-in-the-world']

      return new DescribeImagesResult().withImages(new Image().withImageId('ami-12345'))
    }

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
    }

    def amazonClientProvider = Mock(AmazonClientProvider) {
      1 * getAutoScaling(_, _) >> {
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
      sourceRegionScopedProvider, amazonClientProvider, "sourceAsg", description
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
}
