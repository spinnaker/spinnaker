/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.validator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Errors {
  List<Error> errors = new ArrayList<>();

  public Errors() {
    // empty
  }

  public Errors addError(Error e) {
    errors.add(e);
    return this;
  }

  public boolean hasErrors(boolean includeWarn) {
    if (!includeWarn) {
      return errors.stream().filter(error -> error.severity != Severity.WARN).count() > 0;
    }
    return !errors.isEmpty();
  }

  public Map<String, Object> toResponse() {
    HashMap<String, Object> m = new HashMap<>();
    m.put("errors", errors
      .stream()
      .map(e -> {
        HashMap<String, Object> err = new HashMap<>();
        if (e.message != null) err.put("message", e.message);
        if (e.location != null) err.put("location", e.location);
        if (e.cause != null) err.put("cause", e.cause);
        if (e.suggestion != null) err.put("suggestion", e.suggestion);
        err.put("severity", e.severity);
        return err;
      })
      .collect(Collectors.toList())
    );
    return m;
  }

  public enum Severity {
    FATAL,

    // Warn severity should be used when an error will not end fatally, but should still be addressed
    // Things like best practice / deprecation notices would go here.
    WARN
  }

  public static class Error {
    Severity severity = Severity.FATAL;
    String message;
    String location;
    String cause;
    String suggestion;

    public static Error builder() {
      return new Error();
    }

    public Error withSeverity(Severity severity) {
      this.severity = severity;
      return this;
    }

    public Error withMessage(String message) {
      this.message = message;
      return this;
    }

    public Error withLocation(String location) {
      this.location = location;
      return this;
    }

    public Error withCause(String cause) {
      this.cause = cause;
      return this;
    }

    public Error withSuggestion(String remedy) {
      this.suggestion = remedy;
      return this;
    }
  }
}
