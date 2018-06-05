/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.q

import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.q.Message
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.*
import java.util.*

internal object MessageCompatibilityTest : Spek({

  describe("deserializing ContinueParentStage") {
    val mapper = OrcaObjectMapper.newInstance().apply {
      registerSubtypes(ContinueParentStage::class.java)
    }

    val json = mapOf(
      "kind" to "continueParentStage",
      "executionType" to "PIPELINE",
      "executionId" to UUID.randomUUID().toString(),
      "application" to "covfefe",
      "stageId" to UUID.randomUUID().toString()
    )

    given("an older message with no syntheticStageOwner") {
      on("deserializing the JSON") {
        val message = mapper.convertValue<Message>(json)

        it("doesn't blow up") {
          assertThat(message).isInstanceOf(ContinueParentStage::class.java)
        }

        it("defaults the missing field") {
          assertThat((message as ContinueParentStage).phase).isEqualTo(STAGE_BEFORE)
        }
      }
    }

    given("a newer message with a syntheticStageOwner") {
      val newJson = json + mapOf("phase" to "STAGE_AFTER")

      on("deserializing the JSON") {
        val message = mapper.convertValue<Message>(newJson)

        it("doesn't blow up") {
          assertThat(message).isInstanceOf(ContinueParentStage::class.java)
        }

        it("deserializes the new field") {
          assertThat((message as ContinueParentStage).phase).isEqualTo(STAGE_AFTER)
        }
      }
    }
  }
})
