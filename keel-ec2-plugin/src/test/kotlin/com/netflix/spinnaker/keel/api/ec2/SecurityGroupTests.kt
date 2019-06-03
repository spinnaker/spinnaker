package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.ec2.securityGroup.CidrRule
import com.netflix.spinnaker.keel.api.ec2.securityGroup.PortRange
import com.netflix.spinnaker.keel.api.ec2.securityGroup.ReferenceRule
import com.netflix.spinnaker.keel.api.ec2.securityGroup.SecurityGroupRule.Protocol.TCP
import com.netflix.spinnaker.keel.api.ec2.securityGroup.SecurityGroup
import com.netflix.spinnaker.keel.api.ec2.securityGroup.SelfReferenceRule
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import de.danielbechler.diff.ObjectDifferBuilder
import de.danielbechler.diff.node.DiffNode
import de.danielbechler.diff.node.DiffNode.State.CHANGED
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNull
import strikt.assertions.isTrue

internal object SecurityGroupTests : JUnit5Minutests {

  val differ = ObjectDifferBuilder.buildDefault()

  fun diffTests() = rootContext<SecurityGroup> {
    fixture {
      SecurityGroup(
        moniker = Moniker(
          app = "fnord",
          stack = "ext"
        ),
        accountName = "prod",
        region = "us-north-2",
        vpcName = "vpc0",
        description = "I can see the fnords",
        inboundRules = setOf(
          SelfReferenceRule(TCP, PortRange(7001, 7002)),
          CidrRule(TCP, PortRange(443, 443), "127.0.0.1/16")
        )
      )
    }

    derivedContext<DiffNode>("identical security groups") {
      deriveFixture {
        differ.compare(this, copy())
      }

      test("diff contains no changes") {
        expectThat(this)
          .get { hasChanges() }
          .isFalse()
      }
    }

    derivedContext<DiffNode>("security groups that differ in basic fields") {
      deriveFixture {
        differ.compare(this, copy(region = "ap-south-1"))
      }

      test("diff contains changes") {
        expectThat(this).get { hasChanges() }.isTrue()
      }

      test("diff is detected on the changed property") {
        expectThat(this)
          .get { getChild("region").state }
          .isEqualTo(CHANGED)
      }
    }

    derivedContext<DiffNode>("security groups that differ in ignored fields") {
      deriveFixture {
        differ.compare(this, copy(description = "We can't actually make changes to this so it should be ignored by the diff"))
      }

      test("diff does not contain changes") {
        expectThat(this).get { hasChanges() }.isFalse()
      }
    }

    derivedContext<DiffNode>("security groups that differ in ignored and non-ignored fields") {
      deriveFixture {
        differ.compare(this, copy(
          region = "ap-south-1",
          description = "We can't actually make changes to this so it should be ignored by the diff"
        ))
      }

      test("diff contains changes") {
        expectThat(this).get { hasChanges() }.isTrue()
      }

      test("diff is detected on the changed property") {
        expectThat(this)
          .get { getChild("region").state }
          .isEqualTo(CHANGED)
      }

      test("no diff is detected on the ignored property") {
        expectThat(this)
          .get { getChild("description") }
          .isNull()
      }
    }

    derivedContext<DiffNode>("desired state has differing inbound rules") {
      deriveFixture {
        differ.compare(
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
          .get { getChild("inboundRules").childCount() }
          .isEqualTo(2)
      }
    }

    derivedContext<DiffNode>("desired state has fewer inbound rules") {
      deriveFixture {
        differ.compare(this, copy(inboundRules = inboundRules.take(1).toSet()))
      }

      test("diff contains changes") {
        expectThat(this)
          .get { hasChanges() }
          .isTrue()
      }

      test("there is just one change on the inbound rules") {
        expectThat(this)
          .get { getChild("inboundRules").childCount() }
          .isEqualTo(1)
      }
    }

    derivedContext<DiffNode>("desired state has a new inbound rule") {
      deriveFixture {
        differ.compare(
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
          .get { getChild("inboundRules").childCount() }
          .isEqualTo(1)
      }
    }

    derivedContext<DiffNode>("desired state has a new inbound rule with only a different port range") {
      deriveFixture {
        differ.compare(
          this,
          copy(
            inboundRules = inboundRules
              .plus(ReferenceRule(TCP, this, PortRange(80, 80)))
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
          .get { getChild("inboundRules").childCount() }
          .isEqualTo(1)
      }
    }
  }
}
