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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account;

public class AccountCommandProperties {
  public static final String REQUIRED_GROUP_MEMBERSHIP_DESCRIPTION =
      "A user must be a member of at least one specified group in order to make changes to this account's cloud resources.";

  public static final String READ_PERMISSION_DESCRIPTION =
      "A user must have at least one of these roles in order to view this account's cloud resources.";

  public static final String WRITE_PERMISSION_DESCRIPTION =
      "A user must have at least one of these roles in order to make changes to this account's cloud resources.";

  public static final String ENVIRONMENT_DESCRIPTION =
      "The environment name for the account. Many accounts can share the same environment (e.g. dev, test, prod)";
}
