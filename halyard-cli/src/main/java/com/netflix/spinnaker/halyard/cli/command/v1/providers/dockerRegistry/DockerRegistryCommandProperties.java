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

package com.netflix.spinnaker.halyard.cli.command.v1.providers.dockerRegistry;

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

  static final String PASSWORD_FILE_DESCRIPTION =
      "The path to a file containing your docker password in plaintext "
          + "(not a docker/config.json file)";

  static final String USERNAME_DESCRIPTION = "Your docker registry username";

  static final String EMAIL_DESCRIPTION =
      "Your docker registry email "
          + "(often this only needs to be well-formed, rather than be a real address)";
}
