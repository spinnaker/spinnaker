/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.halyard.config.validate.v1.providers.oracle;

import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.oracle.OracleAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import org.springframework.stereotype.Component;

@Component
public class OracleAccountValidator extends Validator<OracleAccount> {
  @Override
  public void validate(ConfigProblemSetBuilder psBuilder, OracleAccount account) {

    notNullOrEmpty(account.getCompartmentId(), "compartment id", psBuilder);
    notNullOrEmpty(account.getUserId(), "user id", psBuilder);
    notNullOrEmpty(account.getFingerprint(), "fingerprint", psBuilder);
    notNullOrEmpty(account.getSshPrivateKeyFilePath(), "ssh private key file path", psBuilder);
    notNullOrEmpty(account.getTenancyId(), "tenancy id", psBuilder);
    notNullOrEmpty(account.getRegion(), "region", psBuilder);

    // TODO (simonlord): Once BMCS SDK is in maven we can access via
    // spinnaker.dependency("clouddriverOracleBmcs") and test account login
  }

  private void notNullOrEmpty(String param, String paramName, ConfigProblemSetBuilder psBuilder) {
    if (param == null || param.isEmpty()) {
      psBuilder.addProblem(Severity.FATAL, "You must provide a " + paramName);
    }
  }
}
