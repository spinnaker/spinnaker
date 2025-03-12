/*
 * Copyright 2022 OpsMx Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudrun.deploy.description;

import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import groovy.transform.AutoClone;
import groovy.transform.Canonical;
import java.util.List;

@AutoClone
@Canonical
public class DeployCloudrunDescription extends AbstractCloudrunCredentialsDescription
    implements DeployDescription {

  String accountName;
  String region;
  String application;
  List<String> configFiles;
  Boolean promote;
  Boolean stopPreviousVersion;
  String applicationDirectoryRoot;
  Boolean suppressVersionString;

  String stack;

  String freeFormDetails;

  String versionName;

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  public String getApplication() {
    return application;
  }

  public void setApplication(String application) {
    this.application = application;
  }

  public List<String> getConfigFiles() {
    return configFiles;
  }

  public void setConfigFiles(List<String> configFiles) {
    this.configFiles = configFiles;
  }

  public Boolean getPromote() {
    return promote;
  }

  public void setPromote(Boolean promote) {
    this.promote = promote;
  }

  public Boolean getStopPreviousVersion() {
    return stopPreviousVersion;
  }

  public void setStopPreviousVersion(Boolean stopPreviousVersion) {
    this.stopPreviousVersion = stopPreviousVersion;
  }

  public String getApplicationDirectoryRoot() {
    return applicationDirectoryRoot;
  }

  public void setApplicationDirectoryRoot(String applicationDirectoryRoot) {
    this.applicationDirectoryRoot = applicationDirectoryRoot;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public Boolean getSuppressVersionString() {
    return suppressVersionString;
  }

  public void setSuppressVersionString(Boolean suppressVersionString) {
    this.suppressVersionString = suppressVersionString;
  }

  public String getStack() {
    return stack;
  }

  public void setStack(String stack) {
    this.stack = stack;
  }

  public String getFreeFormDetails() {
    return freeFormDetails;
  }

  public void setFreeFormDetails(String freeFormDetails) {
    this.freeFormDetails = freeFormDetails;
  }

  public String getVersionName() {
    return versionName;
  }

  public void setVersionName(String versionName) {
    this.versionName = versionName;
  }
}
