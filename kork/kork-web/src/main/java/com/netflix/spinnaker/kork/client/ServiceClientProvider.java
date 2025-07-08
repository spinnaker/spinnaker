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

@NonnullByDefault
public interface ServiceClientProvider {

  /**
   * An enumeration of the supported retrofit versions.
   *
   * <p>The value passed here should match the annotations on the interface Class type passed in to
   * getService() methods.
   *
   * <ul>
   *   <li>{@link #RETROFIT1} corresponds to retrofit 1.x annotations like {@code retrofit.http.GET}
   *       and {@code retrofit.http.PUT}.
   *   <li>{@link #RETROFIT2} corresponds to retrofit 2.x annotations like {@code
   *       retrofit2.http.GET} and {@code retrofit2.http.PUT}.
   * </ul>
   */
  enum RetrofitVersion {
    RETROFIT1,
    RETROFIT2
  }

  /**
   * Returns the concrete retrofit service client
   *
   * @param type retrofit interface type
   * @param serviceEndpoint endpoint definition
   * @param <T> type of client , usually an interface with all the remote method definitions.
   * @param version the retrofit version
   * @return the retrofit interface implementation
   */
  public <T> T getService(Class<T> type, ServiceEndpoint serviceEndpoint, RetrofitVersion version);

  /**
   * Returns the concrete retrofit service client
   *
   * <p>This method behaves the same as {@link #getService(Class, ServiceEndpoint, RetrofitVersion)}
   * but the retrofit version will be inferred from the implementation.
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
   * @param version the retrofit version
   * @return the retrofit interface implementation
   */
  public <T> T getService(
      Class<T> type,
      ServiceEndpoint serviceEndpoint,
      ObjectMapper objectMapper,
      RetrofitVersion version);

  /**
   * Returns the concrete retrofit service client
   *
   * <p>This method behaves the same as {@link #getService(Class, ServiceEndpoint, ObjectMapper,
   * RetrofitVersion)} but the retrofit version will be inferred from the implementation.
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
   * @param objectMapper object mapper for conversion
   * @param interceptors list of interceptors
   * @param <T> type of client , usually an interface with all the remote method definitions.
   * @param version the retrofit version
   * @return the retrofit interface implementation
   */
  public <T> T getService(
      Class<T> type,
      ServiceEndpoint serviceEndpoint,
      ObjectMapper objectMapper,
      List<Interceptor> interceptors,
      RetrofitVersion version);

  /**
   * Returns the concrete retrofit service client
   *
   * <p>This method behaves the same as {@link #getService(Class, ServiceEndpoint, ObjectMapper,
   * List, RetrofitVersion)} but the retrofit version will be inferred from the implementation.
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
