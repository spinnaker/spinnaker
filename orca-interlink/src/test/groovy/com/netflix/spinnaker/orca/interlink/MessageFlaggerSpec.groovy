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
 */

package com.netflix.spinnaker.orca.interlink

import com.netflix.spinnaker.orca.interlink.events.CancelInterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.DeleteInterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.PauseInterlinkEvent
import com.netflix.spinnaker.orca.time.MutableClock
import spock.lang.Specification

import java.time.Duration

import static com.netflix.spinnaker.config.InterlinkConfigurationProperties.FlaggerProperties
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION

class MessageFlaggerSpec extends Specification {
  def cancel = new CancelInterlinkEvent(ORCHESTRATION, "id", "user", "reason")
  def cancelByOtherUser = new CancelInterlinkEvent(ORCHESTRATION, "id", "otherUser", "reason")
  def pause = new PauseInterlinkEvent(ORCHESTRATION, "id", "user")
  def delete = new DeleteInterlinkEvent(ORCHESTRATION, "id")
  def deleteOtherId = new DeleteInterlinkEvent(ORCHESTRATION, "otherId")
  MutableClock clock = new MutableClock()

  def 'flagger should flag repeated messages'() {
    given:
    def flagger = new MessageFlagger(clock, new FlaggerProperties(threshold: 2))

    when:
    flagger.process(cancel)
    flagger.process(cancel)
    flagger.process(pause)

    then:
    noExceptionThrown()

    when: '3rd time this event is seen trips the flagger'
    flagger.process(cancel)

    then:
    thrown(MessageFlaggedException)
  }

  def 'older events are evicted and not tripping the flagger'() {
    given: 'a small flagger that does not allow duplicates'
    def flagger = new MessageFlagger(clock, new FlaggerProperties(maxSize: 3, threshold: 1))

    when: 'sending a variety of messages that fall off the queue'
    flagger.process(cancel)
    flagger.process(delete)
    flagger.process(pause)
    flagger.process(deleteOtherId)
    flagger.process(cancel)

    then:
    noExceptionThrown()

    when: 'sending duplicates in a row'
    flagger.process(cancel)

    then:
    thrown(MessageFlaggedException)
  }

  def 'expired events do not count toward the threshold'() {
    given: 'a small flagger that with a tight lookback window'
    def flagger = new MessageFlagger(clock, new FlaggerProperties(maxSize: 3, threshold: 1, lookbackSeconds: 60))

    when: 'when time is frozen, we catch duplicate fingerprints'
    flagger.process(cancel)
    flagger.process(delete)
    flagger.process(cancel)

    then:
    thrown(MessageFlaggedException)

    when: 'when we advance time sufficiently'
    clock.incrementBy(Duration.ofMinutes(2))
    flagger.process(cancel)

    then: 'the same fingerprint is not flagged'
    noExceptionThrown()
  }
}
