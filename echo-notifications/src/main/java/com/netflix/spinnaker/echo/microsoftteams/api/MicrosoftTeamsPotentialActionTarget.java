/*
 * Copyright 2020 Cerner Corporation
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

package com.netflix.spinnaker.echo.microsoftteams.api;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MicrosoftTeamsPotentialActionTarget {
  public String os;
  public String uri;

  public MicrosoftTeamsPotentialActionTarget(String uri) {
    // Potential action target can support multiple operating systems
    // Values can be default, iOS, android, and windows
    // As of right now, only default would be supported, but if mobile support is
    // ever added, the other target os types can be implemented here
    this.os = "default";
    this.uri = uri;
  }
}
