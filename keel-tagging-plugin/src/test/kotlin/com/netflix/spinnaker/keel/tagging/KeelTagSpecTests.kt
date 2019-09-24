package com.netflix.spinnaker.keel.tagging

import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.tags.EntityRef
import com.netflix.spinnaker.keel.tags.EntityTag
import com.netflix.spinnaker.keel.tags.KEEL_TAG_NAME
import com.netflix.spinnaker.keel.tags.TagValue
import de.danielbechler.diff.node.DiffNode.State.CHANGED
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class KeelTagSpecTests : JUnit5Minutests {

  fun diffTests() = rootContext<ResourceDiff<KeelTagSpec>> {
    context("difference exists in whether tag is desired") {
      fixture {
        val resource = KeelTagSpec(
          ResourceId("ec2:cluster:mgmt:keel-prestaging"),
          EntityRef("cluster", "keel-prestaging", "keel", "us-west-2", "mgmt", "12345", "aws"),
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

      test("a diff is detected") {
        expectThat(diff.state).isEqualTo(CHANGED)
      }
    }
  }
}
