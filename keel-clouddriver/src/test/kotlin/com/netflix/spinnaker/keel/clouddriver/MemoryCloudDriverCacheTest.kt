package com.netflix.spinnaker.keel.clouddriver

import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spinnaker.hamkrest.shouldEqual
import com.netflix.spinnaker.hamkrest.shouldThrow
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.jupiter.api.Test

object MemoryCloudDriverCacheTest {

  val cloudDriver = mock<CloudDriverService>()
  val subject = MemoryCloudDriverCache(cloudDriver)

  val securityGroups = setOf(
    SecurityGroup("aws", "sg-1", "foo", null, "prod", "us-west-2", "vpc-1", emptyList(), Moniker("covfefe")),
    SecurityGroup("aws", "sg-2", "foo", null, "prod", "us-east-1", "vpc-1", emptyList(), Moniker("covfefe")),
    SecurityGroup("aws", "sg-3", "bar", null, "prod", "us-west-2", "vpc-1", emptyList(), Moniker("covfefe"))
  )

  val vpcs = setOf(
    Network("aws", "vpc-1", "vpcName", "prod", "us-west-2"),
    Network("aws", "vpc-2", "vpcName", "test", "us-west-2"),
    Network("aws", "vpc-3", "vpcName", "prod", "us-east-1"),
    Network("aws", "vpc-4", "otherName", "prod", "us-west-2")
  )

  val subnets = setOf(
    Subnet("1", "vpc-1", "prod", "us-west-2", "us-west-2a"),
    Subnet("2", "vpc-1", "prod", "us-west-2", "us-west-2b"),
    Subnet("3", "vpc-1", "prod", "us-west-2", "us-west-2c"),
    Subnet("4", "vpc-1", "prod", "us-west-1", "us-west-1a"),
    Subnet("5", "vpc-1", "prod", "us-west-1", "us-west-1b"),
    Subnet("6", "vpc-1", "prod", "us-west-1", "us-west-1c"),
    Subnet("7", "vpc-2", "test", "us-west-2", "us-west-2a"),
    Subnet("8", "vpc-2", "test", "us-west-2", "us-west-2b"),
    Subnet("9", "vpc-2", "test", "us-west-2", "us-west-2c"),
    Subnet("a", "vpc-3", "prod", "us-west-2", "us-west-2a"),
    Subnet("b", "vpc-3", "prod", "us-west-2", "us-west-2b"),
    Subnet("c", "vpc-3", "prod", "us-west-2", "us-west-2c")
  )

  @Test
  fun `security groups are looked up from CloudDriver`() {
    whenever(cloudDriver.getSecurityGroups("prod")) doReturn securityGroups

    subject.securityGroupBy("prod", "sg-2").let { securityGroup ->
      securityGroup.name shouldEqual "foo"
      securityGroup.region shouldEqual "us-east-1"
    }
  }

  @Test
  fun `an invalid security group id throws an exception`() {
    whenever(cloudDriver.getSecurityGroups("prod")) doReturn securityGroups

    val block = { subject.securityGroupBy("prod", "sg-4") }
    block shouldThrow isA<ResourceNotFound>()
  }

  @Test
  fun `VPC networks are looked up by id from CloudDriver`() {
    whenever(cloudDriver.listNetworks()) doReturn mapOf("aws" to vpcs)

    subject.networkBy("vpc-2").let { vpc ->
      vpc.name shouldEqual "vpcName"
      vpc.account shouldEqual "test"
      vpc.region shouldEqual "us-west-2"
    }
  }

  @Test
  fun `an invalid VPC id throws an exception`() {
    whenever(cloudDriver.listNetworks()) doReturn mapOf("aws" to vpcs)

    val block = { subject.networkBy("vpc-5") }
    block shouldThrow isA<ResourceNotFound>()
  }

  @Test
  fun `VPC networks are looked up by name and region from CloudDriver`() {
    whenever(cloudDriver.listNetworks()) doReturn mapOf("aws" to vpcs)

    subject.networkBy("vpcName", "test", "us-west-2").let { vpc ->
      vpc.id shouldEqual "vpc-2"
    }
  }

  @Test
  fun `an invalid VPC name and region throws an exception`() {
    whenever(cloudDriver.listNetworks()) doReturn mapOf("aws" to vpcs)

    val block = { subject.networkBy("invalid", "prod", "us-west-2") }
    block shouldThrow isA<ResourceNotFound>()
  }

  @Test
  fun `availability zones are looked up by account, VPC id and region from CloudDriver`() {
    whenever(cloudDriver.listSubnets("aws")) doReturn subnets

    subject.availabilityZonesBy("test", "vpc-2", "us-west-2").let { zones ->
      zones shouldEqual setOf("us-west-2a", "us-west-2b", "us-west-2c")
    }
  }

  @Test
  fun `an invalid account, VPC id and region returns an empty set`() {
    whenever(cloudDriver.listNetworks()) doReturn mapOf("aws" to vpcs)

    subject.availabilityZonesBy("test", "vpc-2", "ew-west-1") shouldMatch isEmpty
  }
}
