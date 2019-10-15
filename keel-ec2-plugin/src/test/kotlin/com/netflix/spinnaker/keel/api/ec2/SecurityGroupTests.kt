package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule.Protocol.TCP
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.model.Moniker
import de.danielbechler.diff.node.DiffNode.State.CHANGED
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNull
import strikt.assertions.isTrue

internal object SecurityGroupTests : JUnit5Minutests {

  fun diffTests() = rootContext<SecurityGroup> {
    fixture {
      SecurityGroup(
        moniker = Moniker(
          app = "fnord",
          stack = "ext"
        ),
        location = SecurityGroup.Location(
          accountName = "prod",
          vpcName = "vpc0",
          region = "us-north-2"
        ),
        description = "I can see the fnords",
        inboundRules = setOf(
          SelfReferenceRule(TCP, PortRange(7001, 7002)),
          CidrRule(TCP, PortRange(443, 443), "127.0.0.1/16")
        )
      )
    }

    derivedContext<ResourceDiff<SecurityGroup>>("identical security groups") {
      deriveFixture {
        ResourceDiff(this, copy())
      }

      test("diff contains no changes") {
        expectThat(this)
          .get { hasChanges() }
          .isFalse()
      }
    }

    derivedContext<ResourceDiff<SecurityGroup>>("security groups that differ in basic fields") {
      deriveFixture {
        ResourceDiff(this,
          copy(
            location = SecurityGroup.Location(
              accountName = location.accountName,
              vpcName = "vpc0",
              region = "ap-south-1"
            )
          )
        )
      }

      test("diff contains changes") {
        expectThat(this).get { hasChanges() }.isTrue()
      }

      test("diff is detected on the changed property") {
        expectThat(this)
          .get { diff.getChild("location").state }
          .isEqualTo(CHANGED)
      }
    }

    derivedContext<ResourceDiff<SecurityGroup>>("security groups that differ in ignored fields") {
      deriveFixture {
        ResourceDiff(this, copy(description = "We can't actually make changes to this so it should be ignored by the diff"))
      }

      test("diff does not contain changes") {
        expectThat(this).get { hasChanges() }.isFalse()
      }
    }

    derivedContext<ResourceDiff<SecurityGroup>>("security groups that differ in ignored and non-ignored fields") {
      deriveFixture {
        ResourceDiff(this, copy(
          location = SecurityGroup.Location(
            accountName = location.accountName,
            vpcName = "vpc0",
            region = "ap-south-1"
          ),
          description = "We can't actually make changes to this so it should be ignored by the diff"
        ))
      }

      test("diff contains changes") {
        expectThat(this).get { hasChanges() }.isTrue()
      }

      test("diff is detected on the changed property") {
        expectThat(this)
          .get { diff.getChild("location").state }
          .isEqualTo(CHANGED)
      }

      test("no diff is detected on the ignored property") {
        expectThat(this)
          .get { diff.getChild("description") }
          .isNull()
      }
    }

    derivedContext<ResourceDiff<SecurityGroup>>("desired state has differing inbound rules") {
      deriveFixture {
        ResourceDiff(
          this,
          copy(inboundRules = inboundRules.map {
            when (it) {
              is SelfReferenceRule -> it.copy(portRange = PortRange(8080, 8083))
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
          .get { diff.getChild("inboundRules").childCount() }
          .isEqualTo(2)
      }
    }

    derivedContext<ResourceDiff<SecurityGroup>>("desired state has fewer inbound rules") {
      deriveFixture {
        ResourceDiff(this, copy(inboundRules = inboundRules.take(1).toSet()))
      }

      test("diff contains changes") {
        expectThat(this)
          .get { hasChanges() }
          .isTrue()
      }

      test("there is just one change on the inbound rules") {
        expectThat(this)
          .get { diff.getChild("inboundRules").childCount() }
          .isEqualTo(1)
      }
    }

    derivedContext<ResourceDiff<SecurityGroup>>("desired state has a new inbound rule") {
      deriveFixture {
        ResourceDiff(
          this,
          copy(
            inboundRules = inboundRules
              .plus(ReferenceRule(TCP, "fnord-int", PortRange(7001, 7002)))
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
          .get { diff.getChild("inboundRules").childCount() }
          .isEqualTo(1)
      }
    }

    derivedContext<ResourceDiff<SecurityGroup>>("desired state has a new inbound rule with only a different port range") {
      deriveFixture {
        ResourceDiff(
          this,
          copy(
            inboundRules = inboundRules
              .plus(ReferenceRule(TCP, moniker.name, PortRange(80, 80)))
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
          .get { diff.getChild("inboundRules").childCount() }
          .isEqualTo(1)
      }
    }
  }
}
