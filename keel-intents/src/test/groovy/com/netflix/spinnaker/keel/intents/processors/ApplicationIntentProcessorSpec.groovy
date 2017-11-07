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
package com.netflix.spinnaker.keel.intents.processors

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.intents.ApplicationIntent
import com.netflix.spinnaker.keel.intents.ApplicationSpec
import com.netflix.spinnaker.keel.intents.BaseApplicationSpec
import com.netflix.spinnaker.keel.intents.ChaosMonkeySpec
import com.netflix.spinnaker.keel.intents.DataSourcesSpec
import com.netflix.spinnaker.keel.intents.NotificationSpec
import com.netflix.spinnaker.keel.intents.ParrotIntent
import com.netflix.spinnaker.keel.intents.ParrotSpec
import com.netflix.spinnaker.keel.tracing.TraceRepository
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject

class ApplicationIntentProcessorSpec extends Specification {

  TraceRepository traceRepository = Mock()
  Front50Service front50Service = Mock()
  ObjectMapper objectMapper = new ObjectMapper()

  @Subject subject = new ApplicationIntentProcessor(traceRepository, front50Service, objectMapper)

  def 'should support ApplicationIntents'() {
    expect:
    !subject.supports(new ParrotIntent(new ParrotSpec("hello", "world", 5)))
    subject.supports(new ApplicationIntent(Mock(BaseApplicationSpec)))
  }

  def 'should create application when app is missing'() {
    given:
    def intent = new ApplicationIntent(createApplicationSpec(null))

    when:
    def result = subject.converge(intent)

    then:
    result.orchestrations.size() == 1
    1 * front50Service.getApplication("keel") >> {
      throw new RetrofitError(null, null, new Response("http://stash.com", 404, "test reason", [], null), null, null, null, null)
    }
    1 * traceRepository.record(_)
    result.orchestrations[0].name == "Create application"
  }

  def 'should update application when app is present'() {
    given:
    def updated = createApplicationSpec("my updated description")

    when:
    def result = subject.converge(new ApplicationIntent(updated))

    then:
    result.orchestrations.size() == 1
    1 * front50Service.getApplication("keel") >> {
      new Application("keel", "my original description", "example@example.com", "1", "1", false, false)
    }
    1 * traceRepository.record(_)
    result.orchestrations[0].name == "Update application"
    result.orchestrations[0].job[0]['application']["description"] == "my updated description"
  }

  static ApplicationSpec createApplicationSpec(String description) {
    new ApplicationSpec(
      "keel",
      description ?: "declarartive service for spinnaker",
      "email@example.com",
      "lastModifiedBy@example.com",
      "owner",
      new ChaosMonkeySpec(
        true,
        2,
        2,
        "cluster",
        true,
        []
      ),
      false,
      [],
      8087,
      "Spinnaker",
      "aws",
      "prod,test",
      "example@example.com",
      new DataSourcesSpec([], []),
      [],
      "spinnaker",
      [:],
      [],
      false,
      false,
      new NotificationSpec(null, null, null)
    )
  }
}
