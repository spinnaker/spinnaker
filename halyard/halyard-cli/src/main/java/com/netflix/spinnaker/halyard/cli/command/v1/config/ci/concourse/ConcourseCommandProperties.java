/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci.concourse;

public class ConcourseCommandProperties {

  static final String USERNAME_DESCRIPTION =
      "The username of the concourse user to authenticate as.";

  static final String PASSWORD_DESCRIPTION =
      "The password of the concourse user to authenticate as.";

  static final String URL_DESCRIPTION = "The url your concourse search is reachable at.";
}
