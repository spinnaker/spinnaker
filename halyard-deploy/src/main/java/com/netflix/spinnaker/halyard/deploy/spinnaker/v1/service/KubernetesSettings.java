/*
 * Copyright 2017 Target, Inc.
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class KubernetesSettings {
  List<String> imagePullSecrets = new ArrayList<>();
  Map<String, String> nodeSelector = new HashMap<>();
  Map<String, String> podAnnotations = new HashMap<>();
  Map<String, String> podLabels = new HashMap<>();
  Map<String, String> serviceLabels = new HashMap<>();
  Map<String, String> serviceAnnotations = new HashMap<>();
  List<ConfigSource> volumes = new ArrayList<>();
  String serviceAccountName = null;
  String serviceType = "ClusterIP";
  String nodePort = null;
  Boolean useExecHealthCheck = true;
  List<String> customHealthCheckExecCommands = new ArrayList<>();
  Boolean useTcpProbe = false;
  KubernetesSecurityContext securityContext = null;
  DeploymentStrategy deploymentStrategy = null;
}
