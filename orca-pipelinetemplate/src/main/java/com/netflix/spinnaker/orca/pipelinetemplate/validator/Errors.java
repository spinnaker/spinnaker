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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Errors {
  @JsonProperty
  List<Error> errors = new ArrayList<>();

  public Errors() {
    // empty
  }

  public Errors add(Error e) {
    errors.add(e);
    return this;
  }

  public Errors addAll(Errors errors) {
    this.errors.addAll(errors.errors);
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
      .map(Error::toResponse)
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
    Errors nestedErrors;
    Map<String, String> details = new HashMap<>();

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

    public Error withNested(Errors errors) {
      this.nestedErrors = errors;
      return this;
    }

    public Error withSuggestion(String remedy) {
      this.suggestion = remedy;
      return this;
    }

    public Error withDetail(String key, String value) {
      details.put(key, value);
      return this;
    }

    public Severity getSeverity() {
      return severity;
    }

    public String getMessage() {
      return message;
    }

    public String getLocation() {
      return location;
    }

    public String getCause() {
      return cause;
    }

    public String getSuggestion() {
      return suggestion;
    }

    public Map<String, String> getDetails() {
      return details;
    }

    public Errors getNestedErrors() {
      return nestedErrors;
    }

    public Map<String, Object> toResponse() {
      HashMap<String, Object> err = new HashMap<>();
      err.put("severity", severity);
      if (message != null) err.put("message", message);
      if (location != null) err.put("location", location);
      if (cause != null) err.put("cause", cause);
      if (suggestion != null) err.put("suggestion", suggestion);
      if (!details.isEmpty()) err.put("details", details);
      if (nestedErrors != null && !nestedErrors.errors.isEmpty()) {
        err.put("nestedErrors", nestedErrors.errors.stream().map(Error::toResponse).collect(Collectors.toList()));
      }
      return err;
    }
  }
}
