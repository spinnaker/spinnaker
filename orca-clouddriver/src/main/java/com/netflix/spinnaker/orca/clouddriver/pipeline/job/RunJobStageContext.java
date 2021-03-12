/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.orca.clouddriver.pipeline.job;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.orca.clouddriver.pipeline.job.model.JobStatus;
import java.util.HashMap;
import java.util.Map;

public class RunJobStageContext {
  private JobStatus jobStatus;
  private String cloudProvider;
  private String cloudProviderType;
  private String application;
  private String credentials;

  @JsonIgnore private Map<String, Object> other = new HashMap<>();

  @JsonAnyGetter
  public Map<String, Object> other() {
    return other;
  }

  @JsonAnySetter
  public void set(String name, Object value) {
    other.put(name, value);
  }

  public JobStatus getJobStatus() {
    return jobStatus;
  }

  public void setJobStatus(JobStatus jobStatus) {
    this.jobStatus = jobStatus;
  }

  public String getCloudProvider() {
    return cloudProvider;
  }

  public void setCloudProvider(String cloudProvider) {
    this.cloudProvider = cloudProvider;
  }

  public String getCloudProviderType() {
    return cloudProviderType;
  }

  public void setCloudProviderType(String cloudProviderType) {
    this.cloudProviderType = cloudProviderType;
  }

  public String getApplication() {
    return application;
  }

  public void setApplication(String application) {
    this.application = application;
  }

  public String getCredentials() {
    return credentials;
  }

  public void setCredentials(String credentials) {
    this.credentials = credentials;
  }
}
