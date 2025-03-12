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
package com.netflix.spinnaker.kork.plugins.api.events;

import javax.annotation.Nullable;

/** The base type for all Spinnaker application events that are accessible by plugins. */
public interface SpinnakerApplicationEvent {

  /**
   * The originating object that created the event.
   *
   * <p>IMPORTANT: Service developers should exercise caution setting this value, as it could leak
   * an internal service class, establishing an unwanted implicit contract with plugin developers.
   */
  @Nullable
  Object getSource();

  /** The timestamp (epoch milliseconds) when the event was created. */
  long getTimestamp();
}
