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

import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.ConvergeResult
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentProcessor
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.IntentStatus
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.Trigger
import org.jetbrains.annotations.NotNull
import spock.lang.Specification
import spock.lang.Subject

class DryRunIntentLauncherSpec extends Specification {

  IntentProcessor<Intent> processor = Mock()

  Registry registry = Mock() {
    createId(_, _) >> { Mock(Id) }
    counter(_) >> { Mock(Counter) }
  }

  @Subject subject = new DryRunIntentLauncher([processor], registry)

  def 'should output human friendly summary of operations'() {
    given:
    def intent = new TestIntent("1", "Test", new TestIntentSpec(id: "hello!"))

    when:
    def result = subject.launch(intent)

    then:
    1 * processor.supports(_) >> { true }
    1 * processor.converge(_) >> {
      new ConvergeResult([
        new OrchestrationRequest("my orchestration", "keel", "testing dry-runs", [
          new Job("wait", [waitTime: 5]),
          new Job("wait", [name: "wait for more time", waitTime: 5])
        ], new Trigger("1", "keel", "keel"))
      ], "Raisins")
    }
    result instanceof DryRunLaunchedIntentResult
    result.reason == "Raisins"
    result.steps.size() == 1
    result.steps[0].name == "my orchestration"
    result.steps[0].description == "testing dry-runs"
    result.steps[0].operations == ["wait", "wait for more time"]
  }

  private static class TestIntent extends Intent<TestIntentSpec> {

    TestIntent(
      @NotNull String schema,
      @NotNull String kind,
      @NotNull TestIntentSpec spec) {
      super(schema, kind, spec, IntentStatus.ACTIVE, [])
    }

    @Override
    String getId() {
      return null
    }
  }

  private static class TestIntentSpec implements IntentSpec {
    String id;
  }
}
