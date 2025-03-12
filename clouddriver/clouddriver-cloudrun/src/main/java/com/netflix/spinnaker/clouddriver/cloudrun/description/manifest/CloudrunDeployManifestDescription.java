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
 *
 */

package com.netflix.spinnaker.clouddriver.cloudrun.description.manifest;

import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.AbstractCloudrunCredentialsDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunService;
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.List;
import java.util.Map;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class CloudrunDeployManifestDescription extends AbstractCloudrunCredentialsDescription
    implements DeployDescription {

  private boolean enableTraffic = false;

  private List<Artifact> optionalArtifacts;

  private String cloudProvider;

  private List<CloudrunService> manifests;

  private Map<String, Object> trafficManagement;

  private boolean enableArtifactBinding = true;

  private Moniker moniker;

  private String source;

  private String region;

  private String stack;

  private String details;

  private String versionName;

  private String application;

  private String account;

  private String accountName;

  private boolean skipExpressionEvaluator;

  private List<Artifact> requiredArtifacts;

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public boolean isEnableTraffic() {
    return enableTraffic;
  }

  public void setEnableTraffic(boolean enableTraffic) {
    this.enableTraffic = enableTraffic;
  }

  public List<Artifact> getOptionalArtifacts() {
    return optionalArtifacts;
  }

  public void setOptionalArtifacts(List<Artifact> optionalArtifacts) {
    this.optionalArtifacts = optionalArtifacts;
  }

  public String getCloudProvider() {
    return cloudProvider;
  }

  public void setCloudProvider(String cloudProvider) {
    this.cloudProvider = cloudProvider;
  }

  public List<CloudrunService> getManifests() {
    return manifests;
  }

  public void setManifests(List<CloudrunService> manifests) {
    this.manifests = manifests;
  }

  public Map<String, Object> getTrafficManagement() {
    return trafficManagement;
  }

  public void setTrafficManagement(Map<String, Object> trafficManagement) {
    this.trafficManagement = trafficManagement;
  }

  public Moniker getMoniker() {
    return moniker;
  }

  public void setMoniker(Moniker moniker) {
    this.moniker = moniker;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getAccount() {
    return account;
  }

  public void setAccount(String account) {
    this.account = account;
  }

  public boolean isSkipExpressionEvaluator() {
    return skipExpressionEvaluator;
  }

  public void setSkipExpressionEvaluator(boolean skipExpressionEvaluator) {
    this.skipExpressionEvaluator = skipExpressionEvaluator;
  }

  public List<Artifact> getRequiredArtifacts() {
    return requiredArtifacts;
  }

  public void setRequiredArtifacts(List<Artifact> requiredArtifacts) {
    this.requiredArtifacts = requiredArtifacts;
  }

  public String getStack() {
    return stack;
  }

  public void setStack(String stack) {
    this.stack = stack;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }

  public String getVersionName() {
    return versionName;
  }

  public void setVersionName(String versionName) {
    this.versionName = versionName;
  }

  public String getApplication() {
    return application;
  }

  public void setApplication(String application) {
    this.application = application;
  }

  public boolean isEnableArtifactBinding() {
    return enableArtifactBinding;
  }

  public void setEnableArtifactBinding(boolean enableArtifactBinding) {
    this.enableArtifactBinding = enableArtifactBinding;
  }
}
