/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.rosco.providers.oracle.config;

import java.util.List;

public class OracleConfigurationProperties {
  private List<ManagedOracleAccount> accounts;

  public List<ManagedOracleAccount> getAccounts() {
    return accounts;
  }

  public void setAccounts(List<ManagedOracleAccount> accounts) {
    this.accounts = accounts;
  }
}
