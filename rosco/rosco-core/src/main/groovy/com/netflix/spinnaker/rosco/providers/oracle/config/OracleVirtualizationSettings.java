/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.rosco.providers.oracle.config;

public class OracleVirtualizationSettings {
  private String baseImageId;

  private String sshUserName;

  public String getBaseImageId() {
    return baseImageId;
  }

  public void setBaseImageId(String baseImageId) {
    this.baseImageId = baseImageId;
  }

  public String getSshUserName() {
    return sshUserName;
  }

  public void setSshUserName(String sshUserName) {
    this.sshUserName = sshUserName;
  }
}
