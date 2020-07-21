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

package com.netflix.spinnaker.kork.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.config.ServiceEndpoint;

/** Factory to build a client for a service. */
public interface ServiceClientFactory {

  /**
   * Builds a concrete client capable of making HTTP calls.
   *
   * @param type client type
   * @param serviceEndpoint endpoint configuration
   * @param objectMapper mapper
   * @param <T> type of client , usually a interface with all the remote method definitions.
   * @return a implementation of the type of client given.
   */
  public <T> T create(Class<T> type, ServiceEndpoint serviceEndpoint, ObjectMapper objectMapper);

  /**
   * Decide if this factory can support the endpoint provided.
   *
   * @param type client type
   * @param serviceEndpoint endpoint configuration
   * @return a client builder
   */
  public default boolean supports(Class<?> type, ServiceEndpoint serviceEndpoint) {
    return true;
  }
}
