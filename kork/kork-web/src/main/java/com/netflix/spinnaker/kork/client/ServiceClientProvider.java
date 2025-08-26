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
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.List;
import okhttp3.Interceptor;
import retrofit2.Converter;

@NonnullByDefault
public interface ServiceClientProvider {
  /**
   * Returns the concrete retrofit service client
   *
   * @param type retrofit interface type
   * @param serviceEndpoint endpoint definition
   * @param <T> type of client , usually an interface with all the remote method definitions.
   * @return the retrofit interface implementation
   */
  public <T> T getService(Class<T> type, ServiceEndpoint serviceEndpoint);

  /**
   * Returns the concrete retrofit service client
   *
   * @param type retrofit interface type
   * @param serviceEndpoint endpoint definition
   * @param objectMapper object mapper for conversion
   * @param <T> type of client , usually an interface with all the remote method definitions.
   * @return the retrofit interface implementation
   */
  public <T> T getService(
      Class<T> type, ServiceEndpoint serviceEndpoint, ObjectMapper objectMapper);

  /**
   * Returns the concrete retrofit service client
   *
   * @param type retrofit interface type
   * @param serviceEndpoint endpoint definition
   * @param converterFactory custom converter factory for conversion
   * @param <T> type of client , usually an interface with all the remote method definitions.
   * @return the retrofit interface implementation
   */
  public <T> T getService(
      Class<T> type, ServiceEndpoint serviceEndpoint, Converter.Factory converterFactory);

  /**
   * Returns the concrete retrofit service client
   *
   * @param type retrofit interface type
   * @param serviceEndpoint endpoint definition
   * @param objectMapper object mapper for conversion
   * @param interceptors list of interceptors
   * @param <T> type of client , usually an interface with all the remote method definitions.
   * @return the retrofit interface implementation
   */
  public <T> T getService(
      Class<T> type,
      ServiceEndpoint serviceEndpoint,
      ObjectMapper objectMapper,
      List<Interceptor> interceptors);
}
