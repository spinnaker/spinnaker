/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci.wercker;

public class WerckerCommandProperties {
  static final String USER_DESCRIPTION = "The username of the Wercker user to authenticate as.";

  static final String TOKEN_DESCRIPTION =
      "The personal token of the Wercker user to authenticate as.";

  static final String ADDRESS_DESCRIPTION = "The address your Wercker master is reachable at.";
}
