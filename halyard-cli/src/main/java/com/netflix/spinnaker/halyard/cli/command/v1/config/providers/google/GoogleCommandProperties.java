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

class GoogleCommandProperties {
  static final String IMAGE_PROJECTS_DESCRIPTION = "A list of Google Cloud Platform projects Spinnaker will be able to cache and deploy images from. "
      + "When this is omitted, it defaults to the current project.";

  static final String ALPHA_LISTED_DESCRIPTION = "Enable this flag if your project has access to alpha features "
      + "and you want Spinnaker to take advantage of them.";
}
