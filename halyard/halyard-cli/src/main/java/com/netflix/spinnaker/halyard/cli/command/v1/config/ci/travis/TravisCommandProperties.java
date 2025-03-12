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
  static final String ADDRESS_DESCRIPTION =
      "The address of the travis API (https://api.travis-ci.org).";

  static final String BASE_URL_DESCRIPTION =
      "The base URL to the travis UI (https://travis-ci.org).";

  static final String GITHUB_TOKEN_DESCRIPTION =
      "The github token to authentiacte against travis with.";

  static final String NUMBER_OF_REPOSITORIES_DESCRIPTION =
      "This property is no longer in use for Spinnaker >= 1.17 and the value will be ignored."
          + " If you want to limit the number of builds retrieved per polling cycle, you can use the property"
          + " --number-of-jobs. Set this property only if you use Spinnaker < 1.17."
          + " Specifies how many repositories the travis integration should"
          + " fetch from the api each time the poller runs. Should be set a bit higher than the expected maximum number of"
          + " repositories built within the poll interval.";

  static final String NUMBER_OF_JOBS_DESCRIPTION =
      "Defines how many jobs the Travis integration should retrieve per polling cycle. Defaults to 100."
          + " Used for spinnaker >= 1.17.";

  static final String BUILD_RESULT_LIMIT_DESCRIPTION =
      "Defines how many builds Igor should return when querying for builds for a specific repo. This"
          + " affects for instance how many builds that will be displayed in the drop down when starting a"
          + " manual execution of a pipeline. If set too high, the Travis API might return an error for"
          + " jobs that writes a lot of logs, which is why the default setting is a bit conservative."
          + " Defaults to 10."
          + " Used for spinnaker >= 1.17.";

  static final String FILTERED_REPOSITORIES_DESCRIPTION =
      "Defines the list of repositories that will be scraped. Useful if the organization has a lot of"
          + " repositories and you wish to speed things up by scanning only a subset.";
}
