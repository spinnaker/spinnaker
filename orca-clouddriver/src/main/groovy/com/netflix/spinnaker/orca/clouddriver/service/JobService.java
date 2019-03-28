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

import com.netflix.spinnaker.orca.clouddriver.config.JobConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.config.PreconfiguredJobStageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JobService {

  @Autowired JobConfigurationProperties jobConfigurationProperties;

  public List<PreconfiguredJobStageProperties> getPreconfiguredStages() {
    if(jobConfigurationProperties.getTitus()==null && jobConfigurationProperties.getKubernetes()==null){
      return Collections.EMPTY_LIST;
    }

    List<PreconfiguredJobStageProperties> preconfiguredJobStageProperties = new ArrayList<>();
    if (jobConfigurationProperties.getTitus() != null && !jobConfigurationProperties.getTitus().isEmpty()) {
      preconfiguredJobStageProperties.addAll(jobConfigurationProperties.getTitus());
    }

    if (jobConfigurationProperties.getKubernetes() != null && !jobConfigurationProperties.getKubernetes().isEmpty()) {
      preconfiguredJobStageProperties.addAll(jobConfigurationProperties.getKubernetes());
    }

    return preconfiguredJobStageProperties;
  }

}
