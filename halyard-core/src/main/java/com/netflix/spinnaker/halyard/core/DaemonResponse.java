/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.halyard.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.List;
import java.util.function.Supplier;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * This serves to return exceptions encountered during validation that the client can explicitly
 * chose to ignore alongside the desired response.
 *
 * @param <T> is the type of the response expected by the client.
 */
@Slf4j
public class DaemonResponse<T> {
  @Getter @Setter private T responseBody;

  @Getter private final ProblemSet problemSet;

  @JsonCreator
  public DaemonResponse(
      @JsonProperty("responseBody") T responseBody,
      @JsonProperty("problemSet") ProblemSet problemSet) {
    this.responseBody = responseBody;
    this.problemSet = problemSet;
  }

  /** This asks for options if "hypothetically" the update was applied */
  @Data
  public static class UpdateOptionsRequestBuilder {
    private Runnable update;
    private Supplier<FieldOptions> fieldOptionsResponse;
    private Severity severity = Severity.WARNING;

    public DaemonResponse<List<String>> build() {
      try {
        update.run();
        FieldOptions options = fieldOptionsResponse.get();
        return new DaemonResponse<>(options.getOptions(), options.getProblemSet());
      } catch (HalException e) {
        // This is OK, propagate the exception to the HalconfigExceptionHandler
        throw e;
      } catch (Exception e) {
        log.error("Unknown exception encountered: ", e);
        throw e;
      }
    }
  }

  /** This asks for options given the current state of the context */
  @Data
  public static class StaticOptionsRequestBuilder {
    private Supplier<FieldOptions> fieldOptionsResponse;
    private Severity severity = Severity.WARNING;

    public DaemonResponse<List<String>> build() {
      try {
        FieldOptions options = fieldOptionsResponse.get();
        return new DaemonResponse<>(options.getOptions(), options.getProblemSet());
      } catch (HalException e) {
        // This is OK, propagate the exception to the HalconfigExceptionHandler
        throw e;
      } catch (Exception e) {
        log.error("Unknown exception encountered: ", e);
        throw e;
      }
    }
  }

  @Data
  public static class StaticRequestBuilder<K> {
    private Supplier<K> buildResponse;
    private Supplier<ProblemSet> validateResponse;
    private Severity severity = Severity.WARNING;
    private Runnable setup = () -> {};

    public StaticRequestBuilder(Supplier<K> buildResponse) {
      this.buildResponse = buildResponse;
    }

    public DaemonResponse<K> build() {
      if (buildResponse == null) {
        throw new IllegalArgumentException("No response provided to build");
      }

      K responseBody;
      ProblemSet problemSet = new ProblemSet();
      try {
        setup.run();
        if (validateResponse != null) {
          problemSet = validateResponse.get();
        }
        problemSet.throwifSeverityExceeds(severity);
      } catch (Exception e) {
        log.error("Unknown exception encountered: ", e);
        throw e;
      }

      try {
        responseBody = buildResponse.get();
      } catch (HalException e) {
        // This is OK, propagate the exception to the HalconfigExceptionHandler
        e.getProblems().addAll(problemSet);
        throw e;
      } catch (Exception e) {
        log.error("Unknown exception encountered: ", e);
        throw e;
      }

      return new DaemonResponse<>(responseBody, problemSet);
    }
  }

  @Data
  public static class UpdateRequestBuilder {
    private Runnable stage = () -> {};
    private Runnable clean = () -> {};
    private Runnable revert;
    private Runnable save;
    private Runnable update;
    private Supplier<ProblemSet> validate;
    private Severity severity = Severity.WARNING;

    public DaemonResponse<Void> build() {
      ProblemSet result;
      try {
        stage.run();
        update.run();
        result = validate.get();
        result.throwifSeverityExceeds(severity);
      } catch (HalException e) {
        revert.run();
        clean.run();
        throw e;
      } catch (Exception e) {
        log.error("Unknown exception encountered: ", e);
        revert.run();
        clean.run();
        throw e;
      }

      save.run();
      clean.run();

      return new DaemonResponse<>(null, result);
    }
  }

  public interface FieldOptions {
    List<String> getOptions();

    ProblemSet getProblemSet();
  }
}
