/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.clouddriver.event

import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class ObjectMappingTest : JUnit5Minutests {

  fun tests() = rootContext<ObjectMapper> {
    fixture {
      ObjectMapper()
        .registerKotlinModule()
        .findAndRegisterModules()
        .apply {
          registerSubtypes(listOf(MyEvent::class.java))
        }
    }

    test("can serialize and deserialize events") {
      val event = MyEvent("world")
      event.setMetadata(
        EventMetadata(
          id = "myid",
          aggregateType = "type",
          aggregateId = "id",
          sequence = 999,
          originatingVersion = 100
        )
      )

      val serializedEvent = writeValueAsString(event)
      expectThat(readValue<MyEvent>(serializedEvent))
        .isA<MyEvent>()
        .and {
          get { hello }.isEqualTo("world")
          // EventMetadata should be excluded from SpinnakerEvent serialization
          get { hasMetadata() }.isEqualTo(false)
        }

      val serializedEventMetadata = writeValueAsString(event.getMetadata())
      expectThat(readValue<EventMetadata>(serializedEventMetadata))
        .and {
          get { id }.isEqualTo("myid")
          get { aggregateType }.isEqualTo("type")
          get { aggregateId }.isEqualTo("id")
          get { sequence }.isEqualTo(999)
          get { originatingVersion }.isEqualTo(100)
        }
    }
  }

  @JsonTypeName("myEvent")
  class MyEvent(
    val hello: String
  ) : AbstractSpinnakerEvent()
}
