/*
 * Copyright 2016 Google, Inc.
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

import io.fabric8.kubernetes.api.model.AuthInfo
import io.fabric8.kubernetes.api.model.Cluster
import io.fabric8.kubernetes.api.model.Context
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.internal.KubeConfigUtils

class KubernetesConfigParser {
  static Config parse(String kubeConfigFile, String cluster, String user, String namespace) {

    def kubeConfig = KubeConfigUtils.parseConfig(new File(kubeConfigFile))
    Config config = new Config()

    Context currentContext = new Context(cluster, null, namespace, user)

    Cluster currentCluster = KubeConfigUtils.getCluster(kubeConfig, currentContext);
    config.setApiVersion("v1") // TODO(lwander) Make config parameter when new versions arrive.
    config.setNoProxy([] as String[])
    if (currentCluster != null) {
      if (!currentCluster.getServer().endsWith("/")) {
        config.setMasterUrl(currentCluster.getServer() + "/")
      }

      config.setNamespace(currentContext.getNamespace())
      config.setTrustCerts(currentCluster.getInsecureSkipTlsVerify() != null && currentCluster.getInsecureSkipTlsVerify())
      config.setCaCertFile(currentCluster.getCertificateAuthority())
      config.setCaCertData(currentCluster.getCertificateAuthorityData())

      AuthInfo currentAuthInfo = KubeConfigUtils.getUserAuthInfo(kubeConfig, currentContext)
        if (currentAuthInfo != null) {
        config.setClientCertFile(currentAuthInfo.getClientCertificate())
        config.setClientCertData(currentAuthInfo.getClientCertificateData())
        config.setClientKeyFile(currentAuthInfo.getClientKey())
        config.setClientKeyData(currentAuthInfo.getClientKeyData())
        config.setOauthToken(currentAuthInfo.getToken())
        config.setUsername(currentAuthInfo.getUsername())
        config.setPassword(currentAuthInfo.getPassword())

        config.getErrorMessages().put(401, "Unauthorized! Token may have expired! Please log-in again.")
        config.getErrorMessages().put(403, "Forbidden! User ${config.getUsername()} doesn't have permission.")
      }
    }

    return config
  }
}
