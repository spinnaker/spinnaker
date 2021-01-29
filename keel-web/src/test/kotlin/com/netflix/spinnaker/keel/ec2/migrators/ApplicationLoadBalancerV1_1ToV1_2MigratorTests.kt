package com.netflix.spinnaker.keel.ec2.migrators

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.old.ApplicationLoadBalancerV1_1Spec
import com.netflix.spinnaker.keel.api.ec2.old.ApplicationLoadBalancerV1_1Spec.ListenerV1_1
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.assertions.isEqualTo
import strikt.assertions.isSuccess

internal class ApplicationLoadBalancerV1_1ToV1_2MigratorTests {
  val subject = ApplicationLoadBalancerV1_1ToV1_2Migrator()

  @Test
  fun `converts certificate ARN to name`() {
    val spec = ApplicationLoadBalancerV1_1Spec(
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
      listeners = setOf(
        ListenerV1_1(
          port = 443,
          protocol = "HTTPS",
          certificateArn = "arn:aws:iam::179727101194:server-certificate/spinnaker.mgmt.netflix.net-DigiCertSHA2SecureServerCA-20210113-20220213"
        )
      ),
      targetGroups = emptySet()
    )

    expectCatching {
      subject.migrate(spec)
    }
      .isSuccess()
      .get { listeners.first().certificate } isEqualTo "spinnaker.mgmt.netflix.net-DigiCertSHA2SecureServerCA-20210113-20220213"
  }
}
