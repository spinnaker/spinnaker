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
package com.netflix.spinnaker.kato.aws.model

import com.google.common.collect.ImmutableMap
import groovy.transform.Canonical

@Canonical
class ResultByZone<T> {
  final ImmutableMap<String, T> successfulResults
  final ImmutableMap<String, String> failures

  static <R> ResultByZone<R> of(Map<String, R> successfulResults, Map<String, String> failures) {
    new ResultByZone(ImmutableMap.copyOf(successfulResults), ImmutableMap.copyOf(failures))
  }

  static class Builder<T> {
    private final Map<String, T> successfulResults = [:]
    private final Map<String, Exception> failures = [:]

    void addSuccessfulResult(String zone, T result) {
      successfulResults[zone] = result
    }

    void addFailure(String zone, Exception failure) {
      failures[zone] = failure
    }

    ResultByZone<T> build() {
      def failureStrings = failures.collectEntries { String key, Exception exception ->
        def stringWriter = new StringWriter()
        exception.printStackTrace(new PrintWriter(stringWriter))
        [(key): stringWriter.toString()]
      }
      of(ImmutableMap.copyOf(successfulResults), ImmutableMap.copyOf(failureStrings))
    }
  }
}
