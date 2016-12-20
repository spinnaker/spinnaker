/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigException;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSet;
import java.util.function.Supplier;
import lombok.Data;
import lombok.Getter;

/**
 * This serves to return exceptions encountered during validation that the client can explicitly chose to ignore alongside the desired response.
 * @param <T> is the type of the response expected by the client.
 */
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
      K responseBody = null;
      if (buildResponse == null) {
        throw new IllegalArgumentException("No response provided to build");
      } else {
        responseBody = buildResponse.get();
      }

      ProblemSet problemSet = new ProblemSet();
      if (validateResponse != null) {
        problemSet = validateResponse.get();
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
      }

      halconfigParser.saveConfig();
      return new DaemonResponse<>(null, result);
    }
  }
}
