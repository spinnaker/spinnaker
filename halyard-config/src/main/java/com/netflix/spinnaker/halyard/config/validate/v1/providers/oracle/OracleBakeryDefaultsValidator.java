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
import com.netflix.spinnaker.halyard.config.model.v1.providers.oracle.OracleBakeryDefaults;
import com.netflix.spinnaker.halyard.config.model.v1.providers.oracle.OracleBaseImage;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
@Data
public class OracleBakeryDefaultsValidator extends Validator<OracleBakeryDefaults> {

  @Override
  public void validate(ConfigProblemSetBuilder psBuilder, OracleBakeryDefaults n) {
    DaemonTaskHandler.message(
        "Validating "
            + n.getNodeName()
            + " with "
            + OracleBakeryDefaultsValidator.class.getSimpleName());

    notNullOrEmpty(n.getAvailabilityDomain(), "availability domain", psBuilder);
    notNullOrEmpty(n.getSubnetId(), "subnet id", psBuilder);
    notNullOrEmpty(n.getInstanceShape(), "instance shape", psBuilder);

    List<OracleBaseImage> baseImages = n.getBaseImages();

    OracleBaseImageValidator oracleBaseImageValidator = new OracleBaseImageValidator();

    baseImages.forEach(
        oracleBaseImage -> oracleBaseImageValidator.validate(psBuilder, oracleBaseImage));
  }

  private void notNullOrEmpty(String param, String paramName, ConfigProblemSetBuilder psBuilder) {
    if (param == null || param.isEmpty()) {
      psBuilder.addProblem(Problem.Severity.FATAL, "You must provide a " + paramName);
    }
  }
}
