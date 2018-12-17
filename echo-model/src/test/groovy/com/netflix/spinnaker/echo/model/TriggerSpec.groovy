/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.echo.model

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Shared
import spock.lang.Specification

class TriggerSpec extends Specification {

  @Shared ObjectMapper objectMapper = new ObjectMapper()

  void 'trigger deserialization should ignore propagateAuth'() {
    setup:
    String triggerJson = ''' {
        "type" : "manual",
        "propagateAuth" : true,
        "user" : "ezimanyi@google.com",
        "parameters" : {},
        "dryRun" : false
     }
     '''
    when:
    Trigger trigger = objectMapper.readValue(triggerJson, Trigger)

    then:
    noExceptionThrown()
    trigger != null
    trigger.getType() == "manual"
    !trigger.isPropagateAuth()
  }

  void 'atPropagateAuth correctly sets propagateAuth'() {
    when:
    Trigger trigger = Trigger.builder().build()

    then:
    !trigger.isPropagateAuth()

    when:
    trigger = trigger.atPropagateAuth(true)

    then:
    trigger.isPropagateAuth()

    when:
    trigger = trigger.atPropagateAuth(false)

    then:
    !trigger.isPropagateAuth()
  }
}
