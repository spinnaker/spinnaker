/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.halyard.config.model.v1.providers.oracle;

import com.netflix.spinnaker.halyard.config.model.v1.node.HasImageProvider;

public class OracleProvider extends HasImageProvider<OracleAccount, OracleBakeryDefaults> {
  @Override
  public ProviderType providerType() {
    return ProviderType.ORACLE;
  }

  @Override
  public OracleBakeryDefaults emptyBakeryDefaults() {
    OracleBakeryDefaults result = new OracleBakeryDefaults();
    result.setTemplateFile("oci.json");
    return result;
  }

  public static OracleProvider mergeOracleBMCSProvider(
      OracleProvider oracle, OracleBMCSProvider bmcs) {
    if (oracle.getPrimaryAccount() == null && bmcs.getPrimaryAccount() != null) {
      return convertFromOracleBMCSProvider(bmcs);
    } else {
      return oracle;
    }
  }

  private static OracleProvider convertFromOracleBMCSProvider(OracleBMCSProvider bmcs) {
    OracleProvider provider = new OracleProvider();
    provider.setEnabled(bmcs.isEnabled());
    provider.setAccounts(bmcs.getAccounts());
    provider.setPrimaryAccount(bmcs.getPrimaryAccount());
    return provider;
  }
}
