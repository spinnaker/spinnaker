/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.front50.graphql.datafetcher

import com.netflix.spinnaker.front50.graphql.datafetcher.TriggersDataFetcher
import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.Trigger
import graphql.schema.DataFetchingEnvironment
import spock.lang.Specification
import spock.lang.Subject

class TriggersDataFetcherSpec extends Specification {

  @Subject
  TriggersDataFetcher subject = new TriggersDataFetcher()

  def "fetches a list of triggers"() {
    given:
    Pipeline pipeline = new Pipeline()
    pipeline.triggers = triggers

    and:
    DataFetchingEnvironment environment = Mock() {
      getSource() >> pipeline
      getArguments() >> []
    }

    when:
    def result = subject.get(environment)

    then:
    result == triggers

    where:
    triggers = [
      new Trigger([type: "jenkins"]),
      new Trigger([type: "cron"])
    ]
  }

  def "fetches triggers by type"() {
    given:
    Pipeline pipeline = new Pipeline()
    pipeline.triggers = triggers

    and:
    DataFetchingEnvironment environment = Mock() {
      getSource() >> pipeline
      getArguments() >> [types: ["cron"]]
    }

    when:
    def result = subject.get(environment)

    then:
    result == triggers

    where:
    triggers = [
      new Trigger([type: "cron"]),
      new Trigger([type: "jenkins"])
    ]
  }
}
