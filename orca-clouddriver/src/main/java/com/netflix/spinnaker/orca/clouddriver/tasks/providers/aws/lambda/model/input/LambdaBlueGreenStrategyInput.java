/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaPipelineArtifact;
import lombok.Builder;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class LambdaBlueGreenStrategyInput extends LambdaBaseStrategyInput {

  String credentials, account, region;
  String appName;
  String functionName;
  String qualifier;
  String latestVersionQualifier;
  String payload;
  String deploymentStrategy;
  String aliasName;
  String aliasDescription;

  private String majorFunctionVersion;
  private String minorFunctionVersion;
  private double weightToMinorFunctionVersion;

  private LambdaPipelineArtifact payloadArtifact;
  private LambdaPipelineArtifact outputArtifact;
  private int timeout;
}
