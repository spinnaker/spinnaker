/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.dryrun

import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Tag
import com.netflix.spinnaker.hamkrest.shouldEqual
import com.netflix.spinnaker.keel.ConvergeResult
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentProcessor
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.IntentStatus.ACTIVE
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.nhaarman.mockito_kotlin.*
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher

object DryRunIntentLauncherTest {

  private val processor = mock<IntentProcessor<TestIntent>>()
  private val applicationEventPublisher = mock<ApplicationEventPublisher>()

  val registry = mock<Registry>().apply {
    whenever(createId(any<String>(), any<Iterable<Tag>>())) doAnswer { mock<Id>() }
    whenever(counter(any<Id>())) doAnswer { mock<Counter>() }
  }

  val subject = DryRunIntentLauncher(listOf(processor), registry, applicationEventPublisher)

  @Test
  fun `should output human friendly summary of operations`() {
    val changeSummary = ChangeSummary("something")
    changeSummary.type = ChangeType.CREATE
    changeSummary.addMessage("Waits, coming right up")

    whenever(processor.supports(any())) doReturn true
    whenever(processor.converge(any())) doReturn ConvergeResult(
      listOf(
        OrchestrationRequest(
          "my orchestration",
          "keel",
          "testing dry-runs",
          listOf(
            Job("wait", mutableMapOf("waitTime" to 5)),
            Job("wait", mutableMapOf("name" to "wait for more time", "waitTime" to 5))
          ),
          OrchestrationTrigger("1", "keel", "keel")
        )
      ),
      changeSummary
    )

    val intent = TestIntent("1", "Test", TestIntentSpec("hello!"))

    subject.launch(intent).let { result ->
      result shouldMatch isA<DryRunLaunchedIntentResult>()
      result.summary shouldEqual changeSummary
    }
  }

  private class TestIntent(
    schema: String,
    kind: String,
    spec: TestIntentSpec
  ) : Intent<TestIntentSpec>(schema, kind, spec, ACTIVE) {
    override val defaultId = ""
  }

  private data class TestIntentSpec(val id: String) : IntentSpec
}
