/*
 * Copyright 2025 Wise, PLC.
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming;

import javax.annotation.Nullable;

/** Exceptions handler for KubernetesQueueProcessor. */
public interface KubernetesQueueProcessorExceptionHandler<T> {
  /**
   * Handle an exception that occurred during processing of an item.
   *
   * @param item the item that caused the exception, or null if not applicable
   * @param e the exception that occurred
   */
  void handle(@Nullable T item, Exception e);
}
