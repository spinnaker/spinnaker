/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.kork.plugins.v2

import com.netflix.spinnaker.kork.plugins.api.events.SpinnakerApplicationEvent
import com.netflix.spinnaker.kork.plugins.api.events.SpinnakerEventListener
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEvent
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class SpringEventListenerAdapterTest {

  @Test
  fun shouldAdaptSpinnakerEvent() {
    val listener = TestListener()
    val subject = SpringEventListenerAdapter(listener)

    subject.onApplicationEvent(KorkApplicationEventImpl(this))

    expectThat(listener.invoked).isTrue()
  }

  @Test
  fun shouldIgnoreSpringEvent() {
    val listener = TestListener()
    val subject = SpringEventListenerAdapter(listener)

    subject.onApplicationEvent(TestSpringEvent(this))

    expectThat(listener.invoked).isFalse()
  }

  interface KorkApplicationEvent : SpinnakerApplicationEvent

  class KorkApplicationEventImpl(source: Any) : KorkApplicationEvent, ApplicationEvent(source)

  private inner class TestSpringEvent(source: Any): ApplicationEvent(source)

  private inner class TestListener : ListenerAbstraction() {
    var invoked: Boolean = false
    override fun onApplicationEvent(event: KorkApplicationEvent) {
      invoked = true
    }
  }

  private abstract class ListenerAbstraction : SpinnakerEventListener<KorkApplicationEvent>

}
