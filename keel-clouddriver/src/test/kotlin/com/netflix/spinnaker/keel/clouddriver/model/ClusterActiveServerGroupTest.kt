package com.netflix.spinnaker.keel.clouddriver.model

import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.retrofit.model.ModelParsingTestSupport

object ClusterActiveServerGroupTest : ModelParsingTestSupport<CloudDriverService, ClusterActiveServerGroup>(CloudDriverService::class.java) {

  override val json = javaClass.getResource("/cluster.json")

  override suspend fun CloudDriverService.call(): ClusterActiveServerGroup? =
    this.activeServerGroup("keel@spinnaker", "keel", "mgmttest", "keel-test", "eu-west-1", "aws")

  override val expected = ClusterActiveServerGroup(
    name = "fletch_test-v000",
    cloudProvider = "aws",
    accountName = "test",
    targetGroups = emptySet(),
    region = "eu-west-1",
    zones = setOf("eu-west-1b", "eu-west-1c", "eu-west-1a"),
    image = ClusterImage(
      imageId = "ami-05a878bfa321e03b4",
      appVersion = "mimirdemo-3.16.0-h205.121d4ac"
    ),
    launchConfig = LaunchConfig(
      ramdiskId = "",
      ebsOptimized = false,
      imageId = "ami-05a878bfa321e03b4",
      instanceType = "t2.nano",
      keyName = "nf-test-keypair-a",
      iamInstanceProfile = "fletch_testInstanceProfile",
      instanceMonitoring = InstanceMonitoring(false)
    ),
    asg = AutoScalingGroup(
      autoScalingGroupName = "fletch_test-v000",
      defaultCooldown = 10,
      healthCheckType = "EC2",
      healthCheckGracePeriod = 600,
      suspendedProcesses = emptySet(),
      enabledMetrics = emptySet(),
      tags = setOf(Tag("spinnaker:application", "fletch_test")),
      terminationPolicies = setOf("Default"),
      vpczoneIdentifier = "subnet-c378eba6,subnet-15e45f62,subnet-08994551"
    ),
    vpcId = "vpc-98b413fd",
    loadBalancers = emptySet(),
    capacity = ServerGroupCapacity(1, 1, 1),
    securityGroups = setOf("sg-01be6e67944355aef", "sg-3a0c495f", "sg-3b0c495e"),
    moniker = Moniker(
      app = "fletch_test",
      cluster = "fletch_test",
      sequence = "0"
    )
  )
}
