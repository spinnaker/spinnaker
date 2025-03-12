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

import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;
import javax.annotation.Nonnull;

/**
 * An application event listener for Spinnaker events that can be utilized by plugins.
 *
 * @param <E> the type of event the listener is for
 */
@FunctionalInterface
public interface SpinnakerEventListener<E extends SpinnakerApplicationEvent>
    extends SpinnakerExtensionPoint {

  /**
   * Handles an event of type {@link E}.
   *
   * @param event the inbound event
   */
  void onApplicationEvent(@Nonnull E event);
}
