package com.netflix.spinnaker.keel.tagging

import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.tags.EntityRef
import com.netflix.spinnaker.keel.tags.EntityTag
import com.netflix.spinnaker.keel.tags.KEEL_TAG_NAME
import com.netflix.spinnaker.keel.tags.TagValue
import de.danielbechler.diff.node.DiffNode.State.CHANGED
import dev.minutest.experimental.SKIP
import dev.minutest.experimental.minus
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class KeelTagSpecTests : JUnit5Minutests {

  fun diffTests() = rootContext<ResourceDiff<KeelTagSpec>> {
    context("difference exists in whether tag is desired") {
      fixture {
        val resource = KeelTagSpec(
          "ec2:cluster:mgmt:us-west-2:keel-prestaging",
          EntityRef("cluster", "keel-prestaging", "keel", "us-west-2", "mgmt", "aws"),
          TagDesired(
            EntityTag(
              value = TagValue(
                message = KEEL_TAG_MESSAGE,
                keelResourceId = toString(),
                type = "notice"
              ),
              namespace = KEEL_TAG_NAMESPACE,
              valueType = "object",
              name = KEEL_TAG_NAME
            )
          )
        )
        ResourceDiff(
          resource,
          resource.copy(tagState = TagNotDesired(0L))
        )
      }

      SKIP - test("a diff is detected") {
        expectThat(diff.state).isEqualTo(CHANGED)
      }
    }
  }
}
