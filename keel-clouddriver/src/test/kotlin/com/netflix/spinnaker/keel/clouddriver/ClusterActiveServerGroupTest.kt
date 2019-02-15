package com.netflix.spinnaker.keel.clouddriver

import com.netflix.spinnaker.keel.clouddriver.model.AutoScalingGroup
import com.netflix.spinnaker.keel.clouddriver.model.BaseModelParsingTest
import com.netflix.spinnaker.keel.clouddriver.model.ClusterActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.InstanceMonitoring
import com.netflix.spinnaker.keel.clouddriver.model.LaunchConfig
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.ServerGroupCapacity
import com.netflix.spinnaker.keel.clouddriver.model.Tag
import kotlinx.coroutines.Deferred

object ClusterActiveServerGroupTest : BaseModelParsingTest<ClusterActiveServerGroup>() {

  override val json = javaClass.getResource("/cluster.json")

  override val call: CloudDriverService.() -> Deferred<ClusterActiveServerGroup?> = {
    activeServerGroup("keel", "mgmttest", "keel-test", "eu-west-1", "aws")
  }

  override val expected = ClusterActiveServerGroup(
    name = "fletch_test-v000",
    accountName = "test",
    targetGroups = emptySet(),
    region = "eu-west-1",
    zones = setOf("eu-west-1b", "eu-west-1c", "eu-west-1a"),
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
