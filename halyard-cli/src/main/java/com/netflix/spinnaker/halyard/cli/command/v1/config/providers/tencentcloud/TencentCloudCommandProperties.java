/*
 * Copyright 2020 THL A29 Limited, a Tencent company.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.tencentcloud;

public class TencentCloudCommandProperties {

  static final String SECRET_ID_DESCRIPTION = "The secret id used to access Tencent Cloud.";

  static final String SECRET_KEY_DESCRIPTION =
      "(Sensitive data - user will be prompted on standard input) The secret key used to access Tencent Cloud.";

  static final String REGIONS_DESCRIPTION =
      "The Tencent CLoud regions this Spinnaker account will manage.";

  static final String ADD_REGION_DESCRIPTION = "Add this region to the list of managed regions.";

  static final String REMOVE_REGION_DESCRIPTION =
      "Remove this region from the list of managed regions.";

  static final String INSTANCE_TYPE_DESCRIPTION = "The instance type for the baking configuration.";

  static final String SOURCE_IMAGE_ID_DESCRIPTION =
      "The source image ID for the baking configuration.";

  static final String SSH_USER_NAME_DESCRIPTION = "The ssh username for the baking configuration.";

  static final String REGION_DESCRIPTION = "The region for the baking configuration.";

  static final String ZONE_DESCRIPTION = "The zone for the baking configuration.";
}
