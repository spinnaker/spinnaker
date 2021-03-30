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
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dockerRegistry;

class DockerRegistryCommandProperties {
  static final String ADDRESS_DESCRIPTION =
      "The registry address you want to pull and deploy images from. "
          + "For example:\n\n"
          + "  index.docker.io     - DockerHub\n"
          + "  quay.io             - Quay\n"
          + "  gcr.io              - Google Container Registry (GCR)\n"
          + "  [us|eu|asia].gcr.io - Regional GCR\n"
          + "  localhost           - Locally deployed registry";

  static final String REPOSITORIES_DESCRIPTION =
      "An optional list of repositories to cache images from. "
          + "If not provided, Spinnaker will attempt to read accessible repositories from the registries _catalog endpoint";

  static final String PASSWORD_DESCRIPTION = "Your docker registry password";

  static final String PASSWORD_COMMAND_DESCRIPTION =
      "Command to retrieve docker token/password, commands must be available in environment";

  static final String PASSWORD_FILE_DESCRIPTION =
      "The path to a file containing your docker password in plaintext "
          + "(not a docker/config.json file)";

  static final String USERNAME_DESCRIPTION = "Your docker registry username";

  static final String EMAIL_DESCRIPTION =
      "Your docker registry email "
          + "(often this only needs to be well-formed, rather than be a real address)";

  static final String CACHE_INTERVAL_SECONDS_DESCRIPTION =
      "How many seconds elapse between polling your docker registry. Certain registries are sensitive to over-polling, and "
          + "larger intervals (e.g. 10 minutes = 600 seconds) are desirable if you're seeing rate limiting.";

  static final String CLIENT_TIMEOUT_MILLIS_DESCRIPTION =
      "Timeout time in milliseconds for this repository.";

  static final String CACHE_THREADS_DESCRIPTION =
      "How many threads to cache all provided repos on. Really only useful if you have a ton of repos.";

  static final String INSECURE_REGISTRY_DESCRIPTION =
      "Treat the docker registry as insecure (don't validate the ssl cert).";

  static final String PAGINATE_SIZE_DESCRIPTION =
      "Paginate size for the docker repository _catalog endpoint.";

  static final String SORT_TAGS_BY_DATE_DESCRIPTION =
      "Sort tags by creation date. Not recommended for use with large "
          + "registries; sorting performance scales poorly due to limitations of the Docker V2 API.";

  static final String TRACK_DIGESTS_DESCRIPTION =
      "Track digest changes. This is not recommended as it consumes a high QPM, and most registries are flaky.";

  static final String REPOSITORIES_REGEX_DESCRIPTION =
      "Allows to specify a Regular Expression to filter the repositories.";
}
