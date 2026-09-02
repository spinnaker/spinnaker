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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.spinnaker.orca.api.preconfigured.jobs.PreconfiguredJobConfigurationProvider;
import com.netflix.spinnaker.orca.api.preconfigured.jobs.PreconfiguredJobStageProperties;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Registers the Run Script Kubernetes preconfigured job stage.
 *
 * <p>Originally contributed as the Armory.PreConfiguredJobPlugin.RunScript plugin
 * (https://github.com/armory-plugins/run-script-plugin). Stage type remains {@code
 * runContainerScript} so existing pipelines keep working.
 */
@Component
@ConditionalOnProperty(
    value = "job.preconfigured.run-script.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RunScriptPreconfiguredJobProvider implements PreconfiguredJobConfigurationProvider {

  static final String JOB_RESOURCE =
      "com/netflix/spinnaker/orca/clouddriver/jobs/run-script-job.yaml";

  private final RunScriptPreconfiguredJobProperties configuration;
  private final ObjectMapper mapper;

  public RunScriptPreconfiguredJobProvider(RunScriptPreconfiguredJobProperties configuration) {
    this.configuration = configuration;
    this.mapper =
        new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  @Override
  public List<? extends PreconfiguredJobStageProperties> getJobConfigurations() {
    KubernetesPreconfiguredJobProperties job = loadJob();
    applyOverrides(configuration, job);
    return Collections.singletonList(job);
  }

  KubernetesPreconfiguredJobProperties loadJob() {
    ClassPathResource resource = new ClassPathResource(JOB_RESOURCE);
    try (InputStream input = resource.getInputStream()) {
      return mapper.readValue(input, KubernetesPreconfiguredJobProperties.class);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load Run Script preconfigured job definition", e);
    }
  }

  static void applyOverrides(
      RunScriptPreconfiguredJobProperties configuration,
      KubernetesPreconfiguredJobProperties jobProperties) {
    if (StringUtils.hasText(configuration.getAccount())) {
      jobProperties.setAccount(configuration.getAccount());
    }
    if (StringUtils.hasText(configuration.getCredentials())) {
      jobProperties.setCredentials(configuration.getCredentials());
    }

    V1Container initContainer =
        jobProperties.getManifest().getSpec().getTemplate().getSpec().getInitContainers().stream()
            .filter(container -> "git".equals(container.getName()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "job manifest must have initContainer with name git"));

    if (StringUtils.hasText(configuration.getInitContainerImage())) {
      initContainer.setImage(configuration.getInitContainerImage());
    }
    if (StringUtils.hasText(configuration.getArtifactServiceUrl())) {
      setEnv(initContainer, "ARTIFACT_SERVICE", configuration.getArtifactServiceUrl());
    }
    if (StringUtils.hasText(configuration.getGitArtifactAccount())) {
      setEnv(initContainer, "ARTIFACT_ACCOUNT", configuration.getGitArtifactAccount());
    }
  }

  private static void setEnv(V1Container container, String name, String value) {
    if (container.getEnv() == null) {
      return;
    }
    for (V1EnvVar envVar : container.getEnv()) {
      if (name.equals(envVar.getName())) {
        envVar.setValue(value);
        return;
      }
    }
  }
}
