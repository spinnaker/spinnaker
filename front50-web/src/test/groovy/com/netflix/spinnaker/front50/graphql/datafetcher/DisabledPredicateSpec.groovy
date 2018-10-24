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

import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import graphql.schema.DataFetchingEnvironment
import spock.lang.Specification
import spock.lang.Unroll

class DisabledPredicateSpec extends Specification {

  @Unroll
  def "filters pipelines by disabled status"() {
    given:
    DataFetchingEnvironment env = Mock() {
      containsArgument(_) >> { disabled != null }
      getArgument(_) >> disabled
    }

    expect:
    passes == new PipelinesDataFetcher.DisabledPredicate(env).test(pipeline)

    where:
    passes || disabled | pipeline
    true   || null     | new Pipeline(id: "1")
    true   || null     | new Pipeline(id: "2", disabled: true)
    true   || null     | new Pipeline(id: "3", disabled: false)
    true   || true     | new Pipeline(id: "4")
    true   || true     | new Pipeline(id: "5", disabled: true)
    false  || true     | new Pipeline(id: "6", disabled: false)
    false  || false    | new Pipeline(id: "7")
    false  || false    | new Pipeline(id: "8", disabled: true)
    true   || false    | new Pipeline(id: "9", disabled: false)
  }
}
