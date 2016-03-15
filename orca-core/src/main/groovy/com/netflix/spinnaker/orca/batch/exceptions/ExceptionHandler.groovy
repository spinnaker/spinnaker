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


package com.netflix.spinnaker.orca.batch.exceptions

import groovy.transform.Canonical

interface ExceptionHandler<T extends Exception> {
  boolean handles(Exception e)

  Response handle(String taskName, T e)

  @Canonical
  static class Response {
    private final Date timestamp = new Date()
    String exceptionType
    String operation
    ResponseDetails details
    boolean shouldRetry = false


    @Override
    public String toString() {
      return "Response{" +
        "timestamp=" + timestamp +
        ", exceptionType='" + exceptionType + '\'' +
        ", operation='" + operation + '\'' +
        ", details=" + details +
        '}';
    }
  }

  @Canonical
  static class ResponseDetails {
    @Delegate
    Map<String, Object> delegate = [:]

    ResponseDetails(String error, List<String> errors = []) {
      put("error", error)
      put("errors", errors)
    }
  }
}
