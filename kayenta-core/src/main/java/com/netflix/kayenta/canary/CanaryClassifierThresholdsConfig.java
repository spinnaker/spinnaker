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

package com.netflix.kayenta.canary;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.*;

@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel(description = "Sets thresholds for canary score.")
public class CanaryClassifierThresholdsConfig {

  @ApiModelProperty(
      value = "If canary score is higher than this value -- canary is considered successful.",
      example = "75.0")
  @NotNull
  @Getter
  private Double pass;

  @ApiModelProperty(
      value = "If canary score is lower than this value -- canary is considered marginal (failed).",
      example = "50.0")
  @NotNull
  @Getter
  private Double marginal;
}
