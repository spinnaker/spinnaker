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

package com.netflix.spinnaker.clouddriver.aws.deploy.handlers

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
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing as AmazonELBV1
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerNotFoundException as LBNFEV1
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.config.AwsConfiguration
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.AsgReferenceCopier
import com.netflix.spinnaker.clouddriver.aws.deploy.AutoScalingWorker
import com.netflix.spinnaker.clouddriver.aws.deploy.BlockDeviceConfig
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.LoadBalancerLookupHelper
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.UpsertAmazonLoadBalancerResult
import com.netflix.spinnaker.clouddriver.aws.deploy.scalingpolicy.ScalingPolicyCopier
import com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.clouddriver.aws.model.AmazonServerGroup
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory.RegionScopedProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static BasicAmazonDeployHandler.SUBNET_ID_OVERRIDE_TAG

class BasicAmazonDeployHandlerUnitSpec extends Specification {

  @Subject
  BasicAmazonDeployHandler handler

  @Shared
  NetflixAmazonCredentials testCredentials = TestCredential.named('test')

  @Shared
  DeployDefaults deployDefaults = new DeployDefaults(
    unknownInstanceTypeBlockDevice: new AmazonBlockDevice(deviceName: "/dev/sdb", size: 40)
  )

  @Shared
  BlockDeviceConfig blockDeviceConfig = new BlockDeviceConfig(deployDefaults)

  @Shared
  Task task = Mock(Task)

  AmazonEC2 amazonEC2 = Mock(AmazonEC2)
  AmazonElasticLoadBalancing elbV2 = Mock(AmazonElasticLoadBalancing)
  AmazonELBV1 elbV1 = Mock(AmazonELBV1)
  AwsConfiguration.AmazonServerGroupProvider amazonServerGroupProvider = Mock(AwsConfiguration.AmazonServerGroupProvider)

  List<AmazonBlockDevice> blockDevices

  ScalingPolicyCopier scalingPolicyCopier = Mock(ScalingPolicyCopier)

  def setup() {
    amazonEC2.describeImages(_) >> new DescribeImagesResult().withImages(new Image().withImageId("ami-12345"))
    this.blockDevices = [new AmazonBlockDevice(deviceName: "/dev/sdb", virtualName: "ephemeral0")]
    def rspf = Stub(RegionScopedProviderFactory) {
      forRegion(_, _) >> Stub(RegionScopedProviderFactory.RegionScopedProvider) {
        getAutoScaling() >> Stub(AmazonAutoScaling)
        getAmazonEC2() >> amazonEC2
        getAmazonElasticLoadBalancingV2(_) >> elbV2
        getAmazonElasticLoadBalancing() >> elbV1
      }
    }
    def defaults = new AwsConfiguration.DeployDefaults(iamRole: 'IamRole')
    def credsRepo = new MapBackedAccountCredentialsRepository()
    credsRepo.save('baz', TestCredential.named('baz'))
    this.handler = new BasicAmazonDeployHandler(
      rspf, credsRepo, amazonServerGroupProvider, defaults, scalingPolicyCopier, blockDeviceConfig
    ) {
      @Override
      LoadBalancerLookupHelper loadBalancerLookupHelper() {
        return new LoadBalancerLookupHelper()
      }
    }

    Task task = Stub(Task) {
      getResultObjects() >> []
    }
    TaskRepository.threadLocalTask.set(task)
  }

