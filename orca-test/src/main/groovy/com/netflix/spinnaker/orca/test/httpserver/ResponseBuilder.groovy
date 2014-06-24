/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.test.httpserver

import groovy.json.JsonBuilder

interface ResponseBuilder {

  /**
   * @param status the HTTP status of the response.
   * @return this object to facilitate chaining method calls.
   */
  ResponseBuilder withStatus(int status)

  /**
   * @param name the name of an HTTP response header that should be sent with the response.
   * @param value the value of the HTTP response header.
   * @return this object to facilitate chaining method calls.
   */
  ResponseBuilder withHeader(String name, String value)

  /**
   * @param responseHeaders any HTTP response headers that should be sent with the response.
   * @return this object to facilitate chaining method calls.
   */
  ResponseBuilder withHeaders(Map<String, String> headers)

  /**
   * @param content a <em>Closure</em> used to construct a response using {@link groovy.json.JsonBuilder}.
   * @return this object to facilitate chaining method calls.
   */
  ResponseBuilder withJsonContent(@DelegatesTo(JsonBuilder) Closure closure)
}
