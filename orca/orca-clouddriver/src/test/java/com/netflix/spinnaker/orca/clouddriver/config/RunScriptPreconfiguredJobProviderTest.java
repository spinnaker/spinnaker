/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import org.junit.jupiter.api.Test;

class RunScriptPreconfiguredJobProviderTest {

  @Test
  void loadsRunScriptYaml() {
    RunScriptPreconfiguredJobProvider provider =
        new RunScriptPreconfiguredJobProvider(new RunScriptPreconfiguredJobProperties());

    KubernetesPreconfiguredJobProperties job = provider.loadJob();

    assertThat(job.getType()).isEqualTo("runContainerScript");
    assertThat(job.getAccount()).isNotNull();
    assertThat(job.getManifest()).isNotNull();
    assertThat(job.getManifest().getSpec().getTemplate().getSpec().getInitContainers())
        .isNotEmpty();
    assertThat(job.getManifest().getSpec().getTemplate().getSpec().getContainers()).isNotEmpty();
  }

  @Test
  void appliesConfigurationOverrides() {
    RunScriptPreconfiguredJobProperties configuration = new RunScriptPreconfiguredJobProperties();
    configuration.setAccount("test-account");
    configuration.setCredentials("test-creds");
    configuration.setArtifactServiceUrl("http://my-test");
    configuration.setInitContainerImage("myrepo/fetch-artifact:latest");
    configuration.setGitArtifactAccount("gitrepo-test");

    RunScriptPreconfiguredJobProvider provider =
        new RunScriptPreconfiguredJobProvider(configuration);
    KubernetesPreconfiguredJobProperties job =
        (KubernetesPreconfiguredJobProperties) provider.getJobConfigurations().get(0);

    assertThat(job.getAccount()).isEqualTo("test-account");
    assertThat(job.getCredentials()).isEqualTo("test-creds");

    V1Container init =
        job.getManifest().getSpec().getTemplate().getSpec().getInitContainers().stream()
            .filter(container -> "git".equals(container.getName()))
            .findFirst()
            .orElseThrow();
    assertThat(init.getImage()).isEqualTo("myrepo/fetch-artifact:latest");
    assertThat(env(init, "ARTIFACT_SERVICE")).isEqualTo("http://my-test");
    assertThat(env(init, "ARTIFACT_ACCOUNT")).isEqualTo("gitrepo-test");
  }

  private static String env(V1Container container, String name) {
    return container.getEnv().stream()
        .filter(variable -> name.equals(variable.getName()))
        .map(V1EnvVar::getValue)
        .findFirst()
        .orElse(null);
  }
}
