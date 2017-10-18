/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.kayenta.metrics

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.kayenta.canary.CanaryScope
import com.netflix.kayenta.retrofit.config.RetrofitClientConfiguration
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant

class CanaryScopeSpec extends Specification {

  @Shared
  CanaryScope scope1 = new CanaryScope(
    start: Instant.parse("2000-01-01T10:11:12Z"),
    end: Instant.parse("2000-01-01T14:11:12Z"),
    step: 300,
    scope: "scopeHere",
    extendedScopeParams: [
      type: "asg"
    ]
  )

  @Shared
  String scope1Json = """
    {
      "start": "2000-01-01T10:11:12Z",
      "end": "2000-01-01T14:11:12Z",
      "step": 300,
      "scope": "scopeHere",
      "extendedScopeParams": {
        "type": "asg"
      }
    }
  """

  @Unroll
  void "should parse json"() {
    when:
    ObjectMapper objectMapper = new RetrofitClientConfiguration().kayentaObjectMapper()

    CanaryScope scope = objectMapper.readValue(scope1Json, CanaryScope)

    then:
    scope.extendedScopeParams.type == "asg"
  }

  @Unroll
  void "should render as json and come back again"() {
    when:
    ObjectMapper objectMapper = new RetrofitClientConfiguration().kayentaObjectMapper()

    StringWriter jsonStream = new StringWriter()
    objectMapper.writeValue(jsonStream, scope1)
    String json = jsonStream.toString()
    CanaryScope scope = objectMapper.readValue(json, CanaryScope)

    then:
    scope == scope1
  }

}
