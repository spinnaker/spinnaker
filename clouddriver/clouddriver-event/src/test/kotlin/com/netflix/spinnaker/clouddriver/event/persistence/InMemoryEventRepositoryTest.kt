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
package com.netflix.spinnaker.clouddriver.event.persistence

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.clouddriver.event.AbstractSpinnakerEvent
import com.netflix.spinnaker.clouddriver.event.config.MemoryEventRepositoryConfigProperties
import com.netflix.spinnaker.clouddriver.event.exceptions.AggregateChangeRejectedException
import com.netflix.spinnaker.clouddriver.event.persistence.EventRepository.ListAggregatesCriteria
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.get
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isSameInstanceAs
import strikt.assertions.map

class InMemoryEventRepositoryTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("no events returned for a non-existent aggregate") {
      expectThat(subject.list("type", "noexist"))
        .isEmpty()
    }

    test("save appends aggregate events") {
      val event = MyEvent("agg", "id", "hello world")
      subject.save("agg", "id", 0L, listOf(event))

      expectThat(subject.list("agg", "id")) {
        get { size }.isEqualTo(1)
        get(0).and {
          isSameInstanceAs(event)
        }
      }

      val event2 = MyEvent("agg", "id", "hello rob")
      subject.save("agg", "id", 1L, listOf(event2))

      expectThat(subject.list("agg", "id")) {
        get { size }.isEqualTo(2)
        get(0).and {
          isSameInstanceAs(event)
        }
        get(1).and {
          isSameInstanceAs(event2)
        }
      }
    }

    test("saving with a new aggregate with a non-zero originating version fails") {
      val event = MyEvent("agg", "id", "hello")
      assertThrows<AggregateChangeRejectedException> {
        subject.save("agg", "id", 10L, listOf(event))
      }
    }

    test("saving an aggregate with an old originating version fails") {
      val event = MyEvent("agg", "id", "hello")
      subject.save("agg", "id", 0L, listOf(event))

      assertThrows<AggregateChangeRejectedException> {
        subject.save("agg", "id", 0L, listOf(event))
      }
    }

    test("newly saved events are published") {
      val event = MyEvent("agg", "id", "hello")
      subject.save("agg", "id", 0L, listOf(event))

      verify { eventPublisher.publishEvent(event) }
      confirmVerified(eventPublisher)
    }

    context("listing aggregates") {
      val event1 = MyEvent("type1", "id", "one")
      val event2 = MyEvent("type2", "id", "two")
      val event3 = MyEvent("type3", "id", "three")

      test("not providing a type") {
        listOf(event1, event2, event3).forEach {
          subject.save(it.aggregateType, it.aggregateId, 0L, listOf(it))
        }

        expectThat(subject.listAggregates(ListAggregatesCriteria())) {
          get { aggregates }.map { it.type }.containsExactly("type1", "type2", "type3")
        }
      }

      test("providing a type") {
        listOf(event1, event2, event3).forEach {
          subject.save(it.aggregateType, it.aggregateId, 0L, listOf(it))
        }

        expectThat(subject.listAggregates(ListAggregatesCriteria(aggregateType = event1.getMetadata().aggregateType))) {
          get { aggregates }.map { it.type }.containsExactly("type1")
        }
      }

      test("providing a non-existent type") {
        listOf(event1, event2, event3).forEach {
          subject.save(it.aggregateType, it.aggregateId, 0L, listOf(it))
        }

        expectThat(subject.listAggregates(ListAggregatesCriteria(aggregateType = "unknown"))) {
          get { aggregates }.isEmpty()
        }
      }
    }
  }

  inner class Fixture {
    var eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    var subject: EventRepository = InMemoryEventRepository(
      MemoryEventRepositoryConfigProperties(),
      eventPublisher,
      NoopRegistry()
    )
  }

  private inner class MyEvent(
    val aggregateType: String,
    val aggregateId: String,
    val value: String
  ) : AbstractSpinnakerEvent()
}
