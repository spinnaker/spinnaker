/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.canary.aws;

public class CommonCanaryAwsCommandProperties {
  public static final String REGION_DESCRIPTION = "The region to use.";

  public static final String PROFILE_NAME_DESCRIPTION =
      "The profile name to use when resolving AWS credentials. Typically found in ~/.aws/credentials (*Default*: `default`).";

  public static final String ENDPOINT_DESCRIPTION =
      "The endpoint used to reach the service implementing the AWS api. Typical use is with Minio.";

  public static final String ACCESS_KEY_ID_DESCRIPTION =
      "The default access key used to communicate with AWS.";

  public static final String SECRET_KEY_DESCRIPTION =
      "The secret key used to communicate with AWS.";
}
