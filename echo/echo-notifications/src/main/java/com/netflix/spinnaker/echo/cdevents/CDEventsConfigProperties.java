/*
    Copyright (C) 2024 Nordix Foundation.
    For a full list of individual contributors, please see the commit history.
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
          http://www.apache.org/licenses/LICENSE-2.0
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    SPDX-License-Identifier: Apache-2.0
*/

package com.netflix.spinnaker.echo.cdevents;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cdevents")
public class CDEventsConfigProperties {
  /** Enable CDEvents notifications. */
  private boolean enabled = false;

  /**
   * Transport protocol: "http" (default, sends CloudEvents over HTTP) or "otlp" (sends as OTel
   * spans via OTLP/gRPC).
   */
  private String transport = "http";

  /** OTLP exporter timeout in seconds. */
  private long otlpTimeoutSeconds = 10;

  /** Path to PEM file containing trusted CA certificate(s) for verifying the OTLP collector. */
  private String otlpCaCertPath;

  /** Path to PEM file containing the client certificate for mTLS. */
  private String otlpClientCertPath;

  /** Path to PEM file containing the client private key for mTLS. Must be PKCS8 format. */
  private String otlpClientKeyPath;
}
