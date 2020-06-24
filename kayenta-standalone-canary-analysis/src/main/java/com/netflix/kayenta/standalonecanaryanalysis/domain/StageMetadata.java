/*
 * Copyright (c) 2018 Nike, inc.
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

package com.netflix.kayenta.standalonecanaryanalysis.domain;

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@ApiModel(
    description =
        "Metadata around an Orca StageExecution of the canary analysis pipeline execution")
public class StageMetadata {
  @NotNull
  @ApiModelProperty(value = "The StageExecution type")
  String type;

  @NotNull
  @ApiModelProperty(value = "The StageExecution name")
  String name;

  @NotNull
  @ApiModelProperty(value = "The Orca execution status of the stage")
  ExecutionStatus status;

  @ApiModelProperty(value = "The execution id if the StageExecution is a runCanary stage")
  String executionId;
}
