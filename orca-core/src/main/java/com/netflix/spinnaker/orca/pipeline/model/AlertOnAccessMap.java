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

import com.google.common.collect.ForwardingMap;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlertOnAccessMap extends ForwardingMap<String, Object> {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final Execution execution;
  private final Map<String, Object> delegate;
  private final Registry registry;

  public AlertOnAccessMap(Execution execution, Registry registry, Map<String, Object> delegate) {
    this.execution = execution;
    this.registry = registry;
    this.delegate = delegate;
  }

  public AlertOnAccessMap(Execution execution, Registry registry) {
    this(execution, registry, new HashMap<>());
  }

  @Override
  protected Map<String, Object> delegate() {
    return delegate;
  }

  @Override
  public Object get(@Nullable Object key) {
    Object value = super.get(key);
    if (value != null) {
      log.warn(
          "Global context key \"{}\" accessed by {} {}[{}] \"{}\"",
          key,
          execution.getApplication(),
          execution.getType(),
          execution.getId(),
          Optional.ofNullable(execution.getName()).orElseGet(execution::getDescription));
      Id counterId =
          registry
              .createId("global.context.access")
              .withTag("application", execution.getApplication())
              .withTag("key", String.valueOf(key));
      registry.counter(counterId).increment();
    }
    return value;
  }
}
