/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.huaweicloud;

public class HuaweiCloudCommandProperties {
  static final String ACCOUNT_TYPE_DESCRIPTION = "The type of account.";

  static final String AUTH_URL_DESCRIPTION = "The auth url of cloud.";

  static final String USERNAME_DESCRIPTION = "The username used to access cloud.";

  static final String PASSWORD_DESCRIPTION =
      "(Sensitive data - user will be prompted on standard input) The password used to access cloud.";

  static final String PROJECT_NAME_DESCRIPTION = "The name of the project within the cloud.";

  static final String DOMAIN_NAME_DESCRIPTION = "The domain name of the cloud.";

  static final String REGIONS_DESCRIPTION = "The region(s) of the cloud.";

  static final String INSECURE_DESCRIPTION =
      "Disable certificate validation on SSL connections. Needed if certificates are self signed. Default false.";

  static final String INSTANCE_TYPE_DESCRIPTION = "The instance type for the baking configuration.";

  static final String SOURCE_IMAGE_ID_DESCRIPTION =
      "The source image ID for the baking configuration.";

  static final String SSH_USER_NAME_DESCRIPTION = "The ssh username for the baking configuration.";

  static final String REGION_DESCRIPTION = "The region for the baking configuration.";

  static final String EIP_TYPE_DESCRIPTION =
      "The eip type for the baking configuration. See the api doc to get its value";
}