  def cleanupSpec() {
    AutoScalingWorker.metaClass = null
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

  void "classic load balancer names are derived from prior execution results"() {
    setup:
    def setlbCalls = 0
    AutoScalingWorker.metaClass.deploy = {}
    AutoScalingWorker.metaClass.setClassicLoadBalancers = { setlbCalls++ }
    def description = new BasicAmazonDeployDescription(amiName: "ami-12345")
    description.availabilityZones = ["us-east-1": []]
    description.credentials = TestCredential.named('baz')

    when:
    handler.handle(description, [new UpsertAmazonLoadBalancerResult(loadBalancers: ["us-east-1": new UpsertAmazonLoadBalancerResult.LoadBalancer("lb", "lb1.nflx")])])

    then:
    setlbCalls
    1 * elbV1.describeLoadBalancers(_) >> new DescribeLoadBalancersResult().withLoadBalancerDescriptions(new LoadBalancerDescription().withLoadBalancerName("lb"))
    1 * amazonEC2.describeVpcClassicLink() >> new DescribeVpcClassicLinkResult()
  }

  void "handles classic load balancers"() {

    def classicLbs = []
    AutoScalingWorker.metaClass.setClassicLoadBalancers = { Collection<String> lbs -> classicLbs.addAll(lbs) }
    def description = new BasicAmazonDeployDescription(amiName: "ami-12345", loadBalancers: ["lb"])
    description.availabilityZones = ["us-east-1": []]
    description.credentials = TestCredential.named('baz')

    when:
    handler.handle(description, [])

    then:
    1 * elbV1.describeLoadBalancers(_) >> new DescribeLoadBalancersResult().withLoadBalancerDescriptions(new LoadBalancerDescription().withLoadBalancerName("lb"))
    1 * amazonEC2.describeVpcClassicLink() >> new DescribeVpcClassicLinkResult()

    classicLbs == ['lb']
  }

  void "should store capacity on DeploymentResult"() {
    given:
    def description = new BasicAmazonDeployDescription(
        amiName: "ami-12345",
        capacity: new BasicAmazonDeployDescription.Capacity(min: 1, max: 10, desired: 5),
        availabilityZones: ["us-east-1": []],
        credentials: TestCredential.named('baz')
    )

    when:
    def deploymentResult = handler.handle(description, [])

    then:
    1 * amazonEC2.describeVpcClassicLink() >> new DescribeVpcClassicLinkResult()

    deploymentResult.deployments.size() == 1
    deploymentResult.deployments[0].capacity == new DeploymentResult.Deployment.Capacity(min: 1, max: 10, desired: 5)
  }

  void "handles application load balancers"() {

    def targetGroupARNs = []
    AutoScalingWorker.metaClass.setTargetGroupArns = { Collection<String> arns -> targetGroupARNs.addAll(arns) }
    def description = new BasicAmazonDeployDescription(amiName: "ami-12345", targetGroups: ["tg"])
    description.availabilityZones = ["us-east-1": []]
    description.credentials = TestCredential.named('baz')

    when:
    handler.handle(description, [])

    then:
    1 * elbV2.describeTargetGroups(new DescribeTargetGroupsRequest().withNames("tg")) >> new DescribeTargetGroupsResult().withTargetGroups(new TargetGroup().withTargetGroupArn("arn:lb:targetGroup1"))
    1 * amazonEC2.describeVpcClassicLink() >> new DescribeVpcClassicLinkResult()

    targetGroupARNs == ['arn:lb:targetGroup1']
  }

  void "fails if load balancer name is not in classic load balancer"() {
    def description = new BasicAmazonDeployDescription(amiName: "ami-12345", loadBalancers: ["lb"])
    description.availabilityZones = ["us-east-1": []]
    description.credentials = TestCredential.named('baz')

    when:
    handler.handle(description, [])

    then:
    1 * elbV1.describeLoadBalancers(_) >> { throw new LBNFEV1("not found") }

    thrown(IllegalStateException)

  }

  void "should populate classic link VPC Id when classic link is enabled"() {
    def actualClassicLinkVpcId
    AutoScalingWorker.metaClass.deploy = {
      actualClassicLinkVpcId = classicLinkVpcId
      "foo"
    }
    def description = new BasicAmazonDeployDescription(
      amiName: "ami-12345",
      availabilityZones: ["us-west-1": []],
      credentials: TestCredential.named('baz')
    )

    when:
    handler.handle(description, [])

    then:
    actualClassicLinkVpcId == "vpc-456"
    1 * amazonEC2.describeVpcClassicLink() >> new DescribeVpcClassicLinkResult(vpcs: [
      new VpcClassicLink(vpcId: "vpc-123", classicLinkEnabled: false),
      new VpcClassicLink(vpcId: "vpc-456", classicLinkEnabled: true),
      new VpcClassicLink(vpcId: "vpc-789", classicLinkEnabled: false)
    ])
  }

  void "should not populate classic link VPC Id when there is a subnetType"() {
    def actualClassicLinkVpcId
    AutoScalingWorker.metaClass.deploy = {
      actualClassicLinkVpcId = classicLinkVpcId
      "foo"
    }
    def description = new BasicAmazonDeployDescription(
      amiName: "ami-12345",
      availabilityZones: ["us-west-1": []],
      credentials: TestCredential.named('baz'),
      subnetType: "internal"
    )

    when:
    handler.handle(description, [])

    then:
    actualClassicLinkVpcId == null
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

  @Unroll
  void "should favour ami block device mappings over explicit description block devices and default config, if useAmiBlockDeviceMappings is set"() {
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
    description.useAmiBlockDeviceMappings = useAmiBlockDeviceMappings
    description.availabilityZones = ["us-west-1": [], "us-east-1": []]
    description.credentials = TestCredential.named('baz')

    when:
    def results = handler.handle(description, [])

    then:
    2 == deployCallCounts
    results.serverGroupNames == ['us-west-1:foo', 'us-east-1:foo']
    2 * amazonEC2.describeVpcClassicLink() >> new DescribeVpcClassicLinkResult()
    2 * amazonEC2.describeImages(_) >>
      new DescribeImagesResult()
        .withImages(new Image()
        .withImageId('ami-12345')
        .withBlockDeviceMappings([new BlockDeviceMapping()
                                    .withDeviceName("/dev/sdh")
                                    .withEbs(new Ebs().withVolumeSize(500))])
        .withVirtualizationType('hvm'))
    setBlockDevices == expectedBlockDevices

    where:
    useAmiBlockDeviceMappings | expectedBlockDevices
    true                      | [new AmazonBlockDevice(deviceName: "/dev/sdh", size: 500)]
    false                     | [new AmazonBlockDevice(deviceName: "/dev/sdb", size: 125)]
    null                      | [new AmazonBlockDevice(deviceName: "/dev/sdb", size: 125)]
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
  void "should copy block devices from source provider if not specified explicitly"() {
    given:
    def asgService = Mock(AsgService) {
      (launchConfig ? 1 : 0) * getLaunchConfiguration(_) >> {
        return new LaunchConfiguration()
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
    targetDescription.blockDevices*.deviceName == expectedBlockDevices

    where:
    description                                                                                   | launchConfig   || expectedBlockDevices
    new BasicAmazonDeployDescription()                                                            | "launchConfig" || ["OLD_DEVICE"]
    new BasicAmazonDeployDescription(blockDevices: [])                                            | "launchConfig" || []
    new BasicAmazonDeployDescription(blockDevices: [new AmazonBlockDevice(deviceName: "DEVICE")]) | "launchConfig" || ["DEVICE"]
  }

  @Unroll
  void "should copy subnet ids from source when available and not explicitly specified"() {
    given:
    def regionScopedProvider = new RegionScopedProviderFactory().forRegion(testCredentials, "us-west-2")
    def description = new BasicAmazonDeployDescription(
      subnetIds: subnetIds,
      copySourceCustomBlockDeviceMappings: false,
      tags: [:]
    )

    when:
    handler.copySourceAttributes(regionScopedProvider, "application-v002", false, description)

    then:
    (subnetIds ? 0 : 1) * amazonServerGroupProvider.getServerGroup("test", "us-west-2", "application-v002") >> {
      def sourceServerGroup = new AmazonServerGroup(asg: [:])
      if (tagValue) {
        sourceServerGroup.asg.tags = [[key: SUBNET_ID_OVERRIDE_TAG, value: tagValue]]
      }
      return sourceServerGroup
    }
    0 * _

    description.subnetIds == expectedSubnetIds
    description.tags == (expectedSubnetIds ? [(SUBNET_ID_OVERRIDE_TAG): expectedSubnetIds.join(",")] : [:])

    where:
    subnetIds    | tagValue            || expectedSubnetIds
    null         | null                || null
    null         | "subnet-1,subnet-2" || ["subnet-1", "subnet-2"]
    ["subnet-1"] | "subnet-1,subnet-2" || ["subnet-1"]               // description takes precedence over source asg tag
  }

  @Unroll
  void "copy source block devices #copySourceBlockDevices feature flags"() {
    given:
    if (copySourceBlockDevices != null) {
      description.copySourceCustomBlockDeviceMappings = copySourceBlockDevices
    }
    int expectedCalls = description.copySourceCustomBlockDeviceMappings ? 1 : 0
    def asgService = Mock(AsgService) {
      (expectedCalls) * getLaunchConfiguration(_) >> {
        return new LaunchConfiguration()
          .withBlockDeviceMappings(new BlockDeviceMapping().withDeviceName("OLD_DEVICE")
        )
      }
    }
    def sourceRegionScopedProvider = Mock(RegionScopedProvider) {
      (expectedCalls) * getAsgService() >> { return asgService }
      1 * getAutoScaling() >> {
        return Mock(AmazonAutoScaling) {
          1 * describeAutoScalingGroups(_) >> {
            return new DescribeAutoScalingGroupsResult().withAutoScalingGroups(
              new AutoScalingGroup().withLaunchConfigurationName('foo'))
          }
        }
      }
    }

    when:
    def targetDescription = handler.copySourceAttributes(
      sourceRegionScopedProvider, "sourceAsg", true, description
    )

    then:
    targetDescription.blockDevices?.deviceName == expectedBlockDevices

    where:
    description                        | copySourceBlockDevices || expectedBlockDevices
    new BasicAmazonDeployDescription() | null                   || ["OLD_DEVICE"]
    new BasicAmazonDeployDescription() | true                   || ["OLD_DEVICE"]
    new BasicAmazonDeployDescription() | false                  || null
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
    String sourceRegion = "us-east-1"
    def sourceRegionScopedProvider = Mock(RegionScopedProvider) {
      1 * getAsgReferenceCopier(testCredentials, targetRegion) >> {
        return Mock(AsgReferenceCopier) {
          1 * copyScheduledActionsForAsg(task, sourceAsgName, targetAsgName)
        }
      }
    }

    when:
    handler.copyScalingPoliciesAndScheduledActions(
      task, sourceRegionScopedProvider, testCredentials, testCredentials, sourceAsgName, targetAsgName, sourceRegion, targetRegion
    )

    then:
    1 * scalingPolicyCopier.copyScalingPolicies(task, sourceAsgName, targetAsgName, testCredentials, testCredentials, sourceRegion, targetRegion)

    where:
    sourceAsgName | targetRegion | targetAsgName
    "sourceAsg"   | "us-west-1"  | "targetAsg"

  }

  @Unroll
  void 'should create #numHooksExpected lifecycle hooks'() {
    given:
    def credentials = TestCredential.named('test', [lifecycleHooks: accountLifecycleHooks])

    def description = new BasicAmazonDeployDescription(lifecycleHooks: lifecycleHooks, includeAccountLifecycleHooks: includeAccount)

    when:
    def result = BasicAmazonDeployHandler.getLifecycleHooks(credentials, description)

    then:
    result.size() == numHooksExpected

    where:
    accountLifecycleHooks                                                                                                  | lifecycleHooks                                                                          | includeAccount || numHooksExpected
    []                                                                                                                     | []                                                                                      | true           || 0
    [new AmazonCredentials.LifecycleHook('role-arn', 'target-arn', 'autoscaling:EC2_INSTANCE_LAUNCHING', 3600, 'ABANDON')] | []                                                                                      | true           || 1
    []                                                                                                                     | [new AmazonAsgLifecycleHook(roleARN: 'role-arn', notificationTargetARN: 'target-arn')]  | true           || 1
    [new AmazonCredentials.LifecycleHook('role-arn', 'target-arn', 'autoscaling:EC2_INSTANCE_LAUNCHING', 3600, 'ABANDON')] | [new AmazonAsgLifecycleHook(roleARN: 'role-arn2', notificationTargetARN: 'target-arn')] | true           || 2
    [new AmazonCredentials.LifecycleHook('role-arn', 'target-arn', 'autoscaling:EC2_INSTANCE_LAUNCHING', 3600, 'ABANDON')] | [new AmazonAsgLifecycleHook(roleARN: 'role-arn2', notificationTargetARN: 'target-arn')] | false          || 1
    [new AmazonCredentials.LifecycleHook('role-arn', 'target-arn', 'autoscaling:EC2_INSTANCE_LAUNCHING', 3600, 'ABANDON')] | []                                                                                      | false          || 0
  }

  void 'should raise exception for unsupported Transition'() {
    def credentials = TestCredential.named('test', [
      lifecycleHooks: [new AmazonCredentials.LifecycleHook('arn', 'arn', 'UNSUPPORTED_TRANSITION', 3600, 'ABANDON')]
    ])

    def description = new BasicAmazonDeployDescription(
      includeAccountLifecycleHooks: true
    )

    when:
    BasicAmazonDeployHandler.getLifecycleHooks(credentials, description)

    then:
    thrown(IllegalArgumentException)
  }

  @Unroll
  void "should convert block device mappings to AmazonBlockDevices"() {
    expect:
    handler.convertBlockDevices([sourceDevice]) == [targetDevice]

    where:
    sourceDevice                                                                                                          || targetDevice
    new BlockDeviceMapping().withDeviceName("Device1").withVirtualName("virtualName")                                     || new AmazonBlockDevice("Device1", "virtualName", null, null, null, null, null, null)
    new BlockDeviceMapping().withDeviceName("Device1").withEbs(new Ebs().withIops(500))                                   || new AmazonBlockDevice("Device1", null, null, null, null, 500, null, null)
    new BlockDeviceMapping().withDeviceName("Device1").withEbs(new Ebs().withDeleteOnTermination(true))                   || new AmazonBlockDevice("Device1", null, null, null, true, null, null, null)
    new BlockDeviceMapping().withDeviceName("Device1").withEbs(new Ebs().withVolumeSize(1024))                            || new AmazonBlockDevice("Device1", null, 1024, null, null, null, null, null)
    new BlockDeviceMapping().withDeviceName("Device1").withEbs(new Ebs().withVolumeType("volumeType"))                    || new AmazonBlockDevice("Device1", null, null, "volumeType", null, null, null, null)
    new BlockDeviceMapping().withDeviceName("Device1").withEbs(new Ebs().withSnapshotId("snapshotId"))                    || new AmazonBlockDevice("Device1", null, null, null, null, null, "snapshotId", null)
    new BlockDeviceMapping().withDeviceName("Device1").withEbs(new Ebs())                                                 || new AmazonBlockDevice("Device1", null, null, null, null, null, null, null)

    // if snapshot is not provided, we should set encryption correctly
    new BlockDeviceMapping().withDeviceName("Device1").withEbs(new Ebs().withEncrypted(null))                             || new AmazonBlockDevice("Device1", null, null, null, null, null, null, null)
    new BlockDeviceMapping().withDeviceName("Device1").withEbs(new Ebs().withEncrypted(true))                             || new AmazonBlockDevice("Device1", null, null, null, null, null, null, true)
    new BlockDeviceMapping().withDeviceName("Device1").withEbs(new Ebs().withEncrypted(false))                            || new AmazonBlockDevice("Device1", null, null, null, null, null, null, false)

    // if snapshot is provided, then we should use the snapshot's encryption value
    new BlockDeviceMapping().withDeviceName("Device1").withEbs(new Ebs().withSnapshotId("snap-123").withEncrypted(null))  || new AmazonBlockDevice("Device1", null, null, null, null, null, "snap-123", null)
    new BlockDeviceMapping().withDeviceName("Device1").withEbs(new Ebs().withSnapshotId("snap-123").withEncrypted(true))  || new AmazonBlockDevice("Device1", null, null, null, null, null, "snap-123", null)
    new BlockDeviceMapping().withDeviceName("Device1").withEbs(new Ebs().withSnapshotId("snap-123").withEncrypted(false)) || new AmazonBlockDevice("Device1", null, null, null, null, null, "snap-123", null)
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
    instanceType | virtualizationType
    'c1.large'   | 'hvm'
    'r3.xlarge'  | 'paravirtual'
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

  @Unroll
  void "should regenerate block device mappings if instance type changes"() {
    setup:
    def description = new BasicAmazonDeployDescription(
      instanceType: targetInstanceType,
      blockDevices: descriptionBlockDevices
    )
    def launchConfiguration = new LaunchConfiguration()
      .withInstanceType(sourceInstanceType)
      .withBlockDeviceMappings(sourceBlockDevices?.collect {
      new BlockDeviceMapping().withVirtualName(it.virtualName).withDeviceName(it.deviceName)
    })

    when:
    def blockDeviceMappings = handler.buildBlockDeviceMappings(description, launchConfiguration)

    then:
    convertBlockDeviceMappings(blockDeviceMappings) == convertBlockDeviceMappings(expectedTargetBlockDevices)

    where:
    sourceInstanceType | targetInstanceType | sourceBlockDevices                              | descriptionBlockDevices || expectedTargetBlockDevices
    "c3.xlarge"        | "c4.xlarge"        | bD("c3.xlarge")                                 | bD("c3.xlarge")         || bD("c3.xlarge")                                 // use the explicitly provided block devices even if instance type has changed
    "c3.xlarge"        | "c4.xlarge"        | bD("c3.xlarge")                                 | []                      || []                                              // use the explicitly provided block devices even if an empty list
    "c3.xlarge"        | "c4.xlarge"        | bD("c3.xlarge")                                 | null                    || bD("c4.xlarge")                                 // was using default block devices, continue to use default block devices for targetInstanceType
    "c3.xlarge"        | "c4.xlarge"        | [new AmazonBlockDevice(deviceName: "/dev/xxx")] | null                    || [new AmazonBlockDevice(deviceName: "/dev/xxx")] // custom block devices should be preserved
    "c3.xlarge"        | "r4.100xlarge"     | bD("c3.xlarge")                                 | null                    || [deployDefaults.unknownInstanceTypeBlockDevice] // no mapping for r4.100xlarge, use the default for unknown instance types
  }

  @Unroll
  void "should substitute {{application}} in iamRole"() {
    given:
    def description = new BasicAmazonDeployDescription(application: application, iamRole: iamRole)
    def deployDefaults = new AwsConfiguration.DeployDefaults(iamRole: defaultIamRole)

    expect:
    BasicAmazonDeployHandler.iamRole(description, deployDefaults) == expectedIamRole

    where:
    application | iamRole                  | defaultIamRole           || expectedIamRole
    "app"       | "iamRole"                | "defaultIamRole"         || "iamRole"
    "app"       | null                     | "defaultIamRole"         || "defaultIamRole"
    "app"       | "{{application}}IamRole" | null                     || "appIamRole"
    "app"       | null                     | "{{application}}IamRole" || "appIamRole"
    null        | null                     | "{{application}}IamRole" || "{{application}}IamRole"
  }

  @Unroll
  void "should assign default EBS optimized flag if unset"() {
    expect:
    BasicAmazonDeployHandler.getDefaultEbsOptimizedFlag(instanceType) == expectedFlag

    where:
    instanceType || expectedFlag
    'invalid'    || false
    'm3.medium'  || false
    'm4.large'   || true
  }

  @Unroll
  void "should apply app/stack/detail tags when `addAppStackDetailTags` is enabled"() {
    given:
    def deployDefaults = new DeployDefaults(addAppStackDetailTags: addAppStackDetailTags)
    def description = new BasicAmazonDeployDescription(
      application: application,
      stack: stack,
      freeFormDetails: details,
      tags: initialTags
    )

    expect:
    buildTags("1", "2", "3") == ["spinnaker:application": "1", "spinnaker:stack": "2", "spinnaker:details": "3"]
    buildTags("1", null, "3") == ["spinnaker:application": "1", "spinnaker:details": "3"]
    buildTags("1", null, null) == ["spinnaker:application": "1"]
    buildTags(null, null, null) == [:]

    when:
    def updatedDescription = BasicAmazonDeployHandler.applyAppStackDetailTags(deployDefaults, description)

    then:
    updatedDescription.tags == expectedTags

    where:
    addAppStackDetailTags | application | stack   | details   | initialTags                          || expectedTags
    false                 | "app"       | "stack" | "details" | [foo: "bar"]                         || ["foo": "bar"]
    true                  | "app"       | "stack" | "details" | [foo: "bar"]                         || [foo: "bar"] + buildTags("app", "stack", "details")
    true                  | "app"       | "stack" | "details" | buildTags("1", "2", "3")             || buildTags("app", "stack", "details")    // override any previous app/stack/details tags
    true                  | "app"       | null    | "details" | [:]                                  || buildTags("app", null, "details")       // avoid creating tags with null values
    true                  | "app"       | null    | "details" | buildTags("app", "stack", "details") || buildTags("app", null, "details")       // should remove pre-existing tags if invalid
    true                  | null        | null    | null      | buildTags("app", "stack", "details") || [:]                                     // should remove pre-existing tags if invalid
    true                  | "app"       | null    | null      | [:]                                  || buildTags("app", null, null)
    true                  | null        | null    | null      | [:]                                  || buildTags(null, null, null)
  }

  void "should not copy reserved aws tags"() {
    expect:
    BasicAmazonDeployHandler.cleanTags(tags) == expected

    where:
    tags                       || expected
    null                       || [:]
    [:]                        || [:]
    ["a": "a"]                 || ["a": "a"]
    ["a": "a", "aws:foo": "3"] || ["a": "a"]
  }

  private static Map buildTags(String application, String stack, String details) {
    def tags = [:]
    if (application) {
      tags["spinnaker:application"] = application
    }
    if (stack) {
      tags["spinnaker:stack"] = stack
    }
    if (details) {
      tags["spinnaker:details"] = details
    }
    return tags
  }

  private Collection<AmazonBlockDevice> bD(String instanceType) {
    return blockDeviceConfig.getBlockDevicesForInstanceType(instanceType)
  }

  private Collection<Map> convertBlockDeviceMappings(Collection<AmazonBlockDevice> blockDevices) {
    return blockDevices.collect {
      [deviceName: it.deviceName, virtualName: it.virtualName]
    }.sort { it.deviceName }
  }
}
