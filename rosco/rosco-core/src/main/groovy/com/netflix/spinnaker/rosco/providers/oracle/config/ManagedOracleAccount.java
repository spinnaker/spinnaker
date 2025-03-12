/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.rosco.providers.oracle.config;

public class ManagedOracleAccount {
  private String name;
  private String compartmentId;
  private String userId;
  private String fingerprint;
  private String sshPrivateKeyFilePath;
  private String privateKeyPassphrase;
  private String tenancyId;
  private String region;

  public String getCompartmentId() {
    return compartmentId;
  }

  public void setCompartmentId(String compartmentId) {
    this.compartmentId = compartmentId;
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

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
