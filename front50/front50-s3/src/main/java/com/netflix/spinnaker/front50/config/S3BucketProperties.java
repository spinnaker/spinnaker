/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.front50.config;

import com.netflix.spinnaker.front50.model.S3StorageService.ServerSideEncryption;

public class S3BucketProperties {
  private String bucket;
  private String region;
  private String endpoint;
  private String proxyHost;
  private String proxyPort;
  private String proxyProtocol;
  private Boolean payloadSigning = false; // disabled by default
  private Boolean versioning = true; // enabled by default
  private Boolean pathStyleAccess = true; // enable by default
  private ServerSideEncryption serverSideEncryption; // options are "AWSKMS" and "AES256"

  public String getBucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    this.bucket = bucket;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getProxyHost() {
    return proxyHost;
  }

  public void setProxyHost(String proxyHost) {
    this.proxyHost = proxyHost;
  }

  public String getProxyPort() {
    return proxyPort;
  }

  public void setProxyPort(String proxyPort) {
    this.proxyPort = proxyPort;
  }

  public String getProxyProtocol() {
    return proxyProtocol;
  }

  public void setProxyProtocol(String proxyProtocol) {
    this.proxyProtocol = proxyProtocol;
  }

  public Boolean getPayloadSigning() {
    return payloadSigning;
  }

  public void setPayloadSigning(Boolean payloadSigning) {
    this.payloadSigning = payloadSigning;
  }

  public Boolean getVersioning() {
    return versioning;
  }

  public void setVersioning(Boolean versioning) {
    this.versioning = versioning;
  }

  public Boolean getPathStyleAccess() {
    return pathStyleAccess;
  }

  public void setPathStyleAccess(Boolean pathStyleAccess) {
    this.pathStyleAccess = pathStyleAccess;
  }

  public ServerSideEncryption getServerSideEncryption() {
    return serverSideEncryption;
  }

  public void setServerSideEncryption(ServerSideEncryption serverSideEncryption) {
    this.serverSideEncryption = serverSideEncryption;
  }
}
