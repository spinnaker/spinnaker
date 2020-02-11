package com.netflix.spinnaker.keel.titus.resource

import com.netflix.spinnaker.keel.api.titus.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.api.titus.cluster.TitusServerGroup
import com.netflix.spinnaker.keel.api.titus.cluster.moniker
import com.netflix.spinnaker.keel.clouddriver.model.Placement
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.ServiceJobProcesses
import com.netflix.spinnaker.keel.clouddriver.model.TitusActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.TitusActiveServerGroupImage
import com.netflix.spinnaker.keel.model.parseMoniker
import org.apache.commons.lang3.RandomStringUtils

fun TitusServerGroup.toClouddriverResponse(
  securityGroups: List<SecurityGroupSummary>,
  awsAccount: String
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
      capacity = capacity,
      cloudProvider = CLOUD_PROVIDER,
      moniker = parseMoniker("$name-v$sequence"),
      env = env,
      constraints = constraints,
      migrationPolicy = migrationPolicy,
      serviceJobProcesses = ServiceJobProcesses(),
      tags = emptyMap(),
      resources = resources,
      capacityGroup = moniker.app
    )
  }
