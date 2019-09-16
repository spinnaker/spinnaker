/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v1.security

import io.fabric8.kubernetes.api.model.AuthInfo
import io.fabric8.kubernetes.api.model.Cluster
import io.fabric8.kubernetes.api.model.Context
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.internal.KubeConfigUtils

import java.nio.file.Files

class KubernetesConfigParser {
  static Config parse(String kubeconfigFile, String context, String cluster, String user, List<String> namespaces, Boolean serviceAccount) {
    if (serviceAccount) {
      return withServiceAccount()
    } else {
      return withKubeConfig(kubeconfigFile, context, cluster, user, namespaces)
    }
  }

  static Config withServiceAccount() {
    Config config = new Config()

    boolean serviceAccountCaCertExists = Files.isRegularFile(new File(Config.KUBERNETES_SERVICE_ACCOUNT_CA_CRT_PATH).toPath())
    if (serviceAccountCaCertExists) {
      config.setCaCertFile(Config.KUBERNETES_SERVICE_ACCOUNT_CA_CRT_PATH)
    } else {
      throw new IllegalStateException("Could not find CA cert for service account at $Config.KUBERNETES_SERVICE_ACCOUNT_CA_CRT_PATH")
    }

    try {
      String serviceTokenCandidate = new String(Files.readAllBytes(new File(Config.KUBERNETES_SERVICE_ACCOUNT_TOKEN_PATH).toPath()))
      if (serviceTokenCandidate != null) {
        String error = "Configured service account doesn't have access. Service account may have been revoked."
        config.setOauthToken(serviceTokenCandidate)
        config.getErrorMessages().put(401, "Unauthorized! " + error)
        config.getErrorMessages().put(403, "Forbidden! " + error)
      } else {
        throw new IllegalStateException("Did not find service account token at $Config.KUBERNETES_SERVICE_ACCOUNT_TOKEN_PATH")
      }
    } catch (IOException e) {
      throw new IllegalStateException("Could not read service account token at $Config.KUBERNETES_SERVICE_ACCOUNT_TOKEN_PATH", e)
    }

    return config
  }

  static Config withKubeConfig(String kubeconfigFile, String context, String cluster, String user, List<String> namespaces) {
    def kubeConfig = KubeConfigUtils.parseConfig(new File(kubeconfigFile))
    Config config = new Config()

    String resolvedContext = context ?: kubeConfig.currentContext
    Context currentContext = kubeConfig.contexts.find { NamedContext it ->
      it.name == resolvedContext
    }?.getContext()

    if (!context && !currentContext) {
      throw new IllegalArgumentException("Context $context was not found in $kubeconfigFile".toString())
    }

    currentContext.user = user ?: currentContext.user
    currentContext.cluster = cluster ?: currentContext.cluster
    if (namespaces) {
      currentContext.namespace = namespaces[0]
    } else if (!currentContext.namespace) {
      currentContext.namespace = "default"
    }

    Cluster currentCluster = KubeConfigUtils.getCluster(kubeConfig, currentContext)
    config.setApiVersion("v1") // TODO(lwander) Make config parameter when new versions arrive.
    String httpProxy = System.getenv("HTTP_PROXY") ?: System.getenv("http_proxy")
    String httpsProxy = System.getenv("HTTPS_PROXY") ?: System.getenv("https_proxy")
    String noProxy = System.getenv("NO_PROXY") ?: System.getenv("no_proxy")
    if (httpProxy != null && httpProxy != "") {
      config.setHttpProxy(httpProxy)
    }
    if (httpsProxy != null && httpsProxy != "") {
      config.setHttpsProxy(httpsProxy)
    }
    if (noProxy != null && noProxy != "") {
      String[] noProxyList = noProxy.split(",")
      config.setNoProxy(noProxyList)
    }
    if (currentCluster != null) {
      config.setMasterUrl(currentCluster.getServer() + (currentCluster.getServer().endsWith("/") ? "":  "/"))

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
        config.getErrorMessages().put(403, "Forbidden! User ${currentContext.user} doesn't have permission.".toString())
      }
    }

    return config
  }
}
