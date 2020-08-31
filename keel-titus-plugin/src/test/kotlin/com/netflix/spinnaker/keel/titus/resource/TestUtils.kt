package com.netflix.spinnaker.keel.titus.resource

import com.netflix.spinnaker.keel.api.ec2.ServerGroup.InstanceCounts
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.Capacity
import com.netflix.spinnaker.keel.clouddriver.model.Constraints
import com.netflix.spinnaker.keel.clouddriver.model.MigrationPolicy
import com.netflix.spinnaker.keel.clouddriver.model.Placement
import com.netflix.spinnaker.keel.clouddriver.model.Resources
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.ServiceJobProcesses
import com.netflix.spinnaker.keel.clouddriver.model.TitusActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.TitusActiveServerGroupImage
import com.netflix.spinnaker.keel.core.parseMoniker
import com.netflix.spinnaker.titus.CLOUD_PROVIDER
import com.netflix.spinnaker.titus.moniker
import org.apache.commons.lang3.RandomStringUtils
import com.netflix.spinnaker.keel.clouddriver.model.InstanceCounts as ClouddriverInstanceCounts
import com.netflix.spinnaker.keel.clouddriver.model.TitusServerGroup as ClouddriverTitusServerGroup

fun TitusServerGroup.toClouddriverResponse(
  securityGroups: List<SecurityGroupSummary>,
  awsAccount: String,
  instanceCounts: InstanceCounts = InstanceCounts(1, 1, 0, 0, 0, 0)
): TitusActiveServerGroup =
  RandomStringUtils.randomNumeric(3).padStart(3, '0').let { sequence ->
    TitusActiveServerGroup(
      name = "$name-v$sequence",
      awsAccount = awsAccount,
      placement = Placement(location.account, location.region, emptyList()),
      region = location.region,
      image = TitusActiveServerGroupImage("${container.organization}/${container.image}", "", container.digest),
      iamProfile = moniker.app + "InstanceProfile",
      entryPoint = entryPoint,
      targetGroups = dependencies.targetGroups,
      loadBalancers = dependencies.loadBalancerNames,
      securityGroups = securityGroups.map(SecurityGroupSummary::id).toSet(),
      capacity = capacity.run { Capacity(min, max, desired) },
      cloudProvider = CLOUD_PROVIDER,
      moniker = parseMoniker("$name-v$sequence"),
      env = env,
      constraints = constraints.run { Constraints(hard, soft) },
      migrationPolicy = migrationPolicy.run { MigrationPolicy(type) },
      serviceJobProcesses = ServiceJobProcesses(),
      tags = emptyMap(),
      resources = resources.run { Resources(cpu, disk, gpu, memory, networkMbps) },
      capacityGroup = moniker.app,
      instanceCounts = instanceCounts.run { ClouddriverInstanceCounts(total, up, down, unknown, outOfService, starting) },
      createdTime = 1544656134371
    )
  }

fun TitusServerGroup.toMultiServerGroupResponse(
  securityGroups: List<SecurityGroupSummary>,
  awsAccount: String,
  instanceCounts: InstanceCounts = InstanceCounts(1, 1, 0, 0, 0, 0),
  allEnabled: Boolean = false
): Set<ClouddriverTitusServerGroup> =
  RandomStringUtils.randomNumeric(3).let { sequence ->
    val sequence1 = sequence.padStart(3, '0')
    val sequence2 = (sequence.toInt() + 1).toString().padStart(3, '0')
    val serverGroups = mutableSetOf<ClouddriverTitusServerGroup>()

    val first = ClouddriverTitusServerGroup(
      name = "$name-v$sequence1",
      awsAccount = awsAccount,
      placement = Placement(location.account, location.region, emptyList()),
      region = location.region,
      image = TitusActiveServerGroupImage("${container.organization}/${container.image}", "", container.digest),
      iamProfile = moniker.app + "InstanceProfile",
      entryPoint = entryPoint,
      targetGroups = dependencies.targetGroups,
      loadBalancers = dependencies.loadBalancerNames,
      securityGroups = securityGroups.map(SecurityGroupSummary::id).toSet(),
      capacity = capacity.run { Capacity(min, max, desired) },
      cloudProvider = CLOUD_PROVIDER,
      moniker = parseMoniker("$name-v$sequence1"),
      env = env,
      constraints = constraints.run { Constraints(hard, soft) },
      migrationPolicy = migrationPolicy.run { MigrationPolicy(type) },
      serviceJobProcesses = ServiceJobProcesses(),
      tags = emptyMap(),
      resources = resources.run { Resources(cpu, disk, gpu, memory, networkMbps) },
      capacityGroup = moniker.app,
      instanceCounts = instanceCounts.run { ClouddriverInstanceCounts(total, up, down, unknown, outOfService, starting) },
      createdTime = 1544656134371,
      disabled = !allEnabled
    )
    serverGroups.add(first)

    val second = ClouddriverTitusServerGroup(
      name = "$name-v$sequence2",
      awsAccount = awsAccount,
      placement = Placement(location.account, location.region, emptyList()),
      region = location.region,
      image = TitusActiveServerGroupImage("${container.organization}/${container.image}", "", container.digest),
      iamProfile = moniker.app + "InstanceProfile",
      entryPoint = entryPoint,
      targetGroups = dependencies.targetGroups,
      loadBalancers = dependencies.loadBalancerNames,
      securityGroups = securityGroups.map(SecurityGroupSummary::id).toSet(),
      capacity = capacity.run { Capacity(min, max, desired) },
      cloudProvider = CLOUD_PROVIDER,
      moniker = parseMoniker("$name-v$sequence2"),
      env = env,
      constraints = constraints.run { Constraints(hard, soft) },
      migrationPolicy = migrationPolicy.run { MigrationPolicy(type) },
      serviceJobProcesses = ServiceJobProcesses(),
      tags = emptyMap(),
      resources = resources.run { Resources(cpu, disk, gpu, memory, networkMbps) },
      capacityGroup = moniker.app,
      instanceCounts = instanceCounts.run { ClouddriverInstanceCounts(total, up, down, unknown, outOfService, starting) },
      createdTime = 1544656134371,
      disabled = false
    )

    serverGroups.add(second)
    return serverGroups
  }

fun TitusActiveServerGroup.toAllServerGroupsResponse(
  disabled: Boolean = false
): ClouddriverTitusServerGroup =
  ClouddriverTitusServerGroup(
    name = name,
    awsAccount = awsAccount,
    placement = placement,
    region = region,
    image = image,
    iamProfile = iamProfile,
    entryPoint = entryPoint,
    targetGroups = targetGroups,
    loadBalancers = loadBalancers,
    securityGroups = securityGroups,
    capacity = capacity,
    cloudProvider = cloudProvider,
    moniker = moniker,
    env = env,
    containerAttributes = containerAttributes,
    migrationPolicy = migrationPolicy,
    serviceJobProcesses = serviceJobProcesses,
    constraints = constraints,
    tags = tags,
    resources = resources,
    capacityGroup = capacityGroup,
    instanceCounts = instanceCounts,
    createdTime = createdTime,
    disabled = disabled
  )
