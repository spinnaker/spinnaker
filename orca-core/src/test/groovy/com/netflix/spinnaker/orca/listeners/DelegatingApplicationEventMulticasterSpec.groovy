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
package com.netflix.spinnaker.orca.listeners

import com.netflix.spinnaker.orca.annotations.Sync
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ApplicationEventMulticaster
import org.springframework.context.event.EventListener
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DelegatingApplicationEventMulticasterSpec extends Specification {

  ApplicationEventMulticaster async = Mock()
  ApplicationEventMulticaster sync = Mock()

  @Subject
  ApplicationEventMulticaster subject = new DelegatingApplicationEventMulticaster(sync, async)

  def "should add listener as async by default"() {
    when:
    subject.addApplicationListener(new AsyncListener())

    then:
    1 * async.addApplicationListener(_)
    0 * sync.addApplicationListener(_)
  }

  @Unroll
  def "should add sync listeners when explicitly flagged"() {
    when:
    subject.addApplicationListener(listener)

    then:
    0 * async.addApplicationListener(_)
    1 * sync.addApplicationListener(_)

    where:
    listener << [
      new ClassSyncListener(),
      new InspectableApplicationListenerMethodAdapter("methodSyncListener", MethodSyncListener, MethodSyncListener.class.getMethod("onEvent", TestEvent))
    ]
  }

  private static class TestEvent extends ApplicationEvent {
    TestEvent(Object source) {
      super(source)
    }
  }

  private static class AsyncListener implements ApplicationListener<TestEvent> {
    @Override
    void onApplicationEvent(TestEvent event) {}
  }

  @Sync
  private static class ClassSyncListener implements ApplicationListener<TestEvent> {
    @Override
    void onApplicationEvent(TestEvent event) {}
  }

  private static class MethodSyncListener {
    @Sync
    @EventListener
    void onEvent(TestEvent event) {}
  }
}
