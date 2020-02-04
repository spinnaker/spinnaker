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
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.*;

@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel(description = "Judge configuration.")
public class CanaryJudgeConfig {

  @ApiModelProperty(
      value = "Judge to use, as of right now there is only `NetflixACAJudge-v1.0`.",
      example = "NetflixACAJudge-v1.0",
      required = true)
  @NotNull
  @Getter
  private String name;

  @ApiModelProperty(
      value =
          "Additional judgement configuration. As of right now, this should always be an empty object.",
      example = "{}",
      required = true)
  @NotNull
  @Singular
  @Getter
  private Map<String, Object> judgeConfigurations;
}
