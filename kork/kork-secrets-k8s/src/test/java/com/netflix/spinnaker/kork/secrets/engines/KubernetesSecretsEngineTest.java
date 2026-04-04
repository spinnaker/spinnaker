/*
 * Copyright 2022 OpsMx, Inc.
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
package com.netflix.spinnaker.kork.secrets.engines;

import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class KubernetesSecretsEngineTest {

  private KubernetesSecretEngine secretEngine = new KubernetesSecretEngine();

  K3sContainer k3s = new K3sContainer(DockerImageName.parse("rancher/k3s:v1.35.3-k3s1"));
  CoreV1Api coreV1Api;

  @BeforeEach
  public void setup() throws Exception {
    k3s.start();
    ApiClient client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new StringReader(k3s.getKubeConfigYaml()))).build();
    coreV1Api = new CoreV1Api(client);
    secretEngine.setApiClient(coreV1Api);
  }


  @Test
  void testThatCanGetBasicScret() throws Exception {
    String expectedSecretValue = RandomStringUtils.randomAlphanumeric(16);
    createSecretInNamespace(expectedSecretValue, "default", "somesecret", "secret");
    byte[] secretValue = secretEngine.decrypt(EncryptedSecret.parse("encrypted:k8s!n:somesecret!k:secret"));
    assertThat(new String(secretValue)).isEqualTo(expectedSecretValue);
  }

  @Test
  void testThatCanGetBasicScretInAnotherNamespace() throws Exception {
    String expectedSecretValue = RandomStringUtils.randomAlphanumeric(16);
    V1Namespace namespace = new V1Namespace();
    namespace.setMetadata(new V1ObjectMeta());
    namespace.getMetadata().setName("otherns");
    coreV1Api.createNamespace(namespace, null, null, null, null);
    createSecretInNamespace(expectedSecretValue,"otherns", "somesecret", "secret");

    byte[] secretValue = secretEngine.decrypt(EncryptedSecret.parse("encrypted:k8s!ns:otherns!n:somesecret!k:secret"));
    assertThat(new String(secretValue)).isEqualTo(expectedSecretValue);
  }

  private void createSecretInNamespace(String expectedSecretValue, String namespace, String secretName, String secretKey) throws IOException, ApiException {

    V1Secret secret = new V1Secret();
    secret.setMetadata(new V1ObjectMeta());
    secret.getMetadata().setName(secretName);
    secret.putStringDataItem(secretKey, expectedSecretValue);

    try {
      coreV1Api.createNamespacedSecret(namespace, secret, null, null, null, null);
    } catch (ApiException e) {
      System.err.println("Status code: " + e.getCode());
      System.err.println("Response body: " + e.getResponseBody());
      e.printStackTrace();
      throw e;
    }
  }

}
