package com.netflix.spinnaker.keel.clouddriver.model

import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.retrofit.model.ModelParsingTestSupport

object SecurityGroupTest : ModelParsingTestSupport<CloudDriverService, SecurityGroup>(CloudDriverService::class.java) {
  override val json = javaClass.getResource("/vpc-sg.json")

  override suspend fun CloudDriverService.call(): SecurityGroup? =
    getSecurityGroup("keel@spinnaker", "account", "type", "name", "region")

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
      detail = null,
      stack = null,
      sequence = null
    )
  )
}
