/*
 * Copyright 2024 Wise Ltd.
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

package com.netflix.spinnaker.clouddriver.docker.registry.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DockerRegistryConfigurationPropertiesTest {

  @Test
  void managedAccountIsComparable() {
    DockerRegistryConfigurationProperties.ManagedAccount account1 = createAccount("docker");
    DockerRegistryConfigurationProperties.ManagedAccount account2 = createAccount("docker");
    DockerRegistryConfigurationProperties.ManagedAccount account3 = createAccount("docker2");

    assertThat(account1).isEqualTo(account2);
    assertThat(account1).isNotEqualTo(account3);
  }

  DockerRegistryConfigurationProperties.ManagedAccount createAccount(String name) {
    DockerRegistryConfigurationProperties.ManagedAccount account =
        new DockerRegistryConfigurationProperties.ManagedAccount();
    account.setName(name);
    account.setEnvironment("production");
    account.setAccountType("dockerRegistry");
    account.setUsername("docker-user");
    account.setPassword("test-password");
    account.setAddress("hub.docker.com");
    account.setCacheThreads(5);
    account.setCacheIntervalSeconds(6);
    account.setClientTimeoutMillis(700);
    account.setRepositories(List.of("repo-1", "repo-2"));
    return account;
  }
}
