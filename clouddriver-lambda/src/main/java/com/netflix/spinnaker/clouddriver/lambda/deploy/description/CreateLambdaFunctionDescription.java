/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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

package com.netflix.spinnaker.clouddriver.lambda.deploy.description;

import com.amazonaws.services.lambda.model.DeadLetterConfig;
import com.amazonaws.services.lambda.model.TracingConfig;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class CreateLambdaFunctionDescription extends AbstractLambdaFunctionDescription {
  String functionName;
  String description;
  String s3bucket;
  String s3key;
  String handler;
  String role;
  String runtime;
  String appName;

  Integer memorySize;
  Integer timeout;

  Map<String, String> tags;

  Boolean publish;

  Map<String, String> envVariables;
  List<String> subnetIds;
  List<String> securityGroupIds;

  String targetGroups;

  DeadLetterConfig deadLetterConfig;
  TracingConfig tracingConfig;
  String kmskeyArn;
}
