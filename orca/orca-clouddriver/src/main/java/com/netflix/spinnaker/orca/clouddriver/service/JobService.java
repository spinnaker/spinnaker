/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.service;

import static java.util.stream.Collectors.toList;

import com.netflix.spinnaker.orca.api.preconfigured.jobs.PreconfiguredJobConfigurationProvider;
import com.netflix.spinnaker.orca.api.preconfigured.jobs.PreconfiguredJobStageProperties;
import com.netflix.spinnaker.orca.clouddriver.config.JobConfigurationProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class JobService {

  private final JobConfigurationProperties jobConfigurationProperties;

  private final ObjectProvider<List<PreconfiguredJobConfigurationProvider>>
      preconfiguredJobConfigurationProviders;

  @Autowired
  JobService(
      JobConfigurationProperties jobConfigurationProperties,
      ObjectProvider<List<PreconfiguredJobConfigurationProvider>>
          preconfiguredJobConfigurationProviders) {
    this.jobConfigurationProperties = jobConfigurationProperties;
    this.preconfiguredJobConfigurationProviders = preconfiguredJobConfigurationProviders;
  }

  public List<PreconfiguredJobStageProperties> getPreconfiguredStages() {

    List<PreconfiguredJobStageProperties> preconfiguredJobStageProperties = new ArrayList<>();
    preconfiguredJobStageProperties.addAll(jobConfigurationProperties.getTitus());
    preconfiguredJobStageProperties.addAll(jobConfigurationProperties.getKubernetes());

    List<PreconfiguredJobConfigurationProvider> providers =
        preconfiguredJobConfigurationProviders.getIfAvailable(ArrayList::new);

    // Also load job configs that are provided via extension implementations(plugins)
    if (!providers.isEmpty()) {

      List<PreconfiguredJobStageProperties> jobStageProperties =
          providers.stream().flatMap(obj -> obj.getJobConfigurations().stream()).collect(toList());

      jobStageProperties.forEach(
          properties -> {
            if (properties.isValid()) {
              preconfiguredJobStageProperties.add(properties);
            } else {
              log.warn(
                  "Pre Configured Job configuration provided via plugin is not valid: {} : {}",
                  properties.getLabel(),
                  properties.getType());
            }
          });
    }

    return preconfiguredJobStageProperties;
  }
}
