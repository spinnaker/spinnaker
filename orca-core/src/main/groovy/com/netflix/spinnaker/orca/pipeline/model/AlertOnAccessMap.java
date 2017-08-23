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

package com.netflix.spinnaker.orca.pipeline.model;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import com.google.common.collect.ForwardingMap;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;

class AlertOnAccessMap<E extends Execution<E>> extends ForwardingMap<String, Object> {

  private final Map<String, Object> delegate;
  private final Registry registry;
  private final Id counterId;

  AlertOnAccessMap(Execution<E> execution, Registry registry, Map<String, Object> delegate) {
    this.registry = registry;
    this.delegate = delegate;
    counterId = registry
      .createId("global.context.access")
      .withTag("execution", execution.getId())
      .withTag("application", execution.getApplication())
      .withTag("name", execution.getName());
  }

  AlertOnAccessMap(Execution<E> execution, Registry registry) {
    this(execution, registry, new HashMap<>());
  }

  @Override protected Map<String, Object> delegate() { return delegate; }

  @Override public Object get(@Nullable Object key) {
    registry
      .counter(counterId.withTag("key", String.valueOf(key)))
      .increment();
    return super.get(key);
  }
}
