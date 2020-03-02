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
 */
package com.netflix.spinnaker.kork.plugins.api.httpclient;

import com.netflix.spinnaker.kork.annotations.Beta;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Represents an HTTP request for {@link HttpClient}.
 *
 * <p>TODO(rz): Retry config
 */
@Beta
public class Request {

  /** The name of the request, used for tracing purposes. */
  @Nonnull private String name;

  /** The absolute request URL path. */
  @Nonnull private String path;

  /** The Content-Type of the request. If undefined, "application/json" will be assumed. */
  @Nonnull private String contentType = "application/json";

  /**
   * Any custom headers that should be included in the request.
   *
   * <p>Spinnaker may populate additional headers.
   */
  @Nonnull private Map<String, String> headers = new HashMap<>();

  /** Any query parameters to attach to the request URL. */
  @Nonnull private Map<String, String> queryParams = new HashMap<>();

  /** The request body, if any. */
  private Object body;

  public Request(@Nonnull String name, @Nonnull String path) {
    this.name = name;
    this.path = path;
  }

  @Nonnull
  public String getName() {
    return name;
  }

  @Nonnull
  public String getPath() {
    return path;
  }

  @Nonnull
  public String getContentType() {
    return contentType;
  }

  public Request setContentType(@Nonnull String contentType) {
    this.contentType = contentType;
    return this;
  }

  @Nonnull
  public Map<String, String> getHeaders() {
    return headers;
  }

  public Request setHeaders(@Nonnull Map<String, String> headers) {
    this.headers = headers;
    return this;
  }

  @Nonnull
  public Map<String, String> getQueryParams() {
    return queryParams;
  }

  public Request setQueryParams(@Nonnull Map<String, String> queryParams) {
    this.queryParams = queryParams;
    return this;
  }

  public Object getBody() {
    return body;
  }

  public Request setBody(Object body) {
    this.body = body;
    return this;
  }
}
