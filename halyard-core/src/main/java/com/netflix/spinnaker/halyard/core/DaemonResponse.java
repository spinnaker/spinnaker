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

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigException;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSet;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * This serves to return exceptions encountered during validation that the client can explicitly chose to ignore alongside the desired response.
 * @param <T> is the type of the response expected by the client.
 */
@Slf4j
public class DaemonResponse<T> {
  @Getter
  private T responseBody;

  @Getter
  private ProblemSet problemSet;

  public DaemonResponse(T responseBody, ProblemSet problemSet) {
    this.responseBody = responseBody;
    this.problemSet = problemSet;
  }

  @Data
  public static class StaticRequestBuilder<K> {
    private Supplier<K> buildResponse;
    private Supplier<ProblemSet> validateResponse;

    public DaemonResponse<K> build() {
      if (buildResponse == null) {
        throw new IllegalArgumentException("No response provided to build");
      }

      K responseBody;
      ProblemSet problemSet;
      try {
        responseBody = buildResponse.get();
        problemSet = new ProblemSet();
        if (validateResponse != null) {
          problemSet = validateResponse.get();
        }
      } catch (HalconfigException e) {
        // This is OK, propagate the exception to the HalconfigExceptionHandler
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
    private HalconfigParser halconfigParser;
    private Runnable update;
    private Supplier<ProblemSet> validate;

    public DaemonResponse<Void> build() {
      ProblemSet result;
      try {
        update.run();
        result = validate.get();
      } catch (HalconfigException e) {
        halconfigParser.undoChanges();
        throw e;
      } catch (Exception e) {
        halconfigParser.undoChanges();
        log.error("Unknown exception encountered: ", e);
        throw e;
      }

      halconfigParser.saveConfig();
      return new DaemonResponse<>(null, result);
    }
  }
}
