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

import com.netflix.spinnaker.front50.graphql.datafetcher.PipelinesDataFetcher
import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.Trigger
import graphql.schema.DataFetchingEnvironment
import spock.lang.Specification
import spock.lang.Unroll

class HavingTriggerTypePredicateSpec extends Specification {

  @Unroll
  def "passes pipelines with more than one trigger"() {
    given:
    DataFetchingEnvironment env = Mock() {
      getArgument(_) >> type
    }

    expect:
    passes == new PipelinesDataFetcher.HavingTriggerTypePredicate(env).test(pipeline)

    where:
    passes || type    | pipeline
    true   || "cron"  | new Pipeline(id: "1", triggers: [new Trigger([type: "cron"])])
    false  || "cron"  | new Pipeline(id: "2")
    false  || "cron"  | new Pipeline(id: "3", triggers: [])
    true   || "cron"  | new Pipeline(id: "4").with { put("triggers", [[type: "cron"]]); it }
    true   || null    | new Pipeline(id: "5", triggers: [])
  }
}
