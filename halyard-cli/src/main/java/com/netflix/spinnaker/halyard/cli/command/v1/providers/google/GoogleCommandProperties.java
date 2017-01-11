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

package com.netflix.spinnaker.halyard.cli.command.v1.providers.google;

class GoogleCommandProperties {
  static final String PROJECT_DESCRIPTION = "The Google Cloud Platform project this Spinnaker account will manage.";

  static final String JSON_PATH_DESCRIPTION = "The path to a JSON service account that Spinnaker will use as credentials. "
      + "This is only needed if Spinnaker is not deployed on a Google Compute Engine VM, "
      + "or needs permissions not afforded to the VM it is running on. "
      + "See https://cloud.google.com/compute/docs/access/service-accounts for more information.";

  static final String IMAGE_PROJECTS_DESCRIPTION = "A list of Google Cloud Platform projects Spinnaker will be able to cache and deploy images from. "
      + "When this is omitted, it defaults to the current project.";

  static final String ALPHA_LISTED_DESCRIPTION = "Enable this flag if your project has access to alpha features "
      + "and you want Spinnaker to take advantage of them.";

}
