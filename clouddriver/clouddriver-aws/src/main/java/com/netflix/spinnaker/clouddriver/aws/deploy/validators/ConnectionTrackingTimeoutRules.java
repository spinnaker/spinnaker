/*
 * Copyright 2026 spinnaker.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.aws.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import javax.annotation.Nullable;

/**
 * Range checks for the security group connection tracking idle timeouts that can be set on a launch
 * template's primary network interface.
 *
 * <p>Limits are taken from the EC2 API reference for ConnectionTrackingSpecificationRequest:
 * https://docs.aws.amazon.com/AWSEC2/latest/APIReference/API_ConnectionTrackingSpecificationRequest.html
 */
public final class ConnectionTrackingTimeoutRules {
  static final int TCP_ESTABLISHED_TIMEOUT_MIN = 60;
  static final int TCP_ESTABLISHED_TIMEOUT_MAX = 432000;
  static final int UDP_STREAM_TIMEOUT_MIN = 60;
  static final int UDP_STREAM_TIMEOUT_MAX = 180;
  static final int UDP_TIMEOUT_MIN = 30;
  static final int UDP_TIMEOUT_MAX = 60;

  private ConnectionTrackingTimeoutRules() {}

  /**
   * Rejects any timeout that is set but outside the range EC2 accepts. Unset (null) timeouts are
   * valid and leave the AWS default in place.
   *
   * @param errorCodePrefix prefix for error codes, e.g. {@code basicAmazonDeployDescription}
   */
  public static void validate(
      String errorCodePrefix,
      @Nullable Integer tcpEstablishedTimeout,
      @Nullable Integer udpStreamTimeout,
      @Nullable Integer udpTimeout,
      ValidationErrors errors) {
    reject(
        errorCodePrefix,
        "tcpEstablishedTimeout",
        tcpEstablishedTimeout,
        TCP_ESTABLISHED_TIMEOUT_MIN,
        TCP_ESTABLISHED_TIMEOUT_MAX,
        errors);
    reject(
        errorCodePrefix,
        "udpStreamTimeout",
        udpStreamTimeout,
        UDP_STREAM_TIMEOUT_MIN,
        UDP_STREAM_TIMEOUT_MAX,
        errors);
    reject(errorCodePrefix, "udpTimeout", udpTimeout, UDP_TIMEOUT_MIN, UDP_TIMEOUT_MAX, errors);
  }

  private static void reject(
      String errorCodePrefix,
      String field,
      @Nullable Integer value,
      int min,
      int max,
      ValidationErrors errors) {
    if (value != null && (value < min || value > max)) {
      errors.rejectValue(
          field,
          errorCodePrefix + "." + field + ".invalid",
          field + " must be between " + min + " and " + max + " seconds (was " + value + ")");
    }
  }
}
