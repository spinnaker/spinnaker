/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca;

import com.netflix.spinnaker.q.Queue;
import com.netflix.spinnaker.q.memory.InMemoryQueue;
import com.netflix.spinnaker.q.metrics.EventPublisher;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.util.Collections;

@TestConfiguration
class StartupTestConfiguration {
  @Bean
  @Primary
  Queue queue(Clock clock, EventPublisher publisher) {
    return new InMemoryQueue(clock, Duration.ofMinutes(1), Collections.emptyList(), publisher);
  }
}
