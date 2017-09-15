/*
 * Copyright 2017 Cisco, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.security

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.SSLUtils;

import groovy.util.logging.Slf4j

import javax.net.ssl.KeyManager;

@Slf4j
public class KubernetesApiClientConfig extends Config {
  String kubeconfigFile

  public KubernetesApiClientConfig(String kubeconfigFile) {
    this.kubeconfigFile = kubeconfigFile
  }

  public ApiClient getApiCient() throws Exception {
    return (kubeconfigFile ? fromConfig(kubeconfigFile) : Config.defaultClient())
  }
}
