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

package com.netflix.spinnaker.orca.pipeline.model

import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import spock.lang.Specification

abstract class AbstractTriggerSpec<T extends Trigger> extends Specification {

  protected abstract Class<T> getType()

  protected abstract String getTriggerJson()

  def mapper = OrcaObjectMapper.newInstance()

  def setup() {
    mapper.registerSubtypes(type)
  }

  def "can parse a trigger"() {
    given:
    def trigger = mapper.readValue(triggerJson, Trigger)

    expect:
    type.isAssignableFrom(trigger.getClass())
  }

  def "returns the correct value for the type property"() {
    given:
    def trigger = mapper.readValue(triggerJson, Trigger)

    expect:
    trigger.type == mapper.readValue(triggerJson, Map).type
  }

  def "can serialize a trigger"() {
    given:
    def trigger = mapper.readValue(triggerJson, Trigger)
    def asJson = new StringWriter().withCloseable {
      mapper.writeValue(it, trigger)
      it.toString()
    }

    expect:
    mapper.readValue(asJson, Trigger) == trigger
  }
}
