package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule.Protocol.TCP
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.javers.core.JaversBuilder
import org.javers.core.diff.Diff
import org.javers.core.diff.changetype.container.SetChange
import org.javers.core.diff.changetype.container.ValueAdded
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isFalse
import strikt.assertions.isPresent
import strikt.assertions.isTrue

internal object SecurityGroupTests : JUnit5Minutests {

  val javers = JaversBuilder
    .javers()
    .build()

  fun diffTests() = rootContext<SecurityGroup> {
    fixture {
      SecurityGroup(
        application = "fnord",
        name = "fnord-ext",
        accountName = "prod",
        region = "us-north-2",
        vpcName = "vpc0",
        description = "I can see the fnords",
        inboundRules = setOf(
          ReferenceSecurityGroupRule(TCP, "fnord-ext", "prod", "vpc0", PortRange(7001, 7002)),
          CidrSecurityGroupRule(TCP, PortRange(443, 443), "127.0.0.1/16")
        )
      )
    }

    derivedContext<Diff>("identical security groups") {
      deriveFixture {
        javers.compare(this, copy())
      }

      test("diff contains no changes") {
        expectThat(this)
          .get { hasChanges() }
          .isFalse()
      }
    }

    derivedContext<Diff>("security groups that differ in basic fields") {
      deriveFixture {
        javers.compare(this, copy(region = "us-south-1"))
      }


      test("diff contains changes") {
        expectThat(this).get { hasChanges() }.isTrue()
      }

      test("diff is in the root object") {
        expectThat(this)
          .get { changes.map { it.affectedObject } }
          .all {
            isPresent()
              .isA<SecurityGroup>()
          }
      }
    }

    derivedContext<Diff>("desired state has differing inbound rules") {
      deriveFixture {
        javers.compare(
          this,
          copy(inboundRules = inboundRules.map {
            when (it) {
              is ReferenceSecurityGroupRule -> it.copy(name = "fnord-int")
              else -> it
            }
          }
            .toSet()
          )
        )
      }

      test("diff contains changes") {
        expectThat(this)
          .get { hasChanges() }
          .isTrue()
      }

      test("there is just one change on the inbound rules") {
        expectThat(this)
          .get { getPropertyChanges("inboundRules") }
          .hasSize(1)
      }
    }

    derivedContext<Diff>("desired state has fewer inbound rules") {
      deriveFixture {
        javers.compare(this, copy(inboundRules = inboundRules.take(1).toSet()))
      }

      test("diff contains changes") {
        expectThat(this)
          .get { hasChanges() }
          .isTrue()
      }

      test("there is just one change on the inbound rules") {
        expectThat(this)
          .get { getPropertyChanges("inboundRules") }
          .hasSize(1)
      }
    }

    derivedContext<Diff>("desired state has a new inbound rule") {
      deriveFixture {
        javers.compare(
          this,
          copy(
            inboundRules = inboundRules
              .plus(ReferenceSecurityGroupRule(TCP, "fnord-int", "prod", "vpc0", PortRange(7001, 7002)))
              .toSet()
          )
        )
      }

      test("diff contains changes") {
        expectThat(this)
          .get { hasChanges() }
          .isTrue()
      }

      test("there is just one change on the inbound rules") {
        expectThat(this)
          .get { getPropertyChanges("inboundRules") }
          .hasSize(1)
          .first()
          .isA<SetChange>()
          .get { changes }
          .hasSize(1)
          .first()
          .isA<ValueAdded>()
      }
    }

    derivedContext<Diff>("desired state has a new inbound rule with only a different port range") {
      deriveFixture {
        javers.compare(
          this,
          copy(
            inboundRules = inboundRules
              .plus(ReferenceSecurityGroupRule(TCP, this, PortRange(80, 80)))
              .toSet()
          )
        )
      }

      test("diff contains changes") {
        expectThat(this)
          .get { hasChanges() }
          .isTrue()
      }

      test("there is just one change on the inbound rules") {
        expectThat(this)
          .get { getPropertyChanges("inboundRules") }
          .hasSize(1)
          .first()
          .isA<SetChange>()
          .get { changes }
          .hasSize(1)
          .first()
          .isA<ValueAdded>()
      }
    }
  }
}
