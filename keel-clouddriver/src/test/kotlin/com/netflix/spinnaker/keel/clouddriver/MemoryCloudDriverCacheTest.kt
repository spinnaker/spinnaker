package com.netflix.spinnaker.keel.clouddriver

import com.netflix.spinnaker.keel.clouddriver.model.Credential
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo

object MemoryCloudDriverCacheTest {

  val cloudDriver = mockk<CloudDriverService>()
  val subject = MemoryCloudDriverCache(cloudDriver)

  val securityGroupSummaries = setOf(
    SecurityGroupSummary("foo", "sg-1", "vpc-1"),
    SecurityGroupSummary("bar", "sg-2", "vpc-1")
  )

  val vpcs = setOf(
    Network("aws", "vpc-1", "vpcName", "prod", "us-west-2"),
    Network("aws", "vpc-2", "vpcName", "test", "us-west-2"),
    Network("aws", "vpc-3", "vpcName", "prod", "us-east-1"),
    Network("aws", "vpc-4", "otherName", "prod", "us-west-2")
  )

  val subnets = setOf(
    Subnet("1", "vpc-1", "prod", "us-west-2", "us-west-2a", null),
    Subnet("2", "vpc-1", "prod", "us-west-2", "us-west-2b", null),
    Subnet("3", "vpc-1", "prod", "us-west-2", "us-west-2c", null),
    Subnet("4", "vpc-1", "prod", "us-west-1", "us-west-1a", null),
    Subnet("5", "vpc-1", "prod", "us-west-1", "us-west-1b", null),
    Subnet("6", "vpc-1", "prod", "us-west-1", "us-west-1c", null),
    Subnet("7", "vpc-2", "test", "us-west-2", "us-west-2a", null),
    Subnet("8", "vpc-2", "test", "us-west-2", "us-west-2b", null),
    Subnet("9", "vpc-2", "test", "us-west-2", "us-west-2c", null),
    Subnet("a", "vpc-3", "prod", "us-west-2", "us-west-2a", null),
    Subnet("b", "vpc-3", "prod", "us-west-2", "us-west-2b", null),
    Subnet("c", "vpc-3", "prod", "us-west-2", "us-west-2c", null)
  )

  @Test
  fun `security groups are looked up from CloudDriver when accessed by id`() {
    coEvery {
      cloudDriver.getSecurityGroupSummaries("prod", "aws", "us-east-1")
    } returns securityGroupSummaries
    coEvery {
      cloudDriver.getCredential("prod")
    } returns Credential("prod", "aws")

    subject.securityGroupById("prod", "us-east-1", "sg-2").let { securityGroupSummary ->
      expectThat(securityGroupSummary) {
        get { name }.isEqualTo("bar")
        get { id }.isEqualTo("sg-2")
      }
    }
  }

  @Test
  fun `security groups are looked up from CloudDriver when accessed by name`() {
    coEvery {
      cloudDriver.getSecurityGroupSummaries("prod", "aws", "us-east-1")
    } returns securityGroupSummaries
    coEvery {
      cloudDriver.getCredential("prod")
    } returns Credential("prod", "aws")

    subject.securityGroupByName("prod", "us-east-1", "bar").let { securityGroupSummary ->
      expectThat(securityGroupSummary) {
        get { name }.isEqualTo("bar")
        get { id }.isEqualTo("sg-2")
      }
    }
  }

  @Test
  fun `an invalid security group id throws an exception`() {
    coEvery {
      cloudDriver.getSecurityGroupSummaries("prod", "aws", "us-east-1")
    } returns securityGroupSummaries

    expectThrows<ResourceNotFound> {
      subject.securityGroupById("prod", "us-east-1", "sg-4")
    }
  }

  @Test
  fun `VPC networks are looked up by id from CloudDriver`() {
    coEvery {
      cloudDriver.listNetworks()
    } returns mapOf("aws" to vpcs)

    subject.networkBy("vpc-2").let { vpc ->
      expectThat(vpc) {
        get { name }.isEqualTo("vpcName")
        get { account }.isEqualTo("test")
        get { region }.isEqualTo("us-west-2")
      }
    }
  }

  @Test
  fun `an invalid VPC id throws an exception`() {
    coEvery {
      cloudDriver.listNetworks()
    } returns mapOf("aws" to vpcs)

    expectThrows<ResourceNotFound> {
      subject.networkBy("vpc-5")
    }
  }

  @Test
  fun `VPC networks are looked up by name and region from CloudDriver`() {
    coEvery {
      cloudDriver.listNetworks()
    } returns mapOf("aws" to vpcs)

    subject.networkBy("vpcName", "test", "us-west-2").let { vpc ->
      expectThat(vpc.id).isEqualTo("vpc-2")
    }
  }

  @Test
  fun `an invalid VPC name and region throws an exception`() {
    coEvery {
      cloudDriver.listNetworks()
    } returns mapOf("aws" to vpcs)

    expectThrows<ResourceNotFound> {
      subject.networkBy("invalid", "prod", "us-west-2")
    }
  }

  @Test
  fun `availability zones are looked up by account, VPC id and region from CloudDriver`() {
    coEvery {
      cloudDriver.listSubnets("aws")
    } returns subnets

    subject.availabilityZonesBy("test", "vpc-2", "us-west-2").let { zones ->
      expectThat(zones)
        .containsExactlyInAnyOrder("us-west-2a", "us-west-2b", "us-west-2c")
    }
  }

  @Test
  fun `an invalid account, VPC id and region returns an empty set`() {
    coEvery {
      cloudDriver.listNetworks()
    } returns mapOf("aws" to vpcs)

    expectThat(
      subject.availabilityZonesBy("test", "vpc-2", "ew-west-1")
    ).isEmpty()
  }
}
