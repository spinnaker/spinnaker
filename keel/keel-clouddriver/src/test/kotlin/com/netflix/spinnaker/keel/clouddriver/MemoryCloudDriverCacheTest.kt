package com.netflix.spinnaker.keel.clouddriver

import com.netflix.spinnaker.keel.caffeine.CacheLoadingException
import com.netflix.spinnaker.keel.caffeine.TEST_CACHE_FACTORY
import com.netflix.spinnaker.keel.clouddriver.model.Certificate
import com.netflix.spinnaker.keel.clouddriver.model.Credential
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.retrofit.RETROFIT_NOT_FOUND
import com.netflix.spinnaker.keel.retrofit.RETROFIT_SERVICE_UNAVAILABLE
import io.mockk.mockk
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.containsExactly
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

internal class MemoryCloudDriverCacheTest {

  val cloudDriver = mockk<CloudDriverService>()
  val subject = MemoryCloudDriverCache(cloudDriver, TEST_CACHE_FACTORY)

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

  val certificates = listOf(
    Certificate("cert-1", "prod", "arn:prod:cert-1"),
    Certificate("cert-2", "prod", "arn:prod:cert-2"),
    Certificate("cert-1", "test", "arn:test:cert-1"),
    Certificate("cert-2", "test", "arn:test:cert-2")
  )

  @Test
  fun `security groups are looked up from CloudDriver when accessed by id`() {
    every {
      cloudDriver.getSecurityGroupSummaryById("prod", "aws", "us-east-1", "sg-2")
    } returns sg2
    every {
      cloudDriver.getCredential("prod")
    } returns Credential("prod", "aws", "prod")

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
    } returns Credential("prod", "aws", "prod")

    subject.securityGroupByName("prod", "us-east-1", "foo").let { securityGroupSummary ->
      expectThat(securityGroupSummary) {
        get { name }.isEqualTo("foo")
        get { id }.isEqualTo("sg-1")
      }
    }
  }

  @Test
  fun `subsequent requests for the same security groups are served from the cache`() {
    every {
      cloudDriver.getCredential("prod")
    } returns Credential("prod", "aws", "prod")
    every {
      cloudDriver.getSecurityGroupSummaryByName("prod", "aws", "us-east-1", "foo")
    } returns sg1

    subject.securityGroupByName("prod", "us-east-1", "foo")
    subject.securityGroupByName("prod", "us-east-1", "foo")

    verify(exactly = 1) {
      cloudDriver.getSecurityGroupSummaryByName(any(), any(), any(), any())
    }
    verify(exactly = 1) {
      cloudDriver.getCredential(any())
    }
  }

  @Test
  fun `a 404 from CloudDriver is translated into a ResourceNotFound exception`() {
    every {
      cloudDriver.getCredential("prod")
    } returns Credential("prod", "aws","prod")
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
      cloudDriver.listNetworks("aws")
    } returns vpcs

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
      cloudDriver.listNetworks("aws")
    } returns vpcs

    expectThrows<ResourceNotFound> {
      subject.networkBy("vpc-5")
    }
  }

  @Test
  fun `VPC networks are looked up by name and region from CloudDriver`() {
    every {
      cloudDriver.listNetworks("aws")
    } returns vpcs

    subject.networkBy("vpcName", "test", "us-west-2").let { vpc ->
      expectThat(vpc.id).isEqualTo("vpc-2")
    }
  }

  @Test
  fun `an invalid VPC name and region throws an exception`() {
    every {
      cloudDriver.listNetworks("aws")
    } returns vpcs

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
      cloudDriver.listSubnets("aws")
    } returns subnets

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

  @ParameterizedTest
  @ValueSource(strings = ["cert-1", "cert-2"])
  fun `certificates are looked up from CloudDriver when requested by account and name`(name: String) {
    every { cloudDriver.getCertificates() } returns certificates

    expectThat(subject.certificateByAccountAndName("test", name))
      .get { serverCertificateName } isEqualTo name
  }

  @ParameterizedTest
  @ValueSource(strings = ["prod", "test"])
  fun `certificates are correctly distinguished by account`(account: String) {
    every { cloudDriver.getCertificates() } returns certificates

    expectThat(subject.certificateByAccountAndName(account, "cert-1")) {
      get { serverCertificateName } isEqualTo "cert-1"
      get { account } isEqualTo account
    }
  }

  @Test
  fun `an unknown certificate name throws an exception`() {
    every { cloudDriver.getCertificates() } returns certificates

    expectThrows<ResourceNotFound> { subject.certificateByAccountAndName("test", "does-not-exist") }
  }

  @Test
  fun `an unknown account for a known certificate name throws an exception`() {
    every { cloudDriver.getCertificates() } returns certificates

    expectThrows<ResourceNotFound> { subject.certificateByAccountAndName("fnord", "cert-1") }
  }

  @ParameterizedTest
  @ValueSource(strings = ["cert-1", "cert-2"])
  fun `subsequent requests for a certificate by name hit the cache`(name: String) {
    every { cloudDriver.getCertificates() } returns certificates

    repeat(4) { subject.certificateByAccountAndName("test", name) }

    verify(exactly = 1) { cloudDriver.getCertificates() }
  }

  // the sleep of 1 ms is not fixing this test, disabling it until we can investigate further.
  @Disabled
  @Test
  fun `all certs are cached at once when requested by name`() {
    every { cloudDriver.getCertificates() } returns certificates

    listOf("cert-1", "cert-2")
      .forEach {
        subject.certificateByAccountAndName("test", it)
        // this test can be vulnerable to an occasional race condition where the 2nd read misses the
        // cache (seemingly due to the way the bulk load works). This seems to be sufficient to
        // prevent it
        Thread.sleep(1)
      }

    verify(exactly = 1) { cloudDriver.getCertificates() }
  }

  @ParameterizedTest
  @ValueSource(strings = ["arn:prod:cert-1", "arn:prod:cert-2", "arn:test:cert-1", "arn:test:cert-2"])
  fun `certificates are looked up from CloudDriver when requested by ARN`(arn: String) {
    every { cloudDriver.getCertificates() } returns certificates

    expectThat(subject.certificateByArn(arn))
      .get { arn } isEqualTo arn
  }

  @Test
  fun `an unknown certificate ARN throws an exception`() {
    every { cloudDriver.getCertificates() } returns certificates

    expectThrows<ResourceNotFound> { subject.certificateByArn("does-not-exist") }
  }

  @ParameterizedTest
  @ValueSource(strings = ["arn:prod:cert-1", "arn:prod:cert-2", "arn:test:cert-1", "arn:test:cert-2"])
  fun `subsequent requests for a certificate by ARN hit the cache`(arn: String) {
    every { cloudDriver.getCertificates() } returns certificates

    repeat(5) { subject.certificateByArn(arn) }

    verify(exactly = 1) { cloudDriver.getCertificates() }
  }

  // the sleep of 1 ms is not fixing this test, disabling it until we can investigate further.
  @Disabled
  @Test
  fun `all certs are cached at once when requested by ARN`() {
    every { cloudDriver.getCertificates() } returns certificates

    listOf("arn:prod:cert-1", "arn:prod:cert-2")
      .forEach {
        subject.certificateByArn(it)
        // this test can be vulnerable to an occasional race condition where the 2nd read misses the
        // cache (seemingly due to the way the bulk load works). This seems to be sufficient to
        // prevent it
        Thread.sleep(1)
      }

    verify(exactly = 1) { cloudDriver.getCertificates() }
  }
}
