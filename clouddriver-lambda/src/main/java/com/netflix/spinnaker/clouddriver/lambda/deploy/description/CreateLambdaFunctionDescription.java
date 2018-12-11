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

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class CreateLambdaFunctionDescription extends AbstractLambdaFunctionDescription {
  String functionName;
  String description;
  String s3Bucket;
  String s3Key;
  String handler;
  String role;
  String runtime;

  Integer memory;
  Integer timeout;

  List<Map<String,String>> tags;

  Boolean publish;
}
