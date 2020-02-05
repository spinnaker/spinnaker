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

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci.gcb;

public class GoogleCloudBuildCommandProperties {
  static final String PROJECT_DESCRIPTION =
      "The name of the GCP project in which to trigger and monitor builds.";

  static final String JSON_KEY_DESCRIPTION =
      "The path to a JSON service account that Spinnaker will use as credentials.";

  static final String SUBSCRIPTION_NAME_DESCRIPTION =
      "The name of the PubSub subscription on which to listen for build changes.";
}
