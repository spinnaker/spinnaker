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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery;

public class BakeryCommandProperties {
  static final String IMAGE_ID_DESCRIPTION =
      "This is the identifier used by your cloud to find this base image.";

  static final String SHORT_DESCRIPTION_DESCRIPTION =
      "A short description to help human operators identify the image.";

  static final String DETAILED_DESCRIPTION_DESCRIPTION =
      "A long description to help human operators identify the image.";

  static final String PACKAGE_TYPE_DESCRIPTION =
      "This is used to help Spinnaker's bakery download the build artifacts you supply it with. "
          + "For example, specifying 'deb' indicates that your artifacts will need to be fetched from a debian repository.";

  public static final String TEMPLATE_FILE_DESCRIPTION =
      "This is the name of the packer template that will be used to bake images from "
          + "this base image. The template file must be found in this list https://github.com/spinnaker/rosco/tree/master/rosco-web/config/packer, or "
          + "supplied as described here: https://spinnaker.io/setup/bakery/";
}
