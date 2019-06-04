package com.netflix.spinnaker.keel.clouddriver.model

import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.retrofit.model.ModelParsingTestSupport
import kotlinx.coroutines.Deferred

object SecurityGroupTest : ModelParsingTestSupport<CloudDriverService, SecurityGroup>(CloudDriverService::class.java) {
  override val json = javaClass.getResource("/vpc-sg.json")

  override val call: CloudDriverService.() -> Deferred<SecurityGroup?> = {
    getSecurityGroup("account", "type", "name", "region")
  }

  override val expected = SecurityGroup(
    type = "aws",
    id = "sg-bpkkjzva",
    name = "covfefe",
    description = "covfefe",
    accountName = "mgmttest",
    region = "us-west-2",
    vpcId = "vpc-b5y5kcad",
    inboundRules = emptySet(),
    moniker = Moniker(
      app = "covfefe",
      cluster = "covfefe",
      detail = null,
      stack = null,
      sequence = null
    )
  )
}
