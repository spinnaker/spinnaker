package com.netflix.spinnaker.keel.clouddriver

import com.netflix.spinnaker.keel.clouddriver.model.Credential
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.retrofit.RETROFIT_NOT_FOUND
import com.netflix.spinnaker.keel.retrofit.RETROFIT_SERVICE_UNAVAILABLE
import io.mockk.coEvery as every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.containsExactly
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo

object MemoryCloudDriverCacheTest {

  val cloudDriver = mockk<CloudDriverService>()
  val subject = MemoryCloudDriverCache(cloudDriver)

  val sg1 = SecurityGroupSummary("foo", "sg-1", "vpc-1")
  val sg2 = SecurityGroupSummary("bar", "sg-2", "vpc-1")

  val securityGroupSummaries = setOf(sg1, sg2)

  val vpcs = setOf(
    Network("aws", "vpc-1", "vpcName", "prod", "us-west-2"),
    Network("aws", "vpc-2", "vpcName", "test", "us-west-2"),
    Network("aws", "vpc-3", "vpcName", "prod", "us-east-1"),
    Network("aws", "vpc-4", "otherName", "prod", "us-west-2")
  )

  val subnets = setOf(
    Subnet("1", "vpc-1", "prod", "us-west-2", "us-west-2a", "internal (vpc1)"),
    Subnet("2", "vpc-1", "prod", "us-west-2", "us-west-2b", "internal (vpc1)"),
    Subnet("3", "vpc-1", "prod", "us-west-2", "us-west-2c", "internal (vpc1)"),
    Subnet("4", "vpc-1", "prod", "us-west-1", "us-west-1a", "internal (vpc1)"),
    Subnet("5", "vpc-1", "prod", "us-west-1", "us-west-1b", "internal (vpc1)"),
    Subnet("6", "vpc-1", "prod", "us-west-1", "us-west-1c", "internal (vpc1)"),
    Subnet("7", "vpc-2", "test", "us-west-2", "us-west-2a", "internal (vpc2)"),
    Subnet("8", "vpc-2", "test", "us-west-2", "us-west-2b", "internal (vpc2)"),
    Subnet("9", "vpc-2", "test", "us-west-2", "us-west-2c", "internal (vpc2)"),
    Subnet("a", "vpc-3", "prod", "us-west-2", "us-west-2a", "internal (vpc3)"),
    Subnet("b", "vpc-3", "prod", "us-west-2", "us-west-2b", "internal (vpc3)"),
    Subnet("c", "vpc-3", "prod", "us-west-2", "us-west-2c", "internal (vpc3)"),
    Subnet("d", "vpc-3", "prod", "us-west-2", "us-west-2d", "external (vpc3)")
  )

  @Test
  fun `security groups are looked up from CloudDriver when accessed by id`() {
    every {
      cloudDriver.getSecurityGroupSummaryById("prod", "aws", "us-east-1", "sg-2")
    } returns sg2
    every {
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
    every {
      cloudDriver.getSecurityGroupSummaryByName("prod", "aws", "us-east-1", "foo")
    } returns sg1
    every {
      cloudDriver.getCredential("prod")
    } returns Credential("prod", "aws")

    subject.securityGroupByName("prod", "us-east-1", "foo").let { securityGroupSummary ->
      expectThat(securityGroupSummary) {
        get { name }.isEqualTo("foo")
        get { id }.isEqualTo("sg-1")
      }
    }
  }

  @Test
  fun `a 404 from CloudDriver is translated into a ResourceNotFound exception`() {
    every {
      cloudDriver.getSecurityGroupSummaryById("prod", "aws", "us-east-1", "sg-4")
    } throws RETROFIT_NOT_FOUND

    expectThrows<ResourceNotFound> {
      subject.securityGroupById("prod", "us-east-1", "sg-4")
    }
  }

  @Test
  fun `any other exception is propagated`() {
    every {
      cloudDriver.getSecurityGroupSummaryById("prod", "aws", "us-east-1", "sg-1")
    } throws RETROFIT_SERVICE_UNAVAILABLE

    expectThrows<CacheLoadingException> {
      subject.securityGroupById("prod", "us-east-1", "sg-1")
    }
  }

  @Test
  fun `VPC networks are looked up by id from CloudDriver`() {
    every {
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
    every {
      cloudDriver.listNetworks()
    } returns mapOf("aws" to vpcs)

    expectThrows<ResourceNotFound> {
      subject.networkBy("vpc-5")
    }
  }

  @Test
  fun `VPC networks are looked up by name and region from CloudDriver`() {
    every {
      cloudDriver.listNetworks()
    } returns mapOf("aws" to vpcs)

    subject.networkBy("vpcName", "test", "us-west-2").let { vpc ->
      expectThat(vpc.id).isEqualTo("vpc-2")
    }
  }

  @Test
  fun `an invalid VPC name and region throws an exception`() {
    every {
      cloudDriver.listNetworks()
    } returns mapOf("aws" to vpcs)

    expectThrows<ResourceNotFound> {
      subject.networkBy("invalid", "prod", "us-west-2")
    }
  }

  @Test
  fun `availability zones are looked up by account, VPC id and region from CloudDriver`() {
    every {
      cloudDriver.listSubnets("aws")
    } returns subnets

    subject.availabilityZonesBy("test", "vpc-2", "internal (vpc2)", "us-west-2").let { zones ->
      expectThat(zones)
        .containsExactlyInAnyOrder("us-west-2a", "us-west-2b", "us-west-2c")
    }
  }

  @Test
  fun `an invalid account, VPC id and region returns an empty set`() {
    every {
      cloudDriver.listNetworks()
    } returns mapOf("aws" to vpcs)

    expectThat(
      subject.availabilityZonesBy("test", "vpc-2", "external (vpc2)", "ew-west-1")
    ).isEmpty()
  }

  @Test
  fun `availability zones are scoped by subnet`() {
    every {
      cloudDriver.listSubnets("aws")
    } returns subnets

    expectThat(
      subject.availabilityZonesBy("prod", "vpc-3", "external (vpc3)", "us-west-2")
    ).containsExactly("us-west-2d")
  }
}
