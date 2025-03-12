/*
 * Copyright 2020 Grab Holdings, Inc.
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
 */

package com.netflix.spinnaker.rosco.manifests.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("helm")
@Data
public class RoscoHelmConfigurationProperties {
  private String v3ExecutablePath = "helm3";
  private String v2ExecutablePath = "helm";
  /**
   * The threshold for determining whether to include overrides directly in the Helm command or
   * write them to a separate file. If the total length of override values is less than this
   * threshold or if threshold is zero, they will be included in the Helm command using "--set" or
   * "--set-string". Otherwise, they will be written to a file, and the file path will be included
   * in the Helm command using "--values".
   */
  private int overridesFileThreshold = 0;
}
