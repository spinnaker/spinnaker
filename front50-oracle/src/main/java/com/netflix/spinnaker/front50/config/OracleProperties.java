/*
 * Copyright (c) 2017, 2018 Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.front50.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spinnaker.oracle")
public class OracleProperties {

  private String bucketName = "_spinnaker_front50_data";
  private String namespace;
  private String compartmentId;
  private String region = "us-phoenix-1";
  private String userId;
  private String fingerprint;
  private String sshPrivateKeyFilePath;
  private String privateKeyPassphrase;
  private String tenancyId;

  public String getBucketName() {
    return bucketName;
  }

  public void setBucketName(String bucketName) {
    this.bucketName = bucketName;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getCompartmentId() {
    return compartmentId;
  }

  public void setCompartmentId(String compartmentId) {
    this.compartmentId = compartmentId;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }

  public String getSshPrivateKeyFilePath() {
    return sshPrivateKeyFilePath;
  }

  public void setSshPrivateKeyFilePath(String sshPrivateKeyFilePath) {
    this.sshPrivateKeyFilePath = sshPrivateKeyFilePath;
  }

  public String getPrivateKeyPassphrase() {
    return privateKeyPassphrase;
  }

  public void setPrivateKeyPassphrase(String privateKeyPassphrase) {
    this.privateKeyPassphrase = privateKeyPassphrase;
  }

  public String getTenancyId() {
    return tenancyId;
  }

  public void setTenancyId(String tenancyId) {
    this.tenancyId = tenancyId;
  }
}
