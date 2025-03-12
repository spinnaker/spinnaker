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
import strikt.assertions.isA

class ApplicationEventListenerBeanPostProcessorTest {

  @Test
  fun shouldAdaptListener() {
    val subject = ApplicationEventListenerBeanPostProcessor()

    expectThat(subject.postProcessBeforeInitialization(TestListener(), "bean"))
      .isA<SpringEventListenerAdapter>()
      .get { eventListener }.isA<TestListener>()
  }

  @Test
  fun shouldIgnoreOtherBeans() {
    val subject = ApplicationEventListenerBeanPostProcessor()

    expectThat(subject.postProcessBeforeInitialization(SomethingElse(), "bean"))
      .isA<SomethingElse>()
  }

  private inner class TestEvent(private val source: Any) : SpinnakerApplicationEvent {
    override fun getSource(): Any = source
    override fun getTimestamp(): Long = 0L
  }

  private inner class TestListener : SpinnakerEventListener<TestEvent> {
    override fun onApplicationEvent(event: TestEvent) {
    }
  }

  private inner class SomethingElse
}
