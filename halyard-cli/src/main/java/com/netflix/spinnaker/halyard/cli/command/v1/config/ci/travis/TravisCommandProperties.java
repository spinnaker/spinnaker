/*
 * Copyright 2017 Schibsted ASA.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci.travis;

public class TravisCommandProperties {
  static final String ADDRESS_DESCRIPTION = "The address of the travis API (https://api.travis-ci.org).";

  static final String BASE_URL_DESCRIPTION = "The base URL to the travis UI (https://travis-ci.org).";

  static final String GITHUB_TOKEN_DESCRIPTION = "The github token to authentiacte against travis with.";

}
