/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.deploymentmonitor.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvaluateHealthResponse {
  /**
   * Directive the deployment monitor sets to indicate to monitored deploy strategy as to how to
   * proceed with the deploy
   */
  public enum NextStepDirective {
    /**
     * Abort the deployment (rollback will be initiated if requested by the user in the stage
     * config)
     */
    @JsonProperty("abort")
    ABORT,

    /** Complete the deployment (i.e. skip to 100% right away) */
    @JsonProperty("complete")
    COMPLETE,

    /**
     * Continue deployment as per default logic - this is the default option if none is specified
     */
    @JsonProperty("continue")
    CONTINUE,

    /** The health can't be determined yet, wait and retry in a little bit (~30s) */
    @JsonProperty("wait")
    WAIT,

    /**
     * The value wasn't specified - this not a value that the monitor is expected to return and will
     * cause regular exception path in the processing of the response
     */
    UNSPECIFIED
  }

  private DeploymentStep nextStep;
  private List<StatusReason> statusReasons;
}
