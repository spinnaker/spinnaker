package com.netflix.spinnaker.keel.clouddriver.model

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.kork.exceptions.SystemException
import dev.minutest.ContextBuilder
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectCatching
import strikt.assertions.isA
import strikt.assertions.isFailure
import strikt.assertions.isSuccess


/**
 * Use ContextBuilder so we can use the same test assertions when constructing both ActiveServerGroup and
 * ServerGroup objects
 */

fun<T : BaseEc2ServerGroup> ContextBuilder<FixtureMaker<T>>.checkServerGroupConstructorBehavior() {
  context("constructing subclasses of BaseEc2ServerGroup") {

    test("when neither launch config nor launch template specified, throw an exception") {
      expectCatching { make(launchConfig = null, launchTemplate = null) }
        .isFailure()
        .isA<SystemException>()
    }

    test("when launch config specified, don't throw an exception") {
      expectCatching {
        make(launchConfig = LaunchConfig(
          ramdiskId = null,
          ebsOptimized = true,
          imageId = "image",
          instanceType = "t2.micro",
          keyName = "mykey",
          iamInstanceProfile = "profile",
          instanceMonitoring = InstanceMonitoring(enabled = true)
        ))
      }.isSuccess()
    }

    test("when launch template specified, don't throw an exception") {
      expectCatching {
        make(launchTemplate = LaunchTemplate(
          launchTemplateData = LaunchTemplateData(
            ramDiskId = null,
            ebsOptimized = true,
            imageId = "image",
            instanceType = "t2.micro",
            keyName = "mykey",
            iamInstanceProfile = IamInstanceProfile(name = "profile"),
            monitoring = InstanceMonitoring(enabled = true)
          )))
      }.isSuccess()
    }
  }
}


class ActiveServerGroupTests : JUnit5Minutests {
  fun tests() = rootContext<FixtureMaker<ActiveServerGroup>> {
    fixture { ActiveServerGroupFixtureMaker() } // makes ActiveServerGroup fixtures

    checkServerGroupConstructorBehavior()
  }
}

class ServerGroupTests : JUnit5Minutests {
  fun tests() = rootContext<FixtureMaker<ServerGroup>> {
    fixture { ServerGroupFixtureMaker() } // makes ServerGroup fixtures

    checkServerGroupConstructorBehavior()
  }
}


// Code to instantiate fixture objects


interface FixtureMaker<T : BaseEc2ServerGroup> {
  fun make(launchConfig: LaunchConfig? = null, launchTemplate: LaunchTemplate? = null) : T
}

class ActiveServerGroupFixtureMaker : FixtureMaker<ActiveServerGroup> {
  override fun make(launchConfig: LaunchConfig?, launchTemplate: LaunchTemplate?) =
    ActiveServerGroup(
      launchConfig = launchConfig,
      launchTemplate = launchTemplate,

      // unused boilerplate data
      name = "fnord",
      region = "us-east-1",
      zones = setOf("us-east-1c"),
      image = ActiveServerGroupImage(
        imageId = "image",
        appVersion = null,
        baseImageVersion = null,
        name = "name",
        imageLocation = "somewhere",
        description = null
      ),
      asg = AutoScalingGroup(
        autoScalingGroupName = "asgName",
        defaultCooldown = 0,
        healthCheckType = "known",
        healthCheckGracePeriod = 0,
        suspendedProcesses = emptySet(),
        enabledMetrics = emptySet(),
        tags = emptySet(),
        terminationPolicies = emptySet(),
        vpczoneIdentifier = "vpc-zone"
      ),
      scalingPolicies = emptyList(),
      vpcId = "vpcId",
      targetGroups = emptySet(),
      loadBalancers = emptySet(),
      capacity = Capacity(
        min = 1,
        max = 1,
        desired = 1
      ),
      cloudProvider = "aws",
      securityGroups = emptySet(),
      accountName = "test",
      moniker = Moniker(
        app = "fnord",
        stack = null,
        detail = null,
        sequence = 1
      ),
      instanceCounts = InstanceCounts (
        total = 1,
        up = 1,
        down = 0,
        unknown = 0,
        outOfService = 0,
        starting = 0
      ),
      createdTime = 0
    )
}

class ServerGroupFixtureMaker: FixtureMaker<ServerGroup> {
  override fun make(launchConfig: LaunchConfig?, launchTemplate: LaunchTemplate?) =
    ServerGroup(
      launchConfig = launchConfig,
      launchTemplate = launchTemplate,

      // unused boilerplate data
      disabled = false,
      name = "fnord",
      region = "us-east-1",
      zones = setOf("us-east-1c"),
      image = ActiveServerGroupImage(
        imageId = "image",
        appVersion = null,
        baseImageVersion = null,
        name = "name",
        imageLocation = "somewhere",
        description = null
      ),
      asg = AutoScalingGroup(
        autoScalingGroupName = "asgName",
        defaultCooldown = 0,
        healthCheckType = "known",
        healthCheckGracePeriod = 0,
        suspendedProcesses = emptySet(),
        enabledMetrics = emptySet(),
        tags = emptySet(),
        terminationPolicies = emptySet(),
        vpczoneIdentifier = "vpc-zone"
      ),
      scalingPolicies = emptyList(),
      vpcId = "vpcId",
      targetGroups = emptySet(),
      loadBalancers = emptySet(),
      capacity = Capacity(
        min = 1,
        max = 1,
        desired = 1
      ),
      cloudProvider = "aws",
      securityGroups = emptySet(),
      moniker = Moniker(
        app = "fnord",
        stack = null,
        detail = null,
        sequence = 1
      ),
      instanceCounts = InstanceCounts (
        total = 1,
        up = 1,
        down = 0,
        unknown = 0,
        outOfService = 0,
        starting = 0
      ),
      createdTime = 0
    )
}
