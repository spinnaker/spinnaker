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
package com.netflix.spinnaker.clouddriver.sql.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.clouddriver.event.AbstractSpinnakerEvent
import com.netflix.spinnaker.clouddriver.event.exceptions.AggregateChangeRejectedException
import com.netflix.spinnaker.clouddriver.event.persistence.EventRepository.ListAggregatesCriteria
import com.netflix.spinnaker.clouddriver.event.persistence.EventRepository.ListAggregatesResult
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.netflix.spinnaker.kork.version.ServiceVersion
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.ApplicationEventPublisher
import org.testcontainers.shaded.com.fasterxml.jackson.annotation.JsonTypeName
import strikt.api.expect
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.containsExactly
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull
import strikt.assertions.isNull

class SqlEventRepositoryTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    context("event lifecycle") {
      test("events can be saved") {
        subject.save("agg", "1", 0, listOf(MyEvent("one")))

        expectThat(subject.listAggregates(ListAggregatesCriteria()))
          .isA<ListAggregatesResult>()
          .get { aggregates }.isNotEmpty()
            .get { first() }
              .and {
                get { type }.isEqualTo("agg")
                get { id }.isEqualTo("1")
                get { version }.isEqualTo(1)
              }

        subject.save("agg", "1", 1, listOf(MyEvent("two"), MyEvent("three")))

        expectThat(subject.list("agg", "1"))
          .isA<List<MyEvent>>()
          .isNotEmpty()
          .hasSize(3)
          .and {
            get { map { it.value } }
              .isA<List<String>>()
              .containsExactly("one", "two", "three")
          }
      }

      test("events saved against old version are rejected") {
        expectThrows<AggregateChangeRejectedException> {
          subject.save("agg", "1", 10, listOf(MyEvent("two")))
        }

        subject.save("agg", "1", 0, listOf(MyEvent("one")))

        expectThrows<AggregateChangeRejectedException> {
          subject.save("agg", "1", 0, listOf(MyEvent("two")))
        }
      }

      test("events correctly increment sequence across transactions") {
        subject.save("agg", "1", 0, listOf(MyEvent("1"), MyEvent("2")))
        subject.save("agg", "1", 1, listOf(MyEvent("3"), MyEvent("4")))

        expectThat(subject.list("agg", "1"))
          .get { map { it.getMetadata().sequence } }
            .isA<List<Long>>()
            .containsExactly(1, 2, 3, 4)
      }

      context("listing aggregates") {
        fun Fixture.setupAggregates() {
          subject.save("foo", "1", 0, listOf(MyEvent("hi foo")))
          subject.save("bar", "1", 0, listOf(MyEvent("hi bar 1")))
          subject.save("bar", "2", 0, listOf(MyEvent("hi bar 2")))
          subject.save("bar", "3", 0, listOf(MyEvent("hi bar 3")))
          subject.save("bar", "4", 0, listOf(MyEvent("hi bar 4")))
          subject.save("bar", "5", 0, listOf(MyEvent("hi bar 5")))
        }

        test("default criteria") {
          setupAggregates()

          expectThat(subject.listAggregates(ListAggregatesCriteria()))
            .isA<ListAggregatesResult>()
            .and {
              get { aggregates }.hasSize(6)
              get { nextPageToken }.isNull()
            }
        }

        test("filtering by type") {
          setupAggregates()

          expectThat(subject.listAggregates(ListAggregatesCriteria(aggregateType = "foo")))
            .isA<ListAggregatesResult>()
            .and {
              get { aggregates }.hasSize(1)
                .get { first() }
                .and {
                  get { type }.isEqualTo("foo")
                  get { id }.isEqualTo("1")
                }
              get { nextPageToken }.isNull()
            }
        }

        test("pagination") {
          setupAggregates()

          expect {
            var response = subject.listAggregates(ListAggregatesCriteria(perPage = 2))
            that(response)
              .describedAs("first page")
              .isA<ListAggregatesResult>()
              .and {
                get { aggregates }.hasSize(2)
                  .and {
                    get { first().type }.isEqualTo("foo")
                    get { last() }
                      .and {
                        get { type }.isEqualTo("bar")
                        get { id }.isEqualTo("1")
                      }
                  }
                get { nextPageToken }.isNotNull()
              }

            response = subject.listAggregates(ListAggregatesCriteria(perPage = 2, token = response.nextPageToken))
            that(response)
              .describedAs("second page")
              .isA<ListAggregatesResult>()
              .and {
                get { aggregates }.hasSize(2)
                  .and {
                    get { first().type }.isEqualTo("bar")
                    get { first().id }.isEqualTo("2")
                    get { last().type }.isEqualTo("bar")
                    get { last().id }.isEqualTo("3")
                  }
                get { nextPageToken }.isNotNull()
              }

            that(subject.listAggregates(ListAggregatesCriteria(perPage = 2, token = response.nextPageToken)))
              .describedAs("last page")
              .isA<ListAggregatesResult>()
              .and {
                get { aggregates }.hasSize(2)
                  .and {
                    get { first().type }.isEqualTo("bar")
                    get { first().id }.isEqualTo("4")
                    get { last().type }.isEqualTo("bar")
                    get { last().id }.isEqualTo("5")
                  }
                get { nextPageToken }.isNull()
              }
          }
        }
      }
    }
  }

  private inner class Fixture {
    val database = SqlTestUtil.initTcMysqlDatabase()!!

    val serviceVersion: ServiceVersion = mockk(relaxed = true)
    val applicationEventPublisher: ApplicationEventPublisher = mockk(relaxed = true)

    val subject = SqlEventRepository(
      jooq = database.context,
      serviceVersion = serviceVersion,
      objectMapper = ObjectMapper().apply {
        registerModules(KotlinModule(), JavaTimeModule())
        registerSubtypes(MyEvent::class.java)
      },
      applicationEventPublisher = applicationEventPublisher,
      registry = NoopRegistry()
    )

    init {
      every { serviceVersion.resolve() } returns "v1.2.3"
      SqlTestUtil.cleanupDb(database.context)
    }
  }

  @JsonTypeName("myEvent")
  private class MyEvent(
    val value: String
  ) : AbstractSpinnakerEvent()
}
