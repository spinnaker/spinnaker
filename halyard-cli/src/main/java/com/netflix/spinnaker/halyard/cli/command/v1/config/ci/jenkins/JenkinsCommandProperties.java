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

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci.jenkins;

public class JenkinsCommandProperties {
  static final String USERNAME_DESCRIPTION = "The username of the jenkins user to authenticate as.";

  static final String PASSWORD_DESCRIPTION = "The password of the jenkins user to authenticate as.";

  static final String ADDRESS_DESCRIPTION = "The address your jenkins master is reachable at.";

  static final String CSRF_DESCRIPTION =
      "Whether or not to negotiate CSRF tokens when calling Jenkins.";
}
