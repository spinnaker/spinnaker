package com.netflix.spinnaker.keel.slack

import com.netflix.spinnaker.config.SlackConfiguration
import com.slack.api.model.block.LayoutBlock
import com.slack.api.model.kotlin_extension.block.withBlocks
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import io.mockk.verify
import org.springframework.core.env.Environment

class SlackNotifierTest: JUnit5Minutests {

  class Fixture {
    val slackConfiguration = SlackConfiguration()
    val springEnv: Environment = mockk(relaxed = true)

    val subject = SlackNotifier(
        springEnv,
        slackConfiguration
      )
    }


  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    //TODO: fix test
    context("send notification") {
//      before {
//        every {
//          springEnv.getProperty("keel.notifications.slack", Boolean::class.java, true)
//        } returns true
//      }

      test("send a successful notification"){
//        verify(exactly = 1) {
//          subject.sendSlackNotification("gal-test", testBlocks(), "testToken")
//        }
      }
    }

  }



  private fun testBlocks(): List<LayoutBlock> {
    return withBlocks {
      section {
        // "text" fields can be constructed via `plainText()` and `markdownText()`
        markdownText("*Hi, this message is being sent from Keel!*")
      }
      divider()
      actions {
        // To align with the JSON structure, you could put the `elements { }` block around the buttons but for brevity it can be omitted
        // The same is true for things such as the section block's "accessory" container
        button {
          // For instances where only `plain_text` is acceptable, the field's name can be filled with `plain_text` inputs
          text("Farmhouse", emoji = true)
          value("v1")
        }
        button {
          text("Kin Khao", emoji = true)
          value("v2")
        }
      }
    }
  }

}

