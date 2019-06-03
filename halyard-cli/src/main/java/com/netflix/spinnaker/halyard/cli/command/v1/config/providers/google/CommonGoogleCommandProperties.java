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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.google;

public class CommonGoogleCommandProperties {
  public static final String PROJECT_DESCRIPTION =
      "The Google Cloud Platform project this Spinnaker account will manage.";

  public static final String JSON_PATH_DESCRIPTION =
      "The path to a JSON service account that Spinnaker will use as credentials. "
          + "This is only needed if Spinnaker is not deployed on a Google Compute Engine VM, "
          + "or needs permissions not afforded to the VM it is running on. "
          + "See https://cloud.google.com/compute/docs/access/service-accounts for more information.";

  public static final String USER_DATA_DESCRIPTION =
      "The path to user data template file. "
          + "Spinnaker has the ability to inject userdata into generated instance templates. "
          + "The mechanism is via a template file that is token replaced to provide some specifics about the deployment. "
          + "See https://github.com/spinnaker/clouddriver/blob/master/clouddriver-aws/UserData.md for more information.";
}
