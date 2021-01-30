package com.netflix.spinnaker.keel.lemur

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.Listener
import com.netflix.spinnaker.keel.api.ec2.EC2_APPLICATION_LOAD_BALANCER_V1_2
import com.netflix.spinnaker.keel.test.resource
import io.mockk.mockk
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import strikt.assertions.isSuccess
import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

class LemurCertificateResolverTests {

  val httpListener = Listener(
    port = 80,
    protocol = "HTTP"
  )

  val httpsListener = Listener(
    port = 443,
    protocol = "HTTPS",
    certificate = "my-certificate-v1"
  )

  val resource = resource(
    kind = EC2_APPLICATION_LOAD_BALANCER_V1_2.kind,
    spec = ApplicationLoadBalancerSpec(
      moniker = Moniker(
        app = "fnord"
      ),
      locations = SubnetAwareLocations(
        account = "test",
        subnet = "external",
        regions = setOf(
          SubnetAwareRegionSpec(
            name = "ap-south-1"
          )
        )
      ),
      listeners = emptySet(),
      targetGroups = emptySet()
    )
  )

  val lemurCertificateByName = mockk<suspend (String) -> LemurCertificateResponse>()
  val subject = LemurCertificateResolver(lemurCertificateByName)

  @Test
  fun `a listener with no certificate is not affected`() {
    expectCatching {
      subject.invoke(resource.withListener(httpListener))
    }
      .isSuccess()
      .get { spec.listeners.first() } isEqualTo httpListener

    verify(exactly = 0) { lemurCertificateByName.invoke(any()) }
  }

  @Test
  fun `a listener with an active certificate is not affected`() {
    every { lemurCertificateByName.invoke(httpsListener.certificate!!)} returns LemurCertificateResponse(
      items = listOf(
        LemurCertificate(
          commonName = "my-certificate",
          name = httpsListener.certificate!!,
          active = true,
          validityStart = Instant.now().minus(28, DAYS),
          validityEnd = Instant.now().plus(28, DAYS)
        )
      )
    )

    expectCatching {
      subject.invoke(resource.withListener(httpsListener))
    }
      .isSuccess()
      .get { spec.listeners.first() } isEqualTo httpsListener
  }

  @Test
  fun `an expires certificate with a replacement is updated to the new certificate`() {
    every { lemurCertificateByName.invoke(httpsListener.certificate!!)} returns LemurCertificateResponse(
      items = listOf(
        LemurCertificate(
          commonName = "my-certificate",
          name = httpsListener.certificate!!,
          active = false,
          validityStart = Instant.now().minus(57, DAYS),
          validityEnd = Instant.now().minus(28, DAYS),
          replacedBy = listOf(
            LemurCertificate(
              commonName = "my-certificate",
              name = "my-certificate-v2",
              active = true,
              validityStart = Instant.now().minus(28, DAYS),
              validityEnd = Instant.now().plus(28, DAYS)
            )
          )
        )
      )
    )

    expectCatching {
      subject.invoke(resource.withListener(httpsListener))
    }
      .isSuccess()
      .get { spec.listeners.first().certificate } isEqualTo "my-certificate-v2"
  }

  @Test
  fun `an expired certificate with no replacement results in an exception being thrown`() {
    every { lemurCertificateByName.invoke(httpsListener.certificate!!)} returns LemurCertificateResponse(
      items = listOf(
        LemurCertificate(
          commonName = "my-certificate",
          name = httpsListener.certificate!!,
          active = false,
          validityStart = Instant.now().minus(57, DAYS),
          validityEnd = Instant.now().minus(28, DAYS)
        )
      )
    )

    expectCatching {
      subject.invoke(resource.withListener(httpsListener))
    }
      .isFailure()
      .isA<CertificateExpired>()
  }

  @Test
  fun `an expired certificate with an expired replacement results in looking further up the chain`() {
    every { lemurCertificateByName.invoke(httpsListener.certificate!!)} returns LemurCertificateResponse(
      items = listOf(
        LemurCertificate(
          commonName = "my-certificate",
          name = httpsListener.certificate!!,
          active = false,
          validityStart = Instant.now().minus(114, DAYS),
          validityEnd = Instant.now().minus(57, DAYS),
          replacedBy = listOf(
            LemurCertificate(
              commonName = "my-certificate",
              name = "my-certificate-v2",
              active = false,
              validityStart = Instant.now().minus(57, DAYS),
              validityEnd = Instant.now().minus(28, DAYS)
            )
          )
        )
      )
    )

    every { lemurCertificateByName.invoke("my-certificate-v2")} returns LemurCertificateResponse(
      items = listOf(
        LemurCertificate(
          commonName = "my-certificate",
          name = "my-certificate-v2",
          active = false,
          validityStart = Instant.now().minus(57, DAYS),
          validityEnd = Instant.now().minus(28, DAYS),
          replacedBy = listOf(
            LemurCertificate(
              commonName = "my-certificate",
              name = "my-certificate-v3",
              active = true,
              validityStart = Instant.now().minus(28, DAYS),
              validityEnd = Instant.now().plus(28, DAYS)
            )
          )
        )
      )
    )

    expectCatching {
      subject.invoke(resource.withListener(httpsListener))
    }
      .isSuccess()
      .get { spec.listeners.first().certificate } isEqualTo "my-certificate-v3"
  }

  @Test
  fun `an empty response from Lemur is handled`() {
    every { lemurCertificateByName.invoke(httpsListener.certificate!!)} returns LemurCertificateResponse(
      items = emptyList()
    )

    expectCatching {
      subject.invoke(resource.withListener(httpsListener))
    }
      .isFailure()
      .isA<CertificateNotFound>()
  }

  private fun Resource<ApplicationLoadBalancerSpec>.withListener(listener: Listener) =
    copy(
      spec = spec.copy(
        listeners = setOf(listener)
      )
    )
}
