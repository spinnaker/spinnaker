/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.kork.pubsub;

import com.netflix.spinnaker.kork.pubsub.model.PubsubBroadcastSubscriber;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PubsubBroadcastSubscribers {
  private List<PubsubBroadcastSubscriber> subscribers = new ArrayList<>();

  public void putAll(List<PubsubBroadcastSubscriber> newEntries) {
    subscribers.addAll(newEntries);
  }

  public List<PubsubBroadcastSubscriber> getAll() {
    return Collections.unmodifiableList(subscribers);
  }

  public List<PubsubBroadcastSubscriber> withType(String pubsubSystem) {
    return subscribers.stream()
        .filter(subscriber -> subscriber.getPubsubSystem().equals(pubsubSystem))
        .collect(Collectors.toList());
  }
}
