/*
 * Copyright 2015 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.front50.model.project;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.front50.model.Timestamped;
import java.util.Collection;

public class Project implements Timestamped {

  private String id;
  private String name;
  private String email;
  private ProjectConfig config = new ProjectConfig();
  private Long updateTs;
  private Long createTs;
  private String lastModifiedBy;

  @Override
  @JsonIgnore
  public Long getLastModified() {
    return updateTs;
  }

  @Override
  public void setLastModified(Long lastModified) {
    this.updateTs = lastModified;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public ProjectConfig getConfig() {
    return config;
  }

  public void setConfig(ProjectConfig config) {
    this.config = config;
  }

  public Long getUpdateTs() {
    return updateTs;
  }

  public void setUpdateTs(Long updateTs) {
    this.updateTs = updateTs;
  }

  public Long getCreateTs() {
    return createTs;
  }

  public void setCreateTs(Long createTs) {
    this.createTs = createTs;
  }

  public String getLastModifiedBy() {
    return lastModifiedBy;
  }

  public void setLastModifiedBy(String lastModifiedBy) {
    this.lastModifiedBy = lastModifiedBy;
  }

  public static class ProjectConfig {

    private Collection<PipelineConfig> pipelineConfigs;
    private Collection<String> applications;
    private Collection<ClusterConfig> clusters;

    public Collection<PipelineConfig> getPipelineConfigs() {
      return pipelineConfigs;
    }

    public void setPipelineConfigs(Collection<PipelineConfig> pipelineConfigs) {
      this.pipelineConfigs = pipelineConfigs;
    }

    public Collection<String> getApplications() {
      return applications;
    }

    public void setApplications(Collection<String> applications) {
      this.applications = applications;
    }

    public Collection<ClusterConfig> getClusters() {
      return clusters;
    }

    public void setClusters(Collection<ClusterConfig> clusters) {
      this.clusters = clusters;
    }
  }

  public static class ClusterConfig {

    private String account;
    private String stack;
    private String detail;
    private Collection<String> applications;

    public String getAccount() {
      return account;
    }

    public void setAccount(String account) {
      this.account = account;
    }

    public String getStack() {
      return stack;
    }

    public void setStack(String stack) {
      this.stack = stack;
    }

    public String getDetail() {
      return detail;
    }

    public void setDetail(String detail) {
      this.detail = detail;
    }

    public Collection<String> getApplications() {
      return applications;
    }

    public void setApplications(Collection<String> applications) {
      this.applications = applications;
    }
  }

  public static class PipelineConfig {

    private String application;
    private String pipelineConfigId;

    public String getApplication() {
      return application;
    }

    public void setApplication(String application) {
      this.application = application;
    }

    public String getPipelineConfigId() {
      return pipelineConfigId;
    }

    public void setPipelineConfigId(String pipelineConfigId) {
      this.pipelineConfigId = pipelineConfigId;
    }
  }
}
