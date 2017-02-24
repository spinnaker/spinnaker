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

  static final String SOURCE_IMAGE_DESCRIPTION = "The source image. If both source image and source image family are set, source image will take precedence.";

  static final String SOURCE_IMAGE_FAMILY_DESCRIPTION = "The source image family to create the image from. The newest, non-deprecated image is used.";

  static final String IS_IMAGE_FAMILY_DESCRIPTION = "todo(duftler) I couldn't find a description on the packer website of what this is.";
}
