/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.cloudfoundry;

public class CloudFoundryCommandProperties {
  public static final String API_HOST_DESCRIPTION =
      "Host of the CloudFoundry Foundation API endpoint " + "ie. `api.sys.somesystem.com`";
  public static final String APPS_MANAGER_URL_DESCRIPTION =
      "HTTP(S) URL of the Apps Manager application for the"
          + " CloudFoundry Foundation ie. `https://apps.sys.somesystem.com`";
  public static final String METRICS_URL_DESCRIPTION =
      "HTTP(S) URL of the metrics application for the CloudFoundry "
          + "Foundation ie. `https://metrics.sys.somesystem.com`";
  public static final String USER_DESCRIPTION =
      "User name for the account to use on for this CloudFoundry Foundation";
  public static final String PASSWORD_DESCRIPTION =
      "Password for the account to use on for this CloudFoundry Foundation";
  public static final String SKIP_SSL_VALIDATION_DESCRIPTION =
      "Skip SSL server certificate validation of the API endpoint";
  public static final String SPACE_FILTER_DESCRIPTION =
      "Organization and Space filter applied to the Spinnaker CF account";
}
