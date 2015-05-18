/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kato.gce.deploy.ops.loadbalancer

class Constants {
  private static final String HEALTH_CHECK_NAME_PREFIX = "hc"

  // These match the values described in the GCE client library documentation.
  private static final String  DEFAULT_IP_PROTOCOL = "TCP"
  private static final String  DEFAULT_PORT_RANGE = "1-65535"
  private static final Integer DEFAULT_CHECK_INTERVAL_SEC = 5
  private static final Integer DEFAULT_HEALTHY_THRESHOLD = 2
  private static final Integer DEFAULT_UNHEALTHY_THRESHOLD = 2
  private static final Integer DEFAULT_PORT = 80
  private static final Integer DEFAULT_TIMEOUT_SEC = 5
  private static final String  DEFAULT_REQUEST_PATH = "/"
}
