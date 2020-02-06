/*
 * Copyright 2020 Amazon.com, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci.codebuild;

public class AwsCodeBuildCommandProperties {
  static final String ACCOUNT_ID_DESCRIPTION =
      "The AWS account ID that will be used to trigger CodeBuild build.";
  static final String REGION_DESCRIPTION = "The AWS region in which your CodeBuild projects live.";
  static final String ASSUME_ROLE_DESCRIPTION =
      "If set, Halyard will configure a credentials provider that uses AWS "
          + "Security Token Service to assume the specified role.\n\n"
          + "Example: \"user/spinnaker\" or \"role/spinnakerManaged\"";
}
