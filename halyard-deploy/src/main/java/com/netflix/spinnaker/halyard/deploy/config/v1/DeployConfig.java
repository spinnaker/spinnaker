/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.deploy.config.v1;

import com.netflix.spinnaker.halyard.core.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutorLocal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class DeployConfig {
  @Bean
  JobExecutor jobExecutor() {
    return new JobExecutorLocal();
  }

  @Bean
  String dockerRegistry(@Value("${spinnaker.artifacts.dockerRegistry:gcr.io/spinnaker-marketplace}") String dockerRegistry) {
    return dockerRegistry;
  }

  @Bean
  String debianRepository(@Value("${spinnaker.artifacts.debianRepository:https://dl.bintray.com/spinnaker-team/spinnakerbuild}") String debianRepository) {
    return debianRepository;
  }

  @Bean
  String googleImageProject(@Value("${spinnaker.artifacts.googleImageProject:marketplace-spinnaker-release}") String googleImageProject) {
    return googleImageProject;
  }

  @Bean
  String vaultSecretPrefix(@Value("${spinnaker.vault.secretPrefix:secrets/spinnaker/}") String vaultSecretPrefix) {
    return vaultSecretPrefix;
  }

  @Bean
  Integer vaultTimeoutSeconds(@Value("${spinnaker.vault.timeoutSeconds:10}") Integer vaultTimeoutSeconds) {
    return vaultTimeoutSeconds;
  }

  @Bean
  String startupScriptPath(@Value("${spinnaker.startup.scriptsPath:/var/spinnaker/startup/}") String startupScriptPath) {
    return startupScriptPath;
  }
}
