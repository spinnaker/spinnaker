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
import com.netflix.spinnaker.halyard.config.model.v1.providers.oracle.OracleBaseImage;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.util.StringUtils;

@EqualsAndHashCode(callSuper = false)
@Data
public class OracleBaseImageValidator extends Validator<OracleBaseImage> {

  @Override
  public void validate(ConfigProblemSetBuilder p, OracleBaseImage n) {
    DaemonTaskHandler.message(
        "Validating "
            + n.getNodeName()
            + " with "
            + OracleBaseImageValidator.class.getSimpleName());

    OracleBaseImage.OracleVirtualizationSettings vs = n.getVirtualizationSettings();
    String baesImageId = vs.getBaseImageId();
    String sshUserName = vs.getSshUserName();

    if (StringUtils.isEmpty(baesImageId)) {
      p.addProblem(Problem.Severity.ERROR, "No base image id supplied for oracle base image.");
    }

    if (StringUtils.isEmpty(sshUserName)) {
      p.addProblem(Problem.Severity.ERROR, "No ssh username supplied for oracle base image.");
    }
  }
}
