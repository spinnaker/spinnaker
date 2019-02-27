package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule.Protocol.TCP
import de.danielbechler.diff.ObjectDifferBuilder
import de.danielbechler.diff.node.DiffNode
import de.danielbechler.diff.node.DiffNode.State.CHANGED
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal object SecurityGroupTests : JUnit5Minutests {

  val differ = ObjectDifferBuilder.buildDefault()

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
        differ.compare(this, copy(region = "us-south-1"))
      }


      test("diff contains changes") {
        expectThat(this).get { hasChanges() }.isTrue()
      }

      test("diff is in the root object") {
        expectThat(this)
          .get { getChild("region").state }
          .isEqualTo(CHANGED)
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
